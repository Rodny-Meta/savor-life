package com.savor.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.savor.dto.Result;
import com.savor.entity.SeckillVoucher;
import com.savor.entity.VoucherOrder;
import com.savor.mapper.VoucherOrderMapper;
import com.savor.service.ISeckillVoucherService;
import com.savor.service.IVoucherOrderService;
import com.savor.utils.RedisConstants;
import com.savor.utils.RedisIdWorker;
import com.savor.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 自己的代理对象（异步消费端调用事务方法用）。
     * 为什么不用 AopContext.currentProxy()：它依赖 ThreadLocal，只在"请求线程经过代理调用"时才有值，
     * 后台线程是裸起的，直接抛 IllegalStateException。
     * 为什么加 @Lazy：自注入发生在 Bean 创建期，懒代理延迟到"首次调用"才解析目标 Bean，
     * 那时容器早已启动完毕，拿到的必然是携带事务能力的最终代理，无循环依赖争议
     */
    @Resource
    @Lazy
    private IVoucherOrderService selfProxy;

    /** 秒杀资格判断脚本：lua/seckill.lua，原子执行「判库存→判一人一单→预扣库存→记录用户」 */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * Stream 消费者组名/消费者名。
     * 8081/8082 两个实例共用同组同名消费者：组内消息分流给不同实例并行消费，同一消息只投递一次
     */
    private static final String STREAM_GROUP = "g1";
    private static final String STREAM_CONSUMER = "c1";

    /** 订单落库线程池：每实例一条消费线程，写库压力从秒杀峰值削平 */
    private static final ExecutorService ORDER_CONSUMER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seckill-order-consumer");
        t.setDaemon(false);
        return t;
    });

    /**
     * 项目启动后：创建消费者组 + 开启后台线程持续监听 stream.orders
     *
     * 消费者组的意义（对比需求4阻塞队列的两大缺陷）：
     * - 消息在 Redis 里持久化 → JVM 宕机/重启消息不丢（解决"数据安全"缺陷）
     * - 内存不再背队列 → 无内存上限与队列满溢出问题（解决"内存限制"缺陷）
     * 消费确认机制：消息被读走后进入 pending-list，处理完成必须 XACK 才移出；
     * 处理失败的消息留在 pending，宕机重启后可重新读取 → 至少被消费一次
     */
    @PostConstruct
    public void initStreamConsumer() {
        // 幂等建组：从第一条消息(0)开始消费；组已存在时 Redis 返回 BUSYGROUP，忽略即可
        try {
            stringRedisTemplate.opsForStream().createGroup(RedisConstants.STREAM_ORDERS_KEY, ReadOffset.from("0"), STREAM_GROUP);
            log.info("Stream消费者组 {} 创建成功", STREAM_GROUP);
        } catch (RedisSystemException e) {
            log.info("Stream消费者组已存在（重启场景），跳过创建");
        }
        ORDER_CONSUMER.submit(() -> {
            // 启动先清一次 pending：处理上次宕机/异常遗留的未确认消息（"0"读pending，读空即返回），
            // 再进入正常消费主循环——否则重启后主循环只读">"新消息，遗留消息永远无人认领
            handlePendingMessages();
            while (true) {
                try {
                    // 读新消息：XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    // ">" = 从未消费的下一个消息开始；BLOCK 2000 = 无消息时阻塞2s（不空转烧CPU）
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(RedisConstants.STREAM_ORDERS_KEY, ReadOffset.lastConsumed()));
                    if (records == null || records.isEmpty()) {
                        continue; // 阻塞超时，无新消息，下一轮
                    }
                    // 解析消息 → 落库 → ACK
                    handleStreamMessage(records.get(0));
                } catch (Exception e) {
                    // 消费异常（Redis抖动/落库报错）：该消息未ACK，留在 pending-list，
                    // 进入 pending 重试通道，确保"至少消费一次"
                    log.error("Stream订单消费异常，转pending重试", e);
                    handlePendingMessages();
                }
            }
        });
    }

    /**
     * 处理一条Stream消息：Map还原订单对象 → 落库 → XACK确认
     * 只有落库动作完成才 ACK；锁被占则不 ACK，消息留在 pending-list 等待重试
     */
    private void handleStreamMessage(MapRecord<String, Object, Object> record) {
        // 消息体 = Lua XADD 写入的键值对 {voucherId, userId, orderId}
        Map<Object, Object> values = record.getValue();
        VoucherOrder order = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
        // 坑：消息字段名是orderId（消息语义），实体字段名是id——fillBeanWithMap按名匹配不上，
        // id不会被自动填充（其余voucherId/userId与实体字段同名，可自动映射），必须手动补这一位
        order.setId(Long.parseLong(values.get("orderId").toString()));
        boolean done = handleVoucherOrder(order);
        if (done) {
            // 手动确认：消息从 pending-list 移除（消息本体仍在Stream中，支持回溯）
            stringRedisTemplate.opsForStream().acknowledge(RedisConstants.STREAM_ORDERS_KEY, STREAM_GROUP, record.getId());
        } else {
            log.error("订单消息处理被搁置（锁被占），消息{}留在pending待重试", record.getId());
        }
    }

    /**
     * pending-list 重试通道：处理"已读取但未确认"的消息
     * 触发场景：消费者处理中途宕机（重启后组内标识继续），或消费过程抛异常
     * "0" = 从 pending-list 第一条未确认消息开始读取；读空说明全部确认完毕
     */
    private void handlePendingMessages() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(STREAM_GROUP, STREAM_CONSUMER),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(RedisConstants.STREAM_ORDERS_KEY, ReadOffset.from("0")));
                if (records == null || records.isEmpty()) {
                    break; // pending 清空，回正常消费主循环
                }
                handleStreamMessage(records.get(0));
            } catch (Exception e) {
                // 毒消息会反复失败：记日志继续重试（生产环境应加重试上限+转入死信，避免无限循环）
                log.error("pending订单消息重试失败", e);
                continue;
            }
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询秒杀券信息（tb_seckill_voucher 主键即 voucher_id）
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("秒杀券不存在");
        }
        // 2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        // 3. 判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        // 4. 提前生成订单id：Lua 通过后直接随消息写入 Stream，响应给用户的
        //    和异步落库的是同一个id（前端可立即用它轮询订单状态）
        long orderId = redisIdWorker.nextId("order");
        Long userId = UserHolder.getUser().getId();
        // 5. 秒杀资格判断 + 订单入队：Lua 在 Redis 端原子执行
        //    「判库存 → 判一人一单 → 预扣库存 → 记录用户 → XADD 消息进 stream.orders」
        //
        //    为什么不需要分布式锁了：上一版用 Redisson 锁（userId粒度）串行化"查重+下单"，
        //    现在 sismember+sadd 在同一个 Lua 脚本里原子执行——同一用户的并发请求，
        //    第一个 sadd 后，第二个的 sismember 必然看到它，直接返回2（不能重复下单）。
        //    判断与记录之间不存在间隙，锁的使命被更轻量的原子脚本取代
        //
        //    对比需求4阻塞队列版：入队动作从 Java 的 offer 挪进 Lua 里 XADD——
        //    资格判断与入队之间不再有间隙，且消息持久化在 Redis，JVM 宕机也不丢
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(RedisConstants.SECKILL_STOCK_KEY + voucherId,
                        RedisConstants.SECKILL_ORDER_KEY + voucherId,
                        RedisConstants.STREAM_ORDERS_KEY),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));
        if (result == null || result.intValue() != 0) {
            // 1=库存不足（Redis预扣阶段拦下） 2=重复下单（一人一单set拦下）
            return Result.fail(result != null && result.intValue() == 1 ? "库存不足！" : "不能重复下单！");
        }
        // 6. 资格通过：订单消息已随 Lua 原子进队，秒杀请求到此结束。
        //    用户立刻拿到 orderId，真正的数据库写入由 Stream 消费线程异步完成（削峰）
        return Result.ok(orderId);
    }

    /**
     * Stream消费线程：处理一条订单消息（写数据库）
     * 加 userId 粒度的 Redisson 锁做幂等兜底：万一同一用户的消息被重复投递
     * （Redis 预扣后异常重试等场景），锁+DB查重双保险，不会写两单
     *
     * @return true=消息已处理完毕（无论落库/查重拦截/库存不足，都是终态，可XACK）
     *         false=锁被占（他人正在处理同一用户），消息不ACK留在pending-list等重试
     */
    private boolean handleVoucherOrder(VoucherOrder order) {
        Long userId = order.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("异步下单：用户{}获取锁失败，订单{}暂不处理", userId, order.getId());
            return false;
        }
        try {
            selfProxy.createVoucherOrder(order);
            // 走到这=事务方法正常返回：可能是落库成功，也可能是查重/库存不足的终态拦截
            // ——重试也不会改变结果，都属于"处理完毕"，返回true让上游ACK
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 1. 一人一单（DB兜底查重）：Redis 的 seckill:order set 是主拦截，
        //    但历史订单/异常场景下 set 可能缺失，数据库查重是最后防线
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        Long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            log.error("异步下单：用户{}对券{}重复下单，DB查重拦截", userId, voucherId);
            return;
        }
        // 2. 扣减库存（乐观锁：一条 UPDATE 把「判断 stock>0 + 扣减」原子化）
        //    Redis 预扣是第一道闸门，这里是数据库真扣减 + 最终防线
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // where voucher_id = ? and stock > 0
                .update();
        if (!success) {
            log.error("异步下单：券{}库存不足，乐观锁拦截（Redis预扣与DB库存的极小窗口不一致）", voucherId);
            return;
        }
        // 3. 落库（订单id在入队前已生成——响应里给用户的和落库的是同一个id）
        save(voucherOrder);
    }
}
