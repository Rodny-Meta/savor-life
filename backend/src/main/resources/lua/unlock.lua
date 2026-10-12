-- 释放锁：先比对锁中的线程标识与当前线程标识，一致才删除
-- 必须用 Lua：GET 判断 + DEL 两步之间锁可能恰好过期被别人抢走（误删问题二）
-- Lua 脚本在 Redis 内原子执行，不存在时间窗口
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
