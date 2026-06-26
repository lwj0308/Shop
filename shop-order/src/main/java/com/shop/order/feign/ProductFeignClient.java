package com.shop.order.feign;

import com.shop.common.result.Result;
import com.shop.model.product.dto.StockDeductItemDTO;
import com.shop.model.product.vo.ProductSkuVO;
import com.shop.order.feign.fallback.ProductFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品服务Feign客户端
 * <p>
 * 通过Feign远程调用商品服务，获取SKU信息和扣减库存。
 * 使用fallbackFactory实现降级：当商品服务不可用时，走降级逻辑返回友好提示，
 * 不会因为商品服务挂了就导致订单服务也跟着挂。
 * </p>
 * <p>
 * 批量接口（batchGetSkuByIds/batchDeductStock/incrSalesBatch）用于一次性处理多个 SKU，
 * 避免循环 N 次 Feign 调用（N+1 远程调用问题）。
 * </p>
 */
@FeignClient(name = "shop-product", fallbackFactory = ProductFeignClientFallbackFactory.class)
public interface ProductFeignClient {

    /**
     * 根据SKU ID获取SKU信息
     * <p>
     * 下单时需要获取商品的价格、名称等信息，用于创建订单明细。
     * </p>
     *
     * @param skuId SKU ID
     * @return SKU信息
     */
    @GetMapping("/product/sku/{skuId}")
    Result<ProductSkuVO> getSkuById(@PathVariable("skuId") Long skuId);

    /**
     * 扣减库存
     * <p>
     * 下单成功后扣减商品库存，防止超卖。
     * 在Seata分布式事务中调用，如果订单创建失败会自动回滚库存。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 扣减数量
     * @return 操作结果
     */
    @PostMapping("/product/sku/{skuId}/deduct")
    Result<Void> deductStock(@PathVariable("skuId") Long skuId, @RequestParam("quantity") Integer quantity);

    /**
     * 回滚库存
     * <p>
     * 订单取消或退款时，把之前扣的库存加回去。
     * </p>
     *
     * @param skuId    SKU ID
     * @param quantity 回滚数量
     * @return 操作结果
     */
    @PostMapping("/product/sku/{skuId}/add")
    Result<Void> addStock(@PathVariable("skuId") Long skuId, @RequestParam("quantity") Integer quantity);

    /**
     * 销量累加（下单成功后调用）
     * <p>
     * 按购买数量累加商品销量，用于首页"热销推荐"排序。
     * 这是弱依赖：如果调用失败，不影响下单主流程（销量没累加上而已）。
     * </p>
     *
     * @param productId 商品ID
     * @param quantity  购买数量
     * @return 操作结果
     */
    @PostMapping("/product/inner/{id}/sales/incr")
    Result<Void> incrSales(@PathVariable("id") Long productId, @RequestParam("quantity") Integer quantity);

    /**
     * 批量获取SKU信息（避免 N+1 远程调用）
     * <p>
     * 下单时一次查询订单中所有 SKU，避免循环 N 次 Feign 调用。
     * </p>
     *
     * @param skuIds SKU ID列表
     * @return SKU 信息列表
     */
    @PostMapping("/product/inner/skus")
    Result<List<ProductSkuVO>> batchGetSkuByIds(@RequestBody List<Long> skuIds);

    /**
     * 批量扣减库存（避免 N+1 远程调用）
     * <p>
     * 一次扣减订单中所有 SKU 库存，shop-product 内部带补偿回退。
     * </p>
     *
     * @param items   扣减项列表
     * @param orderNo 订单号（用于幂等去重）
     * @return 操作结果
     */
    @PostMapping("/product/inner/skus/deduct-batch")
    Result<Void> batchDeductStock(@RequestBody List<StockDeductItemDTO> items,
                                  @RequestParam("orderNo") String orderNo);

    /**
     * 批量销量累加（避免 N+1 远程调用）
     * <p>
     * 下单成功后一次累加订单中所有商品销量。
     * </p>
     *
     * @param productQuantities key=商品ID，value=累加数量
     * @return 操作结果
     */
    @PostMapping("/product/inner/sales/incr-batch")
    Result<Void> incrSalesBatch(@RequestBody Map<Long, Integer> productQuantities);
}
