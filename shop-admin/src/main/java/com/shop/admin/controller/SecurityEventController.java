package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminSecurityEventService;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminSecurityEventHandleDTO;
import com.shop.model.admin.dto.AdminSecurityEventQueryDTO;
import com.shop.model.admin.vo.AdminSecurityEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 安全事件控制器
 * <p>
 * 提供安全事件查询和处理接口，管理员可以查看系统检测到的安全风险，
 * 并对这些事件进行处理（标记为已处理或已忽略）。
 * 安全事件包括暴力破解尝试、异地登录、权限越权等。
 * </p>
 */
@RestController
@RequestMapping("/admin/security/event")
@Tag(name = "安全事件", description = "安全事件管理接口")
@RequiredArgsConstructor
public class SecurityEventController {

    /** 安全事件服务，处理安全事件的查询和处理业务 */
    private final AdminSecurityEventService adminSecurityEventService;

    /**
     * 分页查询安全事件列表
     * <p>
     * 支持按事件类型、用户名、处理状态、时间范围等条件筛选。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @RequirePermission("admin:security:list")
    @OperationLog(module = "安全管理", type = OperationType.QUERY, description = "查询安全事件列表")
    @GetMapping("/list")
    @Operation(summary = "查询安全事件列表", description = "分页查询安全事件，支持条件筛选")
    public Result<PageResult<AdminSecurityEventVO>> getSecurityEventList(AdminSecurityEventQueryDTO queryDTO) {
        PageResult<AdminSecurityEventVO> pageResult = adminSecurityEventService.getSecurityEventList(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 处理安全事件
     * <p>
     * 管理员对安全事件进行处理，比如标记为"已处理"或"已忽略"，
     * 并填写处理说明。
     * </p>
     *
     * @param id  安全事件ID
     * @param dto 处理请求参数，包含处理状态和处理备注
     * @return 操作结果
     */
    @RequirePermission("admin:security:handle")
    @OperationLog(module = "安全管理", type = OperationType.UPDATE, description = "处理安全事件")
    @PutMapping("/{id}/handle")
    @Operation(summary = "处理安全事件", description = "标记安全事件为已处理或已忽略，并填写处理备注")
    public Result<Void> handleSecurityEvent(@PathVariable Long id, @Validated @RequestBody AdminSecurityEventHandleDTO dto) {
        adminSecurityEventService.handleSecurityEvent(id, dto);
        return Result.success(null);
    }
}
