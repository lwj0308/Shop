package com.shop.cart.feign;

import com.shop.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品服务远程调用接口
 * <p>
 * 购物车服务需要调用商品服务获取商品的实时价格和库存状态，
 * 保证用户看到的购物车信息是最新的。
 * </p>
 * <p>
 * 降级策略：
 * 当商品服务不可用时，降级工厂会返回友好的默认值，
 * 购物车列表仍能展示（显示"商品信息暂不可用"），
 * 加购操作降级放行（结算时再校验）。
 * </p>
 */
@FeignClient(
        name = "shop-product",
        path = "/product",
        fallbackFactory = ProductFeignClientFallbackFactory.class
)
public interface ProductFeignClient {

    /**
     * 根据SKU ID列表批量获取SKU信息
     * <p>
     * 用于购物车列表查询时，一次性获取所有商品的实时价格和库存。
     * 比购物车列表里一个一个查要高效得多。
     * </p>
     *
     * @param skuIds SKU ID列表
     * @return SKU信息Map，key是skuId，value是SKU详情
     */
    @PostMapping("/sku/batch")
    Result<Map<Long, Map<String, Object>>> batchGetSkuInfo(@RequestBody List<Long> skuIds);

    /**
     * 根据SKU ID获取SKU价格
     *
     * @param skuId SKU ID
     * @return SKU价格
     */
    @GetMapping("/sku/price")
    Result<BigDecimal> getSkuPrice(@RequestParam("skuId") Long skuId);

    /**
     * 根据SKU ID获取库存状态
     *
     * @param skuId SKU ID
     * @return 是否有库存
     */
    @GetMapping("/sku/stock-status")
    Result<Boolean> getStockStatus(@RequestParam("skuId") Long skuId);

    /**
     * 根据商品ID获取商品名称和主图
     *
     * @param productId 商品ID
     * @return 商品基本信息Map（含name和image）
     */
    @GetMapping("/basic-info")
    Result<Map<String, Object>> getProductBasicInfo(@RequestParam("productId") Long productId);
}
