package com.shop.model.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改密码请求参数
 * <p>
 * 前端调用修改密码接口时传的参数。
 * 需要输入旧密码验证身份，再输入新密码。
 * 这样可以防止别人在你登录的状态下偷偷改你的密码。
 * </p>
 */
@Data
public class UserPasswordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 旧密码（必须输入正确的旧密码，证明是你本人操作） */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码（8-20位，必须包含字母和数字） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "新密码长度必须在8-20位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码必须包含字母和数字")
    private String newPassword;
}
