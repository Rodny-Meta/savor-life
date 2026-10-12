package com.savor;

import com.savor.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局ID生成器测试：验证唯一性/递增性（连真实Redis，观察 icr:test:当天 的自增key）
 */
@SpringBootTest
class RedisIdWorkerTest {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Test
    void nextIdShouldBeIncreasing() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(redisIdWorker.nextId("test"));
        }
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i) > ids.get(i - 1), "ID必须严格递增: " + ids);
        }
        System.out.println("RedisIdWorker生成的ID示例: " + ids);
    }
}
