package com.shop.model.coupon.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板响应VO
 * <p>
 * 返回给前端的优惠券模板信息。
 * type 和 status 字段额外提供 desc 描述，前端可以直接展示中文，不用再维护映射表。
 * </p>
 */
@Data
public class CouponVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 优惠券ID */
    private Long id;

    /** 商家ID（0表示平台券） */
    private Long merchantId;

    /** 优惠券名称 */
    private String name;

    /** 类型：1满减 2折扣 3立减 */
    private Integer type;

    /** 类型描述（如"满减"、"折扣"，由后端根据 type 翻译） */
    private String typeDesc;

    /** 面额（满减/立减为金额，折扣为折扣率） */
    private BigDecimal amount;

    /** 使用门槛金额 */
    private BigDecimal threshold;

    /** 发放总量（0表示不限量） */
    private Integer totalCount;

    /** 已领取数量 */
    private Integer receivedCount;

    /** 已使用数量 */
    private Integer usedCount;

    /** 每人限领数量 */
    private Integer perLimit;

    /** 领取开始时间 */
    private LocalDateTime receiveStartTime;

    /** 领取结束时间 */
    private LocalDateTime receiveEndTime;

    /** 有效期开始时间 */
    private LocalDateTime validStartTime;

    /** 有效期结束时间 */
    private LocalDateTime validEndTime;

    /** 状态：0待生效 1进行中 2已结束 3已下架 */
    private Integer status;

    /** 状态描述（如"进行中"、"已结束"，由后端根据 status 翻译） */
    private String statusDesc;

    /** 描述说明 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 剩余可领取数量（领券中心展示用）
     * 计算方式：totalCount=0 表示不限量返回 -1；否则返回 totalCount - receivedCount
     */
    private Integer remainCount;
}
