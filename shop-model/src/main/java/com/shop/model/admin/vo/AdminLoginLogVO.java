package com.shop.model.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志响应数据
 * <p>
 * 返回给前端的登录日志详细信息，记录了每次登录的情况。
 * 包括谁登录的、从哪里登录的、用什么浏览器、成功还是失败等。
 * 用于安全审计，比如发现异常登录可以及时处理。
 * </p>
 */
@Data
public class AdminLoginLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 登录IP地址（脱敏显示，如192.168.1.*） */
    @JsonSerialize(using = IpDesensitizeSerializer.class)
    private String ip;

    /** 登录地点，根据IP解析出的地理位置，比如"北京市" */
    private String location;

    /** 浏览器信息，比如"Chrome 120" */
    private String browser;

    /** 操作系统信息，比如"Windows 10" */
    private String os;

    /** 登录状态：0失败 1成功 */
    private Integer status;

    /** 失败原因，登录失败时的原因，比如"密码错误"、"账号已禁用" */
    private String failReason;

    /** 登录时间 */
    private LocalDateTime loginTime;
}
