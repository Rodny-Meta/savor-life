package com.savor.utils;

import com.savor.dto.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserHolder（ThreadLocal 封装）单元测试：纯内存验证，不依赖 Spring 容器与任何中间件。
 */
class UserHolderTest {

    @AfterEach
    void cleanup() {
        // 模拟拦截器 afterCompletion 的清理动作，保证测试之间互不污染
        UserHolder.removeUser();
    }

    @Test
    @DisplayName("保存后能在同线程读取，remove后读不到")
    void testSaveAndGet() {
        UserDTO user = new UserDTO();
        user.setId(1010L);
        user.setNickName("测试用户");

        UserHolder.saveUser(user);
        assertEquals(1010L, UserHolder.getUser().getId());
        assertEquals("测试用户", UserHolder.getUser().getNickName());

        UserHolder.removeUser();
        assertNull(UserHolder.getUser());
    }

    @Test
    @DisplayName("线程隔离：主线程存的用户，子线程读不到（防串号的根基）")
    void testThreadIsolation() throws InterruptedException {
        UserDTO mainUser = new UserDTO();
        mainUser.setId(1L);
        mainUser.setNickName("主线程用户A");
        UserHolder.saveUser(mainUser);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.execute(() -> {
                // 子线程视角：读不到主线程的数据
                assertNull(UserHolder.getUser(), "子线程不应看到主线程的用户");
                // 子线程存自己的用户
                UserDTO subUser = new UserDTO();
                subUser.setId(2L);
                subUser.setNickName("子线程用户B");
                UserHolder.saveUser(subUser);
                assertEquals(2L, UserHolder.getUser().getId());
                UserHolder.removeUser();
                latch.countDown();
            });
            latch.await();
            // 主线程视角：子线程的存取不影响主线程
            assertEquals(1L, UserHolder.getUser().getId(), "子线程操作不应影响主线程");
        } finally {
            pool.shutdown();
        }
    }
}
