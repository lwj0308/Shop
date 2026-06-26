package com.shop.order.feign;

import com.shop.common.result.Result;
import com.shop.order.feign.fallback.CartFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 购物车服务Feign客户端
 * <p>
 * 通过Feign远程调用购物车服务，下单成功后删除购物车中已购买的商品。
 * 用户不想买了的东西还在购物车里，但已经下单的东西应该从购物车移除。
 * </p>
 */
@FeignClient(name = "shop-cart", fallbackFactory = CartFeignClientFallbackFactory.class)
public interface CartFeignClient {

    /**
     * 根据SKU ID列表删除购物车项
     * <p>
     * 下单成功后，把已购买的商品从购物车里删掉。
     * 即使删除失败也不影响下单流程（购物车里有冗余也没关系），
     * 所以这个调用失败不需要回滚订单。
     * </p>
     *
     * @param userId  用户ID
     * @param skuIds  要删除的SKU ID列表
     * @return 操作结果
     */
    @DeleteMapping("/cart/items")
    Result<Void> deleteBySkuIds(@RequestParam("userId") Long userId, @RequestParam("skuIds") List<Long> skuIds);
}
