package com.shop.model.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 管理员状态枚举
 * <p>
 * 把管理员状态从魔法数字（0、1）变成有意义的枚举值，
 * 这样代码里看到 NORMAL 就知道是"正常"，不用再猜 1 代表什么了。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum AdminUserStatusEnum {

    /** 禁用：管理员账号被禁用，不能登录后台 */
    DISABLED(0, "禁用"),

    /** 正常：管理员账号正常，可以登录后台 */
    NORMAL(1, "正常");

    /** 状态值，对应数据库里的 status 字段 */
    private final int code;

    /** 状态描述，给人看的中文说明 */
    private final String desc;

    /**
     * 根据状态值获取枚举
     * <p>
     * 从数据库查出 status=1，用这个方法转成枚举，方便做业务判断。
     * </p>
     *
     * @param code 状态值
     * @return 对应的枚举，找不到返回null
     */
    public static AdminUserStatusEnum fromCode(int code) {
        for (AdminUserStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据状态值获取描述文字
     * <p>
     * 直接拿到中文描述，比如给前端显示"正常"、"禁用"等。
     * </p>
     *
     * @param code 状态值
     * @return 状态描述，找不到返回"未知状态"
     */
    public static String getDescByCode(int code) {
        AdminUserStatusEnum status = fromCode(code);
        return status != null ? status.desc : "未知状态";
    }
}
