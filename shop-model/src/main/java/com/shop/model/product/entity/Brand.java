package com.shop.model.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌实体
 * <p>
 * 对应数据库 brand 表，存储品牌信息。
 * 比如苹果、华为、小米等品牌，商品可以关联一个品牌。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("brand")
public class Brand extends BaseEntity {

    /** 品牌名称，比如"苹果"、"华为" */
    private String name;

    /** 品牌Logo图片URL */
    private String logo;

    /** 品牌描述，介绍品牌的故事和特色 */
    private String description;
}
