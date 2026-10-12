package com.savor.lock;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁（SETNX 实现，课程版）
 *
 * 防误删问题一（业务超时锁先过期 → 删了别人的锁）：
 *   锁的 value 存线程标识 = UUID + 线程ID——UUID 区分 JVM（集群下不同实例），
 *   线程ID 区分 JVM 内线程；释放前先比对标识，是自己的锁才删
 *
 * 防误删问题二（GET 判断 + DEL 两步非原子 → 判断后锁刚过期被抢）：
 *   释放锁用 Lua 脚本把「判断 + 删除」打包成原子命令（resources/lua/unlock.lua）
 *
 * 死锁防护：setIfAbsent 一条命令完成「加锁 + 过期时间」，无两步窗口
 */
public class SimpleRedisLock implements ILock {

    /** 锁的 key 前缀：lock:{业务名} */
    private static final String KEY_PREFIX = "lock:";

    /**
     * 线程标识前缀：UUID 区分不同 JVM（集群下 synchronized 失效的根因就是
     * 锁对象只在单 JVM 内；分布式锁的标识必须跨 JVM 全局唯一）
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString().replace("-", "") + "-";

    /** 业务名（锁的主体，如 "order:1010"），与前缀拼接成完整 key */
    private final String name;

    private final StringRedisTemplate stringRedisTemplate;

    /** 释放锁脚本：类加载时读入一次，避免每次释放锁都读文件 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 线程标识：UUID-JVM 内线程ID（如 a1b2c3...-33）
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // SET key threadId NX EX timeoutSec：一条命令原子完成「加锁 + 过期兜底」
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        // 释放锁：Lua 脚本原子执行「比对线程标识 → 一致才 DEL」
        // KEYS[1]=锁key，ARGV[1]=当前线程标识；脚本内比对，封死误删问题二的时间窗口
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                threadId);
    }
}
