package com.shop.admin.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * <p>
 * 加在Service方法上，MyBatis拦截器会自动根据当前管理员的数据权限范围，
 * 在SQL中追加过滤条件（如 WHERE dept_id IN (...) 或 WHERE user_id = ?）。
 * 不加此注解的方法不会进行数据权限过滤。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     &#64;DataScope(deptAlias = "d", userAlias = "u")
 *     public PageResult&lt;AdminUserVO&gt; getAdminUserList(AdminUserQueryDTO queryDTO) { ... }
 * </pre>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 部门表的别名，如 "d"。如果不指定，默认为 "d" */
    String deptAlias() default "d";

    /** 用户表的别名，如 "u"。如果不指定，默认为 "u" */
    String userAlias() default "u";

    /** 部门ID字段名，默认 "dept_id" */
    String deptField() default "dept_id";

    /** 用户ID字段名，默认 "user_id" */
    String userField() default "user_id";
}
