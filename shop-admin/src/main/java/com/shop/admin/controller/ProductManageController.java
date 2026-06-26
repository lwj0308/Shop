package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.ProductFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.product.vo.ProductDetailVO;
import com.shop.model.product.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理控制器
 * <p>
 * 管理后台对商品的管理接口，包括商品列表查询、详情查询、强制下架、审批上架。
 * 所有接口都需要管理员拥有对应的权限才能访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/product")
@Tag(name = "商品管理", description = "管理后台对商品的管理接口")
@RequiredArgsConstructor
public class ProductManageController {

    /** 商品服务Feign客户端，远程调用商品服务 */
    private final ProductFeignClient productFeignClient;

    /**
     * 分页查询商品列表
     * <p>
     * 管理员查看所有商品，支持按分类、状态和关键词筛选。
     * 需要 product:list 权限。
     * </p>
     *
     * @param page       页码
     * @param size       每页条数
     * @param categoryId 分类ID（可选）
     * @param status     商品状态（可选）
     * @param keyword    搜索关键词（可选）
     * @return 分页商品列表
     */
    @Operation(summary = "查询商品列表", description = "分页查询商品列表，支持条件筛选")
    @GetMapping("/list")
    @RequirePermission("product:list")
    @OperationLog(module = "商品管理", type = OperationType.QUERY, description = "查询商品列表")
    public Result<PageResult<ProductVO>> listProducts(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam(required = false) Long categoryId,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(required = false) String keyword) {
        return productFeignClient.listProducts(page, size, categoryId, status, keyword);
    }

    /**
     * 根据商品ID查询商品详情
     * <p>
     * 管理员查看某个商品的完整信息，包含SKU列表、规格列表、评价摘要等。
     * 需要 product:detail 权限。
     * </p>
     *
     * @param id 商品ID
     * @return 商品详情
     */
    @Operation(summary = "查询商品详情", description = "根据ID查询商品详细信息")
    @GetMapping("/{id}")
    @RequirePermission("product:detail")
    @OperationLog(module = "商品管理", type = OperationType.QUERY, description = "查询商品详情：#id")
    public Result<ProductDetailVO> getProductDetail(@PathVariable Long id) {
        return productFeignClient.getProductDetail(id);
    }

    /**
     * 强制下架商品
     * <p>
     * 管理员强制下架违规商品，下架后用户无法看到该商品。
     * 需要 product:off 权限。
     * </p>
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @Operation(summary = "强制下架商品", description = "管理员强制下架违规商品")
    @PutMapping("/{id}/off-shelf")
    @RequirePermission("product:off")
    @OperationLog(module = "商品管理", type = OperationType.UPDATE, description = "强制下架商品：#id")
    public Result<Void> offShelfProduct(@PathVariable Long id) {
        return productFeignClient.offShelfProduct(id);
    }

    /**
     * 审批通过并上架商品
     * <p>
     * 管理员审批通过后，商品上架对用户可见。
     * 需要 product:audit 权限。
     * </p>
     *
     * @param id 商品ID
     * @return 操作结果
     */
    @Operation(summary = "审批上架商品", description = "审批通过并上架商品")
    @PutMapping("/{id}/on-shelf")
    @RequirePermission("product:audit")
    @OperationLog(module = "商品管理", type = OperationType.UPDATE, description = "审批上架商品：#id")
    public Result<Void> onShelfProduct(@PathVariable Long id) {
        return productFeignClient.onShelfProduct(id);
    }
}
