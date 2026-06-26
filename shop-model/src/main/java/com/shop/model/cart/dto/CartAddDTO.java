package com.shop.model.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 加入购物车请求参数
 * <p>
 * 用户点击"加入购物车"按钮时，前端传过来的参数。
 * 必须告诉后端：哪个商品、哪个规格、买几个。
 * </p>
 */
@Data
public class CartAddDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID（SPU级别，比如"iPhone 16"的ID） */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** SKU ID（规格级别，比如"iPhone 16 黑色 128G"的ID） */
    @NotNull(message = "SKU ID不能为空")
    private Long skuId;

    /** 商品数量（至少买1个） */
    @NotNull(message = "商品数量不能为空")
    @Min(value = 1, message = "商品数量至少为1")
    private Integer quantity;
}
