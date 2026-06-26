package com.shop.model.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细实体
 * <p>
 * 对应数据库 order_item 表，存储订单中每个商品的详细信息。
 * 一个订单可以包含多个商品，每个商品就是一条明细记录。
 * 这里面存的是商品快照（下单时的价格、名称等），即使商品后来改了价格，
 * 订单里的价格也不会变，这就是"快照"的意思。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_item")
public class OrderItem extends BaseEntity {

    /** 订单ID（这条明细属于哪个订单） */
    private Long orderId;

    /** 订单号（方便查询，不用每次都join order_info表） */
    private String orderNo;

    /** 商品ID（SPU级别，比如"iPhone 16"的ID） */
    private Long productId;

    /** SKU ID（规格级别，比如"iPhone 16 黑色 128G"的ID） */
    private Long skuId;

    /** 商品名称快照（下单时的商品名称，后续商品改名不影响订单） */
    private String productName;

    /** SKU规格快照（下单时的规格信息，比如"黑色 128G"） */
    private String skuSpec;

    /** 商品图片快照（下单时的商品主图URL） */
    private String productImage;

    /** 商品单价快照（下单时的价格，不是现在的价格） */
    private BigDecimal price;

    /** 购买数量 */
    private Integer quantity;

    /** 小计金额（单价 × 数量） */
    private BigDecimal subtotal;
}
