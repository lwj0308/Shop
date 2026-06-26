package com.shop.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shop.common.exception.BusinessException;
import com.shop.common.model.PageResult;
import com.shop.common.result.ErrorCode;
import com.shop.model.notification.dto.NotificationQueryDTO;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.entity.Notification;
import com.shop.model.notification.enums.NotificationTypeEnum;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import com.shop.model.notification.vo.NotificationVO;
import com.shop.user.mapper.NotificationMapper;
import com.shop.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息通知服务实现类
 * <p>
 * 实现通知的发送、查询、标记已读等核心业务逻辑。
 * 通知统一存在 shop_user 库的 notification 表，用 receiver_type 区分接收人类型。
 * </p>
 * <p>
 * 设计说明：
 * - 用户端接口（如标记已读）会校验通知归属权，防止用户操作别人的通知
 * - 内部接口（如发送、查询列表）不校验归属权，由调用方（其他微服务）保证参数正确
 * - 通知表没有 deleted 字段，通知只标记已读不删除
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** 通知Mapper，操作notification表 */
    private final NotificationMapper notificationMapper;

    /**
     * 发送一条通知
     * <p>
     * 把 DTO 转成实体类直接插入数据库。isRead 默认为 0（未读）。
     * </p>
     *
     * @param dto 通知内容
     */
    @Override
    public void sendNotification(NotificationSendDTO dto) {
        // 参数校验：接收人类型、接收人ID、标题、内容不能为空
        if (dto.getReceiverType() == null || dto.getReceiverId() == null
                || dto.getTitle() == null || dto.getContent() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "通知参数不完整");
        }

        // 校验接收人类型是否合法
        if (ReceiverTypeEnum.getByCode(dto.getReceiverType()) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "无效的接收人类型");
        }

        // 校验通知类型是否合法（如果传了的话）
        if (dto.getType() != null && NotificationTypeEnum.getByCode(dto.getType()) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "无效的通知类型");
        }

        Notification notification = new Notification();
        notification.setReceiverType(dto.getReceiverType());
        notification.setReceiverId(dto.getReceiverId());
        notification.setType(dto.getType());
        notification.setTitle(dto.getTitle());
        notification.setContent(dto.getContent());
        notification.setBizType(dto.getBizType());
        notification.setBizId(dto.getBizId());
        notification.setIsRead(0); // 默认未读

        notificationMapper.insert(notification);
        log.info("发送通知: receiverType={}, receiverId={}, type={}, title={}",
                dto.getReceiverType(), dto.getReceiverId(), dto.getType(), dto.getTitle());
    }

    /**
     * 查询通知列表（分页）
     * <p>
     * 支持按通知类型和已读状态筛选，按创建时间倒序排列（最新的在前）。
     * </p>
     *
     * @param queryDTO 查询条件
     * @return 分页通知列表
     */
    @Override
    public PageResult<NotificationVO> getNotificationList(NotificationQueryDTO queryDTO) {
        // 构建查询条件
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverType, queryDTO.getReceiverType())
                .eq(Notification::getReceiverId, queryDTO.getReceiverId())
                // 可选条件：按通知类型筛选
                .eq(queryDTO.getType() != null, Notification::getType, queryDTO.getType())
                // 可选条件：按已读状态筛选
                .eq(queryDTO.getIsRead() != null, Notification::getIsRead, queryDTO.getIsRead())
                // 按创建时间倒序，最新的通知排前面
                .orderByDesc(Notification::getCreateTime);

        // 分页查询（page从1开始）
        Page<Notification> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        Page<Notification> resultPage = notificationMapper.selectPage(page, wrapper);

        // 把实体列表转成VO列表（顺便填充typeDesc）
        List<NotificationVO> voList = resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.from(resultPage, voList);
    }

    /**
     * 查询未读通知数量
     *
     * @param receiverType 接收人类型
     * @param receiverId   接收人ID
     * @return 未读通知数量
     */
    @Override
    public long getUnreadCount(Integer receiverType, Long receiverId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getReceiverType, receiverType)
                        .eq(Notification::getReceiverId, receiverId)
                        .eq(Notification::getIsRead, 0)
        );
    }

    /**
     * 全部标记已读
     * <p>
     * 把指定接收人的所有未读通知（is_read=0）批量更新为已读（is_read=1）。
     * 使用 LambdaUpdateWrapper 构造条件更新，不用先查再改，效率更高。
     * </p>
     *
     * @param receiverType 接收人类型
     * @param receiverId   接收人ID
     */
    @Override
    public void markAllAsRead(Integer receiverType, Long receiverId) {
        Notification update = new Notification();
        update.setIsRead(1);

        notificationMapper.update(update,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getReceiverType, receiverType)
                        .eq(Notification::getReceiverId, receiverId)
                        .eq(Notification::getIsRead, 0)
        );
        log.info("全部标记已读: receiverType={}, receiverId={}", receiverType, receiverId);
    }

    /**
     * 标记单条通知为已读
     * <p>
     * 用户端调用，会校验通知是否属于当前用户，防止用户通过改ID操作别人的通知。
     * 如果通知已经是已读状态，不做任何操作（幂等）。
     * </p>
     *
     * @param userId         当前登录用户ID
     * @param notificationId 通知ID
     */
    @Override
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "通知不存在");
        }

        // 校验归属权：用户端只能操作自己的通知
        // 用户端的 receiverType 固定为 1（用户）
        // ReceiverTypeEnum.getCode() 返回 int，包装成 Integer 用 equals 比较（避免拆箱NPE）
        if (!Integer.valueOf(ReceiverTypeEnum.USER.getCode()).equals(notification.getReceiverType())
                || !userId.equals(notification.getReceiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作此通知");
        }

        // 如果已经是已读，直接返回（幂等操作）
        if (notification.getIsRead() == 1) {
            return;
        }

        // 更新为已读
        Notification update = new Notification();
        update.setId(notificationId);
        update.setIsRead(1);
        notificationMapper.updateById(update);
    }

    /**
     * Notification实体转NotificationVO
     * <p>
     * 把数据库查出来的实体转成返回给前端的VO，
     * 顺便根据 type 字段翻译出 typeDesc（中文类型名），前端不用再维护一份类型映射表。
     * </p>
     *
     * @param notification 通知实体
     * @return 通知VO
     */
    private NotificationVO convertToVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setReceiverType(notification.getReceiverType());
        vo.setReceiverId(notification.getReceiverId());
        vo.setType(notification.getType());
        // 翻译类型描述
        NotificationTypeEnum typeEnum = NotificationTypeEnum.getByCode(notification.getType());
        vo.setTypeDesc(typeEnum != null ? typeEnum.getDesc() : "");
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setBizType(notification.getBizType());
        vo.setBizId(notification.getBizId());
        vo.setIsRead(notification.getIsRead());
        vo.setCreateTime(notification.getCreateTime());
        return vo;
    }
}
