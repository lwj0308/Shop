package com.shop.model.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量勾选/取消勾选请求参数
 * <p>
 * 用户在购物车页面勾选多个商品或取消勾选时，前端传过来的参数。
 * 比如用户勾选了3个商品准备结算，就传这3个购物车项的ID。
 * </p>
 */
@Data
public class CartCheckDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 购物车项ID列表（要勾选或取消勾选的那些商品） */
    @NotEmpty(message = "购物车项ID列表不能为空")
    private List<Long> cartItemIds;

    /** 是否勾选：0取消勾选 1勾选 */
    @NotNull(message = "勾选状态不能为空")
    @Min(value = 0, message = "勾选状态只能为0或1")
    @Max(value = 1, message = "勾选状态只能为0或1")
    private Integer checked;
}
