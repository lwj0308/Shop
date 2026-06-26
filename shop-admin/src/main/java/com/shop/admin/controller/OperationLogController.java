package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminOperationLogService;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminOperationLogQueryDTO;
import com.shop.model.admin.vo.AdminOperationLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志控制器
 * <p>
 * 提供操作日志查询接口，管理员可以查看系统中的操作记录。
 * 操作日志记录了管理员在后台的每一次操作，包括操作人、操作模块、
 * 请求参数、响应结果、耗时等信息，方便审计和排查问题。
 * </p>
 */
@RestController
@RequestMapping("/admin/log/operation")
@Tag(name = "操作日志", description = "操作日志查询接口")
@RequiredArgsConstructor
public class OperationLogController {

    /** 操作日志服务，处理操作日志的查询业务 */
    private final AdminOperationLogService adminOperationLogService;

    /**
     * 分页查询操作日志列表
     * <p>
     * 支持按用户名、模块、操作类型、状态、时间范围等条件筛选。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @RequirePermission("admin:log:operation")
    @OperationLog(module = "日志管理", type = OperationType.QUERY, description = "查询操作日志")
    @GetMapping("/list")
    @Operation(summary = "查询操作日志列表", description = "分页查询操作日志，支持条件筛选")
    public Result<PageResult<AdminOperationLogVO>> getOperationLogList(AdminOperationLogQueryDTO queryDTO) {
        PageResult<AdminOperationLogVO> pageResult = adminOperationLogService.getOperationLogList(queryDTO);
        return Result.success(pageResult);
    }
}
