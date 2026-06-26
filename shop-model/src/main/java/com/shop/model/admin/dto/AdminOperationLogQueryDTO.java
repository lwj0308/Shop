package com.shop.model.admin.dto;

import com.shop.common.model.PageRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 操作日志查询条件
 * <p>
 * 在后台查询操作日志时，可以用这些条件进行筛选。
 * 比如按操作人搜索、按模块筛选、按操作类型筛选、按时间范围筛选等。
 * 继承PageRequest，自带分页参数（默认第1页，每页10条，pageSize上限100）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOperationLogQueryDTO extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 操作人用户名，模糊搜索 */
    private String username;

    /** 操作模块，比如"用户管理"、"角色管理" */
    private String module;

    /** 操作类型：新增、修改、删除、查询、导出 */
    private String operationType;

    /** 操作状态：0失败 1成功 */
    private Integer status;

    /** 操作开始时间，筛选此时间之后的日志 */
    private LocalDateTime startTime;

    /** 操作结束时间，筛选此时间之前的日志 */
    private LocalDateTime endTime;
}
