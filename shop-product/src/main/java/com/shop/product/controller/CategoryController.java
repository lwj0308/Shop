package com.shop.product.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.shop.common.result.Result;
import com.shop.model.product.dto.CategoryDTO;
import com.shop.model.product.vo.CategoryVO;
import com.shop.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品分类控制器
 * <p>
 * 处理分类的增删改查接口，包括获取分类树。
 * 写操作（添加/修改/删除）需要管理员登录，读操作（分类树）公开访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/product/category")
@RequiredArgsConstructor
@Tag(name = "商品分类", description = "分类增删改查、分类树")
public class CategoryController {

    /** 分类服务 */
    private final CategoryService categoryService;

    // ==================== 管理端接口（需要登录） ====================

    /**
     * 添加分类
     * <p>
     * 添加新的商品分类，需要管理员登录。
     * </p>
     *
     * @param dto 分类参数
     * @return 操作结果
     */
    @PostMapping
    @SaCheckLogin
    @Operation(summary = "添加分类", description = "添加新的商品分类，需要管理员登录")
    public Result<Void> addCategory(@Validated @RequestBody CategoryDTO dto) {
        categoryService.addCategory(dto);
        return Result.success("添加成功", null);
    }

    /**
     * 修改分类
     * <p>
     * 修改分类信息，需要管理员登录。
     * </p>
     *
     * @param id  分类ID
     * @param dto 分类参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "修改分类", description = "修改分类信息，需要管理员登录")
    public Result<Void> updateCategory(
            @PathVariable @Parameter(description = "分类ID") Long id,
            @Validated @RequestBody CategoryDTO dto) {
        categoryService.updateCategory(id, dto);
        return Result.success("修改成功", null);
    }

    /**
     * 删除分类
     * <p>
     * 删除分类（有子分类或商品时不能删除），需要管理员登录。
     * </p>
     *
     * @param id 分类ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "删除分类", description = "删除分类（有子分类或商品时不能删除），需要管理员登录")
    public Result<Void> deleteCategory(@Parameter(description = "分类ID") @PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success("删除成功", null);
    }

    // ==================== C端接口（公开访问） ====================

    /**
     * 获取分类树
     * <p>
     * 获取所有分类的树形结构，公开访问。
     * </p>
     *
     * @return 树形分类列表
     */
    @GetMapping("/tree")
    @Operation(summary = "获取分类树", description = "获取所有分类的树形结构，公开访问")
    public Result<List<CategoryVO>> getCategoryTree() {
        return Result.success(categoryService.getCategoryTree());
    }
}
