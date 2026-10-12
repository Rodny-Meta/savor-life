package com.savor.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 用户公开信息视图对象：他人主页展示（替代原 UserInfo PO 直出 + setNull 抹字段的写法）
 */
@Data
public class UserInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String city;

    private String introduce;

    private Integer fans;

    private Integer followee;

    private Boolean gender;

    private LocalDate birthday;

    private Integer credits;

    private Boolean level;
}
