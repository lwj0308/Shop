package com.shop.model.payment.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付信息响应VO（View Object）
 * <p>
 * 返回给前端的支付详细信息，包含支付单号、金额、状态等。
 * 比如用户查看订单支付详情时，就返回这个对象。
 * </p>
 */
@Data
public class PaymentVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 支付ID */
    private Long id;

    /** 支付单号（系统生成的唯一编号） */
    private String paymentNo;

    /** 关联订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 支付金额（单位：元） */
    private BigDecimal amount;

    /** 支付方式：1模拟支付 2微信 3支付宝 */
    private Integer payType;

    /** 支付状态：0待支付 1支付中 2已支付 3已关闭 4已失败 5退款中 6已退款（见 PayStatusEnum） */
    private Integer payStatus;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
