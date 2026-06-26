package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminRoleService;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminRoleCreateDTO;
import com.shop.model.admin.dto.AdminRoleQueryDTO;
import com.shop.model.admin.dto.AdminRoleUpdateDTO;
import com.shop.model.admin.vo.AdminRoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器
 * <p>
 * 提供角色CRUD接口和权限分配接口，包括角色列表查询、所有角色查询（下拉框用）、
 * 详情查询、新增、修改、删除等。
 * </p>
 */
@RestController
@RequestMapping("/admin/role")
@Tag(name = "角色管理", description = "角色CRUD + 权限分配接口")
@RequiredArgsConstructor
public class AdminRoleController {

    /** 角色服务，处理角色相关的业务逻辑 */
    private final AdminRoleService adminRoleService;

    /**
     * 分页查询角色列表
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @RequirePermission("admin:role:list")
    @OperationLog(module = "角色管理", type = OperationType.QUERY, description = "查询角色列表")
    @Operation(summary = "查询角色列表", description = "分页查询角色列表，支持条件筛选")
    @GetMapping("/list")
    public Result<PageResult<AdminRoleVO>> getAdminRoleList(AdminRoleQueryDTO queryDTO) {
        PageResult<AdminRoleVO> pageResult = adminRoleService.getAdminRoleList(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 查询所有正常状态的角色
     * <p>
     * 用于下拉选择框，比如给管理员分配角色时用。
     * </p>
     *
     * @return 角色列表
     */
    @RequirePermission("admin:role:list")
    @OperationLog(module = "角色管理", type = OperationType.QUERY, description = "查询所有角色")
    @Operation(summary = "查询所有角色", description = "查询所有正常状态的角色，用于下拉选择")
    @GetMapping("/all")
    public Result<List<AdminRoleVO>> getAllRoles() {
        List<AdminRoleVO> roles = adminRoleService.getAllRoles();
        return Result.success(roles);
    }

    /**
     * 根据ID查询角色详情
     *
     * @param id 角色ID
     * @return 角色详细信息（包含权限列表）
     */
    @RequirePermission("admin:role:query")
    @OperationLog(module = "角色管理", type = OperationType.QUERY, description = "查询角色详情")
    @Operation(summary = "查询角色详情", description = "根据ID查询角色详细信息，包含权限列表")
    @GetMapping("/{id}")
    public Result<AdminRoleVO> getAdminRoleById(@PathVariable Long id) {
        AdminRoleVO vo = adminRoleService.getAdminRoleById(id);
        return Result.success(vo);
    }

    /**
     * 新增角色
     *
     * @param dto 新增参数
     * @return 操作结果
     */
    @RequirePermission("admin:role:add")
    @OperationLog(module = "角色管理", type = OperationType.CREATE, description = "新增角色")
    @Operation(summary = "新增角色", description = "创建一个新的角色并分配权限")
    @PostMapping
    public Result<Void> createAdminRole(@Validated @RequestBody AdminRoleCreateDTO dto) {
        adminRoleService.createAdminRole(dto);
        return Result.success(null);
    }

    /**
     * 修改角色信息
     *
     * @param id  角色ID
     * @param dto 修改参数
     * @return 操作结果
     */
    @RequirePermission("admin:role:edit")
    @OperationLog(module = "角色管理", type = OperationType.UPDATE, description = "修改角色")
    @Operation(summary = "修改角色信息", description = "修改角色基本信息和权限分配")
    @PutMapping("/{id}")
    public Result<Void> updateAdminRole(@PathVariable Long id, @Validated @RequestBody AdminRoleUpdateDTO dto) {
        adminRoleService.updateAdminRole(id, dto);
        return Result.success(null);
    }

    /**
     * 删除角色
     *
     * @param id 角色ID
     * @return 操作结果
     */
    @RequirePermission("admin:role:remove")
    @OperationLog(module = "角色管理", type = OperationType.DELETE, description = "删除角色")
    @Operation(summary = "删除角色", description = "逻辑删除角色，有管理员使用时不可删除")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAdminRole(@PathVariable Long id) {
        adminRoleService.deleteAdminRole(id);
        return Result.success(null);
    }
}
