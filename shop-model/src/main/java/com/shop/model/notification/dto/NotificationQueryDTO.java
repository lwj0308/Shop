package com.shop.model.notification.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通知查询请求DTO
 * <p>
 * 查询通知列表时使用的筛选条件，支持按通知类型和已读状态筛选。
 * 接收人类型和接收人ID由后端根据调用方身份自动填充，前端不需要传。
 * </p>
 */
@Data
public class NotificationQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 接收人类型：1用户 2商家 3管理员（内部接口使用，前端不需要传） */
    private Integer receiverType;

    /** 接收人ID（内部接口使用，前端不需要传） */
    private Long receiverId;

    /** 通知类型筛选（可选，不传则查全部类型） */
    private Integer type;

    /** 已读状态筛选（可选：0未读 1已读，不传则查全部） */
    private Integer isRead;

    /** 页码（从1开始） */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 10;
}
