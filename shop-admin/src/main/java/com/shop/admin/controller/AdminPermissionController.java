package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminPermissionService;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminPermissionCreateDTO;
import com.shop.model.admin.vo.AdminPermissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限管理控制器
 * <p>
 * 提供权限/菜单树接口，包括权限树查询、详情查询、新增、修改、删除等。
 * 权限分三种类型：目录、菜单、按钮，通过树形结构展示。
 * </p>
 */
@RestController
@RequestMapping("/admin/permission")
@Tag(name = "权限管理", description = "权限/菜单树接口")
@RequiredArgsConstructor
public class AdminPermissionController {

    /** 权限服务，处理权限相关的业务逻辑 */
    private final AdminPermissionService adminPermissionService;

    /**
     * 获取权限树
     * <p>
     * 返回树形结构的权限列表，前端可以递归渲染成菜单树。
     * </p>
     *
     * @return 权限树列表
     */
    @RequirePermission("admin:permission:list")
    @OperationLog(module = "权限管理", type = OperationType.QUERY, description = "查询权限树")
    @Operation(summary = "获取权限树", description = "查询所有权限，按树形结构返回")
    @GetMapping("/tree")
    public Result<List<AdminPermissionVO>> getPermissionTree() {
        List<AdminPermissionVO> tree = adminPermissionService.getPermissionTree();
        return Result.success(tree);
    }

    /**
     * 根据ID查询权限详情
     *
     * @param id 权限ID
     * @return 权限详细信息
     */
    @RequirePermission("admin:permission:query")
    @OperationLog(module = "权限管理", type = OperationType.QUERY, description = "查询权限详情")
    @Operation(summary = "查询权限详情", description = "根据ID查询权限详细信息")
    @GetMapping("/{id}")
    public Result<AdminPermissionVO> getPermissionById(@PathVariable Long id) {
        AdminPermissionVO vo = adminPermissionService.getPermissionById(id);
        return Result.success(vo);
    }

    /**
     * 新增权限
     *
     * @param dto 新增参数
     * @return 操作结果
     */
    @RequirePermission("admin:permission:add")
    @OperationLog(module = "权限管理", type = OperationType.CREATE, description = "新增权限")
    @Operation(summary = "新增权限", description = "创建一个新的权限/菜单")
    @PostMapping
    public Result<Void> createPermission(@Validated @RequestBody AdminPermissionCreateDTO dto) {
        adminPermissionService.createPermission(dto);
        return Result.success(null);
    }

    /**
     * 修改权限
     *
     * @param id  权限ID
     * @param dto 修改参数
     * @return 操作结果
     */
    @RequirePermission("admin:permission:edit")
    @OperationLog(module = "权限管理", type = OperationType.UPDATE, description = "修改权限")
    @Operation(summary = "修改权限", description = "修改权限/菜单信息")
    @PutMapping("/{id}")
    public Result<Void> updatePermission(@PathVariable Long id, @Validated @RequestBody AdminPermissionCreateDTO dto) {
        adminPermissionService.updatePermission(id, dto);
        return Result.success(null);
    }

    /**
     * 删除权限
     *
     * @param id 权限ID
     * @return 操作结果
     */
    @RequirePermission("admin:permission:remove")
    @OperationLog(module = "权限管理", type = OperationType.DELETE, description = "删除权限")
    @Operation(summary = "删除权限", description = "删除权限/菜单，有子权限时不可删除")
    @DeleteMapping("/{id}")
    public Result<Void> deletePermission(@PathVariable Long id) {
        adminPermissionService.deletePermission(id);
        return Result.success(null);
    }
}
