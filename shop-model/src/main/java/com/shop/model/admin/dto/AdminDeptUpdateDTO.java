package com.shop.model.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑部门请求参数
 * <p>
 * 修改部门信息时提交的数据，所有字段都是可选的，
 * 只传需要修改的字段即可。不能修改父级部门，如需调整部门层级需要走专门的移动接口。
 * </p>
 */
@Data
public class AdminDeptUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门名称，比如"技术部"、"运营部"，最长50个字符 */
    @Size(max = 50, message = "部门名称长度不能超过50个字符")
    private String name;

    /** 排序号，数字越小越靠前，同级部门按此排序 */
    private Integer sort;

    /** 部门负责人姓名，最长50个字符 */
    @Size(max = 50, message = "负责人长度不能超过50个字符")
    private String leader;

    /** 状态：0禁用 1正常，用来启用或禁用部门 */
    private Integer status;
}
