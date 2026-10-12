package com.savor.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOP_LOGIC_KEY = "cache:shop:logic:";
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shop:type";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    /** 秒杀一人一单锁：SimpleRedisLock 内部再拼 "lock:" 前缀，即 lock:order:{userId} */
    public static final String LOCK_ORDER_KEY = "order:";

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 秒杀一人一单：Set结构，记录已抢到券的用户id，key = seckill:order:{voucherId} */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    /** 秒杀订单消息队列（Redis Stream）：资格判断通过的订单消息由Lua XADD写入，后台线程消费落库 */
    public static final String STREAM_ORDERS_KEY = "stream.orders";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    /** 我关注的人集合（Set结构）：follows:{userId}，member=被关注用户id，用于共同关注 SINTER */
    public static final String FOLLOW_KEY = "follows:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
