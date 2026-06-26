package com.shop.model.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录实体
 * <p>
 * 对应数据库的 payment_info 表，记录每笔支付的详细信息。
 * 比如用户下了一笔订单要付款，就会生成一条支付记录，
 * 里面包含支付单号、金额、支付方式、支付状态等。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment_info")
public class PaymentInfo extends BaseEntity {

    /** 支付单号（系统生成的唯一编号，比如 PAY20240101123456） */
    private String paymentNo;

    /** 关联订单号（这笔支付对应哪个订单） */
    private String orderNo;

    /** 用户ID（谁付的钱） */
    private Long userId;

    /** 支付金额（单位：元，BigDecimal 保留两位小数表示到分） */
    private BigDecimal amount;

    /** 支付方式：1模拟支付 2微信 3支付宝（MVP阶段只用模拟支付） */
    private Integer payType;

    /** 支付状态：0待支付 1支付中 2已支付 3已关闭 4已失败 5退款中 6已退款（见 PayStatusEnum） */
    private Integer payStatus;

    /** 支付时间（用户实际付款的时间） */
    private LocalDateTime payTime;

    /** 回调时间（第三方支付平台通知我们支付结果的时间） */
    private LocalDateTime callbackTime;
}
