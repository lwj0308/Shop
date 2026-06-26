package com.shop.model.admin.dto;

import com.shop.common.model.PageRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 安全事件查询条件
 * <p>
 * 在后台查询安全事件时，可以用这些条件进行筛选。
 * 比如按事件类型筛选、按用户名搜索、按处理状态筛选、按时间范围筛选等。
 * 继承PageRequest，自带分页参数（默认第1页，每页10条，pageSize上限100）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminSecurityEventQueryDTO extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件类型：频繁登录失败、异常IP、权限越权、敏感操作 */
    private String eventType;

    /** 相关用户名，模糊搜索 */
    private String username;

    /** 处理状态：0未处理 1已处理 2已忽略 */
    private Integer status;

    /** 事件开始时间，筛选此时间之后的事件 */
    private LocalDateTime startTime;

    /** 事件结束时间，筛选此时间之前的事件 */
    private LocalDateTime endTime;
}
