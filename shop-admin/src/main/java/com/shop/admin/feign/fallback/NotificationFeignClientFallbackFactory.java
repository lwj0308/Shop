package com.shop.admin.feign.fallback;

import com.shop.admin.feign.NotificationFeignClient;
import com.shop.common.model.PageResult;
import com.shop.common.result.Result;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.model.notification.vo.NotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 通知服务Feign降级工厂（管理后台用）
 * <p>
 * 当用户服务不可用时，Feign会自动走这里的降级逻辑。
 * </p>
 * <p>
 * 降级策略：
 * - 发送通知：降级返回失败提示
 * - 查询通知列表：降级返回空列表，管理端显示"暂无通知"
 * - 查询未读数量：降级返回0
 * - 全部标记已读：降级返回失败提示
 * </p>
 */
@Slf4j
@Component
public class NotificationFeignClientFallbackFactory implements FallbackFactory<NotificationFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的NotificationFeignClient实例
     */
    @Override
    public NotificationFeignClient create(Throwable cause) {
        log.error("通知服务调用失败，触发降级", cause);
        return new NotificationFeignClient() {

            /** 发送通知降级：返回失败提示 */
            @Override
            public Result<Void> sendNotification(NotificationSendDTO dto) {
                log.error("发送通知降级: receiverType={}, receiverId={}, title={}",
                        dto.getReceiverType(), dto.getReceiverId(), dto.getTitle());
                return Result.fail("通知服务暂时不可用");
            }

            /** 查询通知列表降级：返回空列表 */
            @Override
            public Result<PageResult<NotificationVO>> getNotificationList(Integer receiverType, Long receiverId,
                                                                          Integer type, Integer isRead,
                                                                          Integer page, Integer size) {
                log.warn("查询通知列表降级: receiverType={}, receiverId={}", receiverType, receiverId);
                return Result.success(PageResult.empty());
            }

            /** 查询未读数量降级：返回0 */
            @Override
            public Result<Long> getUnreadCount(Integer receiverType, Long receiverId) {
                log.warn("查询未读数量降级: receiverType={}, receiverId={}", receiverType, receiverId);
                return Result.success(0L);
            }

            /** 全部标记已读降级：返回失败提示 */
            @Override
            public Result<Void> markAllAsRead(Integer receiverType, Long receiverId) {
                log.warn("全部标记已读降级: receiverType={}, receiverId={}", receiverType, receiverId);
                return Result.fail("通知服务暂时不可用");
            }
        };
    }
}
