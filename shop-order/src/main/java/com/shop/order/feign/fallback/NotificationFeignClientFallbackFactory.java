package com.shop.order.feign.fallback;

import com.shop.common.result.Result;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.order.feign.NotificationFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 通知服务Feign降级工厂（订单服务用）
 * <p>
 * 当用户服务不可用时，Feign会自动走这里的降级逻辑。
 * </p>
 * <p>
 * 降级策略：
 * - 发送通知：降级时只记录日志，不抛异常。
 *   因为通知发送失败不应该影响订单主流程（比如发货已经成功了）。
 *   通知可以后续通过定时任务补偿，或者用户主动查看订单状态。
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

            /**
             * 发送通知降级：记录日志，不抛异常
             * <p>
             * 通知发送失败不影响订单主流程，只记录日志方便排查。
             * </p>
             */
            @Override
            public Result<Void> sendNotification(NotificationSendDTO dto) {
                log.error("发送通知降级: receiverType={}, receiverId={}, title={}",
                        dto.getReceiverType(), dto.getReceiverId(), dto.getTitle());
                return Result.fail("通知服务暂时不可用，通知将延迟发送");
            }
        };
    }
}
