package com.shop.model.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分类实体
 * <p>
 * 对应数据库 category 表，存储商品分类信息。
 * 分类是树形结构，通过 parentId 字段实现父子关系，
 * 比如：手机数码 → 手机 → 5G手机，就是三级分类。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("category")
public class Category extends BaseEntity {

    /** 父分类ID（0表示顶级分类，比如"手机数码"就是顶级分类） */
    private Long parentId;

    /** 分类名称，比如"手机数码"、"智能手机" */
    private String name;

    /** 分类图标URL，分类前面显示的小图标 */
    private String icon;

    /** 排序值，数字越小越靠前显示 */
    private Integer sort;

    /** 状态：0禁用 1启用（禁用后该分类不会在前台展示） */
    private Integer status;
}
