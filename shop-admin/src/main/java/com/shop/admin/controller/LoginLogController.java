package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminLoginLogService;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminLoginLogQueryDTO;
import com.shop.model.admin.vo.AdminLoginLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志控制器
 * <p>
 * 提供登录日志查询接口，管理员可以查看系统中的登录记录。
 * 登录日志记录了管理员的每一次登录尝试，包括成功和失败，
 * 可以用来检测异常登录行为（比如频繁失败可能是暴力破解）。
 * </p>
 */
@RestController
@RequestMapping("/admin/log/login")
@Tag(name = "登录日志", description = "登录日志查询接口")
@RequiredArgsConstructor
public class LoginLogController {

    /** 登录日志服务，处理登录日志的查询业务 */
    private final AdminLoginLogService adminLoginLogService;

    /**
     * 分页查询登录日志列表
     * <p>
     * 支持按用户名、IP、状态、时间范围等条件筛选。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @RequirePermission("admin:log:login")
    @OperationLog(module = "日志管理", type = OperationType.QUERY, description = "查询登录日志")
    @GetMapping("/list")
    @Operation(summary = "查询登录日志列表", description = "分页查询登录日志，支持条件筛选")
    public Result<PageResult<AdminLoginLogVO>> getLoginLogList(AdminLoginLogQueryDTO queryDTO) {
        PageResult<AdminLoginLogVO> pageResult = adminLoginLogService.getLoginLogList(queryDTO);
        return Result.success(pageResult);
    }
}
