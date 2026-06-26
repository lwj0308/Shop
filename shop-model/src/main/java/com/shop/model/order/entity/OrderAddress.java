package com.shop.model.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单地址快照实体
 * <p>
 * 对应数据库 order_address 表，存储下单时的收货地址信息。
 * 为什么不直接用用户的地址？因为用户可能会修改地址，
 * 但订单的收货地址应该是下单那一刻的地址，不能跟着变。
 * 所以下单时把地址信息"拍个快照"存到这里。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_address")
public class OrderAddress extends BaseEntity {

    /** 订单ID（这个地址属于哪个订单） */
    private Long orderId;

    /** 订单号（方便查询） */
    private String orderNo;

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
}
