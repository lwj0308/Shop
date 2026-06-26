package com.shop.model.admin.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 部门信息响应数据（树形结构）
 * <p>
 * 返回给前端的部门信息，支持树形结构展示。
 * 通过 children 字段实现父子层级关系，前端可以递归渲染成部门树。
 * 比如顶层是"总公司"，下面有"技术部"、"运营部"，技术部下面还有"前端组"、"后端组"。
 * </p>
 */
@Data
public class AdminDeptVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门ID */
    private Long id;

    /** 父级部门ID，0表示顶级部门 */
    private Long parentId;

    /** 部门名称，比如"技术部"、"运营部" */
    private String name;

    /** 排序号，数字越小越靠前 */
    private Integer sort;

    /** 部门负责人姓名 */
    private String leader;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 子部门列表，树形结构的下级节点 */
    private List<AdminDeptVO> children;
}
