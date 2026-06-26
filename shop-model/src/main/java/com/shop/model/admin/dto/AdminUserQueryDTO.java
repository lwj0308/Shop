package com.shop.model.admin.dto;

import com.shop.common.model.PageRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 管理员查询条件
 * <p>
 * 在后台查询管理员列表时，可以用这些条件进行筛选。
 * 比如按用户名搜索、按状态筛选、按部门筛选等。
 * 继承PageRequest，自带分页参数（默认第1页，每页10条，pageSize上限100）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserQueryDTO extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名，模糊搜索 */
    private String username;

    /** 昵称，模糊搜索 */
    private String nickname;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 部门ID，按部门筛选 */
    private Long deptId;
}
