package com.shop.model.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品分类请求参数
 * <p>
 * 前端调用添加/修改分类接口时传的参数。
 * 分类是树形结构，parentId=0表示顶级分类。
 * </p>
 */
@Data
public class CategoryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父分类ID（0表示顶级分类，比如"手机数码"就是顶级分类） */
    @NotNull(message = "父分类ID不能为空")
    private Long parentId;

    /** 分类名称，比如"手机数码"、"智能手机" */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长50个字符")
    private String name;

    /** 分类图标URL */
    @Size(max = 255, message = "图标URL最长255个字符")
    private String icon;

    /** 排序值，数字越小越靠前显示 */
    private Integer sort;
}
