package com.shop.model.product.dto;

import com.shop.common.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 商品搜索请求参数
 * <p>
 * 前端调用搜索商品接口时传的参数。
 * 继承 PageRequest 获得分页能力（pageNum、pageSize）。
 * 支持关键词搜索、分类筛选、品牌筛选、价格区间筛选、排序等。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductSearchDTO extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 搜索关键词，比如输入"手机"搜索相关商品 */
    private String keyword;

    /** 分类ID，按分类筛选商品 */
    private Long categoryId;

    /** 品牌ID，按品牌筛选商品 */
    private Long brandId;

    /** 最低价格，筛选大于等于这个价格的商品 */
    private BigDecimal minPrice;

    /** 最高价格，筛选小于等于这个价格的商品 */
    private BigDecimal maxPrice;

    /** 排序字段，比如"price"按价格排序，"sales"按销量排序，"createTime"按时间排序 */
    private String sortField;

    /** 排序方式：asc升序（从低到高），desc降序（从高到低） */
    private String sortOrder;
}
