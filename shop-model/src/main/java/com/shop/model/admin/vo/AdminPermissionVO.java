package com.shop.model.admin.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 权限/菜单信息响应数据（树形结构）
 * <p>
 * 返回给前端的权限/菜单信息，支持树形结构展示。
 * 通过 children 字段实现父子层级关系，前端可以递归渲染成菜单树。
 * 比如顶层是"系统管理"目录，下面有"用户管理"菜单，菜单下有"新增用户"按钮。
 * </p>
 */
@Data
public class AdminPermissionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 权限ID */
    private Long id;

    /** 父级权限ID，0表示顶级 */
    private Long parentId;

    /** 权限/菜单名称，比如"用户管理"、"新增用户" */
    private String name;

    /** 权限类型：1目录 2菜单 3按钮 */
    private Integer type;

    /** 权限标识，用于代码中做权限判断，比如"user:create" */
    private String permissionKey;

    /** 菜单路径，前端路由地址，比如"/system/user" */
    private String path;

    /** 菜单图标，前端显示的图标名称 */
    private String icon;

    /** 排序号，数字越小越靠前 */
    private Integer sort;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 子权限列表，树形结构的下级节点 */
    private List<AdminPermissionVO> children;
}
