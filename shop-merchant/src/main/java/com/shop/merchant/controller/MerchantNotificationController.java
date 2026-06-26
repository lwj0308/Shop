package com.shop.merchant.controller;

import com.shop.common.result.Result;
import com.shop.common.util.SecurityUtils;
import com.shop.merchant.feign.NotificationFeignClient;
import com.shop.merchant.service.MerchantService;
import com.shop.model.merchant.vo.MerchantVO;
import com.shop.model.notification.enums.ReceiverTypeEnum;
import com.shop.model.notification.vo.NotificationVO;
import com.shop.common.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商家消息通知控制器
 * <p>
 * 提供商家端的消息通知查询、未读数量统计、标记已读等接口。
 * 所有接口都需要商家登录，通过 SecurityUtils 获取当前登录用户ID，
 * 再查出对应的商家ID，商家只能查看自己的通知。
 * </p>
 * <p>
 * 通知数据实际存储在 shop_user 库的 notification 表中，
 * 本服务通过 Feign 调用 shop-user 的内部接口获取数据。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/merchant/notification")
@RequiredArgsConstructor
@Tag(name = "商家消息通知", description = "商家端消息通知查询和标记已读")
public class MerchantNotificationController {

    /** 通知服务Feign客户端，远程调用用户服务获取通知数据 */
    private final NotificationFeignClient notificationFeignClient;

    /** 商家服务，用于通过用户ID查找商家ID */
    private final MerchantService merchantService;

    /**
     * 查询当前商家的通知列表
     * <p>
     * 商家端通知列表页调用，支持按通知类型和已读状态筛选。
     * 接收人类型固定为 2（商家），接收人ID为当前登录商家的ID。
     * </p>
     *
     * @param type   通知类型筛选（可选）
     * @param isRead 已读状态筛选（可选：0未读 1已读）
     * @param page   页码
     * @param size   每页条数
     * @return 分页通知列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询通知列表", description = "查询当前商家的通知列表，支持分页和筛选")
    public Result<PageResult<NotificationVO>> getNotificationList(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long merchantId = getCurrentMerchantId();
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }

        Result<PageResult<NotificationVO>> result = notificationFeignClient.getNotificationList(
                ReceiverTypeEnum.MERCHANT.getCode(), merchantId, type, isRead, page, size);
        return result;
    }

    /**
     * 查询当前商家的未读通知数量
     * <p>
     * 商家端顶部的铃铛徽章会调用这个接口，显示未读数量。
     * </p>
     *
     * @return 未读通知数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "查询未读数量", description = "查询当前商家的未读通知数量")
    public Result<Long> getUnreadCount() {
        Long merchantId = getCurrentMerchantId();
        if (merchantId == null) {
            return Result.success(0L);
        }

        Result<Long> result = notificationFeignClient.getUnreadCount(
                ReceiverTypeEnum.MERCHANT.getCode(), merchantId);
        return result;
    }

    /**
     * 全部标记已读
     * <p>
     * 把当前商家的所有未读通知一次性标记为已读。
     * </p>
     *
     * @return 操作结果
     */
    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读", description = "把当前商家的所有未读通知标记为已读")
    public Result<Void> markAllAsRead() {
        Long merchantId = getCurrentMerchantId();
        if (merchantId == null) {
            return Result.fail("商家信息不存在");
        }

        Result<Void> result = notificationFeignClient.markAllAsRead(
                ReceiverTypeEnum.MERCHANT.getCode(), merchantId);
        return result;
    }

    /**
     * 获取当前登录商家的ID
     * <p>
     * 商家登录时用的是用户ID（userId），需要通过用户ID查找对应的商家ID。
     * </p>
     *
     * @return 商家ID，如果当前用户不是商家返回 null
     */
    private Long getCurrentMerchantId() {
        Long userId = SecurityUtils.requireLogin();
        MerchantVO merchant = merchantService.getMerchantByUserId(userId);
        return merchant != null ? merchant.getId() : null;
    }
}
