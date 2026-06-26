package com.shop.model.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家入驻申请参数
 * <p>
 * 用户想成为商家时，需要填写这个表单提交申请。
 * 包含商家基本信息和营业执照等资质信息，提交后等待管理员审核。
 * </p>
 */
@Data
public class MerchantApplyDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商家名称，比如"张三的数码店"，不能为空 */
    @NotBlank(message = "商家名称不能为空")
    private String name;

    /** 联系电话，商家的重要联系方式，不能为空 */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String contactPhone;

    /** 营业执照号，就是营业执照上那个统一社会信用代码 */
    @NotBlank(message = "营业执照号不能为空")
    private String licenseNo;

    /** 营业执照图片地址，管理员要看图片确认资质真实性 */
    @NotBlank(message = "营业执照图片不能为空")
    private String licenseImg;

    /** 法人姓名，营业执照上的法定代表人姓名 */
    @NotBlank(message = "法人姓名不能为空")
    private String legalPerson;

    /** 商家密码，用于商家登录 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度必须在6-20位之间")
    private String password;
}
