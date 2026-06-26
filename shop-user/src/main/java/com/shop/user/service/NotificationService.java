package com.shop.user.service;

import com.shop.common.model.PageResult;
import com.shop.model.notification.dto.NotificationQueryDTO;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.vo.NotificationVO;

/**
 * 消息通知服务接口
 * <p>
 * 定义消息通知的核心业务方法，包括发送通知、查询通知列表、
 * 查询未读数量、标记已读等。
 * </p>
 * <p>
 * 接口分为两类：
 * 1. 用户端接口：由当前登录用户操作自己的通知（receiverId 从 UserContext 获取）
 * 2. 内部接口：供其他微服务通过 Feign 调用，操作任意接收人的通知
 * </p>
 */
public interface NotificationService {

    /**
     * 发送一条通知（内部接口使用）
     * <p>
     * 其他微服务（如 shop-order 发货时、shop-merchant 审核时）通过 Feign 调用此方法发送通知。
     * </p>
     *
     * @param dto 通知内容
     */
    void sendNotification(NotificationSendDTO dto);

    /**
     * 查询通知列表（内部接口使用）
     * <p>
     * 供商家端、管理端通过 Feign 调用，查询指定接收人的通知列表。
     * </p>
     *
     * @param queryDTO 查询条件（包含 receiverType、receiverId、type、isRead、分页参数）
     * @return 分页通知列表
     */
    PageResult<NotificationVO> getNotificationList(NotificationQueryDTO queryDTO);

    /**
     * 查询未读通知数量（内部接口使用）
     *
     * @param receiverType 接收人类型：1用户 2商家 3管理员
     * @param receiverId   接收人ID
     * @return 未读通知数量
     */
    long getUnreadCount(Integer receiverType, Long receiverId);

    /**
     * 全部标记已读（内部接口使用）
     * <p>
     * 把指定接收人的所有未读通知标记为已读。
     * </p>
     *
     * @param receiverType 接收人类型
     * @param receiverId   接收人ID
     */
    void markAllAsRead(Integer receiverType, Long receiverId);

    /**
     * 标记单条通知为已读
     * <p>
     * 用户端调用，只能标记自己的通知。会校验通知归属权。
     * </p>
     *
     * @param userId     当前登录用户ID
     * @param notificationId 通知ID
     */
    void markAsRead(Long userId, Long notificationId);
}
