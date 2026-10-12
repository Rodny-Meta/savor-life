package com.savor.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.savor.dto.UserDTO;
import com.savor.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.savor.utils.RedisConstants.LOGIN_USER_KEY;
import static com.savor.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * 第一层拦截器（order=0，全路径）：
 * 只负责"有登录态就续命 + 存ThreadLocal"，永远放行，不拦截任何请求。
 * 这样用户浏览任何页面（包括无需登录的首页）都会刷新token有效期，不会逛着逛着掉线。
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (token == null || token.isBlank()) {
            return true;
        }
        String key = LOGIN_USER_KEY + token;
        // 2. 从Redis查询用户会话
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return true;
        }
        // 3. 存入ThreadLocal，供业务代码获取当前用户
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 4. 刷新token有效期（活跃用户不掉线）
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 放在第一层清理：无论第二层是否放行，第一层的afterCompletion一定会执行
        UserHolder.removeUser();
    }
}
