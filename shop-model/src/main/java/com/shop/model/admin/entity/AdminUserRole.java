package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员-角色关联实体
 * <p>
 * 对应数据库 admin_user_role 表，存储管理员和角色的对应关系。
 * 这是一张中间表，实现"一个管理员可以有多个角色"的多对多关系。
 * 注意：该表只有id和两个外键字段，没有逻辑删除和时间字段，所以不继承BaseEntity。
 * </p>
 */
@Data
@TableName("admin_user_role")
public class AdminUserRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 管理员ID，关联 admin_user 表的 id */
    private Long userId;

    /** 角色ID，关联 admin_role 表的 id */
    private Long roleId;
}
