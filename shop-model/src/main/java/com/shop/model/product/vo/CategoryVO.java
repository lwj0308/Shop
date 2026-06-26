package com.shop.model.product.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类响应VO（View Object）
 * <p>
 * 返回给前端的分类信息，包含子分类列表（children），
 * 前端可以直接用这个树形结构渲染分类菜单。
 * </p>
 */
@Data
public class CategoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    private Long id;

    /** 父分类ID（0表示顶级分类） */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 分类图标URL */
    private String icon;

    /** 排序值，数字越小越靠前 */
    private Integer sort;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 子分类列表，比如"手机数码"下面有"智能手机"、"功能手机"等 */
    private List<CategoryVO> children;
}
