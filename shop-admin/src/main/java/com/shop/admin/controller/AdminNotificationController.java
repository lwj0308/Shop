package com.shop.admin.controller;

import com.shop.admin.feign.NotificationFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import com.shop.model.notification.vo.NotificationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员消息通知控制器
 * <p>
 * 提供管理端的消息通知查询、未读数量统计、标记已读等接口。
 * 所有接口都需要管理员登录，通过 SecurityUtils 获取当前登录管理员ID。
 * 管理员只能查看发给自己的通知。
 * </p>
 * <p>
 * 通知数据实际存储在 shop_user 库的 notification 表中，
 * 本服务通过 Feign 调用 shop-user 的内部接口获取数据。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/notification")
@RequiredArgsConstructor
@Tag(name = "管理员消息通知", description = "管理端消息通知查询和标记已读")
public class AdminNotificationController {

    /** 通知服务Feign客户端，远程调用用户服务获取通知数据 */
    private final NotificationFeignClient notificationFeignClient;

    /**
     * 查询当前管理员的通知列表
     * <p>
     * 管理端通知列表页调用，支持按通知类型和已读状态筛选。
     * 接收人类型固定为 3（管理员），接收人ID为当前登录管理员的ID。
     * </p>
     *
     * @param type   通知类型筛选（可选）
     * @param isRead 已读状态筛选（可选：0未读 1已读）
     * @param page   页码
     * @param size   每页条数
     * @return 分页通知列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询通知列表", description = "查询当前管理员的通知列表，支持分页和筛选")
    public Result<PageResult<NotificationVO>> getNotificationList(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long adminId = SecurityUtils.requireLogin();

        Result<PageResult<NotificationVO>> result = notificationFeignClient.getNotificationList(
                ReceiverTypeEnum.ADMIN.getCode(), adminId, type, isRead, page, size);
        return result;
    }

    /**
     * 查询当前管理员的未读通知数量
     * <p>
     * 管理端顶部的铃铛徽章会调用这个接口，显示未读数量。
     * </p>
     *
     * @return 未读通知数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "查询未读数量", description = "查询当前管理员的未读通知数量")
    public Result<Long> getUnreadCount() {
        Long adminId = SecurityUtils.requireLogin();

        Result<Long> result = notificationFeignClient.getUnreadCount(
                ReceiverTypeEnum.ADMIN.getCode(), adminId);
        return result;
    }

    /**
     * 全部标记已读
     * <p>
     * 把当前管理员的所有未读通知一次性标记为已读。
     * </p>
     *
     * @return 操作结果
     */
    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读", description = "把当前管理员的所有未读通知标记为已读")
    public Result<Void> markAllAsRead() {
        Long adminId = SecurityUtils.requireLogin();

        Result<Void> result = notificationFeignClient.markAllAsRead(
                ReceiverTypeEnum.ADMIN.getCode(), adminId);
        return result;
    }
}
