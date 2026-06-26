package com.shop.model.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付回调请求参数
 * <p>
 * 第三方支付平台（如支付宝、微信）通知我们支付结果时传过来的参数。
 * 在MVP阶段，模拟支付也会走这个DTO来模拟回调流程。
 * </p>
 */
@Data
public class PayCallbackDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 支付单号（我们系统生成的支付编号） */
    @NotBlank(message = "支付单号不能为空")
    private String paymentNo;

    /** 第三方交易号（支付宝/微信那边的订单号，用于幂等校验防重复） */
    @NotBlank(message = "第三方交易号不能为空")
    private String outTradeNo;

    /** 回调渠道（比如 wechat、alipay、mock） */
    @NotBlank(message = "回调渠道不能为空")
    private String channel;

    /** 回调数据（第三方传过来的原始数据，JSON格式） */
    @NotNull(message = "回调数据不能为空")
    private String callbackData;
}
