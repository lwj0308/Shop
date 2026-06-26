package com.shop.model.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算流水实体
 * <p>
 * 对应数据库 settlement_record 表，记录每笔订单的结算信息。
 * 当订单完成（用户确认收货）时，系统自动生成一条结算流水，
 * 记录这笔订单商家应得多少钱（扣除平台抽成后的金额）。
 * </p>
 * <p>
 * 结算金额计算：settlement_amount = order_amount - commission_amount
 * 其中 commission_amount = order_amount * commission_rate
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("settlement_record")
public class SettlementRecord extends BaseEntity {

    /** 商家ID */
    private Long merchantId;

    /** 订单号 */
    private String orderNo;

    /** 订单金额（元） */
    private BigDecimal orderAmount;

    /** 平台抽成比例（如0.05表示5%） */
    private BigDecimal commissionRate;

    /** 平台抽成金额（元） */
    private BigDecimal commissionAmount;

    /** 商家应得金额（元）= 订单金额 - 平台抽成 */
    private BigDecimal settlementAmount;

    /** 结算状态：0-待结算 1-已结算 2-已退款 */
    private Integer status;

    /** 结算时间（订单完成时写入） */
    private LocalDateTime settleTime;
}
