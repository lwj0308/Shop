package com.shop.model.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改购物车项请求参数
 * <p>
 * 用户在购物车页面修改某个商品的数量或勾选状态时，前端传过来的参数。
 * 数量和勾选状态都是可选的，传哪个就改哪个。
 * </p>
 */
@Data
public class CartUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品数量（如果要改数量，至少为1） */
    @Min(value = 1, message = "商品数量至少为1")
    private Integer quantity;

    /** 是否勾选：0未勾选 1已勾选（null表示不改勾选状态） */
    @Min(value = 0, message = "勾选状态只能为0或1")
    @Max(value = 1, message = "勾选状态只能为0或1")
    private Integer checked;
}
