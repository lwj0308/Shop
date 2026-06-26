package com.shop.model.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体
 * <p>
 * 对应数据库 coupon 表，存储商家或平台创建的优惠券模板。
 * 优惠券模板定义了优惠券的规则（类型、面额、门槛）、发放量、领取和使用时间窗口等。
 * </p>
 * <p>
 * merchant_id=0 表示平台券（管理员创建），>0 表示商家券（对应商家创建）。
 * type=1满减（满threshold元减amount元），type=2折扣（打amount折），type=3立减（无门槛减amount元）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon")
public class Coupon extends BaseEntity {

    /** 商家ID（0表示平台券，>0表示对应商家创建的券） */
    private Long merchantId;

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

    /** 使用门槛金额（满减用，满多少元可用；立减和折扣为0） */
    private BigDecimal threshold;

    /** 发放总量（0表示不限量） */
    private Integer totalCount;

    /** 已领取数量 */
    private Integer receivedCount;

    /** 已使用数量 */
    private Integer usedCount;

    /** 每人限领数量（默认1） */
    private Integer perLimit;

    /** 领取开始时间 */
    private LocalDateTime receiveStartTime;

    /** 领取结束时间 */
    private LocalDateTime receiveEndTime;

    /** 有效期开始时间（使用优惠券的时间窗口起点） */
    private LocalDateTime validStartTime;

    /** 有效期结束时间（使用优惠券的时间窗口终点） */
    private LocalDateTime validEndTime;

    /** 状态：0待生效 1进行中 2已结束 3已下架（对应 CouponStatusEnum） */
    private Integer status;

    /** 描述说明 */
    private String description;
}
