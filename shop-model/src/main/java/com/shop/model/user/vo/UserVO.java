package com.shop.model.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.shop.model.admin.vo.PhoneDesensitizeSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息响应VO（View Object）
 * <p>
 * 返回给前端的用户信息，不包含密码等敏感字段。
 * 手机号通过 @JsonSerialize 强制脱敏（138****1234），防止泄露用户隐私。
 * </p>
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 手机号（序列化时强制脱敏，比如138****1234） */
    @JsonSerialize(using = PhoneDesensitizeSerializer.class)
    private String phone;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 状态：0禁用 1正常 */
    private Integer status;

    /** 注册时间 */
    private LocalDateTime createTime;
}
