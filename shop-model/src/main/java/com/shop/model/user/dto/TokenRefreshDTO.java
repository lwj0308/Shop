package com.shop.model.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 刷新Token请求参数
 * <p>
 * 当AccessToken过期后，前端用RefreshToken来换取新的AccessToken。
 * 这样用户就不用重新登录了，体验更好。
 * RefreshToken的有效期比AccessToken长很多（7天 vs 2小时）。
 * </p>
 */
@Data
public class TokenRefreshDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 刷新令牌（登录时返回的refreshToken） */
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
