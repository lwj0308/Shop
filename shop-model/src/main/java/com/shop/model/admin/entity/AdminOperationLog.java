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
 * 操作日志实体
 * <p>
 * 对应数据库 admin_operation_log 表，记录管理员在后台的每一次操作。
 * 包括谁在什么时间做了什么操作、请求参数和返回结果等，方便审计和排查问题。
 * 注意：该表没有逻辑删除和更新时间字段，所以不继承BaseEntity，自己定义id和createTime。
 * </p>
 */
@Data
@TableName("admin_operation_log")
public class AdminOperationLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人ID，关联 admin_user 表的 id */
    private Long userId;

    /** 操作人用户名，冗余存储，方便查询展示 */
    private String username;

    /** 操作模块，比如"用户管理"、"角色管理" */
    private String module;

    /** 操作类型，比如"新增"、"修改"、"删除"、"查询" */
    private String operationType;

    /** 操作描述，具体做了什么事，比如"新增了用户张三" */
    private String description;

    /** 请求URL，比如"/api/admin/user" */
    private String requestUrl;

    /** 请求方法，比如"GET"、"POST"、"PUT"、"DELETE" */
    private String requestMethod;

    /** 请求参数，JSON格式存储，方便回溯操作细节 */
    private String requestParams;

    /** 响应结果，JSON格式存储，方便排查接口返回问题 */
    private String responseResult;

    /** 操作者IP地址 */
    private String ip;

    /** 操作地点，根据IP解析出来的地理位置，比如"广东省深圳市" */
    private String location;

    /** 耗时（毫秒），接口响应花了多长时间，用来监控性能 */
    private Integer duration;

    /** 状态：0失败 1成功（记录操作是否成功执行） */
    private Integer status;

    /** 错误信息，操作失败时的错误原因 */
    private String errorMsg;

    /** 创建时间（操作发生的时间，新增时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
