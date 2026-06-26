package com.shop.admin.annotation;

/**
 * 操作类型枚举
 * <p>
 * 定义操作日志中记录的操作类型，方便分类查询和统计。
 * </p>
 */
public enum OperationType {
    /** 新增操作，比如新增用户、新增角色 */
    CREATE,
    /** 修改操作，比如修改用户信息、修改角色权限 */
    UPDATE,
    /** 删除操作，比如删除用户、删除角色 */
    DELETE,
    /** 查询操作，比如导出报表、查看详情 */
    QUERY,
    /** 导出操作，比如导出用户列表、导出订单数据 */
    EXPORT
}
