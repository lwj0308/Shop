package com.shop.admin.annotation;

/**
 * 权限逻辑枚举
 * <p>
 * 定义权限校验时的逻辑关系：
 * - AND：需要同时拥有所有权限（默认）
 * - OR：只需要拥有其中一个权限即可
 * </p>
 * <p>
 * 举个例子：
 * - AND模式下，@RequirePermission({"user:list", "user:detail"}) 要求同时拥有这两个权限
 * - OR模式下，@RequirePermission(value = {"user:list", "user:detail"}, logical = Logical.OR) 只需其中一个
 * </p>
 */
public enum Logical {
    /** 需要同时拥有所有权限 */
    AND,
    /** 只需要拥有其中一个权限 */
    OR
}
