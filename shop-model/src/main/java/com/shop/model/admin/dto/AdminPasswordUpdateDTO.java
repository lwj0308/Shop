package com.shop.model.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改密码请求参数
 * <p>
 * 管理员修改自己密码时提交的数据，需要输入旧密码验证身份，
 * 再输入新密码。新密码长度要求6-20位。
 * </p>
 */
@Data
public class AdminPasswordUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 旧密码，用来验证是不是本人操作，不能为空 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码，设置的新密码，不能为空，长度6-20位 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度必须在6-20位之间")
    private String newPassword;
}
