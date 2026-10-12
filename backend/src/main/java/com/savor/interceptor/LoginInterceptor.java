package com.savor.interceptor;

import com.savor.dto.UserDTO;
import com.savor.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 第二层拦截器（order=1，仅拦截需要登录的路径）：
 * 登录门卫——ThreadLocal里没有用户就返回401（前端axios收到401会跳转登录页）。
 * 用户信息由第一层 RefreshTokenInterceptor 查 Redis 并存入，这里只做校验，不再访问 Redis。
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
