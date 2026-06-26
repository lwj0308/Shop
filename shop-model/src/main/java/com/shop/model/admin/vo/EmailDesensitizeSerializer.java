package com.shop.model.admin.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 邮箱脱敏序列化器
 * <p>
 * 把邮箱@前面的部分只保留首字母，其余用星号替换，比如alice@example.com变成a***@example.com。
 * </p>
 */
public class EmailDesensitizeSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || !value.contains("@")) {
            gen.writeString(value);
            return;
        }
        String[] parts = value.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 1) {
            gen.writeString(value);
            return;
        }
        // 保留首字母，其余用星号替换
        String desensitized = name.charAt(0) + "***" + "@" + domain;
        gen.writeString(desensitized);
    }
}
