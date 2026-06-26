package com.shop.common.model;

/**
 * 校验分组接口
 * <p>
 * 为什么需要分组？因为同一个DTO在"新增"和"修改"时，校验规则可能不同。
 * 比如创建用户时ID不需要传（数据库自动生成），但修改用户时ID必须传。
 * 通过分组，可以让同一个DTO在不同场景下走不同的校验规则。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     // 1. 在DTO字段上指定分组
 *     public class UserDTO {
 *         &#64;Null(groups = Create.class, message = "新增时ID必须为空")
 *         &#64;NotNull(groups = Update.class, message = "修改时ID不能为空")
 *         private Long id;
 *
 *         &#64;NotBlank(groups = Create.class, message = "名称不能为空")
 *         private String name;
 *     }
 *
 *     // 2. 在Controller上用@Validated指定走哪个分组
 *     &#64;PostMapping
 *     public Result&lt;Void&gt; create(@Validated(Create.class) @RequestBody UserDTO dto) { ... }
 *
 *     &#64;PutMapping
 *     public Result&lt;Void&gt; update(@Validated(Update.class) @RequestBody UserDTO dto) { ... }
 * </pre>
 * </p>
 */
public class ValidationGroups {

    /**
     * 新增操作校验分组
     * 用在Controller的@Validated注解上，表示这次校验走"新增"规则
     */
    public interface Create {
    }

    /**
     * 修改操作校验分组
     * 用在Controller的@Validated注解上，表示这次校验走"修改"规则
     */
    public interface Update {
    }
}
