package com.shop.model.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全事件响应数据
 * <p>
 * 返回给前端的安全事件详细信息，记录了系统检测到的安全风险。
 * 比如某个IP频繁登录失败、有人在非常规时间做敏感操作等。
 * 管理员可以查看并处理这些安全事件。
 * </p>
 */
@Data
public class AdminSecurityEventVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件ID */
    private Long id;

    /** 事件类型：频繁登录失败、异常IP、权限越权、敏感操作 */
    private String eventType;

    /** 相关用户ID */
    private Long userId;

    /** 相关用户名 */
    private String username;

    /** 事件详情，描述发生了什么 */
    private String detail;

    /** 触发事件的IP地址（脱敏显示，如192.168.1.*） */
    @JsonSerialize(using = IpDesensitizeSerializer.class)
    private String ip;

    /** 处理状态：0未处理 1已处理 2已忽略 */
    private Integer status;

    /** 处理备注，管理员处理时填写的说明 */
    private String handleNote;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 事件创建时间 */
    private LocalDateTime createTime;
}
