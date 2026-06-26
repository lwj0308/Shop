package com.shop.model.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付回调日志实体
 * <p>
 * 对应数据库的 payment_callback 表，记录第三方支付平台回调我们的数据。
 * 比如用户在支付宝付完钱后，支付宝会通知我们"这笔钱收到了"，
 * 我们就把这个通知的内容记录下来，方便对账和排查问题。
 * </p>
 * <p>
 * 重要：out_trade_no 字段有唯一索引，用来防止重复处理同一笔回调（幂等性保证）。
 * 如果支付宝因为网络原因发了两次同样的回调，第二次插入会失败，不会重复处理。
 * 注意：这个表没有updateTime/deleted字段，所以不继承BaseEntity。
 * </p>
 */
@Data
@TableName("payment_callback")
public class PaymentCallback implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 回调ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 支付ID（关联payment_info表，知道这条回调对应哪笔支付） */
    private Long paymentId;

    /** 回调渠道（比如 wechat、alipay、mock 等，表示是哪个平台通知我们的） */
    private String channel;

    /** 回调数据（第三方传过来的原始数据，JSON格式，方便排查问题） */
    private String callbackData;

    /** 第三方交易号（支付宝/微信那边的订单号，唯一索引保证幂等） */
    private String outTradeNo;

    /** 创建时间（新增时自动填充） */
    private LocalDateTime createTime;
}
