package com.shop.model.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品图片实体
 * <p>
 * 对应数据库 product_image 表，存储商品的展示图片。
 * 一个商品可以有多张图片，按sort字段排序显示。
 * 注意：这个表没有createTime/updateTime/deleted字段，所以不继承BaseEntity。
 * </p>
 */
@Data
@TableName("product_image")
public class ProductImage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 图片ID（雪花算法自动生成，不用手动赋值） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品ID，关联product表，表示这张图片属于哪个商品 */
    private Long productId;

    /** 图片URL，图片的访问地址 */
    private String url;

    /** 排序值，数字越小越靠前显示 */
    private Integer sort;
}
