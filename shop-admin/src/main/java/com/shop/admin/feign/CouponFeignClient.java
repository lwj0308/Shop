package com.shop.admin.feign;

import com.shop.admin.feign.fallback.CouponFeignClientFallbackFactory;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.coupon.dto.CouponCreateDTO;
import com.shop.model.coupon.vo.CouponVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券服务Feign客户端
 * <p>
 * shop-admin（管理后台）通过此客户端调用 shop-marketing 的优惠券管理端接口。
 * 管理员可以创建平台券、查看全平台优惠券、下架违规优惠券。
 * </p>
 * <p>
 * 鉴权说明：FeignAuthConfig 会自动把管理员的 Sa-Token 透传给 shop-marketing，
 * shop-marketing 校验登录态后放行（管理端接口不需要商家身份，只要登录即可）。
 * </p>
 */
@FeignClient(name = "shop-marketing", path = "/marketing/coupon", fallbackFactory = CouponFeignClientFallbackFactory.class)
public interface CouponFeignClient {

    /**
     * 创建平台优惠券
     * <p>
     * 管理员创建平台券（merchantId=0），全平台用户可领取。
     * </p>
     *
     * @param dto 优惠券参数
     * @return 优惠券ID
     */
    @PostMapping("/admin/create")
    Result<Long> adminCreateCoupon(@RequestBody CouponCreateDTO dto);

    /**
     * 分页查询全平台优惠券
     * <p>
     * 管理员查看所有优惠券（含平台券和商家券），支持按状态和类型筛选。
     * </p>
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   优惠券状态（可选）：0待生效 1进行中 2已结束 3已下架
     * @param type     优惠券类型（可选）：1满减 2折扣 3立减
     * @return 分页优惠券列表
     */
    @GetMapping("/admin/list")
    Result<PageResult<CouponVO>> adminGetCouponList(@RequestParam("pageNum") int pageNum,
                                                     @RequestParam("pageSize") int pageSize,
                                                     @RequestParam(value = "status", required = false) Integer status,
                                                     @RequestParam(value = "type", required = false) Integer type);

    /**
     * 下架优惠券
     * <p>
     * 管理员下架任意优惠券（含商家券），下架后用户不能再领取，已领取的券仍可使用。
     * </p>
     *
     * @param couponId 优惠券ID
     * @return 操作结果
     */
    @PutMapping("/admin/{couponId}/offline")
    Result<Void> adminOfflineCoupon(@PathVariable("couponId") Long couponId);
}
