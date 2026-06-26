package com.shop.model.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 规格值实体
 * <p>
 * 对应数据库 product_spec_value 表，存储规格维度下的具体值。
 * 比如规格维度是"颜色"，那规格值就是"红色"、"蓝色"、"黑色"等。
 * 注意：这个表没有createTime/updateTime/deleted字段，所以不继承BaseEntity。
 * </p>
 */
@Data
@TableName("product_spec_value")
public class ProductSpecValue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规格值ID（雪花算法自动生成，不用手动赋值） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 规格ID，关联product_spec表，表示这个值属于哪个规格维度 */
    private Long specId;

    /** 规格值，比如"红色"、"XL"、"256GB" */
    private String value;
}
