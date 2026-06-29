package com.shop.marketing.controller;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.marketing.feign.MerchantFeignClient;
import com.shop.marketing.service.CouponService;
import com.shop.model.coupon.dto.CouponCreateDTO;
import com.shop.model.coupon.dto.CouponQueryDTO;
import com.shop.model.coupon.entity.Coupon;
import com.shop.model.coupon.vo.CouponVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券控制器
 * <p>
 * 提供优惠券模板的创建、修改、下架、查询等功能。
 * 包含三类接口：
 * 1. 商家端接口（/marketing/coupon/*）：商家管理自己的优惠券，需登录
 * 2. 管理端接口（/marketing/coupon/admin/*）：管理员管理所有优惠券，供 shop-admin 通过 Feign 调用
 * 3. 内部接口（/marketing/coupon/inner/*）：供其他微服务通过 Feign 调用，不鉴权
 * </p>
 */
@Tag(name = "优惠券管理", description = "优惠券模板的创建、修改、下架、查询")
@RestController
@RequestMapping("/marketing/coupon")
@RequiredArgsConstructor
public class MarketingCouponController {

    /** 优惠券服务 */
    private final CouponService couponService;

    /** 商家服务 Feign 客户端，用于通过用户ID查找商家ID */
    private final MerchantFeignClient merchantFeignClient;

    // ==================== 商家端接口 ====================

    /**
     * 创建优惠券
     */
    @Operation(summary = "商家-创建优惠券", description = "商家创建自己的优惠券")
    @PostMapping
    public Result<Long> createCoupon(@Validated @RequestBody CouponCreateDTO dto) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        Long couponId = couponService.createCoupon(merchantId, dto);
        return Result.success(couponId);
    }

    /**
     * 修改优惠券（仅待生效状态可改）
     */
    @Operation(summary = "商家-修改优惠券", description = "修改优惠券规则，仅待生效状态可修改")
    @PutMapping("/{couponId}")
    public Result<Void> updateCoupon(@PathVariable Long couponId, @Validated @RequestBody CouponCreateDTO dto) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        couponService.updateCoupon(merchantId, couponId, dto);
        return Result.success(null);
    }

    /**
     * 下架优惠券
     */
    @Operation(summary = "商家-下架优惠券", description = "下架后用户不能再领取，已领取的券仍可使用")
    @PutMapping("/{couponId}/offline")
    public Result<Void> offlineCoupon(@PathVariable Long couponId) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        couponService.offlineCoupon(merchantId, couponId);
        return Result.success(null);
    }

    /**
     * 查询商家自己的优惠券列表
     */
    @Operation(summary = "商家-优惠券列表", description = "查询当前商家的优惠券列表")
    @GetMapping("/list")
    public Result<PageResult<CouponVO>> getCouponList(CouponQueryDTO query) {
        Long userId = SecurityUtils.requireLogin();
        Long merchantId = getMerchantId(userId);
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }
        PageResult<CouponVO> list = couponService.getCouponList(merchantId, query);
        return Result.success(list);
    }

    /**
     * 查询优惠券详情
     */
    @Operation(summary = "商家-优惠券详情", description = "查询指定优惠券的详细信息")
    @GetMapping("/{couponId}")
    public Result<CouponVO> getCouponDetail(@PathVariable Long couponId) {
        CouponVO vo = couponService.getCouponDetail(couponId);
        return Result.success(vo);
    }

    // ==================== 管理端接口（供 shop-admin 通过 Feign 调用） ====================

    /**
     * 管理端创建平台券
     * <p>merchantId=0 表示平台券</p>
     */
    @Operation(summary = "管理端-创建平台券", description = "管理员创建平台优惠券")
    @PostMapping("/admin/create")
    public Result<Long> adminCreateCoupon(@Validated @RequestBody CouponCreateDTO dto) {
        Long couponId = couponService.createCoupon(0L, dto);
        return Result.success(couponId);
    }

    /**
     * 管理端查询所有优惠券（含平台券和商家券）
     */
    @Operation(summary = "管理端-优惠券列表", description = "管理员查看全平台优惠券")
    @GetMapping("/admin/list")
    public Result<PageResult<CouponVO>> adminGetCouponList(CouponQueryDTO query) {
        PageResult<CouponVO> list = couponService.getCouponList(null, query);
        return Result.success(list);
    }

    /**
     * 管理端下架优惠券
     */
    @Operation(summary = "管理端-下架优惠券", description = "管理员下架任意优惠券")
    @PutMapping("/admin/{couponId}/offline")
    public Result<Void> adminOfflineCoupon(@PathVariable Long couponId) {
        couponService.offlineCoupon(0L, couponId);
        return Result.success(null);
    }

    // ==================== 内部接口（供其他微服务通过 Feign 调用，不鉴权） ====================

    /**
     * 获取优惠券模板信息（内部接口）
     * <p>shop-user 领券时通过 Feign 调用此接口获取模板信息</p>
     */
    @Operation(summary = "内部-获取优惠券模板", description = "根据ID获取优惠券模板信息")
    @GetMapping("/inner/{couponId}")
    public Result<Coupon> getCouponById(@PathVariable Long couponId) {
        Coupon coupon = couponService.getCouponById(couponId);
        if (coupon == null) {
            return Result.fail("优惠券不存在");
        }
        return Result.success(coupon);
    }

    /**
     * 查询可领取的优惠券列表（内部接口）
     * <p>shop-user 领券中心通过 Feign 调用此接口获取可领取的券列表</p>
     */
    @Operation(summary = "内部-可领取优惠券列表", description = "返回所有进行中且在领取时间窗口内的优惠券")
    @GetMapping("/inner/receivable")
    public Result<List<CouponVO>> getReceivableCouponList() {
        List<CouponVO> list = couponService.getReceivableCouponList(null);
        return Result.success(list);
    }

    /**
     * 增加已领取数量（内部接口）
     * <p>shop-user 领券成功后通过 Feign 调用此接口</p>
     *
     * @return true=增加成功，false=已领完
     */
    @Operation(summary = "内部-增加领取数", description = "用户领取优惠券后增加已领取数量")
    @PostMapping("/inner/{couponId}/incr-received")
    public Result<Boolean> incrReceivedCount(@PathVariable Long couponId) {
        boolean success = couponService.incrReceivedCount(couponId);
        return Result.success(success);
    }

    /**
     * 增加已使用数量（内部接口）
     * <p>shop-order 核销优惠券时通过 Feign 调用此接口</p>
     */
    @Operation(summary = "内部-增加使用数", description = "用户核销优惠券后增加已使用数量")
    @PostMapping("/inner/{couponId}/incr-used")
    public Result<Void> incrUsedCount(@PathVariable Long couponId) {
        couponService.incrUsedCount(couponId);
        return Result.success(null);
    }

    /**
     * 减少已使用数量（内部接口，订单取消回退时用）
     */
    @Operation(summary = "内部-减少使用数", description = "订单取消时回退优惠券使用数量")
    @PostMapping("/inner/{couponId}/decr-used")
    public Result<Void> decrUsedCount(@PathVariable Long couponId) {
        couponService.decrUsedCount(couponId);
        return Result.success(null);
    }

    // ==================== 私有方法 ====================

    /**
     * 通过用户ID获取商家ID
     * <p>
     * 通过 Feign 调用 shop-merchant 的内部接口，用 userId 查出对应的 merchantId。
     * 拆分前是直接调用本地的 MerchantService，拆分后商家数据在 shop-merchant，需要远程调用。
     * </p>
     *
     * @param userId 当前登录用户ID
     * @return 商家ID，如果用户不是商家则返回null
     */
    private Long getMerchantId(Long userId) {
        Result<Long> result = merchantFeignClient.getMerchantIdByUserId(userId);
        return result != null ? result.getData() : null;
    }
}
