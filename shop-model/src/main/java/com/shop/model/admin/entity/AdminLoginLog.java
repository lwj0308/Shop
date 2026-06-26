package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志实体
 * <p>
 * 对应数据库 admin_login_log 表，记录管理员的每一次登录尝试。
 * 包括登录成功和失败记录，可以用来检测异常登录行为（比如频繁失败可能是暴力破解）。
 * 注意：该表没有逻辑删除和更新时间字段，所以不继承BaseEntity，自己定义id。
 * </p>
 */
@Data
@TableName("admin_login_log")
public class AdminLoginLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录用户名，输入的账号（不管登录成功还是失败都记录） */
    private String username;

    /** 登录IP地址 */
    private String ip;

    /** 登录地点，根据IP解析出来的地理位置，比如"广东省深圳市" */
    private String location;

    /** 浏览器信息，比如"Chrome 120" */
    private String browser;

    /** 操作系统信息，比如"Windows 11" */
    private String os;

    /** 状态：0失败 1成功（记录登录是否成功） */
    private Integer status;

    /** 失败原因，登录失败时的具体原因，比如"密码错误"、"账号已禁用" */
    private String failReason;

    /** 登录时间 */
    private LocalDateTime loginTime;
}
