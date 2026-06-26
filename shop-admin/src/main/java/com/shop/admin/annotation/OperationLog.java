package com.shop.admin.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * <p>
 * 加在Controller方法上，AOP切面会自动记录操作日志到数据库。
 * 只需要在方法上加一个注解，不用手动写日志记录代码。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     &#64;OperationLog(module = "用户管理", type = OperationType.CREATE, description = "新增管理员")
 *     &#64;PostMapping
 *     public Result&lt;Void&gt; create(@RequestBody AdminUserCreateDTO dto) { ... }
 * </pre>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    /** 操作模块，如"用户管理"、"角色管理" */
    String module();
    /** 操作类型 */
    OperationType type();
    /** 操作描述，支持SpEL表达式读取方法参数，如"审核通过商家：#dto.merchantId" */
    String description();
}
