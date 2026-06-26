package com.shop.model.user.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录响应VO
 * <p>
 * 登录成功后返回给前端的数据，包含双Token和用户基本信息。
 * 双Token机制说明：
 * - AccessToken：短期有效（2小时），用于日常接口请求
 * - RefreshToken：长期有效（7天），用于刷新AccessToken
 * 这样设计的好处是：即使AccessToken被盗，2小时后就失效了，损失有限。
 * </p>
 */
@Data
public class UserLoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 访问令牌（每次请求都要带上，2小时后过期） */
    private String accessToken;

    /** 刷新令牌（AccessToken过期后用它换新的，7天后过期） */
    private String refreshToken;

    /** 用户基本信息（脱敏后的） */
    private UserVO userInfo;
}
