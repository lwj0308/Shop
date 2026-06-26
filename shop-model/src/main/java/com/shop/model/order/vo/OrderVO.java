package com.shop.model.order.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表响应VO
 * <p>
 * 返回给前端的订单列表项信息，用于订单列表页展示。
 * 只包含订单的核心信息，不包含明细、地址等详细信息。
 * </p>
 */
@Data
public class OrderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单ID */
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 实付金额 */
    private BigDecimal payAmount;

    /** 订单状态：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款 */
    private Integer status;

    /** 订单状态描述（中文，方便前端直接展示，比如"待付款"） */
    private String statusDesc;

    /** 订单中第一件商品的图片（列表页展示用） */
    private String firstItemImage;

    /** 订单中商品的总数量 */
    private Integer totalQuantity;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 支付时间 */
    private LocalDateTime payTime;
}
