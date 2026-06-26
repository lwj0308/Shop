package com.shop.model.admin.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * IP地址脱敏序列化器
 * <p>
 * 把IPv4地址最后一段替换成星号，比如192.168.1.100变成192.168.1.*。
 * 这样前端能看到IP的大致来源，但看不到完整地址，保护用户隐私。
 * </p>
 */
public class IpDesensitizeSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null || value.isEmpty() || "unknown".equals(value)) {
            gen.writeString(value);
            return;
        }
        // IPv4地址：保留前三段，最后一段用星号替换
        if (value.contains(".")) {
            int lastDot = value.lastIndexOf(".");
            String desensitized = value.substring(0, lastDot + 1) + "*";
            gen.writeString(desensitized);
            return;
        }
        // IPv6或其他格式：不脱敏，直接返回
        gen.writeString(value);
    }
}
