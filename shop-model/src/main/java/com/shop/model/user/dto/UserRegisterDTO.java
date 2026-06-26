package com.shop.model.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求参数
 * <p>
 * 前端调用注册接口时传的参数，所有字段都有校验规则。
 * 手机号必须是11位数字，密码至少6位，两次密码必须一致。
 * </p>
 */
@Data
public class UserRegisterDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 手机号（必须是11位数字，以1开头） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 密码（8-20位，必须包含字母和数字，提高安全性） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度必须在8-20位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须包含字母和数字")
    private String password;

    /** 确认密码（必须和password一致，防止用户输错密码） */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
