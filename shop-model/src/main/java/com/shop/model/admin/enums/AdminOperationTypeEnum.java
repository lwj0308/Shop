package com.shop.model.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作类型枚举
 * <p>
 * 记录管理员做了什么类型的操作，用于操作日志分类。
 * 比如管理员新增了一个用户，操作类型就是"新增"；
 * 修改了角色信息，操作类型就是"修改"。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum AdminOperationTypeEnum {

    /** 新增：创建新数据，比如新增用户、新增角色 */
    CREATE("CREATE", "新增"),

    /** 修改：更新已有数据，比如修改用户信息、修改角色权限 */
    UPDATE("UPDATE", "修改"),

    /** 删除：删除数据，比如删除用户、删除角色 */
    DELETE("DELETE", "删除"),

    /** 查询：查看数据，比如查询用户列表、导出报表 */
    QUERY("QUERY", "查询"),

    /** 导出：导出数据到文件，比如导出用户Excel、导出订单报表 */
    EXPORT("EXPORT", "导出");

    /** 操作类型标识，对应数据库里的 operation_type 字段 */
    private final String code;

    /** 操作类型描述，给人看的中文说明 */
    private final String desc;

    /**
     * 根据操作类型标识获取枚举
     * <p>
     * 从数据库查出 operation_type="CREATE"，用这个方法转成枚举。
     * </p>
     *
     * @param code 操作类型标识
     * @return 对应的枚举，找不到返回null
     */
    public static AdminOperationTypeEnum fromCode(String code) {
        for (AdminOperationTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据操作类型标识获取描述文字
     * <p>
     * 直接拿到中文描述，比如给前端显示"新增"、"修改"等。
     * </p>
     *
     * @param code 操作类型标识
     * @return 操作类型描述，找不到返回"未知操作"
     */
    public static String getDescByCode(String code) {
        AdminOperationTypeEnum type = fromCode(code);
        return type != null ? type.desc : "未知操作";
    }
}
