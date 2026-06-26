package com.shop.order.feign.fallback;

import com.shop.common.result.Result;
import com.shop.order.feign.CartFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 购物车服务Feign降级工厂
 * <p>
 * 当购物车服务不可用时，走降级逻辑。
 * 购物车删除失败不影响下单流程，所以降级时只记录日志，不抛异常。
 * </p>
 */
@Slf4j
@Component
public class CartFeignClientFallbackFactory implements FallbackFactory<CartFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的CartFeignClient实例
     */
    @Override
    public CartFeignClient create(Throwable cause) {
        log.error("购物车服务调用失败，触发降级", cause);
        return (userId, skuIds) -> {
            // 购物车删除失败不影响下单，只记录日志
            log.warn("购物车删除失败，不影响下单流程: userId={}, skuIds={}", userId, skuIds);
            return Result.success(null);
        };
    }
}
