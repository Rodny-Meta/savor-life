package com.savor.config;

import com.savor.interceptor.LoginInterceptor;
import com.savor.interceptor.RefreshTokenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 第一层：全路径——查token、存ThreadLocal、刷新有效期，永远放行
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
        // 第二层：仅拦截需要登录的接口——没有用户则401
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns(
                        "/user/**",
                        "/blog/**",
                        "/blog-comments/**",
                        "/follow/**",
                        "/upload/**",
                        "/voucher-order/**"
                )
                .excludePathPatterns(
                        "/user/code",
                        "/user/login"
                )
                .order(1);
    }
}
