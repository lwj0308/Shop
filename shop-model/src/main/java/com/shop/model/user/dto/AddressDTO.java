package com.shop.model.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 收货地址请求参数
 * <p>
 * 前端调用添加/修改收货地址接口时传的参数。
 * 所有字段都是必填的，因为快递需要完整的地址才能送达。
 * </p>
 */
@Data
public class AddressDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 收货人姓名（谁来收快递） */
    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 50, message = "收货人姓名最长50个字符")
    private String name;

    /** 收货人手机号（快递员联系谁） */
    @NotBlank(message = "收货人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 省（比如"广东省"） */
    @NotBlank(message = "省份不能为空")
    @Size(max = 20, message = "省份最长20个字符")
    private String province;

    /** 市（比如"深圳市"） */
    @NotBlank(message = "城市不能为空")
    @Size(max = 20, message = "城市最长20个字符")
    private String city;

    /** 区（比如"南山区"） */
    @NotBlank(message = "区县不能为空")
    @Size(max = 20, message = "区县最长20个字符")
    private String district;

    /** 详细地址（比如"科技园路1号A栋3楼"） */
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 200, message = "详细地址最长200个字符")
    private String detail;

    /** 是否设为默认地址：true是 false否 */
    private Boolean isDefault;
}
