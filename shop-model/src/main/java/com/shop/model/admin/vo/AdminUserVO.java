package com.shop.model.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员信息响应数据
 * <p>
 * 返回给前端的管理员详细信息，包含基本信息、部门信息和角色列表。
 * 密码等敏感字段不会返回，保证安全性。
 * </p>
 */
@Data
public class AdminUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 管理员ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像图片地址 */
    private String avatar;

    /** 邮箱（脱敏显示，如a***@example.com） */
    @JsonSerialize(using = EmailDesensitizeSerializer.class)
    private String email;

    /** 手机号（脱敏显示，如138****1234） */
    @JsonSerialize(using = PhoneDesensitizeSerializer.class)
    private String phone;

    /** 部门ID */
    private Long deptId;

    /** 部门名称，冗余字段，方便前端直接显示 */
    private String deptName;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 角色列表，该管理员拥有的所有角色 */
    private List<AdminRoleVO> roles;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
