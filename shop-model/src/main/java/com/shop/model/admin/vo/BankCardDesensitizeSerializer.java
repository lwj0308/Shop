package com.shop.model.admin.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 银行账号脱敏序列化器
 * <p>
 * 把银行账号中间部分替换成星号，只保留前4位和后4位，
 * 比如 6222021234567890 显示为 6222********7890。
 * 这样前端能看到卡号头尾，但看不到完整卡号，保护商家资金安全。
 * </p>
 */
public class BankCardDesensitizeSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value.length() <= 8) {
            // 卡号太短就不脱敏了，直接返回原值
            gen.writeString(value);
            return;
        }
        // 保留前4位和后4位，中间用星号替换
        int starCount = value.length() - 8;
        String stars = "*".repeat(starCount);
        String desensitized = value.substring(0, 4) + stars + value.substring(value.length() - 4);
        gen.writeString(desensitized);
    }
}
