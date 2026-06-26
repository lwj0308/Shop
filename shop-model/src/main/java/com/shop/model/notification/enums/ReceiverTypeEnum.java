package com.shop.model.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知接收人类型枚举
 * <p>
 * 用于区分一条通知是发给谁的。
 * 因为用户、商家、管理员的 ID 可能重复（比如用户ID=1 和商家ID=1），
 * 所以需要用 receiver_type + receiver_id 组合才能唯一确定接收人。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ReceiverTypeEnum {

    /** 接收人是普通用户（用户端 web-user） */
    USER(1, "用户"),
    /** 接收人是商家（商家端 web-merchant） */
    MERCHANT(2, "商家"),
    /** 接收人是管理员（管理端 web-admin） */
    ADMIN(3, "管理员");

    /** 类型码，和数据库里存的数字一致 */
    private final int code;
    /** 类型描述 */
    private final String desc;

    /**
     * 根据类型码获取枚举
     *
     * @param code 类型码
     * @return 对应的枚举，找不到返回 null
     */
    public static ReceiverTypeEnum getByCode(int code) {
        for (ReceiverTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
