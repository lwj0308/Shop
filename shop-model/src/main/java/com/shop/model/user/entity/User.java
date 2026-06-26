package com.shop.model.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户基本信息实体
 * <p>
 * 对应数据库的 user 表，存储用户的核心信息。
 * 手机号和密码都做了加密处理，不会明文存储。
 * 密码字段加了@JsonIgnore，序列化时不会返回给前端，防止密码泄露。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 手机号（AES加密存储，防止泄露） */
    private String phone;

    /** 密码（BCrypt加密，不可逆，安全性高；@JsonIgnore防止序列化时返回前端） */
    @JsonIgnore
    private String password;

    /** 昵称（用户给自己取的名字） */
    private String nickname;

    /** 头像URL（用户头像图片的地址） */
    private String avatar;

    /** 状态：0禁用 1正常（管理员可以封禁用户） */
    private Integer status;
}
