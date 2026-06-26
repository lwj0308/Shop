package com.shop.order.feign.fallback;

import com.shop.common.result.Result;
import com.shop.model.promotion.dto.PromotionCalculateDTO;
import com.shop.order.feign.PromotionFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 满减活动服务Feign降级工厂
 * <p>
 * 当 shop-merchant 服务不可用时，走降级逻辑。
 * 满减是弱依赖：服务不可用时返回 0 优惠金额，不影响用户下单主流程。
 * （用户只是暂时享受不到满减优惠，但可以正常下单）
 * </p>
 */
@Slf4j
@Component
public class PromotionFeignClientFallbackFactory implements FallbackFactory<PromotionFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的PromotionFeignClient实例
     */
    @Override
    public PromotionFeignClient create(Throwable cause) {
        log.error("满减活动服务调用失败，触发降级（返回0优惠，不影响下单）", cause);
        return new PromotionFeignClient() {
            /**
             * 满减计算降级：返回0优惠金额
             * <p>满减是弱依赖，不影响下单主流程</p>
             */
            @Override
            public Result<BigDecimal> calculatePromotion(PromotionCalculateDTO dto) {
                log.warn("满减计算降级: merchantId={}, orderAmount={}", dto.getMerchantId(), dto.getOrderAmount());
                return Result.success(BigDecimal.ZERO);
            }
        };
    }
}
