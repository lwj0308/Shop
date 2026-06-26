package com.shop.model.notification.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知响应VO
 * <p>
 * 返回给前端的通知信息。type 字段额外提供 desc 描述，
 * 前端可以直接展示中文类型名，不用再维护一份类型映射表。
 * </p>
 */
@Data
public class NotificationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 通知ID */
    private Long id;

    /** 接收人类型：1用户 2商家 3管理员 */
    private Integer receiverType;

    /** 接收人ID */
    private Long receiverId;

    /** 通知类型：1订单 2支付 3退款 4商家审核 5提现 6系统 */
    private Integer type;

    /** 通知类型描述（如"订单"、"提现"，由后端根据 type 翻译） */
    private String typeDesc;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联业务类型（如 order/withdraw/merchant） */
    private String bizType;

    /** 关联业务ID（如订单号、提现单号） */
    private String bizId;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 创建时间 */
    private LocalDateTime createTime;
}
