package com.shop.model.coupon.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建优惠券请求DTO
 * <p>
 * 商家或管理员创建优惠券时提交的参数。
 * 商家创建时 merchantId 由后端从登录态获取，管理员创建时 merchantId=0（平台券）。
 * </p>
 */
@Data
public class CouponCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 优惠券名称（如"满100减20"） */
    private String name;

    /** 类型：1满减 2折扣 3立减（对应 CouponTypeEnum） */
    private Integer type;

    /**
     * 面额
     * - 满减/立减：金额（如 20.00 表示减20元）
     * - 折扣：折扣率（如 0.85 表示85折）
     */
    private BigDecimal amount;

    /** 使用门槛金额（满减用，满多少元可用；立减和折扣传0） */
    private BigDecimal threshold;

    /** 发放总量（0表示不限量） */
    private Integer totalCount;

    /** 每人限领数量（默认1） */
    private Integer perLimit;

    /** 领取开始时间 */
    private LocalDateTime receiveStartTime;

    /** 领取结束时间 */
    private LocalDateTime receiveEndTime;

    /** 有效期开始时间 */
    private LocalDateTime validStartTime;

    /** 有效期结束时间 */
    private LocalDateTime validEndTime;

    /** 描述说明 */
    private String description;
}
