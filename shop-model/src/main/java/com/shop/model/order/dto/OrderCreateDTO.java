package com.shop.model.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建订单请求参数
 * <p>
 * 用户点击"提交订单"按钮时，前端传过来的参数。
 * 需要告诉后端：送到哪个地址、买哪些商品、有没有备注。
 * </p>
 */
@Data
public class OrderCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 收货地址ID（从用户的地址列表里选一个） */
    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    /** 商品列表（要买哪些商品，每个商品买几个） */
    @NotEmpty(message = "商品列表不能为空")
    @Valid
    private List<OrderItemDTO> items;

    /** 订单备注（比如"放门口"、"不要辣"） */
    private String remark;

    /** 用户优惠券ID（可选，用户下单时选择的优惠券，对应 user_coupon 表的 id） */
    private Long userCouponId;

    /** 幂等Token（防止重复提交订单，前端下单时带上） */
    private String idempotentToken;

    /**
     * 订单商品项
     * <p>
     * 表示一个要购买的商品，包含SKU ID和购买数量。
     * </p>
     */
    @Data
    public static class OrderItemDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** SKU ID（具体到规格，比如"iPhone 16 黑色 128G"的ID） */
        @NotNull(message = "SKU ID不能为空")
        private Long skuId;

        /** 购买数量（至少买1个） */
        @NotNull(message = "购买数量不能为空")
        private Integer quantity;
    }
}
