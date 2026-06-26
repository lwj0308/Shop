package com.shop.model.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址实体
 * <p>
 * 对应数据库的 user_address 表，存储用户的收货地址信息。
 * 一个用户可以添加多个收货地址，其中一个可以设为默认地址。
 * 下单时默认使用默认地址，用户也可以选择其他地址。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_address")
public class UserAddress extends BaseEntity {

    /** 用户ID（这个地址属于哪个用户） */
    private Long userId;

    /** 收货人姓名（谁来收快递） */
    private String name;

    /** 收货人手机号（快递员联系谁） */
    private String phone;

    /** 省（比如"广东省"） */
    private String province;

    /** 市（比如"深圳市"） */
    private String city;

    /** 区（比如"南山区"） */
    private String district;

    /** 详细地址（比如"科技园路1号A栋3楼"） */
    private String detail;

    /** 是否默认：0否 1是（每个用户只能有一个默认地址） */
    private Integer isDefault;
}
