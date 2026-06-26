package com.shop.merchant.feign;

import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.merchant.feign.fallback.NotificationFeignClientFallbackFactory;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.vo.NotificationVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 通知服务Feign客户端（商家服务用）
 * <p>
 * 通过Feign远程调用用户服务，发送消息通知和查询商家通知。
 * 调用的是用户服务的内部接口（/user/notification/inner/**），不需要登录鉴权。
 * </p>
 * <p>
 * 使用场景：
 * - 商家入驻审核结果通知（发送给商家）
 * - 提现申请/审核结果通知（发送给商家）
 * - 商家端查询自己的通知列表、未读数量、标记已读
 * </p>
 * <p>
 * 降级策略：
 * - 发送通知：降级时只记录日志，不抛异常（通知失败不影响审核主流程）
 * - 查询通知：降级返回空列表/0，商家端显示"暂无通知"
 * </p>
 */
@FeignClient(name = "shop-user", path = "/user/notification", fallbackFactory = NotificationFeignClientFallbackFactory.class)
public interface NotificationFeignClient {

    /**
     * 发送通知
     *
     * @param dto 通知内容
     * @return 操作结果
     */
    @PostMapping("/inner/send")
    Result<Void> sendNotification(@RequestBody NotificationSendDTO dto);

    /**
     * 查询通知列表（商家端用）
     *
     * @param receiverType 接收人类型（商家端固定传2）
     * @param receiverId   接收人ID（商家ID）
     * @param type         通知类型筛选（可选）
     * @param isRead       已读状态筛选（可选）
     * @param page         页码
     * @param size         每页条数
     * @return 分页通知列表
     */
    @GetMapping("/inner/list")
    Result<PageResult<NotificationVO>> getNotificationList(@RequestParam("receiverType") Integer receiverType,
                                                            @RequestParam("receiverId") Long receiverId,
                                                            @RequestParam(value = "type", required = false) Integer type,
                                                            @RequestParam(value = "isRead", required = false) Integer isRead,
                                                            @RequestParam("page") Integer page,
                                                            @RequestParam("size") Integer size);

    /**
     * 查询未读通知数量（商家端用）
     *
     * @param receiverType 接收人类型
     * @param receiverId   接收人ID
     * @return 未读通知数量
     */
    @GetMapping("/inner/unread-count")
    Result<Long> getUnreadCount(@RequestParam("receiverType") Integer receiverType,
                                 @RequestParam("receiverId") Long receiverId);

    /**
     * 全部标记已读（商家端用）
     *
     * @param receiverType 接收人类型
     * @param receiverId   接收人ID
     * @return 操作结果
     */
    @PutMapping("/inner/read-all")
    Result<Void> markAllAsRead(@RequestParam("receiverType") Integer receiverType,
                                @RequestParam("receiverId") Long receiverId);
}
