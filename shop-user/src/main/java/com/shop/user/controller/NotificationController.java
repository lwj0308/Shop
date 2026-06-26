package com.shop.user.controller;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.notification.dto.NotificationQueryDTO;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import com.shop.model.notification.vo.NotificationVO;
import com.shop.user.service.NotificationService;
import com.shop.user.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 消息通知控制器
 * <p>
 * 提供消息通知的查询、标记已读等接口。
 * 接口分为两类：
 * </p>
 * <p>
 * 1. 用户端接口（/user/notification/**）：需要登录，操作当前登录用户的通知。
 *    接收人类型固定为 1（用户），接收人ID从 UserContext 获取。
 * </p>
 * <p>
 * 2. 内部接口（/user/notification/inner/**）：不鉴权，供其他微服务通过 Feign 调用。
 *    用于发送通知、查询商家/管理员的通知等。
 *    路径在 SaTokenConfig 中加入白名单。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/user/notification")
@RequiredArgsConstructor
@Tag(name = "消息通知", description = "消息通知的查询、标记已读等接口")
public class NotificationController {

    /** 消息通知服务 */
    private final NotificationService notificationService;

    // ==================== 用户端接口（需要登录） ====================

    /**
     * 查询当前用户的通知列表
     * <p>
     * 用户端调用，查询当前登录用户的通知列表，支持按类型和已读状态筛选。
     * 接收人类型固定为 1（用户），接收人ID从登录态获取。
     * </p>
     *
     * @param type   通知类型筛选（可选）
     * @param isRead 已读状态筛选（可选：0未读 1已读）
     * @param page   页码
     * @param size   每页条数
     * @return 分页通知列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询通知列表", description = "查询当前用户的通知列表，支持分页和筛选")
    public Result<PageResult<NotificationVO>> getNotificationList(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long userId = UserContext.getUserId();

        NotificationQueryDTO queryDTO = new NotificationQueryDTO();
        queryDTO.setReceiverType(ReceiverTypeEnum.USER.getCode());
        queryDTO.setReceiverId(userId);
        queryDTO.setType(type);
        queryDTO.setIsRead(isRead);
        queryDTO.setPage(page);
        queryDTO.setSize(size);

        PageResult<NotificationVO> pageResult = notificationService.getNotificationList(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 查询当前用户的未读通知数量
     * <p>
     * 用户端顶部的铃铛徽章会调用这个接口，显示未读数量。
     * </p>
     *
     * @return 未读通知数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "查询未读数量", description = "查询当前用户的未读通知数量")
    public Result<Long> getUnreadCount() {
        Long userId = UserContext.getUserId();
        long count = notificationService.getUnreadCount(ReceiverTypeEnum.USER.getCode(), userId);
        return Result.success(count);
    }

    /**
     * 标记单条通知为已读
     * <p>
     * 用户点击某条通知时调用。会校验通知是否属于当前用户。
     * </p>
     *
     * @param id 通知ID
     * @return 操作结果
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "标记单条已读", description = "把指定通知标记为已读")
    public Result<Void> markAsRead(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        notificationService.markAsRead(userId, id);
        return Result.success("标记成功", null);
    }

    /**
     * 全部标记已读
     * <p>
     * 把当前用户的所有未读通知一次性标记为已读。
     * </p>
     *
     * @return 操作结果
     */
    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读", description = "把当前用户的所有未读通知标记为已读")
    public Result<Void> markAllAsRead() {
        Long userId = UserContext.getUserId();
        notificationService.markAllAsRead(ReceiverTypeEnum.USER.getCode(), userId);
        return Result.success("全部已读", null);
    }

    // ==================== 内部接口（不鉴权，供 Feign 调用） ====================

    /**
     * 发送通知（内部接口）
     * <p>
     * 供其他微服务通过 Feign 调用，发送一条通知。
     * 路径 /inner/** 在 SaTokenConfig 中加入白名单，不需要登录。
     * </p>
     *
     * @param dto 通知内容
     * @return 操作结果
     */
    @PostMapping("/inner/send")
    @Operation(summary = "发送通知（内部接口）", description = "供其他微服务通过Feign调用，发送一条通知")
    public Result<Void> sendNotification(@Validated @RequestBody NotificationSendDTO dto) {
        notificationService.sendNotification(dto);
        return Result.success("发送成功", null);
    }

    /**
     * 查询通知列表（内部接口）
     * <p>
     * 供商家端、管理端通过 Feign 调用，查询指定接收人的通知列表。
     * </p>
     *
     * @param receiverType 接收人类型：1用户 2商家 3管理员
     * @param receiverId   接收人ID
     * @param type         通知类型筛选（可选）
     * @param isRead       已读状态筛选（可选）
     * @param page         页码
     * @param size         每页条数
     * @return 分页通知列表
     */
    @GetMapping("/inner/list")
    @Operation(summary = "查询通知列表（内部接口）", description = "供商家端、管理端通过Feign调用")
    public Result<PageResult<NotificationVO>> innerGetNotificationList(
            @RequestParam Integer receiverType,
            @RequestParam Long receiverId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        NotificationQueryDTO queryDTO = new NotificationQueryDTO();
        queryDTO.setReceiverType(receiverType);
        queryDTO.setReceiverId(receiverId);
        queryDTO.setType(type);
        queryDTO.setIsRead(isRead);
        queryDTO.setPage(page);
        queryDTO.setSize(size);

        PageResult<NotificationVO> pageResult = notificationService.getNotificationList(queryDTO);
        return Result.success(pageResult);
    }

    /**
     * 查询未读通知数量（内部接口）
     * <p>
     * 供商家端、管理端通过 Feign 调用，查询指定接收人的未读通知数量。
     * </p>
     *
     * @param receiverType 接收人类型
     * @param receiverId   接收人ID
     * @return 未读通知数量
     */
    @GetMapping("/inner/unread-count")
    @Operation(summary = "查询未读数量（内部接口）", description = "供商家端、管理端通过Feign调用")
    public Result<Long> innerGetUnreadCount(
            @RequestParam Integer receiverType,
            @RequestParam Long receiverId) {
        long count = notificationService.getUnreadCount(receiverType, receiverId);
        return Result.success(count);
    }

    /**
     * 全部标记已读（内部接口）
     * <p>
     * 供商家端、管理端通过 Feign 调用，把指定接收人的所有未读通知标记为已读。
     * </p>
     *
     * @param receiverType 接收人类型
     * @param receiverId   接收人ID
     * @return 操作结果
     */
    @PutMapping("/inner/read-all")
    @Operation(summary = "全部标记已读（内部接口）", description = "供商家端、管理端通过Feign调用")
    public Result<Void> innerMarkAllAsRead(
            @RequestParam Integer receiverType,
            @RequestParam Long receiverId) {
        notificationService.markAllAsRead(receiverType, receiverId);
        return Result.success("全部已读", null);
    }
}
