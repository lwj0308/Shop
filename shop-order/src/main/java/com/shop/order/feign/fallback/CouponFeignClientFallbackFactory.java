package com.shop.order.feign.fallback;

import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponUseDTO;
import com.shop.order.feign.CouponFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 用户优惠券Feign降级工厂
 * <p>
 * 当 shop-user 服务不可用时，走降级逻辑：
 * - 核销降级：返回失败，下单流程会抛异常（优惠券核销是强依赖，不能跳过）
 * - 回退降级：只记录日志不抛异常（回退失败不影响取消订单的主流程）
 * </p>
 */
@Slf4j
@Component
public class CouponFeignClientFallbackFactory implements FallbackFactory<CouponFeignClient> {

    @Override
    public CouponFeignClient create(Throwable cause) {
        log.error("用户优惠券服务调用失败，触发降级", cause);
        return new CouponFeignClient() {

            @Override
            public Result<BigDecimal> useCoupon(CouponUseDTO dto) {
                log.warn("降级：核销优惠券失败 userCouponId={}", dto.getUserCouponId());
                return Result.fail("优惠券服务暂时不可用，请稍后重试");
            }

            @Override
            public Result<Void> rollbackCoupon(String orderNo) {
                log.warn("降级：回退优惠券失败 orderNo={}（不影响取消订单）", orderNo);
                return Result.success(null);
            }
        };
    }
}
