package com.shop.admin.annotation;

import java.lang.annotation.*;

/**
 * 权限校验注解
 * <p>
 * 加在Controller方法上，声明式校验当前管理员是否拥有指定权限。
 * 支持AND和OR两种逻辑：默认AND（需要同时拥有所有权限），logical=Logical.OR则只需其中一个。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     // 需要同时拥有 user:list 和 user:detail 权限
 *     &#64;RequirePermission({"user:list", "user:detail"})
 *
 *     // 只需要其中一个权限即可
 *     &#64;RequirePermission(value = {"user:list", "user:detail"}, logical = Logical.OR)
 * </pre>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    /** 权限标识数组，如 {"user:list", "user:detail"} */
    String[] value();
    /** 权限逻辑：AND(默认，需要全部权限) / OR(只需其中一个) */
    Logical logical() default Logical.AND;
}
