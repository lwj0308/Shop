package com.shop.model.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增权限/菜单请求参数
 * <p>
 * 在后台创建一个新的权限或菜单时提交的数据。
 * 权限分三种类型：目录（用来分组）、菜单（页面）、按钮（操作权限）。
 * 比如创建"用户管理"菜单，或者在"用户管理"下创建"新增用户"按钮权限。
 * </p>
 */
@Data
public class AdminPermissionCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父级权限ID，如果是顶级菜单就传null或0 */
    private Long parentId;

    /** 权限/菜单名称，比如"用户管理"、"新增用户"，不能为空，最长50个字符 */
    @NotBlank(message = "权限名称不能为空")
    @Size(max = 50, message = "权限名称长度不能超过50个字符")
    private String name;

    /** 权限类型：1目录 2菜单 3按钮，不能为空 */
    @NotNull(message = "权限类型不能为空")
    private Integer type;

    /** 权限标识，用于代码中做权限判断，比如"user:create"、"user:delete"，最长100个字符 */
    @Size(max = 100, message = "权限标识长度不能超过100个字符")
    private String permissionKey;

    /** 菜单路径，前端路由地址，比如"/system/user"，最长200个字符 */
    @Size(max = 200, message = "菜单路径长度不能超过200个字符")
    private String path;

    /** 菜单图标，前端显示的图标名称，最长50个字符 */
    @Size(max = 50, message = "图标长度不能超过50个字符")
    private String icon;

    /** 排序号，数字越小越靠前，同级菜单按此排序 */
    private Integer sort;
}
