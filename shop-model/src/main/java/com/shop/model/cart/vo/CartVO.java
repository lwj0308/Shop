package com.shop.model.cart.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车响应VO（View Object）
 * <p>
 * 返回给前端的整个购物车信息，包含：
 * - 购物车中所有商品的列表
 * - 总数量（所有商品加起来多少件）
 * - 选中商品的总价（只算勾选的，用于结算）
 * - 是否全选（前端用来控制全选按钮的状态）
 * </p>
 */
@Data
public class CartVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 购物车项列表（里面是每个商品的详细信息） */
    private List<CartItemVO> items;

    /** 购物车总数量（所有商品的数量之和，包括未勾选的） */
    private Integer totalCount;

    /** 选中商品总价（只计算勾选的商品，用于结算展示） */
    private BigDecimal checkedTotalPrice;

    /** 是否全选（所有商品都勾选了就是true，前端用来控制全选按钮） */
    private Boolean isAllChecked;
}
