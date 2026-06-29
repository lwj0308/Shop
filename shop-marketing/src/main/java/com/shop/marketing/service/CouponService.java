package com.shop.marketing.service;

import com.shop.common.model.PageResult;
import com.shop.model.coupon.dto.CouponCreateDTO;
import com.shop.model.coupon.dto.CouponQueryDTO;
import com.shop.model.coupon.entity.Coupon;
import com.shop.model.coupon.vo.CouponVO;

/**
 * 优惠券服务接口
 * <p>
 * 定义优惠券模板的创建、查询、上下架等操作。
 * 商家端只能管理自己的券，管理端可以管理所有券（含平台券）。
 * </p>
 */
public interface CouponService {

    /**
     * 创建优惠券
     *
     * @param merchantId 商家ID（0表示平台券）
     * @param dto        创建参数
     * @return 优惠券ID
     */
    Long createCoupon(Long merchantId, CouponCreateDTO dto);

    /**
     * 修改优惠券
     * <p>仅待生效状态可修改</p>
     *
     * @param merchantId 商家ID（校验归属权，平台券 merchantId=0 仅管理员可改）
     * @param couponId   优惠券ID
     * @param dto        修改参数
     */
    void updateCoupon(Long merchantId, Long couponId, CouponCreateDTO dto);

    /**
     * 下架优惠券
     *
     * @param merchantId 商家ID（校验归属权）
     * @param couponId   优惠券ID
     */
    void offlineCoupon(Long merchantId, Long couponId);

    /**
     * 查询优惠券列表（商家端/管理端通用）
     *
     * @param merchantId 商家ID（null 表示管理端查询所有）
     * @param query      查询条件
     * @return 分页优惠券列表
     */
    PageResult<CouponVO> getCouponList(Long merchantId, CouponQueryDTO query);

    /**
     * 查询优惠券详情
     *
     * @param couponId 优惠券ID
     * @return 优惠券VO
     */
    CouponVO getCouponDetail(Long couponId);

    /**
     * 查询可领取的优惠券列表（用户端领券中心用）
     *
     * @param merchantId 商家ID（null 表示查询所有进行中的券）
     * @return 优惠券列表
     */
    java.util.List<CouponVO> getReceivableCouponList(Long merchantId);

    // ==================== 内部接口（供其他微服务通过 Feign 调用） ====================

    /**
     * 根据ID获取优惠券模板（内部接口）
     *
     * @param couponId 优惠券ID
     * @return 优惠券实体
     */
    Coupon getCouponById(Long couponId);

    /**
     * 增加已领取数量（内部接口）
     * <p>用户领取优惠券时，shop-user 通过 Feign 调用此接口</p>
     *
     * @param couponId 优惠券ID
     * @return 是否增加成功（false 表示已领完）
     */
    boolean incrReceivedCount(Long couponId);

    /**
     * 增加已使用数量（内部接口）
     * <p>用户核销优惠券时，shop-order 通过 shop-user 间接调用此接口</p>
     *
     * @param couponId 优惠券ID
     */
    void incrUsedCount(Long couponId);

    /**
     * 减少已使用数量（内部接口，订单取消回退时用）
     *
     * @param couponId 优惠券ID
     */
    void decrUsedCount(Long couponId);
}
