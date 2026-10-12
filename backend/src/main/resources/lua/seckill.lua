-- 秒杀资格判断 + 订单消息入队（需求5：Stream消息队列版）
--
-- 在"判断资格"的同一原子操作里直接 XADD 把订单消息写进 stream.orders：
-- 判断、预扣、记录、发消息一气呵成，不存在"资格通过但消息丢失"的间隙
--
-- KEYS[1]: 库存key   seckill:stock:{voucherId}
-- KEYS[2]: 一人一单set key  seckill:order:{voucherId}
-- KEYS[3]: 消息队列  stream.orders
-- ARGV[1]: voucherId  ARGV[2]: userId  ARGV[3]: orderId（Java侧提前生成的全局ID）
-- 返回值: 0=资格通过且消息已入队  1=库存不足  2=重复下单

-- 1. 判断库存是否充足（key不存在视为库存不足，防tonumber(nil)报错）
local stock = tonumber(redis.call('get', KEYS[1]))
if (stock == nil or stock <= 0) then
    return 1
end
-- 2. 判断用户是否已抢过（一人一单）
if (redis.call('sismember', KEYS[2], ARGV[2]) == 1) then
    return 2
end
-- 3. 预扣库存（Redis 是资格闸门，真正的扣减仍在数据库）
redis.call('incrby', KEYS[1], -1)
-- 4. 记录用户（后续同用户请求在第2步被拦截）
redis.call('sadd', KEYS[2], ARGV[2])
-- 5. 订单消息写入Stream：* = Redis自动生成消息id（时间戳-序号），内容为3个键值对
redis.call('xadd', KEYS[3], '*', 'voucherId', ARGV[1], 'userId', ARGV[2], 'orderId', ARGV[3])
return 0
