package com.shop.model.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家登录参数
 * <p>
 * 商家使用手机号和密码登录时提交的参数。
 * 登录成功后会获得一个Token，后续请求带上Token就能识别是哪个商家。
 * </p>
 */
@Data
public class MerchantLoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 联系电话，商家入驻时填写的手机号 */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String contactPhone;

    /** 密码，商家入驻时设置的密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
