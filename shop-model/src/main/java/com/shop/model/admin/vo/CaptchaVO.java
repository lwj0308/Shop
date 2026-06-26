package com.shop.model.admin.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 验证码响应数据
 * <p>
 * 获取验证码时返回的数据，captchaKey 是验证码的唯一标识，
 * captchaImage 是验证码图片的 Base64 编码，前端直接用 img 标签显示。
 * 登录时需要把 captchaKey 和用户输入的验证码一起提交，后端根据 key 找到正确答案做比对。
 * </p>
 */
@Data
public class CaptchaVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 验证码key，登录时需要带上，后端根据这个key找到正确的验证码 */
    private String captchaKey;

    /** 验证码图片，Base64编码格式，前端直接用data:image/png;base64,拼接显示 */
    private String captchaImage;
}
