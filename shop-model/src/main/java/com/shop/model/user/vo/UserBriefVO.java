package com.shop.model.user.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户简要信息VO（View Object）
 * <p>
 * 仅包含最基础的昵称和头像信息，用于其他微服务（如商品服务）批量查询用户信息时返回。
 * 相比 UserVO，不包含手机号、状态等敏感字段，更轻量、更安全。
 * </p>
 * <p>
 * 使用场景：商品评价列表展示用户昵称和头像时，shop-product 通过 Feign 调用 shop-user 的内部接口批量获取。
 * </p>
 */
@Data
public class UserBriefVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 用户昵称 */
    private String nickname;

    /** 用户头像URL */
    private String avatar;
}
