package com.shop.product.feign;

import com.shop.common.result.Result;
import com.shop.model.merchant.vo.ShopVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商家服务Feign客户端（商品端专用）
 * <p>
 * 商品服务通过Feign远程调用商家服务，查询店铺相关的信息。
 * 目前主要用于：根据店铺ID查询店铺信息，从而拿到商家ID（merchantId）。
 * merchantId会在下单时写入订单，避免订单的merchantId被硬编码为0。
 * </p>
 * <p>
 * 使用fallbackFactory实现降级：当商家服务不可用时，走降级逻辑返回友好提示，
 * 不影响商品查询的主流程。
 * </p>
 */
@FeignClient(
        name = "shop-merchant",
        contextId = "productMerchant",
        path = "/merchant/shop",
        fallbackFactory = MerchantFeignClientFallbackFactory.class
)
public interface MerchantFeignClient {

    /**
     * 根据店铺ID查询店铺信息（公开接口）
     * <p>
     * 调用商家服务的 GET /merchant/shop/{shopId} 接口，
     * 返回的ShopVO里包含merchantId字段，用于关联商品归属的商家。
     * </p>
     *
     * @param shopId 店铺ID
     * @return 店铺信息（包含merchantId）
     */
    @GetMapping("/{shopId}")
    Result<ShopVO> getShopById(@PathVariable("shopId") Long shopId);
}
