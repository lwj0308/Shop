package com.shop.model.admin.dto;

import com.shop.common.model.PageRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 角色查询条件
 * <p>
 * 在后台查询角色列表时，可以用这些条件进行筛选。
 * 比如按角色名称搜索、按角色标识搜索、按状态筛选等。
 * 继承PageRequest，自带分页参数（默认第1页，每页10条，pageSize上限100）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminRoleQueryDTO extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色名称，模糊搜索 */
    private String roleName;

    /** 角色标识，模糊搜索 */
    private String roleKey;

    /** 状态：0禁用 1正常 */
    private Integer status;
}
