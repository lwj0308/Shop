package com.shop.model.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录日志实体
 * <p>
 * 对应数据库的 user_login_log 表，记录用户每次登录的IP和设备信息。
 * 主要用于安全审计，比如发现异常登录可以及时提醒用户。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_login_log")
public class UserLoginLog extends BaseEntity {

    /** 用户ID（谁登录的） */
    private Long userId;

    /** 登录IP（从哪个网络地址登录的，可以用来判断是否异地登录） */
    private String loginIp;

    /** 登录设备（用什么设备登录的，比如"Chrome/Windows"） */
    private String loginDevice;

    /** 登录时间（什么时候登录的） */
    private LocalDateTime loginTime;

    /** 登录状态：0失败 1成功 */
    private Integer loginStatus;

    /** 失败原因（登录失败时记录，如"密码错误"、"账号不存在"） */
    private String failReason;
}
