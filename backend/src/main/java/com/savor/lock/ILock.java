package com.savor.lock;

/**
 * 分布式锁统一接口（面向接口编程：SimpleRedisLock 是 Redis 实现，
 * 将来换 Redisson 时只换实现类，业务代码不动）
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的超时释放时间（过期自动释放，防止死锁）
     * @return true=获取成功
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁（必须由加锁线程释放：内部校验线程标识 + Lua 原子删除）
     */
    void unLock();
}
