package com.shop.model.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 权限类型枚举
 * <p>
 * 权限分三种类型：目录（用来分组）、菜单（页面）、按钮（操作权限）。
 * 比如系统管理是目录，用户管理是菜单，新增用户是按钮。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum AdminPermissionTypeEnum {

    /** 目录：只是用来分组，不对应具体页面，比如"系统管理" */
    DIRECTORY(1, "目录"),

    /** 菜单：对应一个页面，点击后跳转到对应页面，比如"用户管理" */
    MENU(2, "菜单"),

    /** 按钮：页面上的操作权限，控制按钮是否显示，比如"新增用户"按钮 */
    BUTTON(3, "按钮");

    /** 类型值，对应数据库里的 type 字段 */
    private final int code;

    /** 类型描述，给人看的中文说明 */
    private final String desc;

    /**
     * 根据类型值获取枚举
     * <p>
     * 从数据库查出 type=2，用这个方法转成枚举，方便做业务判断。
     * </p>
     *
     * @param code 类型值
     * @return 对应的枚举，找不到返回null
     */
    public static AdminPermissionTypeEnum fromCode(int code) {
        for (AdminPermissionTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据类型值获取描述文字
     * <p>
     * 直接拿到中文描述，比如给前端显示"目录"、"菜单"、"按钮"等。
     * </p>
     *
     * @param code 类型值
     * @return 类型描述，找不到返回"未知类型"
     */
    public static String getDescByCode(int code) {
        AdminPermissionTypeEnum type = fromCode(code);
        return type != null ? type.desc : "未知类型";
    }
}
