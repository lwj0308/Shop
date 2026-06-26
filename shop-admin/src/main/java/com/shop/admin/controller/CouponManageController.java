package com.shop.admin.controller;

import com.shop.admin.annotation.OperationLog;
import com.shop.admin.annotation.OperationType;
import com.shop.admin.annotation.RequirePermission;
import com.shop.admin.feign.CouponFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponCreateDTO;
import com.shop.model.coupon.vo.CouponVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券管理控制器
 * <p>
 * 管理后台对优惠券的管理接口，包括创建平台券、查看全平台优惠券、下架优惠券。
 * 所有接口都需要管理员拥有对应的权限才能访问（超级管理员自动放行）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/manage/coupon")
@Tag(name = "优惠券管理", description = "管理后台对优惠券的管理接口")
@RequiredArgsConstructor
public class CouponManageController {

    /** 优惠券服务Feign客户端，远程调用商家服务 */
    private final CouponFeignClient couponFeignClient;

    /**
     * 创建平台优惠券
     * <p>
     * 管理员创建平台券（merchantId=0），全平台用户可领取。
     * 需要 coupon:create 权限。
     * </p>
     *
     * @param dto 优惠券参数
     * @return 优惠券ID
     */
    @Operation(summary = "创建平台券", description = "管理员创建平台优惠券，全平台用户可领取")
    @PostMapping
    @RequirePermission("coupon:create")
    @OperationLog(module = "优惠券管理", type = OperationType.CREATE, description = "创建平台券：#dto.name")
    public Result<Long> createCoupon(@RequestBody @Validated CouponCreateDTO dto) {
        return couponFeignClient.adminCreateCoupon(dto);
    }

    /**
     * 分页查询全平台优惠券
     * <p>
     * 管理员查看所有优惠券（含平台券和商家券），支持按状态和类型筛选。
     * 需要 coupon:list 权限。
     * </p>
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   优惠券状态（可选）
     * @param type     优惠券类型（可选）
     * @return 分页优惠券列表
     */
    @Operation(summary = "查询优惠券列表", description = "分页查询全平台优惠券，支持条件筛选")
    @GetMapping("/list")
    @RequirePermission("coupon:list")
    @OperationLog(module = "优惠券管理", type = OperationType.QUERY, description = "查询优惠券列表")
    public Result<PageResult<CouponVO>> listCoupons(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(required = false) Integer type) {
        return couponFeignClient.adminGetCouponList(pageNum, pageSize, status, type);
    }

    /**
     * 下架优惠券
     * <p>
     * 管理员下架任意优惠券（含商家券），下架后用户不能再领取，已领取的券仍可使用。
     * 需要 coupon:offline 权限。
     * </p>
     *
     * @param couponId 优惠券ID
     * @return 操作结果
     */
    @Operation(summary = "下架优惠券", description = "下架后用户不能再领取，已领取的券仍可使用")
    @PutMapping("/{couponId}/offline")
    @RequirePermission("coupon:offline")
    @OperationLog(module = "优惠券管理", type = OperationType.UPDATE, description = "下架优惠券：#couponId")
    public Result<Void> offlineCoupon(@PathVariable Long couponId) {
        return couponFeignClient.adminOfflineCoupon(couponId);
    }
}
