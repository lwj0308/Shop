package com.shop.order.feign;

import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponUseDTO;
import com.shop.order.feign.fallback.CouponFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 用户优惠券Feign客户端
 * <p>
 * shop-order 通过此客户端调用 shop-user 的内部接口，核销和回退优惠券。
 * 仅调用 /user/coupon/inner/** 路径的内部接口（不鉴权，在 SaTokenConfig 白名单中）。
 * </p>
 * <p>
 * 使用场景：
 * 1. 下单时核销优惠券（calculateDiscount），获取优惠金额用于计算实付金额
 * 2. 取消订单时回退优惠券（rollbackCoupon），恢复优惠券为未使用状态
 * </p>
 */
@FeignClient(name = "shop-user", fallbackFactory = CouponFeignClientFallbackFactory.class)
public interface CouponFeignClient {

    /**
     * 核销优惠券（内部接口）
     * <p>
     * 下单时调用，校验用户券状态和门槛，计算优惠金额，标记为已使用。
     * </p>
     *
     * @param dto 核销参数（含用户ID、用户券ID、订单号、订单金额）
     * @return 优惠金额
     */
    @PostMapping("/user/coupon/inner/use")
    Result<BigDecimal> useCoupon(@RequestBody CouponUseDTO dto);

    /**
     * 回退优惠券（内部接口）
     * <p>取消订单时调用，恢复优惠券为未使用状态</p>
     *
     * @param orderNo 订单号
     */
    @PostMapping("/user/coupon/inner/rollback")
    Result<Void> rollbackCoupon(@RequestParam("orderNo") String orderNo);
}
