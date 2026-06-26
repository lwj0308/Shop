package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.BrandFeignClient;
import com.shop.common.result.Result;
import com.shop.model.product.dto.BrandDTO;
import com.shop.model.product.vo.BrandVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 品牌管理控制器
 * <p>
 * 管理后台对品牌的管理接口，包括品牌列表查询、详情查询、新增、修改、删除。
 * 所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/brand")
@Tag(name = "品牌管理", description = "管理后台对品牌的管理接口")
@RequiredArgsConstructor
public class BrandManageController {

    /** 品牌服务Feign客户端，远程调用商品服务中的品牌模块 */
    private final BrandFeignClient brandFeignClient;

    /**
     * 获取品牌列表
     * <p>
     * 获取所有品牌的列表，用于品牌管理页展示。
     * 需要 brand:list 权限。
     * </p>
     *
     * @return 品牌列表
     */
    @Operation(summary = "查询品牌列表", description = "获取所有品牌列表")
    @GetMapping("/list")
    @RequirePermission("brand:list")
    @OperationLog(module = "品牌管理", type = OperationType.QUERY, description = "查询品牌列表")
    public Result<List<BrandVO>> getBrandList() {
        return brandFeignClient.getBrandList();
    }

    /**
     * 根据品牌ID查询品牌详情
     * <p>
     * 查看某个品牌的详细信息。
     * 需要 brand:list 权限。
     * </p>
     *
     * @param id 品牌ID
     * @return 品牌信息
     */
    @Operation(summary = "查询品牌详情", description = "根据ID查询品牌详细信息")
    @GetMapping("/{id}")
    @RequirePermission("brand:list")
    @OperationLog(module = "品牌管理", type = OperationType.QUERY, description = "查询品牌详情：#id")
    public Result<BrandVO> getBrandById(@PathVariable Long id) {
        return brandFeignClient.getBrandById(id);
    }

    /**
     * 新增品牌
     * <p>
     * 管理员添加新的品牌，比如新增"小米"品牌。
     * 需要 brand:add 权限。
     * </p>
     *
     * @param dto 品牌参数
     * @return 操作结果
     */
    @Operation(summary = "新增品牌", description = "添加新的品牌")
    @PostMapping
    @RequirePermission("brand:add")
    @OperationLog(module = "品牌管理", type = OperationType.CREATE, description = "新增品牌：#dto.name")
    public Result<Void> addBrand(@RequestBody @Validated BrandDTO dto) {
        return brandFeignClient.addBrand(dto);
    }

    /**
     * 修改品牌
     * <p>
     * 管理员修改品牌信息，比如修改品牌名称、Logo、描述等。
     * 需要 brand:edit 权限。
     * </p>
     *
     * @param id  品牌ID
     * @param dto 品牌参数
     * @return 操作结果
     */
    @Operation(summary = "修改品牌", description = "修改品牌信息")
    @PutMapping("/{id}")
    @RequirePermission("brand:edit")
    @OperationLog(module = "品牌管理", type = OperationType.UPDATE, description = "修改品牌：#id")
    public Result<Void> updateBrand(@PathVariable Long id, @RequestBody @Validated BrandDTO dto) {
        return brandFeignClient.updateBrand(id, dto);
    }

    /**
     * 删除品牌
     * <p>
     * 管理员删除品牌，删除前需要确保该品牌下没有商品。
     * 需要 brand:delete 权限。
     * </p>
     *
     * @param id 品牌ID
     * @return 操作结果
     */
    @Operation(summary = "删除品牌", description = "删除品牌")
    @DeleteMapping("/{id}")
    @RequirePermission("brand:delete")
    @OperationLog(module = "品牌管理", type = OperationType.DELETE, description = "删除品牌：#id")
    public Result<Void> deleteBrand(@PathVariable Long id) {
        return brandFeignClient.deleteBrand(id);
    }
}
