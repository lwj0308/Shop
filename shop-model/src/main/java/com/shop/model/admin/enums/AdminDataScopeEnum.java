package com.shop.model.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据权限范围枚举
 * <p>
 * 控制角色能看到哪些数据。比如"运营经理"可能只能看本部门的数据，
 * 而"超级管理员"可以看所有数据。这就是数据权限的作用。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum AdminDataScopeEnum {

    /** 全部数据：不限制，可以看到所有数据，超级管理员一般用这个 */
    ALL(1, "全部数据"),

    /** 本部门数据：只能看到自己所在部门的数据 */
    DEPT(2, "本部门数据"),

    /** 本部门及下级：可以看到自己部门和下级部门的数据 */
    DEPT_AND_CHILD(3, "本部门及下级"),

    /** 仅本人数据：只能看到自己创建的数据 */
    SELF(4, "仅本人数据");

    /** 权限范围值，对应数据库里的 data_scope 字段 */
    private final int code;

    /** 权限范围描述，给人看的中文说明 */
    private final String desc;

    /**
     * 根据权限范围值获取枚举
     * <p>
     * 从数据库查出 data_scope=2，用这个方法转成枚举，方便做数据过滤。
     * </p>
     *
     * @param code 权限范围值
     * @return 对应的枚举，找不到返回null
     */
    public static AdminDataScopeEnum fromCode(int code) {
        for (AdminDataScopeEnum scope : values()) {
            if (scope.code == code) {
                return scope;
            }
        }
        return null;
    }

    /**
     * 根据权限范围值获取描述文字
     * <p>
     * 直接拿到中文描述，比如给前端显示"全部数据"、"本部门数据"等。
     * </p>
     *
     * @param code 权限范围值
     * @return 权限范围描述，找不到返回"未知范围"
     */
    public static String getDescByCode(int code) {
        AdminDataScopeEnum scope = fromCode(code);
        return scope != null ? scope.desc : "未知范围";
    }
}
