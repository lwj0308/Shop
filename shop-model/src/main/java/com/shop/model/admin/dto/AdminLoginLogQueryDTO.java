package com.shop.model.admin.dto;

import com.shop.common.model.PageRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 登录日志查询条件
 * <p>
 * 在后台查询登录日志时，可以用这些条件进行筛选。
 * 比如按用户名搜索、按IP地址搜索、按登录状态筛选、按时间范围筛选等。
 * 继承PageRequest，自带分页参数（默认第1页，每页10条，pageSize上限100）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminLoginLogQueryDTO extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录用户名，模糊搜索 */
    private String username;

    /** 登录IP地址，模糊搜索 */
    private String ip;

    /** 登录状态：0失败 1成功 */
    private Integer status;

    /** 登录开始时间，筛选此时间之后的日志 */
    private LocalDateTime startTime;

    /** 登录结束时间，筛选此时间之前的日志 */
    private LocalDateTime endTime;
}
