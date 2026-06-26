package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.service.AdminDeptService;
import com.shop.common.result.Result;
import com.shop.model.admin.dto.AdminDeptCreateDTO;
import com.shop.model.admin.dto.AdminDeptUpdateDTO;
import com.shop.model.admin.vo.AdminDeptVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理控制器
 * <p>
 * 提供部门树CRUD接口，包括部门树查询、详情查询、新增、修改、删除等。
 * 部门通过树形结构展示，前端可以递归渲染成部门树。
 * </p>
 */
@RestController
@RequestMapping("/admin/dept")
@Tag(name = "部门管理", description = "部门树CRUD接口")
@RequiredArgsConstructor
public class AdminDeptController {

    /** 部门服务，处理部门相关的业务逻辑 */
    private final AdminDeptService adminDeptService;

    /**
     * 获取部门树
     * <p>
     * 返回树形结构的部门列表，前端可以递归渲染成部门树。
     * </p>
     *
     * @return 部门树列表
     */
    @RequirePermission("admin:dept:list")
    @OperationLog(module = "部门管理", type = OperationType.QUERY, description = "查询部门树")
    @Operation(summary = "获取部门树", description = "查询所有部门，按树形结构返回")
    @GetMapping("/tree")
    public Result<List<AdminDeptVO>> getDeptTree() {
        List<AdminDeptVO> tree = adminDeptService.getDeptTree();
        return Result.success(tree);
    }

    /**
     * 根据ID查询部门详情
     *
     * @param id 部门ID
     * @return 部门详细信息
     */
    @RequirePermission("admin:dept:query")
    @OperationLog(module = "部门管理", type = OperationType.QUERY, description = "查询部门详情")
    @Operation(summary = "查询部门详情", description = "根据ID查询部门详细信息")
    @GetMapping("/{id}")
    public Result<AdminDeptVO> getDeptById(@PathVariable Long id) {
        AdminDeptVO vo = adminDeptService.getDeptById(id);
        return Result.success(vo);
    }

    /**
     * 新增部门
     *
     * @param dto 新增参数
     * @return 操作结果
     */
    @RequirePermission("admin:dept:add")
    @OperationLog(module = "部门管理", type = OperationType.CREATE, description = "新增部门")
    @Operation(summary = "新增部门", description = "创建一个新的部门")
    @PostMapping
    public Result<Void> createDept(@Validated @RequestBody AdminDeptCreateDTO dto) {
        adminDeptService.createDept(dto);
        return Result.success(null);
    }

    /**
     * 修改部门信息
     *
     * @param id  部门ID
     * @param dto 修改参数
     * @return 操作结果
     */
    @RequirePermission("admin:dept:edit")
    @OperationLog(module = "部门管理", type = OperationType.UPDATE, description = "修改部门")
    @Operation(summary = "修改部门信息", description = "修改部门基本信息")
    @PutMapping("/{id}")
    public Result<Void> updateDept(@PathVariable Long id, @Validated @RequestBody AdminDeptUpdateDTO dto) {
        adminDeptService.updateDept(id, dto);
        return Result.success(null);
    }

    /**
     * 删除部门
     *
     * @param id 部门ID
     * @return 操作结果
     */
    @RequirePermission("admin:dept:remove")
    @OperationLog(module = "部门管理", type = OperationType.DELETE, description = "删除部门")
    @Operation(summary = "删除部门", description = "逻辑删除部门，有子部门或管理员时不可删除")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDept(@PathVariable Long id) {
        adminDeptService.deleteDept(id);
        return Result.success(null);
    }
}
