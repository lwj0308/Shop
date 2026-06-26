package com.shop.model.merchant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家信息实体
 * <p>
 * 对应数据库 merchant 表，存储商家的基本信息。
 * 商家入驻后需要审核才能正常经营，状态流转：待审核(0) → 已通过(1) / 已拒绝(2) / 已禁用(3)
 * 密码字段加了@JsonIgnore，序列化时不会返回给前端，防止密码泄露。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant")
public class Merchant extends BaseEntity {

    /** 商家名称，比如"张三的数码店" */
    private String name;

    /** 商家Logo图片地址 */
    private String logo;

    /** 商家描述，简单介绍商家是卖什么的 */
    private String description;

    /** 联系电话（AES加密存储，保护用户隐私） */
    private String contactPhone;

    /** 关联的用户ID，一个用户只能入驻一个商家 */
    private Long userId;

    /** 商家密码（加密存储；@JsonIgnore防止序列化时返回前端） */
    @JsonIgnore
    private String password;

    /** 状态：0待审核 1已通过 2已拒绝 3已禁用 */
    private Integer status;
}
