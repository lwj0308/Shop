package com.shop.user.service;

import com.shop.common.model.PageResult;
import com.shop.model.coupon.dto.CouponUseDTO;
import com.shop.model.coupon.vo.CouponVO;
import com.shop.model.coupon.vo.UserCouponVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户优惠券服务接口
 * <p>
 * 定义用户优惠券的领取、查询、核销、回退等操作。
 * </p>
 */
public interface UserCouponService {

    /**
     * 用户领取优惠券
     * <p>
     * 流程：查询优惠券模板 → 校验（状态/时间/限领数/余量）→ 写入用户券 → 增加模板领取数
     * </p>
     *
     * @param userId   用户ID
     * @param couponId 优惠券模板ID
     */
    void receiveCoupon(Long userId, Long couponId);

    /**
     * 查询我的优惠券列表
     *
     * @param userId 用户ID
     * @param status 状态筛选（可选）：0未使用 1已使用 2已过期
     * @param page   页码
     * @param size   每页条数
     * @return 分页用户优惠券列表
     */
    PageResult<UserCouponVO> getMyCoupons(Long userId, Integer status, Integer page, Integer size);

    /**
     * 查询可领取的优惠券列表（领券中心用）
     *
     * @return 可领取的优惠券列表
     */
    List<CouponVO> getReceivableCouponList();

    /**
     * 查询用户可用优惠券（下单时用）
     * <p>返回未使用且在有效期内的优惠券，并计算每张券在当前订单金额下的优惠金额</p>
     *
     * @param userId       用户ID
     * @param orderAmount  订单金额
     * @return 可用优惠券列表（含优惠金额）
     */
    List<UserCouponVO> getUsableCoupons(Long userId, BigDecimal orderAmount);

    // ==================== 内部接口（供 shop-order 通过 Feign 调用） ====================

    /**
     * 核销优惠券（内部接口）
     * <p>
     * shop-order 下单时通过 Feign 调用此接口。
     * 校验用户券状态、有效期、门槛，计算优惠金额，标记为已使用。
     * </p>
     *
     * @param dto 核销参数（含用户ID、用户券ID、订单号、订单金额）
     * @return 优惠金额
     */
    BigDecimal useCoupon(CouponUseDTO dto);

    /**
     * 回退优惠券（内部接口）
     * <p>
     * shop-order 取消订单时通过 Feign 调用此接口，将优惠券恢复为未使用状态。
     * </p>
     *
     * @param orderNo 订单号
     */
    void rollbackCoupon(String orderNo);
}
