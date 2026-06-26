package com.shop.model.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 新增管理员请求参数
 * <p>
 * 管理员在后台新增一个管理员账号时提交的数据，包含账号基本信息和角色分配。
 * 用户名和密码是必填的，其他信息可选。
 * </p>
 */
@Data
public class AdminUserCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名，管理员登录账号，不能为空，最长50个字符 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50个字符")
    private String username;

    /** 密码，管理员登录密码，不能为空，长度6-20位 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    private String password;

    /** 昵称，管理员在系统中显示的名称，最长50个字符 */
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    /** 邮箱，管理员的联系邮箱，需要符合邮箱格式 */
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 手机号，管理员的联系电话，最长20个字符 */
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone;

    /** 部门ID，管理员所属的部门 */
    private Long deptId;

    /** 角色ID列表，给管理员分配的角色，一个管理员可以有多个角色 */
    private List<Long> roleIds;
}
