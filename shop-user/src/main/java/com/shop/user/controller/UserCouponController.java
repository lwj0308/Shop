package com.shop.user.controller;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponUseDTO;
import com.shop.model.coupon.vo.CouponVO;
import com.shop.model.coupon.vo.UserCouponVO;
import com.shop.user.service.UserCouponService;
import com.shop.user.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户优惠券控制器
 * <p>
 * 提供用户优惠券的领取、查询、核销等接口。
 * 接口分为两类：
 * </p>
 * <p>
 * 1. 用户端接口（/user/coupon/**）：需要登录，操作当前登录用户的优惠券。
 *    用户ID从 UserContext 获取。
 * </p>
 * <p>
 * 2. 内部接口（/user/coupon/inner/**）：不鉴权，供 shop-order 通过 Feign 调用。
 *    用于核销优惠券（下单时）、回退优惠券（取消订单时）。
 *    路径在 SaTokenConfig 中加入白名单。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user/coupon")
@RequiredArgsConstructor
@Tag(name = "用户优惠券", description = "优惠券领取、查询、核销等接口")
public class UserCouponController {

    /** 用户优惠券服务 */
    private final UserCouponService userCouponService;

    // ==================== 用户端接口（需要登录） ====================

    /**
     * 领取优惠券
     * <p>用户在领券中心点击"立即领取"时调用</p>
     *
     * @param couponId 优惠券模板ID
     */
    @PostMapping("/receive/{couponId}")
    @Operation(summary = "领取优惠券", description = "用户领取指定优惠券")
    public Result<Void> receiveCoupon(@PathVariable Long couponId) {
        Long userId = UserContext.getUserId();
        userCouponService.receiveCoupon(userId, couponId);
        return Result.success(null);
    }

    /**
     * 查询我的优惠券列表
     *
     * @param status 状态筛选（可选）：0未使用 1已使用 2已过期
     * @param page   页码
     * @param size   每页条数
     */
    @GetMapping("/my")
    @Operation(summary = "我的优惠券", description = "查询当前用户的优惠券列表")
    public Result<PageResult<UserCouponVO>> getMyCoupons(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long userId = UserContext.getUserId();
        PageResult<UserCouponVO> result = userCouponService.getMyCoupons(userId, status, page, size);
        return Result.success(result);
    }

    /**
     * 查询领券中心的可领取优惠券列表
     */
    @GetMapping("/receivable")
    @Operation(summary = "领券中心", description = "查询当前可领取的优惠券列表")
    public Result<List<CouponVO>> getReceivableCouponList() {
        List<CouponVO> list = userCouponService.getReceivableCouponList();
        return Result.success(list);
    }

    /**
     * 查询下单可用优惠券
     * <p>下单确认页调用，返回当前用户在该订单金额下可使用的优惠券</p>
     *
     * @param orderAmount 订单金额
     */
    @GetMapping("/usable")
    @Operation(summary = "可用优惠券", description = "查询当前订单可用的优惠券列表")
    public Result<List<UserCouponVO>> getUsableCoupons(
            @RequestParam BigDecimal orderAmount) {
        Long userId = UserContext.getUserId();
        List<UserCouponVO> list = userCouponService.getUsableCoupons(userId, orderAmount);
        return Result.success(list);
    }

    // ==================== 内部接口（供 shop-order 通过 Feign 调用，不鉴权） ====================

    /**
     * 核销优惠券（内部接口）
     * <p>
     * shop-order 下单时通过 Feign 调用此接口。
     * 校验用户券状态、有效期、门槛，计算优惠金额，标记为已使用。
     * </p>
     *
     * @param dto 核销参数
     * @return 优惠金额
     */
    @PostMapping("/inner/use")
    @Operation(summary = "内部-核销优惠券", description = "下单时核销优惠券，返回优惠金额")
    public Result<BigDecimal> useCoupon(@RequestBody CouponUseDTO dto) {
        BigDecimal discount = userCouponService.useCoupon(dto);
        return Result.success(discount);
    }

    /**
     * 回退优惠券（内部接口）
     * <p>shop-order 取消订单时通过 Feign 调用此接口，恢复优惠券为未使用状态</p>
     *
     * @param orderNo 订单号
     */
    @PostMapping("/inner/rollback")
    @Operation(summary = "内部-回退优惠券", description = "取消订单时回退优惠券")
    public Result<Void> rollbackCoupon(@RequestParam String orderNo) {
        userCouponService.rollbackCoupon(orderNo);
        return Result.success(null);
    }
}
