package com.shop.model.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品发布请求参数
 * <p>
 * 前端调用发布商品接口时传的参数。
 * 发布商品时需要同时传入规格和SKU信息，
 * 后端会一次性创建SPU+规格+SKU，保证数据一致性。
 * </p>
 */
@Data
public class ProductCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类ID，商品属于哪个分类 */
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    /** 品牌ID，商品属于哪个品牌 */
    private Long brandId;

    /** 商品名称，比如"Apple iPhone 15 Pro Max" */
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称最长200个字符")
    private String name;

    /** 商品副标题，比如"全新A17 Pro芯片，钛金属设计" */
    @Size(max = 200, message = "副标题最长200个字符")
    private String subtitle;

    /** 主图URL，商品列表展示时用的图片 */
    @NotBlank(message = "主图不能为空")
    private String mainImage;

    /** 图片列表，商品的多张展示图片 */
    private List<String> images;

    /** 商品详情（富文本HTML），商品详情页展示的内容 */
    private String detail;

    /** 规格列表，定义商品有哪些规格维度（如颜色、尺码） */
    @Valid
    private List<SpecDTO> specs;

    /** SKU列表，定义商品的具体规格组合和价格库存 */
    @Valid
    private List<SkuDTO> skus;

    /**
     * 规格参数
     * <p>
     * 定义一个规格维度，比如"颜色"这个维度下面有"红色"、"蓝色"等值。
     * </p>
     */
    @Data
    public static class SpecDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 规格名称，比如"颜色"、"尺码" */
        @NotBlank(message = "规格名称不能为空")
        @Size(max = 50, message = "规格名称最长50个字符")
        private String name;

        /** 规格值列表，比如["红色","蓝色","黑色"] */
        private List<@NotBlank @Size(max = 50) String> values;
    }

    /**
     * SKU参数
     * <p>
     * 定义一个具体的规格组合，比如"红色+128G"对应一个SKU，
     * 每个SKU有独立的价格和库存。
     * </p>
     */
    @Data
    public static class SkuDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 规格值组合，比如{"颜色":"红色","存储":"128G"} */
        @NotNull(message = "规格值组合不能为空")
        private Map<String, String> specValues;

        /** 销售价格，用户实际付的钱 */
        @NotNull(message = "销售价格不能为空")
        private BigDecimal price;

        /** 原价，用来展示划线价 */
        private BigDecimal originalPrice;

        /** 库存数量，还有多少件可以卖 */
        @NotNull(message = "库存不能为空")
        private Integer stock;

        /** SKU图片URL */
        private String image;
    }
}
