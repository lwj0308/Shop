package com.shop.model.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺信息实体
 * <p>
 * 对应数据库 shop 表，存储店铺的基本信息。
 * 商家审核通过后系统会自动创建一个默认店铺，商家也可以修改店铺信息。
 * 一个商家可以拥有多个店铺，每个店铺独立运营。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop")
public class Shop extends BaseEntity {

    /** 商家ID，关联 merchant 表的 id */
    private Long merchantId;

    /** 店铺名称，比如"张三数码旗舰店" */
    private String name;

    /** 店铺Logo图片地址 */
    private String logo;

    /** 店铺Banner图片地址，店铺首页顶部的大图 */
    private String banner;

    /** 店铺描述，介绍店铺卖什么、有什么特色 */
    private String description;

    /** 状态：0关闭 1正常 */
    private Integer status;
}
