package com.shop.model.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体
 * <p>
 * 对应数据库 refund_order 表，存储退款申请的详细信息。
 * 用户申请退款后，会生成一条退款记录，商家审核通过后退款。
 * 一个订单可以有多条退款记录（比如买了3件商品，退了1件，后来又退了1件）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("refund_order")
public class RefundOrder extends BaseEntity {

    /** 退款单号（唯一标识，给用户看的） */
    private String refundNo;

    /** 订单ID（这个退款属于哪个订单） */
    private Long orderId;

    /** 订单号（方便查询） */
    private String orderNo;

    /** 订单明细ID（退的是哪个商品） */
    private Long orderItemId;

    /** 用户ID（谁申请的退款） */
    private Long userId;

    /** 商家ID（哪个商家的商品退款） */
    private Long merchantId;

    /** 退款金额（退多少钱） */
    private BigDecimal refundAmount;

    /** 退款原因（用户填的，比如"商品有质量问题"） */
    private String reason;

    /** 退款状态：0待审核 1已同意 2已拒绝 3退款中 4已退款（详见RefundStatusEnum） */
    private Integer status;

    /** 审核备注（商家审核时填的说明，比如"同意退款"或"不符合退款条件"） */
    private String auditNote;

    /** 审核时间（商家审核的时间） */
    private LocalDateTime auditTime;

    /** 退款完成时间（钱退回用户账户的时间） */
    private LocalDateTime refundTime;
}
