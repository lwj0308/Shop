package com.shop.model.order.vo;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品销量排行项VO
 * <p>用于展示销量Top商品</p>
 */
@Data
public class ProductRankItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 商品名称 */
    private String productName;

    /** 销量（件数） */
    private Integer salesCount;

    /** 销售额（分） */
    private BigDecimal salesAmount;
}
