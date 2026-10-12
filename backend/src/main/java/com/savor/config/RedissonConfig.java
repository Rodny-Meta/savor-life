package com.savor.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置
 *
 * 为什么不引 redisson-spring-boot-starter：
 * starter 会接管 Spring Data Redis 的自动配置（替换 RedisTemplate/连接工厂），
 * 与我们现有 StringRedisTemplate 缓存体系冲突；只用核心包 + 自配 RedissonClient，
 * 两套客户端并存：缓存走 Lettuce（StringRedisTemplate），分布式锁走 Redisson
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 单节点模式（与 savor-redis 容器一致：127.0.0.1:6380，密码 12345678）
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6380")
                .setPassword("12345678")
                // 连接池：最小空闲连接数（默认24太大，本地开发调小）
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(16);
        return Redisson.create(config);
    }
}
