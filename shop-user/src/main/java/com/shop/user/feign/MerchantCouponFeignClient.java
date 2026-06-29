package com.shop.user.feign;

import com.shop.common.result.Result;
import com.shop.model.coupon.entity.Coupon;
import com.shop.model.coupon.vo.CouponVO;
import com.shop.user.feign.fallback.MerchantCouponFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * 优惠券服务Feign客户端
 * <p>
 * shop-user 通过此客户端调用 shop-marketing 的内部接口，获取优惠券模板信息、增加领取数等。
 * 仅调用 /marketing/coupon/inner/** 路径的内部接口（不鉴权）。
 * </p>
 * <p>
 * 使用场景：
 * 1. 用户领取优惠券时，先查询优惠券模板信息（校验状态、时间窗口、余量）
 * 2. 领取成功后，通知 shop-marketing 增加已领取数量
 * </p>
 */
@FeignClient(name = "shop-marketing", path = "/marketing/coupon", fallbackFactory = MerchantCouponFeignClientFallbackFactory.class)
public interface MerchantCouponFeignClient {

    /**
     * 获取优惠券模板信息（内部接口）
     *
     * @param couponId 优惠券ID
     * @return 优惠券模板实体
     */
    @GetMapping("/inner/{couponId}")
    Result<Coupon> getCouponById(@PathVariable("couponId") Long couponId);

    /**
     * 查询可领取的优惠券列表（内部接口）
     * <p>领券中心展示用，返回所有进行中且在领取时间窗口内的券</p>
     *
     * @return 可领取的优惠券列表
     */
    @GetMapping("/inner/receivable")
    Result<List<CouponVO>> getReceivableCouponList();

    /**
     * 增加已领取数量（内部接口）
     *
     * @param couponId 优惠券ID
     * @return true=增加成功，false=已领完
     */
    @PostMapping("/inner/{couponId}/incr-received")
    Result<Boolean> incrReceivedCount(@PathVariable("couponId") Long couponId);

    /**
     * 增加已使用数量（内部接口，核销时调用）
     *
     * @param couponId 优惠券模板ID
     */
    @PostMapping("/inner/{couponId}/incr-used")
    Result<Void> incrUsedCount(@PathVariable("couponId") Long couponId);

    /**
     * 减少已使用数量（内部接口，订单取消回退时调用）
     *
     * @param couponId 优惠券模板ID
     */
    @PostMapping("/inner/{couponId}/decr-used")
    Result<Void> decrUsedCount(@PathVariable("couponId") Long couponId);
}
