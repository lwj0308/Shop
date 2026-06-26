package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理员信息实体
 * <p>
 * 对应数据库 admin_user 表，存储后台管理员的基本信息。
 * 管理员登录后台系统后可以管理商家、商品、订单等业务数据。
 * 密码字段加了@JsonIgnore，序列化时不会返回给前端，防止密码泄露。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_user")
public class AdminUser extends BaseEntity {

    /** 用户名，管理员登录时用的账号，比如"admin" */
    private String username;

    /** 密码（加密存储；@JsonIgnore防止序列化时返回前端） */
    @JsonIgnore
    private String password;

    /** 昵称，管理员在系统里显示的名字，比如"超级管理员" */
    private String nickname;

    /** 头像图片地址 */
    private String avatar;

    /** 邮箱地址 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 部门ID，关联 admin_dept 表的 id */
    private Long deptId;

    /** 状态：0禁用 1正常（禁用后管理员无法登录系统） */
    private Integer status;

    /** 最后登录IP地址，记录管理员上次从哪里登录的 */
    private String lastLoginIp;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 密码修改时间，用来判断密码是否过期 */
    private LocalDateTime passwordChangeTime;
}
