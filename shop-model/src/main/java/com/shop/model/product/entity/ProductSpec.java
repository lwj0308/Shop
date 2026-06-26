package com.shop.model.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 规格模板实体
 * <p>
 * 对应数据库 product_spec 表，存储商品的规格维度。
 * 比如"颜色"、"尺码"、"存储容量"就是规格维度，
 * 每个规格维度下面有多个具体的规格值（在 ProductSpecValue 中）。
 * 注意：这个表没有createTime/updateTime/deleted字段，所以不继承BaseEntity。
 * </p>
 */
@Data
@TableName("product_spec")
public class ProductSpec implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规格ID（雪花算法自动生成，不用手动赋值） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商品ID，关联product表，表示这个规格属于哪个商品 */
    private Long productId;

    /** 规格名称，比如"颜色"、"尺码"、"存储容量" */
    private String name;
}
