package com.savor.interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 双拦截器行为集成测试：MockMvc 走完整请求链（拦截器→Controller），但不起真实端口。
 * 依赖：savor-mysql(3307)/savor-redis(6380) 容器在运行（docker compose up -d）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginGuardTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("未带token访问需登录接口：第二层拦截器应返回401")
    void testMeWithoutTokenShouldBe401() throws Exception {
        mockMvc.perform(get("/user/me"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("伪造token访问：Redis查无会话，仍应401")
    void testMeWithFakeTokenShouldBe401() throws Exception {
        mockMvc.perform(get("/user/me")
                        .header("authorization", "fake-token-00000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("游客浏览商铺类型：公开接口应放行200且返回数据")
    void testGuestCanBrowseShopType() throws Exception {
        mockMvc.perform(get("/shop-type/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").exists());
    }

    @Test
    @DisplayName("游客浏览商铺详情：公开接口200，且出参不含存储字段（VO生效）")
    void testGuestCanQueryShop() throws Exception {
        mockMvc.perform(get("/shop/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").exists())
                .andExpect(jsonPath("$.data.createTime").doesNotExist())
                .andExpect(jsonPath("$.data.updateTime").doesNotExist());
    }
}
