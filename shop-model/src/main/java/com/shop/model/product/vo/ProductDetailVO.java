package com.shop.model.product.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 商品详情响应VO
 * <p>
 * 返回给前端的商品详情信息，比ProductVO更丰富，
 * 包含SPU信息 + SKU列表 + 规格列表 + 评价摘要 + 店铺信息。
 * 用于商品详情页展示。
 * </p>
 */
@Data
public class ProductDetailVO implements Serializable {

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

    /** 店铺名称 */
    private String shopName;

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

    /** 最低价格 */
    private BigDecimal minPrice;

    /** 总库存 */
    private Integer totalStock;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** SKU列表 */
    private List<ProductSkuVO> skus;

    /** 规格列表 */
    private List<ProductVO.SpecVO> specs;

    /** 评价摘要 */
    private CommentSummary commentSummary;

    /**
     * 评价摘要
     * <p>
     * 商品详情页展示的评价概要信息，
     * 包括总评价数、平均评分、好评率等。
     * </p>
     */
    @Data
    public static class CommentSummary implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 总评价数 */
        private Long totalCount;

        /** 平均评分 */
        private BigDecimal avgScore;

        /** 好评率（4-5分算好评） */
        private BigDecimal goodRate;
    }
}
