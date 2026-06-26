package com.shop.model.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改个人信息请求参数
 * <p>
 * 前端调用修改个人信息接口时传的参数。
 * 目前只支持修改昵称和头像，手机号和密码需要走专门的接口修改。
 * </p>
 */
@Data
public class UserUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 昵称（用户给自己取的名字，最长50个字） */
    @Size(max = 50, message = "昵称最长50个字符")
    private String nickname;

    /** 头像URL（头像图片的地址） */
    @Size(max = 255, message = "头像URL最长255个字符")
    private String avatar;
}
