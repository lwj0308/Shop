package com.shop.model.admin.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色信息响应数据
 * <p>
 * 返回给前端的角色详细信息，包含角色基本信息和该角色拥有的权限列表。
 * 用于角色管理页面展示和编辑时回显数据。
 * </p>
 */
@Data
public class AdminRoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色ID */
    private Long id;

    /** 角色名称，比如"运营经理"、"财务主管" */
    private String roleName;

    /** 角色标识，用于代码中做权限判断，比如"admin"、"operator" */
    private String roleKey;

    /** 数据权限范围：1全部数据 2本部门数据 3本部门及下级 4仅本人数据 */
    private Integer dataScope;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 权限列表，该角色拥有的所有权限 */
    private List<AdminPermissionVO> permissions;

    /** 创建时间 */
    private LocalDateTime createTime;
}
