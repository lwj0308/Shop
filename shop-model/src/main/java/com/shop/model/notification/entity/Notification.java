package com.shop.model.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息通知实体
 * <p>
 * 对应数据库的 notification 表，统一存储用户/商家/管理员三类角色的站内通知。
 * 通过 receiver_type 字段区分接收人类型，避免为每类角色建一张表。
 * </p>
 * <p>
 * 注意：本表没有 deleted 逻辑删除字段（通知只标记已读，不删除），
 * 所以不继承 BaseEntity，自己管理 id/createTime/updateTime 字段。
 * </p>
 */
@Data
@TableName("notification")
public class Notification implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 通知ID（雪花算法自动生成） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收人类型：1用户 2商家 3管理员（对应 ReceiverTypeEnum） */
    private Integer receiverType;

    /** 接收人ID（根据 receiver_type 分别对应用户ID/商家ID/管理员ID） */
    private Long receiverId;

    /** 通知类型：1订单 2支付 3退款 4商家审核 5提现 6系统（对应 NotificationTypeEnum） */
    private Integer type;

    /** 通知标题（比如"您的订单已发货"） */
    private String title;

    /** 通知内容（比如"订单 SO202606250001 已由顺丰发出，单号SF123"） */
    private String content;

    /** 关联业务类型（如 order/withdraw/merchant，便于前端跳转到对应业务详情页） */
    private String bizType;

    /** 关联业务ID（如订单号、提现单号，便于前端跳转） */
    private String bizId;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 创建时间（新增时自动填充） */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（新增和修改时自动填充） */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
