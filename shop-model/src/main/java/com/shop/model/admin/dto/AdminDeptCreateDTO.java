package com.shop.model.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增部门请求参数
 * <p>
 * 在后台创建一个新的部门时提交的数据，比如创建"技术部"、"运营部"。
 * 部门可以有上下级关系，通过 parentId 来指定父部门。
 * </p>
 */
@Data
public class AdminDeptCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 父级部门ID，如果是顶级部门就传null或0 */
    private Long parentId;

    /** 部门名称，比如"技术部"、"运营部"，不能为空，最长50个字符 */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称长度不能超过50个字符")
    private String name;

    /** 排序号，数字越小越靠前，同级部门按此排序 */
    private Integer sort;

    /** 部门负责人姓名，最长50个字符 */
    @Size(max = 50, message = "负责人长度不能超过50个字符")
    private String leader;
}
