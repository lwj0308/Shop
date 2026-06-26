package com.shop.product.feign;

import com.shop.common.result.Result;
import com.shop.product.feign.fallback.OrderFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单服务Feign客户端（商品端专用）
 * <p>
 * 商品服务通过Feign远程调用订单服务，主要用于评价功能：
 * - 校验订单归属：发表评价前校验订单是否属于当前用户
 * - 标记已评价：评价成功后标记订单为已评价，防止重复评价
 * </p>
 * <p>
 * 使用fallbackFactory实现降级：当订单服务不可用时，走降级逻辑返回友好提示，
 * 不影响评价功能的主流程（比如校验失败时不允许评价）。
 * </p>
 */
@FeignClient(
        name = "shop-order",
        contextId = "productOrder",
        fallbackFactory = OrderFeignClientFallbackFactory.class
)
public interface OrderFeignClient {

    /**
     * 校验订单归属
     * <p>
     * 检查指定订单是否属于指定用户。发表评价前调用，确保用户只能评价自己的订单。
     * 调用 shop-order 的 GET /order/inner/{orderId}/owner 接口。
     * </p>
     *
     * @param orderId 订单ID
     * @param userId  用户ID
     * @return true=归属正确 false=不属于该用户
     */
    @GetMapping("/order/inner/{orderId}/owner")
    Result<Boolean> checkOrderOwnership(@PathVariable("orderId") Long orderId,
                                        @RequestParam("userId") Long userId);

    /**
     * 标记订单为已评价
     * <p>
     * 用户发表评价后调用，将 is_reviewed 设为 1，防止重复评价。
     * 调用 shop-order 的 POST /order/inner/{orderId}/mark-reviewed 接口。
     * </p>
     *
     * @param orderId 订单ID
     * @return 操作结果
     */
    @PostMapping("/order/inner/{orderId}/mark-reviewed")
    Result<Void> markOrderReviewed(@PathVariable("orderId") Long orderId);
}
