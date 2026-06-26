package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminUserService;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminPasswordUpdateDTO;
import com.shop.model.admin.dto.AdminUserCreateDTO;
import com.shop.model.admin.dto.AdminUserQueryDTO;
import com.shop.model.admin.dto.AdminUserUpdateDTO;
import com.shop.model.admin.vo.AdminUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员管理控制器
 * <p>
 * 提供管理员CRUD接口，包括管理员列表查询、详情查询、新增、修改、删除、
 * 密码修改、状态切换、获取当前登录管理员信息等。
 * </p>
 */
@RestController
@RequestMapping("/admin/user")
@Tag(name = "管理员管理", description = "管理员CRUD接口")
@RequiredArgsConstructor
public class AdminUserController {

    /** 管理员服务，处理管理员相关的业务逻辑 */
    private final AdminUserService adminUserService;

    /**
     * 分页查询管理员列表
     * <p>
     * 支持按用户名、昵称模糊搜索，按状态和部门筛选。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @RequirePermission("admin:user:list")
    @OperationLog(module = "管理员管理", type = OperationType.QUERY, description = "查询管理员列表")
    @Operation(summary = "查询管理员列表", description = "分页查询管理员列表，支持条件筛选")
    @GetMapping("/list")
    public Result<PageResult<AdminUserVO>> getAdminUserList(AdminUserQueryDTO queryDTO) {
        PageResult<AdminUserVO> pageResult = adminUserService.getAdminUserList(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据ID查询管理员详情
     *
     * @param id 管理员ID
     * @return 管理员详细信息
     */
    @RequirePermission("admin:user:query")
    @OperationLog(module = "管理员管理", type = OperationType.QUERY, description = "查询管理员详情")
    @Operation(summary = "查询管理员详情", description = "根据ID查询管理员详细信息")
    @GetMapping("/{id}")
    public Result<AdminUserVO> getAdminUserById(@PathVariable Long id) {
        AdminUserVO vo = adminUserService.getAdminUserById(id);
        return Result.success(vo);
    }

    /**
     * 新增管理员
     *
     * @param dto 新增参数
     * @return 操作结果
     */
    @RequirePermission("admin:user:add")
    @OperationLog(module = "管理员管理", type = OperationType.CREATE, description = "新增管理员")
    @Operation(summary = "新增管理员", description = "创建一个新的管理员账号")
    @PostMapping
    public Result<Void> createAdminUser(@Validated @RequestBody AdminUserCreateDTO dto) {
        adminUserService.createAdminUser(dto);
        return Result.success(null);
    }

    /**
     * 修改管理员信息
     *
     * @param id  管理员ID
     * @param dto 修改参数
     * @return 操作结果
     */
    @RequirePermission("admin:user:edit")
    @OperationLog(module = "管理员管理", type = OperationType.UPDATE, description = "修改管理员")
    @Operation(summary = "修改管理员信息", description = "修改管理员基本信息和角色分配")
    @PutMapping("/{id}")
    public Result<Void> updateAdminUser(@PathVariable Long id, @Validated @RequestBody AdminUserUpdateDTO dto) {
        adminUserService.updateAdminUser(id, dto);
        return Result.success(null);
    }

    /**
     * 删除管理员
     *
     * @param id 管理员ID
     * @return 操作结果
     */
    @RequirePermission("admin:user:remove")
    @OperationLog(module = "管理员管理", type = OperationType.DELETE, description = "删除管理员")
    @Operation(summary = "删除管理员", description = "逻辑删除管理员账号")
    @DeleteMapping("/{id}")
    public Result<Void> deleteAdminUser(@PathVariable Long id) {
        adminUserService.deleteAdminUser(id);
        return Result.success(null);
    }

    /**
     * 修改管理员密码
     *
     * @param id  管理员ID
     * @param dto 密码修改参数
     * @return 操作结果
     */
    @RequirePermission("admin:user:edit")
    @OperationLog(module = "管理员管理", type = OperationType.UPDATE, description = "修改管理员密码")
    @Operation(summary = "修改管理员密码", description = "验证旧密码后修改新密码")
    @PutMapping("/{id}/password")
    public Result<Void> updatePassword(@PathVariable Long id, @Validated @RequestBody AdminPasswordUpdateDTO dto) {
        adminUserService.updatePassword(id, dto);
        return Result.success(null);
    }

    /**
     * 重置管理员密码
     * <p>
     * 管理员忘记密码时，由有权限的管理员把密码重置为默认密码（123456）。
     * 重置后管理员可以用默认密码登录，再自行修改密码。
     * </p>
     *
     * @param id 管理员ID
     * @return 操作结果
     */
    @RequirePermission("admin:user:edit")
    @OperationLog(module = "管理员管理", type = OperationType.UPDATE, description = "重置管理员密码")
    @Operation(summary = "重置管理员密码", description = "将管理员密码重置为默认密码123456")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        adminUserService.resetPassword(id);
        return Result.success(null);
    }

    /**
     * 修改管理员状态
     *
     * @param id     管理员ID
     * @param status 状态：0禁用 1正常
     * @return 操作结果
     */
    @RequirePermission("admin:user:edit")
    @OperationLog(module = "管理员管理", type = OperationType.UPDATE, description = "修改管理员状态")
    @Operation(summary = "修改管理员状态", description = "启用或禁用管理员账号")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminUserService.updateStatus(id, status);
        return Result.success(null);
    }

    /**
     * 获取当前登录管理员信息
     *
     * @return 当前管理员详细信息
     */
    @RequirePermission("admin:user:query")
    @Operation(summary = "获取当前管理员信息", description = "获取当前登录管理员的详细信息")
    @GetMapping("/current")
    public Result<AdminUserVO> getCurrentAdminInfo() {
        AdminUserVO vo = adminUserService.getCurrentAdminInfo();
        return Result.success(vo);
    }
}
