package com.shop.model.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体
 * <p>
 * 对应数据库 order_info 表，存储订单的核心信息。
 * 一个订单就是用户一次"买买买"的记录，包括订单号、金额、状态等。
 * 订单号用雪花算法生成，不用数据库自增，方便以后分库分表。
 * 订单状态是状态机模式：0待付款→1已取消→2待发货→3运输中→4已收货→5已完成→6退款中→7已退款
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_info")
public class OrderInfo extends BaseEntity {

    /** 订单号（雪花算法生成，给用户看的，比如"202401011234567890"） */
    private String orderNo;

    /** 用户ID（谁下的单） */
    private Long userId;

    /** 商家ID（卖家的ID） */
    private Long merchantId;

    /** 订单总金额（所有商品价格加起来的总和） */
    private BigDecimal totalAmount;

    /** 实付金额（用户实际付的钱，可能有优惠） */
    private BigDecimal payAmount;

    /** 运费金额 */
    private BigDecimal freightAmount;

    /** 优惠金额（打折、满减等省了多少钱） */
    private BigDecimal discountAmount;

    /** 满减优惠金额（仅满减活动的优惠，用于取消订单时区分满减和优惠券） */
    private BigDecimal promotionDiscount;

    /** 订单类型：1普通订单 2秒杀订单（默认1，秒杀下单时设为2） */
    private Integer orderType;

    /** 秒杀活动ID（仅秒杀订单有值，普通订单为null；取消订单时用于回退Redis秒杀库存） */
    private Long seckillId;

    /** 是否已评价：0未评价 1已评价（订单完成后用户可评价，防重复评价用） */
    private Integer isReviewed;

    /** 订单状态：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款（详见OrderStatusEnum） */
    private Integer status;

    /** 支付时间（用户付完钱的时间） */
    private LocalDateTime payTime;

    /** 发货时间（商家发货的时间） */
    private LocalDateTime deliveryTime;

    /** 收货时间（用户确认收货的时间） */
    private LocalDateTime receiveTime;

    /** 完成时间（订单变成"已完成"的时间） */
    private LocalDateTime finishTime;

    /** 取消时间（订单被取消的时间） */
    private LocalDateTime cancelTime;

    /** 订单备注（用户下单时填的备注，比如"放门口"） */
    private String remark;

    /** 取消原因（订单被取消的原因，比如"不想买了"） */
    private String cancelReason;
}
