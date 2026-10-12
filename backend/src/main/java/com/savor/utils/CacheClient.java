package com.savor.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.savor.utils.RedisConstants.CACHE_NULL_TTL;
import static com.savor.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.savor.utils.RedisConstants.LOCK_SHOP_TTL;

/**
 * 基于 StringRedisTemplate 的通用缓存工具类
 *
 * 覆盖场景：
 * - 写：set（TTL超时剔除）/ setWithLogicalExpire（逻辑过期，防击穿）
 * - 读：queryWithPassThrough（空值缓存，防穿透）
 *       queryWithLogicalExpire（逻辑过期+异步重建，防击穿）
 *       queryWithMutex（互斥锁重建，防击穿 + 兼防穿透）
 * - 锁：tryLock / unLock（SETNX + 过期时间，一条原子命令防死锁）
 */
@Slf4j
@Component
public class CacheClient {

    /** 逻辑过期方案专用的缓存重建线程池（异步重建，不阻塞请求线程） */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /* ==================== 写方法 ==================== */

    /**
     * 方法1：将任意Java对象序列化为JSON存入string类型key，并设置TTL（超时剔除）
     * 【防雪崩】TTL 在基础值上加随机偏移（基础TTL的 0~20%）：
     * 大量key在同一时刻集中创建、集中过期，过期瞬间的高并发会全部打向数据库
     * 加随机后每个key的过期时刻自然错开，把"同一秒的雪崩"摊薄成"一段时间内的零星miss"
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        // 如 30min → 实际 30~36min 过期，每个key各不相同
        long randomOffset = RandomUtil.randomLong(0, Math.max(1, time / 5));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time + randomOffset, unit);
    }

    /**
     * 空值缓存的TTL：基础2min + 0~2min随机
     * 空值也可能被批量制造（攻击者同时打一批不存在id），同样要防雪崩
     */
    private Long nullValueTtl() {
        return CACHE_NULL_TTL + RandomUtil.randomLong(0, 2);
    }

    /**
     * 方法2：将任意Java对象序列化为JSON存入string类型key，并设置逻辑过期时间（防击穿方案二）
     * key本身永不过期，过期时间存在value里（RedisData.expireTime），到期后由查询方触发异步重建
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /* ==================== 读方法 ==================== */

    /**
     * 方法3：查询缓存并反序列化为指定类型，用【缓存空值】解决缓存穿透
     * 库中不存在 → 写入空字符串（短TTL兜底），拦截后续同key请求
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 2. 命中空值（之前缓存过的""）→ 数据库没有，直接返回null
        if (json != null) {
            return null;
        }
        // 3. 缓存未命中 → 查库
        R r = dbFallback.apply(id);
        if (r == null) {
            // 4. 库中不存在 → 缓存空值（短TTL+随机，防穿透兼防雪崩），防止同一不存在key反复打库
            stringRedisTemplate.opsForValue().set(key, "", nullValueTtl(), TimeUnit.MINUTES);
            return null;
        }
        // 5. 库中存在 → 回填缓存
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 方法4：查询缓存并反序列化为指定类型，用【逻辑过期】解决缓存击穿
     * 未过期 → 直接返回；过期 → 拿锁异步重建，期间返回旧值（牺牲一致性换可用性）
     * 注意：依赖预热保证key一定存在，miss时直接返回null
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查缓存（逻辑过期方案key永不过期，miss说明没预热）
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 2. 反序列化：外层是RedisData（data + expireTime）
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 3. 未到逻辑过期时间 → 直接返回
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return r;
        }
        // 4. 已过期 → 尝试拿锁，拿到就异步重建（拿不到说明别人在重建，直接返回旧值）
        String lockKey = LOCK_SHOP_KEY + id;
        if (tryLock(lockKey)) {
            log.info("逻辑过期：线程{}拿到锁，开始异步重建缓存 {}", Thread.currentThread().getName(), key);
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R dbR = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, dbR, time, unit);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        // 5. 无论谁重建，当前请求都返回旧值（可用性优先）
        return r;
    }

    /**
     * 方法5（额外封装）：查询缓存并反序列化为指定类型，用【互斥锁】解决缓存击穿（兼防穿透）
     * 拿到锁 → 双检缓存 → 查库回填；拿不到 → 休眠50ms后重查缓存，直到查到或拿到锁
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null; // 命中空值 → 防穿透
        }
        // 2. miss → 互斥锁重建
        String lockKey = LOCK_SHOP_KEY + id;
        while (true) {
            if (tryLock(lockKey)) {
                log.info("互斥锁：线程{}拿到锁{}，开始重建缓存", Thread.currentThread().getName(), lockKey);
                try {
                    // 3. 双检：拿锁等待过程中，缓存可能已被别的线程重建
                    String again = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(again)) {
                        return JSONUtil.toBean(again, type);
                    }
                    if (again != null) {
                        return null;
                    }
                    // 4. 查库回填
                    R r = dbFallback.apply(id);
                    if (r == null) {
                        // 库中不存在 → 空值缓存防穿透（短TTL+随机防雪崩）
                        stringRedisTemplate.opsForValue().set(key, "", nullValueTtl(), TimeUnit.MINUTES);
                        return null;
                    }
                    this.set(key, r, time, unit);
                    return r;
                } finally {
                    unLock(lockKey);
                }
            }
            // 拿不到锁 → 休眠50ms后重查缓存（别的线程可能已重建完成），仍未命中则重新抢锁
            log.debug("互斥锁：线程{}未拿到锁{}，休眠50ms重试", Thread.currentThread().getName(), lockKey);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            if (json != null) {
                return null;
            }
        }
    }

    /* ==================== 分布式锁原语 ==================== */

    /**
     * 尝试获取锁：SETNX + 过期时间（一条原子命令，即使拿锁后服务宕机，锁也会自动过期释放，防死锁）
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁：直接删除key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
