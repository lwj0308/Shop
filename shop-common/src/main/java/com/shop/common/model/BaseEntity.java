package com.shop.common.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公共实体基类
 * <p>
 * 所有数据库实体类的"老祖宗"，把每个表都有的公共字段抽到一起。
 * 子类就不用重复写 id、createTime、updateTime、deleted 这些字段了，
 * 改一处全局生效，维护起来更方便。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 *     // 继承BaseEntity后，只需要写自己特有的字段
 *     &#64;Data
 *     &#64;TableName("user")
 *     &#64;EqualsAndHashCode(callSuper = true)
 *     public class User extends BaseEntity {
 *         private String phone;
 *         private String password;
 *         // ... 其他字段
 *     }
 * </pre>
 * </p>
 */
@Data
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法自动生成，不用手动赋值） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间（新增数据时自动填充，不用手动设置） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（新增和修改数据时自动填充，不用手动设置） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0未删除 1已删除（删了也不会真从数据库删掉，只是改个标记） */
    @TableLogic
    private Integer deleted;
}
