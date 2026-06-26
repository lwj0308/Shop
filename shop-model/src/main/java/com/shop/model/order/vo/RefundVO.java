package com.shop.model.order.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单响应VO
 * <p>
 * 返回给前端的退款单信息，包含退款状态、金额、原因等。
 * </p>
 */
@Data
public class RefundVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退款单ID */
    private Long id;

    /** 退款单号 */
    private String refundNo;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 订单明细ID */
    private Long orderItemId;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String reason;

    /** 退款状态：0待审核 1已同意 2已拒绝 3已退款 */
    private Integer status;

    /** 退款状态描述（中文，比如"待审核"） */
    private String statusDesc;

    /** 审核备注 */
    private String auditNote;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
