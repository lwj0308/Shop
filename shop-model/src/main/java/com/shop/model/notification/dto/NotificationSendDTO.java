package com.shop.model.notification.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发送通知请求DTO
 * <p>
 * 其他微服务（如 shop-order、shop-merchant）通过 Feign 调用 shop-user 内部接口发送通知时，
 * 把通知内容封装在这个 DTO 里传过来。
 * </p>
 * <p>
 * 使用示例（shop-order 发送发货通知）：
 * <pre>
 * NotificationSendDTO dto = NotificationSendDTO.builder()
 *     .receiverType(ReceiverTypeEnum.USER.getCode())
 *     .receiverId(order.getUserId())
 *     .type(NotificationTypeEnum.ORDER.getCode())
 *     .title("您的订单已发货")
 *     .content("订单 " + order.getOrderNo() + " 已发货")
 *     .bizType("order")
 *     .bizId(order.getOrderNo())
 *     .build();
 * userFeignClient.sendNotification(dto);
 * </pre>
 * </p>
 */
@Data
public class NotificationSendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 接收人类型：1用户 2商家 3管理员（对应 ReceiverTypeEnum） */
    private Integer receiverType;

    /** 接收人ID（用户ID/商家ID/管理员ID） */
    private Long receiverId;

    /** 通知类型：1订单 2支付 3退款 4商家审核 5提现 6系统（对应 NotificationTypeEnum） */
    private Integer type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联业务类型（如 order/withdraw/merchant，可选） */
    private String bizType;

    /** 关联业务ID（如订单号、提现单号，可选） */
    private String bizId;
}
