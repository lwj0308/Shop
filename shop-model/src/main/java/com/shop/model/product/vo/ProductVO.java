package com.shop.model.product.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 商品SPU响应VO
 * <p>
 * 返回给前端的商品信息，包含SKU列表和规格列表。
 * 前端可以用这些数据渲染商品详情页。
 * </p>
 */
@Data
public class ProductVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品ID */
    private Long id;

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

    /** 商品名称 */
    private String name;

    /** 商品副标题 */
    private String subtitle;

    /** 主图URL */
    private String mainImage;

    /** 图片列表 */
    private List<String> images;

    /** 商品详情（富文本HTML） */
    private String detail;

    /** 状态：0下架 1上架 */
    private Integer status;

    /** 销量（用于热销推荐排序，值越大越热门） */
    private Integer sales;

    /** 浏览量（用于热度统计） */
    private Integer viewCount;

    /** 最低价格（从所有SKU中取最低价，方便列表页展示"¥XX起"） */
    private BigDecimal minPrice;

    /** 总库存（所有SKU库存之和） */
    private Integer totalStock;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** SKU列表，商品的所有规格组合 */
    private List<ProductSkuVO> skus;

    /** 规格列表，商品的所有规格维度 */
    private List<SpecVO> specs;

    /**
     * 规格VO
     * <p>
     * 一个规格维度及其所有值，比如"颜色"下面有"红色"、"蓝色"。
     * </p>
     */
    @Data
    public static class SpecVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 规格名称 */
        private String name;

        /** 规格值列表 */
        private List<String> values;
    }
}
