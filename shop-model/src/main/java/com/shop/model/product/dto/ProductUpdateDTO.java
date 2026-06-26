package com.shop.model.product.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品编辑请求参数
 * <p>
 * 前端调用编辑商品接口时传的参数。
 * 和 ProductCreateDTO 类似，但所有字段都是可选的，
 * 只更新传了的字段，没传的保持不变。
 * </p>
 */
@Data
public class ProductUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    private Long categoryId;

    /** 品牌ID */
    private Long brandId;

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

    /** 规格列表 */
    private List<ProductCreateDTO.SpecDTO> specs;

    /** SKU列表 */
    private List<ProductCreateDTO.SkuDTO> skus;
}
