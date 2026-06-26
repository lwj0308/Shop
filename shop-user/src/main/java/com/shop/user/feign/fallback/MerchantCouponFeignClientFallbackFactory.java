package com.shop.user.feign.fallback;

import com.shop.common.result.Result;
import com.shop.model.coupon.entity.Coupon;
import com.shop.model.coupon.vo.CouponVO;
import com.shop.user.feign.MerchantCouponFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 商家优惠券Feign客户端降级工厂
 * <p>
 * 当 shop-merchant 服务不可用时，走降级逻辑，避免影响主流程。
 * 查询类降级返回 null（表示优惠券不存在），写入类降级只记录日志不抛异常。
 * </p>
 */
@Slf4j
@Component
public class MerchantCouponFeignClientFallbackFactory implements FallbackFactory<MerchantCouponFeignClient> {

    @Override
    public MerchantCouponFeignClient create(Throwable cause) {
        log.error("调用 shop-merchant 优惠券服务失败: {}", cause.getMessage());
        return new MerchantCouponFeignClient() {

            @Override
            public Result<Coupon> getCouponById(Long couponId) {
                log.warn("降级：获取优惠券模板失败 couponId={}", couponId);
                return Result.fail("优惠券服务暂时不可用");
            }

            @Override
            public Result<java.util.List<CouponVO>> getReceivableCouponList() {
                log.warn("降级：查询可领取优惠券列表失败");
                return Result.success(Collections.emptyList());
            }

            @Override
            public Result<Boolean> incrReceivedCount(Long couponId) {
                log.warn("降级：增加领取数失败 couponId={}", couponId);
                return Result.fail("优惠券服务暂时不可用");
            }

            @Override
            public Result<Void> incrUsedCount(Long couponId) {
                log.warn("降级：增加使用数失败 couponId={}", couponId);
                return Result.fail("优惠券服务暂时不可用");
            }

            @Override
            public Result<Void> decrUsedCount(Long couponId) {
                log.warn("降级：减少使用数失败 couponId={}", couponId);
                return Result.fail("优惠券服务暂时不可用");
            }
        };
    }
}
