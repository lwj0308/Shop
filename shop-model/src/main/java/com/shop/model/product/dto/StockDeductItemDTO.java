package com.shop.model.product.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库存批量扣减项
 * <p>
 * 用于 shop-order 一次性把订单里所有 SKU 的扣减请求打包传给 shop-product，
 * 避免循环 N 次远程调用（N+1 查询问题）。
 * 小白理解：原来买 3 件商品要打 3 次电话给商品服务扣库存，
 * 现在把 3 件商品的扣减信息装进一个列表，打 1 次电话就搞定。
 * </p>
 */
@Data
public class StockDeductItemDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SKU ID（要扣减哪个规格的库存） */
    private Long skuId;

    /** 扣减数量（用户购买几件） */
    private Integer quantity;
}
