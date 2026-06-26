package com.shop.model.cart.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车项响应VO（View Object）
 * <p>
 * 返回给前端的购物车中单个商品的信息。
 * 除了购物车本身的数量和勾选状态，还包含商品的名称、图片、价格等，
 * 这些信息需要从商品服务实时获取，保证价格和库存是最新的。
 * </p>
 */
@Data
public class CartItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 购物车项ID */
    private Long id;

    /** 商品ID（SPU级别） */
    private Long productId;

    /** SKU ID（规格级别） */
    private Long skuId;

    /** 商品名称（比如"iPhone 16"） */
    private String productName;

    /** 商品主图URL */
    private String productImage;

    /** SKU规格名称（比如"黑色 128G"） */
    private String skuName;

    /** SKU价格（实时从商品服务获取，保证价格是最新的） */
    private BigDecimal skuPrice;

    /** 购买数量 */
    private Integer quantity;

    /** 是否勾选：0未勾选 1已勾选 */
    private Integer checked;

    /** 库存状态：true有货 false无货（实时从商品服务获取） */
    private Boolean inStock;

    /** 小计金额 = skuPrice × quantity（前端展示用） */
    private BigDecimal subtotal;
}
