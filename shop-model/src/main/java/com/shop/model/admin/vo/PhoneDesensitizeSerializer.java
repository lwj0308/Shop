package com.shop.model.admin.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 手机号脱敏序列化器
 * <p>
 * 把手机号中间4位替换成星号，比如13812345678变成138****5678。
 * 这样前端能看到手机号的前3位和后4位，但看不到完整号码，保护用户隐私。
 * </p>
 */
public class PhoneDesensitizeSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.length() < 7) {
            // 手机号太短就不脱敏了，直接返回原值
            gen.writeString(value);
            return;
        }
        // 保留前3位和后4位，中间用星号替换
        String desensitized = value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        gen.writeString(desensitized);
    }
}
