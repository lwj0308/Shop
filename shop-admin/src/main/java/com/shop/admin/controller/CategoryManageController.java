package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.CategoryFeignClient;
import com.shop.common.result.Result;
import com.shop.model.product.dto.CategoryDTO;
import com.shop.model.product.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类管理控制器
 * <p>
 * 管理后台对商品分类的管理接口，包括分类树查询、新增、修改、删除。
 * 所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/category")
@Tag(name = "分类管理", description = "管理后台对分类的管理接口")
@RequiredArgsConstructor
public class CategoryManageController {

    /** 分类服务Feign客户端，远程调用商品服务中的分类模块 */
    private final CategoryFeignClient categoryFeignClient;

    /**
     * 获取分类树形结构
     * <p>
     * 获取所有分类的树形结构，前端可以直接用来渲染分类菜单。
     * 需要 category:list 权限。
     * </p>
     *
     * @return 分类树形列表
     */
    @Operation(summary = "获取分类树", description = "获取分类树形结构，用于分类管理")
    @GetMapping("/tree")
    @RequirePermission("category:list")
    @OperationLog(module = "分类管理", type = OperationType.QUERY, description = "查询分类树")
    public Result<List<CategoryVO>> getCategoryTree() {
        return categoryFeignClient.getCategoryTree();
    }

    /**
     * 新增分类
     * <p>
     * 管理员添加新的商品分类，比如新增"智能家居"分类。
     * 需要 category:add 权限。
     * </p>
     *
     * @param dto 分类参数
     * @return 操作结果
     */
    @Operation(summary = "新增分类", description = "添加新的商品分类")
    @PostMapping
    @RequirePermission("category:add")
    @OperationLog(module = "分类管理", type = OperationType.CREATE, description = "新增分类：#dto.name")
    public Result<Void> addCategory(@RequestBody @Validated CategoryDTO dto) {
        return categoryFeignClient.addCategory(dto);
    }

    /**
     * 修改分类
     * <p>
     * 管理员修改分类信息，比如修改分类名称、图标、排序等。
     * 需要 category:edit 权限。
     * </p>
     *
     * @param id  分类ID
     * @param dto 分类参数
     * @return 操作结果
     */
    @Operation(summary = "修改分类", description = "修改分类信息")
    @PutMapping("/{id}")
    @RequirePermission("category:edit")
    @OperationLog(module = "分类管理", type = OperationType.UPDATE, description = "修改分类：#id")
    public Result<Void> updateCategory(@PathVariable Long id, @RequestBody @Validated CategoryDTO dto) {
        return categoryFeignClient.updateCategory(id, dto);
    }

    /**
     * 删除分类
     * <p>
     * 管理员删除分类，删除前需要确保该分类下没有子分类和商品。
     * 需要 category:delete 权限。
     * </p>
     *
     * @param id 分类ID
     * @return 操作结果
     */
    @Operation(summary = "删除分类", description = "删除商品分类")
    @DeleteMapping("/{id}")
    @RequirePermission("category:delete")
    @OperationLog(module = "分类管理", type = OperationType.DELETE, description = "删除分类：#id")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        return categoryFeignClient.deleteCategory(id);
    }
}
