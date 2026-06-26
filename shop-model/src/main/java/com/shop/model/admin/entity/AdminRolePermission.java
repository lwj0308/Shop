package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色-权限关联实体
 * <p>
 * 对应数据库 admin_role_permission 表，存储角色和权限的对应关系。
 * 这是一张中间表，实现"一个角色可以拥有多个权限"的多对多关系。
 * 注意：该表只有id和两个外键字段，没有逻辑删除和时间字段，所以不继承BaseEntity。
 * </p>
 */
@Data
@TableName("admin_role_permission")
public class AdminRolePermission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 角色ID，关联 admin_role 表的 id */
    private Long roleId;

    /** 权限ID，关联 admin_permission 表的 id */
    private Long permissionId;
}
