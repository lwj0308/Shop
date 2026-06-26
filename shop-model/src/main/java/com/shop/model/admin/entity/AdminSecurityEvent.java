package com.shop.model.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安全事件实体
 * <p>
 * 对应数据库 admin_security_event 表，记录系统中的安全相关事件。
 * 比如暴力破解尝试、异地登录、异常操作等，方便安全团队审计和处理。
 * 管理员可以对这些事件进行处理、标记或忽略。
 * 注意：该表没有逻辑删除和更新时间字段，所以不继承BaseEntity，自己定义id和createTime。
 * </p>
 */
@Data
@TableName("admin_security_event")
public class AdminSecurityEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 事件类型，比如"BRUTE_FORCE"（暴力破解）、"ABNORMAL_LOGIN"（异常登录） */
    private String eventType;

    /** 关联管理员ID，关联 admin_user 表的 id */
    private Long userId;

    /** 关联用户名，冗余存储方便查询 */
    private String username;

    /** 事件详情，描述具体发生了什么 */
    private String detail;

    /** 触发事件的IP地址 */
    private String ip;

    /** 状态：0未处理 1已处理 2已忽略（安全团队可以标记处理结果） */
    private Integer status;

    /** 处理备注，安全团队处理时填写的说明 */
    private String handleNote;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 创建时间（事件发生的时间，新增时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
