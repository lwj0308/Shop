package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门信息实体
 * <p>
 * 对应数据库 admin_dept 表，存储后台部门信息。
 * 部门用来组织管理员，配合角色的数据权限范围控制管理员能看到哪些数据。
 * 通过父子关系（parentId）构建树形部门结构，比如"技术部"下面可以有"前端组"、"后端组"。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_dept")
public class AdminDept extends BaseEntity {

    /** 父级部门ID，顶级部门的parentId为0，用来构建树形部门结构 */
    private Long parentId;

    /** 部门名称，比如"技术部"、"运营部" */
    private String name;

    /** 排序号，数字越小越靠前 */
    private Integer sort;

    /** 部门负责人姓名 */
    private String leader;

    /** 状态：0禁用 1正常（禁用后该部门下的管理员可能受影响） */
    private Integer status;
}
