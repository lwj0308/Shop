package com.shop.model.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家修改密码参数
 * <p>
 * 商家修改登录密码时提交的参数，需要验证旧密码才能修改新密码，
 * 防止别人偷偷改了商家的密码。
 * </p>
 */
@Data
public class MerchantChangePasswordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 旧密码，必须输入正确的旧密码才能修改，防止被盗号 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码，商家想设置的新密码 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "新密码长度必须在6-20位之间")
    private String newPassword;
}
