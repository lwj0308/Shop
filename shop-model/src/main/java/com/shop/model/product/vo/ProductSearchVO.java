package com.shop.model.product.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品搜索结果响应VO
 * <p>
 * 返回给前端的搜索结果，包含高亮标题（搜索关键词会被<em>标签包裹）。
 * 用于搜索结果页展示。
 * </p>
 */
@Data
public class ProductSearchVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID */
    private Long id;

    /** 商品名称（可能包含高亮标签，比如"Apple <em>iPhone</em> 15"） */
    private String name;

    /** 商品副标题 */
    private String subtitle;

    /** 主图URL */
    private String mainImage;

    /** 最低价格 */
    private BigDecimal minPrice;

    /** 总库存 */
    private Integer totalStock;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 品牌ID */
    private Long brandId;

    /** 品牌名称 */
    private String brandName;

    /** 店铺ID */
    private Long shopId;

    /** 店铺名称 */
    private String shopName;
}
