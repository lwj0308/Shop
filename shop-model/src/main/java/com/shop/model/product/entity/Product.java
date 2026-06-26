package com.shop.model.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 商品SPU实体
 * <p>
 * 对应数据库 product 表，存储商品的标准信息（SPU = Standard Product Unit）。
 * SPU是商品的最小信息单位，比如"iPhone 15"就是一个SPU。
 * 一个SPU下面可以有多个SKU（不同颜色、不同存储容量等）。
 * images字段用JSON格式存储图片列表，比如["img1.jpg","img2.jpg"]。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "product", autoResultMap = true)
public class Product extends BaseEntity {

    /** 分类ID，关联category表，表示这个商品属于哪个分类 */
    private Long categoryId;

    /** 品牌ID，关联brand表，表示这个商品是哪个品牌的 */
    private Long brandId;

    /** 店铺ID，关联shop表，表示这个商品是哪个店铺卖的 */
    private Long shopId;

    /** 商品名称，比如"Apple iPhone 15 Pro Max" */
    private String name;

    /** 商品副标题，比如"全新A17 Pro芯片，钛金属设计" */
    private String subtitle;

    /** 主图URL，商品列表展示时用的图片 */
    private String mainImage;

    /** 图片列表（JSON数组），存储商品的多张展示图片 */
    @com.baomidou.mybatisplus.annotation.TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    /** 商品详情（富文本HTML），商品详情页展示的内容 */
    private String detail;

    /** 状态：0下架 1上架（下架后用户看不到这个商品） */
    private Integer status;

    /** 销量（用户下单时累加，用于热销推荐排序，值越大越热门） */
    private Integer sales;

    /** 浏览量（用户查看商品详情时累加，用于热度统计） */
    private Integer viewCount;
}
