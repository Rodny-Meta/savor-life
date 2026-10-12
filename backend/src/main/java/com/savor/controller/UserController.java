package com.savor.controller;


import cn.hutool.core.bean.BeanUtil;
import com.savor.dto.LoginFormDTO;
import com.savor.dto.Result;
import com.savor.dto.UserDTO;
import com.savor.entity.UserInfo;
import com.savor.service.IUserInfoService;
import com.savor.service.IUserService;
import com.savor.utils.UserHolder;
import com.savor.vo.UserInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader(value = "authorization", required = false) String token){
        return userService.logout(token);
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("未登录");
        }
        return Result.ok(user);
    }

    /**
     * 用户签到（BitMap）：每天一次，key=sign:{userId}:{yyyyMM}，offset=本月第几天-1
     */
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 本月连续签到天数统计
     */
    @GetMapping("/sign/count")
    public Result signCountDays(){
        return userService.signCountDays();
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        // PO转VO（时间等存储字段在VO里不存在，无需手动抹除）
        return Result.ok(BeanUtil.copyProperties(info, UserInfoVO.class));
    }
}
