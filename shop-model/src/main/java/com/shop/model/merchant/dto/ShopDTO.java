package com.shop.model.merchant.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 店铺信息修改参数
 * <p>
 * 商家修改自己店铺信息时使用的参数。
 * 所有字段都是可选的，前端只传需要修改的字段就行。
 * </p>
 */
@Data
public class ShopDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 店铺名称，比如"张三数码旗舰店" */
    private String name;

    /** 店铺Logo图片地址 */
    private String logo;

    /** 店铺Banner图片地址，店铺首页顶部的大图 */
    private String banner;

    /** 店铺描述，介绍店铺卖什么、有什么特色 */
    private String description;
}
