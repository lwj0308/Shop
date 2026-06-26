package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限/菜单实体
 * <p>
 * 对应数据库 admin_permission 表，存储后台的权限和菜单信息。
 * 权限分三种类型：目录（一级分类）、菜单（可点击的页面）、按钮（页面上的操作权限）。
 * 通过父子关系（parentId）构建树形菜单结构。
 * 注意：该表没有逻辑删除字段，所以不继承BaseEntity，自己定义id和时间字段。
 * </p>
 */
@Data
@TableName("admin_permission")
public class AdminPermission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父级权限ID，顶级权限的parentId为0，用来构建树形菜单 */
    private Long parentId;

    /** 权限/菜单名称，比如"用户管理"、"新增用户" */
    private String name;

    /** 类型：1目录 2菜单 3按钮（目录用来分组，菜单对应页面，按钮对应操作） */
    private Integer type;

    /** 权限标识，代码里用来判断权限的key，比如"user:list"、"user:add" */
    private String permissionKey;

    /** 路由路径，前端路由地址，比如"/system/user" */
    private String path;

    /** 图标名称，菜单前面显示的图标 */
    private String icon;

    /** 排序号，数字越小越靠前 */
    private Integer sort;

    /** 状态：0禁用 1正常（禁用后该权限不会显示，也无法访问） */
    private Integer status;

    /** 创建时间（新增数据时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（新增和修改数据时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
