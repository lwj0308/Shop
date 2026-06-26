package com.shop.model.cart.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车项实体
 * <p>
 * 对应数据库的 cart_item 表，记录用户购物车里的每一条商品。
 * 比如用户把"iPhone 16 黑色 128G"加入购物车，就会生成一条记录。
 * 同一个SKU加购多次不会新增记录，而是把数量累加。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cart_item")
public class CartItem extends BaseEntity {

    /** 用户ID（这个商品属于哪个用户的购物车） */
    private Long userId;

    /** 商品ID（SPU级别，比如"iPhone 16"这个商品） */
    private Long productId;

    /** SKU ID（规格级别，比如"iPhone 16 黑色 128G"这个具体规格） */
    private Long skuId;

    /** 商品数量（同一SKU多次加购会累加这个值） */
    private Integer quantity;

    /** 是否勾选：0未勾选 1已勾选（结算时只算勾选的商品） */
    private Integer checked;
}
