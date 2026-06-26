package com.shop.model.admin.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 管理员登录响应数据
 * <p>
 * 管理员登录成功后返回的数据，包含登录凭证（token）和基本信息。
 * 前端拿到 token 后存到本地，后续请求都带上这个 token 来证明身份。
 * permissions 和 roles 是前端用来控制菜单和按钮显示的。
 * </p>
 */
@Data
public class AdminLoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录凭证，前端每次请求都要带上，用来验证身份 */
    private String token;

    /** 管理员ID */
    private Long adminUserId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像图片地址 */
    private String avatar;

    /** 权限标识集合，比如"user:create"、"role:delete"，前端用来控制按钮显示 */
    private Set<String> permissions;

    /** 角色标识集合，比如"admin"、"operator"，前端用来控制菜单显示 */
    private Set<String> roles;
}
