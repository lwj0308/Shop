package com.shop.model.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 安全事件类型枚举
 * <p>
 * 系统检测到的安全风险类型，每种类型代表一种安全威胁。
 * 比如某个IP短时间内多次登录失败，就是"频繁登录失败"；
 * 有人从异常地区登录，就是"异常IP"。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum AdminSecurityEventTypeEnum {

    /** 频繁登录失败：短时间内多次登录失败，可能是暴力破解密码 */
    FREQUENT_LOGIN_FAIL("FREQUENT_LOGIN_FAIL", "频繁登录失败"),

    /** 异常IP：从不常见的IP地址登录，可能是账号被盗 */
    ABNORMAL_IP("ABNORMAL_IP", "异常IP"),

    /** 权限越权：尝试访问没有权限的资源，可能是恶意探测 */
    PERMISSION_VIOLATION("PERMISSION_VIOLATION", "权限越权"),

    /** 敏感操作：执行了高风险操作，比如批量删除数据、修改超级管理员密码 */
    SENSITIVE_OPERATION("SENSITIVE_OPERATION", "敏感操作");

    /** 事件类型标识，对应数据库里的 event_type 字段 */
    private final String code;

    /** 事件类型描述，给人看的中文说明 */
    private final String desc;

    /**
     * 根据事件类型标识获取枚举
     * <p>
     * 从数据库查出 event_type="FREQUENT_LOGIN_FAIL"，用这个方法转成枚举。
     * </p>
     *
     * @param code 事件类型标识
     * @return 对应的枚举，找不到返回null
     */
    public static AdminSecurityEventTypeEnum fromCode(String code) {
        for (AdminSecurityEventTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据事件类型标识获取描述文字
     * <p>
     * 直接拿到中文描述，比如给前端显示"频繁登录失败"、"异常IP"等。
     * </p>
     *
     * @param code 事件类型标识
     * @return 事件类型描述，找不到返回"未知事件"
     */
    public static String getDescByCode(String code) {
        AdminSecurityEventTypeEnum type = fromCode(code);
        return type != null ? type.desc : "未知事件";
    }
}
