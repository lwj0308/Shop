package com.shop.model.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 新增角色请求参数
 * <p>
 * 在后台创建一个新的角色时提交的数据，比如创建"运营经理"角色。
 * 角色名和角色标识是必填的，同时可以给角色分配权限。
 * </p>
 */
@Data
public class AdminRoleCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称，比如"运营经理"、"财务主管"，不能为空，最长50个字符 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过50个字符")
    private String roleName;

    /** 角色标识，用于代码中做权限判断，比如"admin"、"operator"，不能为空，最长50个字符 */
    @NotBlank(message = "角色标识不能为空")
    @Size(max = 50, message = "角色标识长度不能超过50个字符")
    private String roleKey;

    /** 数据权限范围：1全部数据 2本部门数据 3本部门及下级 4仅本人数据 */
    private Integer dataScope;

    /** 备注，对角色的补充说明，最长200个字符 */
    @Size(max = 200, message = "备注长度不能超过200个字符")
    private String remark;

    /** 权限ID列表，给角色分配的权限，一个角色可以有多个权限 */
    private List<Long> permissionIds;
}
