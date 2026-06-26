package com.shop.model.product.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 商品SKU响应VO
 * <p>
 * 返回给前端的SKU信息，包含规格组合、价格、库存等。
 * 用户选择不同规格组合后，前端会展示对应SKU的价格和库存。
 * </p>
 */
@Data
public class ProductSkuVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SKU ID */
    private Long id;

    /** 商品ID（SPU） */
    private Long productId;

    /** 规格值组合，比如{"颜色":"红色","存储":"128G"} */
    private Map<String, String> specValues;

    /** 销售价格 */
    private BigDecimal price;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 库存数量 */
    private Integer stock;

    /** SKU图片URL */
    private String image;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 商家ID（通过商品→店铺→商家关联获取，下单时写入订单） */
    private Long merchantId;
}
