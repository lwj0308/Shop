package com.shop.model.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 编辑角色请求参数
 * <p>
 * 修改角色信息时提交的数据，和新增不同，所有字段都是可选的，
 * 只传需要修改的字段即可。
 * </p>
 */
@Data
public class AdminRoleUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称，比如"运营经理"、"财务主管"，最长50个字符 */
    @Size(max = 50, message = "角色名称长度不能超过50个字符")
    private String roleName;

    /** 角色标识，用于代码中做权限判断，比如"admin"、"operator"，最长50个字符 */
    @Size(max = 50, message = "角色标识长度不能超过50个字符")
    private String roleKey;

    /** 数据权限范围：1全部数据 2本部门数据 3本部门及下级 4仅本人数据 */
    private Integer dataScope;

    /** 状态：0禁用 1正常，用来启用或禁用角色 */
    private Integer status;

    /** 备注，对角色的补充说明，最长200个字符 */
    @Size(max = 200, message = "备注长度不能超过200个字符")
    private String remark;

    /** 权限ID列表，重新分配的权限，会覆盖之前的权限 */
    private List<Long> permissionIds;
}
