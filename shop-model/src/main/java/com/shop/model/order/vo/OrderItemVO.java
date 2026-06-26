package com.shop.model.order.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细响应VO
 * <p>
 * 返回给前端的订单商品明细信息，展示订单中每个商品的详情。
 * 包含商品快照信息（下单时的名称、价格等），不受商品后续修改的影响。
 * </p>
 */
@Data
public class OrderItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单明细ID */
    private Long id;

    /** 商品ID */
    private Long productId;

    /** SKU ID */
    private Long skuId;

    /** 商品名称快照 */
    private String productName;

    /** SKU规格快照 */
    private String skuSpec;

    /** 商品图片快照 */
    private String productImage;

    /** 商品单价快照 */
    private BigDecimal price;

    /** 购买数量 */
    private Integer quantity;

    /** 小计金额（单价 × 数量） */
    private BigDecimal subtotal;
}
