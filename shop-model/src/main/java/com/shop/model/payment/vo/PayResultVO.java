package com.shop.model.payment.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付结果响应VO（View Object）
 * <p>
 * 返回给前端的支付结果，告诉用户支付是成功还是失败了。
 * 比如用户点击"立即支付"后，前端需要知道结果来跳转页面。
 * </p>
 */
@Data
public class PayResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 支付单号（方便前端查询支付详情） */
    private String paymentNo;

    /** 是否支付成功（true成功，false失败） */
    private Boolean success;

    /** 结果描述（比如"支付成功"、"余额不足"等） */
    private String message;
}
