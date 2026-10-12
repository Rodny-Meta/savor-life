package com.savor.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.savor.dto.LoginFormDTO;
import com.savor.dto.Result;
import com.savor.entity.User;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result logout(String token);

    /**
     * 用户签到（BitMap）：sign:{userId}:{yyyyMM} 当天偏移位置1
     * @return 无；当天已签到时返回提示
     */
    Result sign();

    /**
     * 统计本月连续签到天数：BITFIELD 取本月第1天~今天的签到位，从今天往前数连续1
     * @return 连续签到天数
     */
    Result signCountDays();
}
