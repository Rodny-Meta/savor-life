package com.savor.utils;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一ID生成器（Redis自增版）
 *
 * 64bit结构：符号位(1) + 时间戳(31) + 序列号(32)
 * - 符号位：恒为0，保证ID为正数
 * - 时间戳：相对起始时间（2022-01-01 UTC）的秒数差，31bit约可用69年
 * - 序列号：Redis INCR自增，秒内理论上限2^32个；key按天分段，单key压力可控，还能当每日单量计数器
 *
 * 解决的问题：自增ID规律性太明显（可被遍历/窥探业务量）+ 单表数据量上限
 */
@Component
public class RedisIdWorker {

    /** 起始时间戳：2022-01-01 00:00:00 UTC（秒） */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    /** 序列号位数 */
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成全局唯一ID
     * @param keyPrefix 业务前缀（如 "order"），实际自增key为 icr:order:20261009
     */
    public long nextId(String keyPrefix) {
        // 1. 生成时间戳：当前秒 - 起始秒
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2. 生成序列号：Redis自增（key带日期，按天分段）
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3. 拼接并返回：时间戳左移32位腾出序列号位置，或运算合并（符号位天然为0）
        return timestamp << COUNT_BITS | count;
    }
}
