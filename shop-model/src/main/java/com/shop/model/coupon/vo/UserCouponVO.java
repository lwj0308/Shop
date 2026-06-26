package com.shop.model.coupon.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券响应VO
 * <p>
 * 返回给前端的用户优惠券信息（"我的优惠券"列表）。
 * 包含优惠券模板的冗余信息（名称/面额/门槛等），前端可以直接展示。
 * couponType 字段额外提供 desc 描述。
 * </p>
 */
@Data
public class UserCouponVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户券ID（user_coupon 表的主键） */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 优惠券模板ID */
    private Long couponId;

    /** 商家ID（0表示平台券） */
    private Long merchantId;

    /** 优惠券名称 */
    private String couponName;

    /** 优惠券类型：1满减 2折扣 3立减 */
    private Integer couponType;

    /** 优惠券类型描述（如"满减"、"折扣"，由后端翻译） */
    private String couponTypeDesc;

    /** 面额 */
    private BigDecimal amount;

    /** 使用门槛 */
    private BigDecimal threshold;

    /** 有效期开始时间 */
    private LocalDateTime validStartTime;

    /** 有效期结束时间 */
    private LocalDateTime validEndTime;

    /** 状态：0未使用 1已使用 2已过期 */
    private Integer status;

    /** 状态描述（如"未使用"、"已使用"，由后端翻译） */
    private String statusDesc;

    /** 使用的订单号 */
    private String orderNo;

    /** 领取时间 */
    private LocalDateTime getTime;

    /** 使用时间 */
    private LocalDateTime useTime;

    /**
     * 计算优惠金额（前端展示用，可选）
     * 满减：amount 元
     * 立减：amount 元
     * 折扣：orderAmount * (1 - amount) 元
     */
    private BigDecimal discountAmount;
}
