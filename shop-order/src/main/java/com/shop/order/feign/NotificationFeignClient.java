package com.shop.order.feign;

import com.shop.common.result.Result;
import com.shop.model.notification.dto.NotificationSendDTO;
import com.shop.order.feign.fallback.NotificationFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 通知服务Feign客户端（订单服务用）
 * <p>
 * 通过Feign远程调用用户服务，发送消息通知给用户。
 * 调用的是用户服务的内部接口（/user/notification/inner/**），不需要登录鉴权。
 * </p>
 * <p>
 * 使用场景：
 * - 订单发货时通知用户
 * - 用户确认收货时通知用户
 * - 订单取消时通知用户
 * </p>
 * <p>
 * 降级策略：通知发送失败不影响订单主流程（订单已发货/已收货），
 * 降级时只记录日志，通知可以后续补偿。
 * </p>
 */
@FeignClient(name = "shop-user", path = "/user/notification", fallbackFactory = NotificationFeignClientFallbackFactory.class)
public interface NotificationFeignClient {

    /**
     * 发送通知
     * <p>
     * 把通知内容传给用户服务，用户服务会插入 notification 表。
     * </p>
     *
     * @param dto 通知内容
     * @return 操作结果
     */
    @PostMapping("/inner/send")
    Result<Void> sendNotification(@RequestBody NotificationSendDTO dto);
}
