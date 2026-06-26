package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色信息实体
 * <p>
 * 对应数据库 admin_role 表，存储后台角色信息。
 * 角色是权限管理的核心概念，比如"超级管理员"、"运营"、"客服"等。
 * 一个管理员可以拥有多个角色，一个角色可以拥有多个权限（RBAC模型）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_role")
public class AdminRole extends BaseEntity {

    /** 角色名称，比如"超级管理员"、"运营专员" */
    private String roleName;

    /** 角色标识，代码里用来判断权限的key，比如"admin"、"operator" */
    private String roleKey;

    /** 数据权限范围：1全部数据 2本部门数据 3本部门及下级部门数据 4仅本人数据 */
    private Integer dataScope;

    /** 状态：0禁用 1正常（禁用后该角色下的管理员失去对应权限） */
    private Integer status;

    /** 备注，对角色的补充说明 */
    private String remark;
}
