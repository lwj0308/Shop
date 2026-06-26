package com.shop.product.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.shop.common.result.Result;
import com.shop.model.product.dto.BrandDTO;
import com.shop.model.product.vo.BrandVO;
import com.shop.product.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 品牌控制器
 * <p>
 * 处理品牌的增删改查接口。
 * 写操作（添加/修改/删除）需要管理员登录，读操作（列表/详情）公开访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/product/brand")
@RequiredArgsConstructor
@Tag(name = "品牌管理", description = "品牌增删改查")
public class BrandController {

    /** 品牌服务 */
    private final BrandService brandService;

    // ==================== 管理端接口（需要登录） ====================

    /**
     * 添加品牌
     * <p>
     * 添加新的品牌，需要管理员登录。
     * </p>
     *
     * @param dto 品牌参数
     * @return 操作结果
     */
    @PostMapping
    @SaCheckLogin
    @Operation(summary = "添加品牌", description = "添加新的品牌，需要管理员登录")
    public Result<Void> addBrand(@Validated @RequestBody BrandDTO dto) {
        brandService.addBrand(dto);
        return Result.success("添加成功", null);
    }

    /**
     * 修改品牌
     * <p>
     * 修改品牌信息，需要管理员登录。
     * </p>
     *
     * @param id  品牌ID
     * @param dto 品牌参数
     * @return 操作结果
     */
    @PutMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "修改品牌", description = "修改品牌信息，需要管理员登录")
    public Result<Void> updateBrand(
            @Parameter(description = "品牌ID") @PathVariable Long id,
            @Validated @RequestBody BrandDTO dto) {
        brandService.updateBrand(id, dto);
        return Result.success("修改成功", null);
    }

    /**
     * 删除品牌
     * <p>
     * 删除品牌（有商品时不能删除），需要管理员登录。
     * </p>
     *
     * @param id 品牌ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @SaCheckLogin
    @Operation(summary = "删除品牌", description = "删除品牌（有商品时不能删除），需要管理员登录")
    public Result<Void> deleteBrand(@Parameter(description = "品牌ID") @PathVariable Long id) {
        brandService.deleteBrand(id);
        return Result.success("删除成功", null);
    }

    // ==================== C端接口（公开访问） ====================

    /**
     * 品牌列表
     * <p>
     * 获取所有品牌列表，公开访问。
     * </p>
     *
     * @return 所有品牌列表
     */
    @GetMapping("/list")
    @Operation(summary = "品牌列表", description = "获取所有品牌列表，公开访问")
    public Result<List<BrandVO>> getBrandList() {
        return Result.success(brandService.getBrandList());
    }

    /**
     * 品牌详情
     * <p>
     * 根据ID获取品牌详情，公开访问。
     * </p>
     *
     * @param id 品牌ID
     * @return 品牌详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "品牌详情", description = "根据ID获取品牌详情，公开访问")
    public Result<BrandVO> getBrandById(@Parameter(description = "品牌ID") @PathVariable Long id) {
        return Result.success(brandService.getBrandById(id));
    }
}
