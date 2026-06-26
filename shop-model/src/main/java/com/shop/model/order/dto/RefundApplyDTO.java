package com.shop.model.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退款申请请求参数
 * <p>
 * 用户申请退款时填写的参数，需要指定退哪个商品和退款原因。
 * 只有"待发货"状态的订单才能申请退款。
 * </p>
 */
@Data
public class RefundApplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单ID（哪个订单要退款） */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 订单明细ID（退的是订单里的哪个商品） */
    @NotNull(message = "订单明细ID不能为空")
    private Long orderItemId;

    /** 退款原因（比如"商品有质量问题"、"发错货了"） */
    @NotBlank(message = "退款原因不能为空")
    private String reason;

    /** 退款金额（不传则默认退该商品的全额，传了则不能超过实付金额） */
    private BigDecimal refundAmount;
}
