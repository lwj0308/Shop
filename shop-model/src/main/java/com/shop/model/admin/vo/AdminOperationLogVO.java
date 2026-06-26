package com.shop.model.admin.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志响应数据
 * <p>
 * 返回给前端的操作日志详细信息，记录了管理员在后台的每一个操作。
 * 包括谁操作的、操作了什么模块、请求了什么接口、花了多长时间、成功还是失败等。
 * 用于安全审计和问题排查。
 * </p>
 */
@Data
public class AdminOperationLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long id;

    /** 操作人ID */
    private Long userId;

    /** 操作人用户名 */
    private String username;

    /** 操作模块，比如"用户管理"、"角色管理" */
    private String module;

    /** 操作类型：新增、修改、删除、查询、导出 */
    private String operationType;

    /** 操作描述，比如"新增用户 张三" */
    private String description;

    /** 请求URL，比如"/api/admin/users" */
    private String requestUrl;

    /** 请求方法，比如"POST"、"PUT"、"DELETE" */
    private String requestMethod;

    /** 操作IP地址（脱敏显示，如192.168.1.*） */
    @JsonSerialize(using = IpDesensitizeSerializer.class)
    private String ip;

    /** 操作地点，根据IP解析出的地理位置，比如"北京市" */
    private String location;

    /** 操作耗时，单位毫秒 */
    private Integer duration;

    /** 操作状态：0失败 1成功 */
    private Integer status;

    /** 错误信息，操作失败时的错误原因 */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime createTime;
}
