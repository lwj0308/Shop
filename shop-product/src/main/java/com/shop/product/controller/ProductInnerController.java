package com.shop.product.controller;

import com.shop.common.result.Result;
import com.shop.model.product.dto.StockDeductItemDTO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品内部接口（供其他微服务通过 Feign 调用，不鉴权）
 * <p>
 * 供 shop-order 调用：
 * - 销量累加：用户下单成功后，订单服务通过 Feign 调用此接口累加商品销量，
 *   用于热销推荐排序。
 * - 批量获取 SKU：下单时一次查询多个 SKU，避免 N+1 远程调用。
 * - 批量扣减库存：下单时一次扣减多个 SKU 库存，内部带补偿回退。
 * - 批量销量累加：下单时一次累加多个商品销量。
 * </p>
 * <p>
 * 路径 /product/inner/** 在 SaTokenConfig 中加入白名单，不需要登录。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/product/inner")
@RequiredArgsConstructor
@Tag(name = "商品内部接口", description = "供其他微服务Feign调用，不鉴权")
public class ProductInnerController {

    /** 商品服务 */
    private final ProductService productService;

    /**
     * 销量累加（供 shop-order Feign 调用）
     * <p>
     * 用户下单成功后，订单服务按商品购买数量调用此接口累加销量。
     * 销量数据用于首页"热销推荐"排序。
     * </p>
     *
     * @param id       商品ID
     * @param quantity 购买数量
     * @return 操作结果
     */
    @PostMapping("/{id}/sales/incr")
    @Operation(summary = "销量累加", description = "下单成功后累加商品销量，供shop-order Feign调用")
    public Result<Void> incrSales(
            @Parameter(description = "商品ID") @PathVariable Long id,
            @Parameter(description = "购买数量") @RequestParam Integer quantity) {
        productService.incrSales(id, quantity);
        return Result.success();
    }

    /**
     * 批量获取 SKU 信息（供 shop-order Feign 调用）
     * <p>
     * 下单时一次查询订单中所有 SKU，避免循环 N 次远程调用（N+1 问题）。
     * </p>
     *
     * @param skuIds SKU ID列表
     * @return SKU 信息列表
     */
    @PostMapping("/skus")
    @Operation(summary = "批量获取SKU信息", description = "一次查询多个SKU，避免N+1远程调用")
    public Result<List<ProductSkuVO>> batchGetSkuByIds(@RequestBody List<Long> skuIds) {
        return Result.success(productService.batchGetSkuByIds(skuIds));
    }

    /**
     * 批量扣减库存（供 shop-order Feign 调用）
     * <p>
     * 一次处理订单中所有 SKU 的扣减，任一失败在内部回滚已扣减的，
     * shop-order 只需调用一次 Feign。
     * </p>
     *
     * @param items   扣减项列表
     * @param orderNo 订单号（用于幂等去重）
     * @return 是否全部成功
     */
    @PostMapping("/skus/deduct-batch")
    @Operation(summary = "批量扣减库存", description = "一次扣减多个SKU库存，内部带补偿回退")
    public Result<Void> batchDeductStock(
            @RequestBody List<StockDeductItemDTO> items,
            @Parameter(description = "订单号，用于幂等去重") @RequestParam String orderNo) {
        boolean success = productService.batchDeductStock(items, orderNo);
        if (!success) {
            return Result.fail("库存扣减失败");
        }
        return Result.success("扣减成功", null);
    }

    /**
     * 批量销量累加（供 shop-order Feign 调用）
     * <p>
     * 下单成功后一次累加订单中所有商品的销量，避免循环 N 次远程调用。
     * </p>
     *
     * @param productQuantities key=商品ID，value=累加数量
     * @return 操作结果
     */
    @PostMapping("/sales/incr-batch")
    @Operation(summary = "批量销量累加", description = "一次累加多个商品销量，避免N+1远程调用")
    public Result<Void> incrSalesBatch(@RequestBody Map<Long, Integer> productQuantities) {
        productService.incrSalesBatch(productQuantities);
        return Result.success();
    }
}
