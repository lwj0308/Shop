package com.shop.model.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 安全事件状态枚举
 * <p>
 * 安全事件的处理状态，从"未处理"到"已处理"或"已忽略"。
 * 管理员查看安全事件后，可以标记为已处理（确认并解决），
 * 也可以标记为已忽略（确认但不需要处理）。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum AdminSecurityEventStatusEnum {

    /** 未处理：新发现的安全事件，等待管理员查看处理 */
    UNHANDLED(0, "未处理"),

    /** 已处理：管理员已经确认并处理了该安全事件 */
    HANDLED(1, "已处理"),

    /** 已忽略：管理员确认了该事件但认为不需要处理，比如误报 */
    IGNORED(2, "已忽略");

    /** 状态值，对应数据库里的 status 字段 */
    private final int code;

    /** 状态描述，给人看的中文说明 */
    private final String desc;

    /**
     * 根据状态值获取枚举
     * <p>
     * 从数据库查出 status=0，用这个方法转成枚举，方便做业务判断。
     * </p>
     *
     * @param code 状态值
     * @return 对应的枚举，找不到返回null
     */
    public static AdminSecurityEventStatusEnum fromCode(int code) {
        for (AdminSecurityEventStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据状态值获取描述文字
     * <p>
     * 直接拿到中文描述，比如给前端显示"未处理"、"已处理"、"已忽略"等。
     * </p>
     *
     * @param code 状态值
     * @return 状态描述，找不到返回"未知状态"
     */
    public static String getDescByCode(int code) {
        AdminSecurityEventStatusEnum status = fromCode(code);
        return status != null ? status.desc : "未知状态";
    }
}
