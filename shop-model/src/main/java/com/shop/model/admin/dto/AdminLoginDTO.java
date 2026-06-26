package com.shop.model.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员登录请求参数
 * <p>
 * 管理员登录后台系统时提交的表单数据，包含用户名、密码和验证码。
 * 验证码是可选的，如果系统开启了验证码校验，就需要传 captchaKey 和 captchaCode。
 * </p>
 */
@Data
public class AdminLoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户名，管理员登录账号，不能为空 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码，管理员登录密码，不能为空 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码的key，用来从缓存中取出正确的验证码做比对 */
    private String captchaKey;

    /** 验证码的值，用户输入的验证码内容 */
    private String captchaCode;
}
