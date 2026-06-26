package com.shop.model.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建支付请求参数
 * <p>
 * 用户下单后要付款时，前端传过来的参数。
 * 必须告诉后端：哪个订单、付多少钱、用什么方式付。
 * </p>
 */
@Data
public class PayCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号（这笔支付对应哪个订单） */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 支付金额（单位：元，必须大于0） */
    @NotNull(message = "支付金额不能为空")
    @DecimalMin(value = "0.01", message = "支付金额必须大于0")
    private BigDecimal amount;

    /** 支付方式：1模拟支付 2微信 3支付宝（MVP阶段传1即可） */
    @NotNull(message = "支付方式不能为空")
    private Integer payType;
}
