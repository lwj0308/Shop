package com.shop.payment.feign.fallback;

import com.shop.common.result.Result;
import com.shop.payment.feign.OrderFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 订单服务Feign降级工厂
 * <p>
 * 当订单服务不可用时，走降级逻辑。
 * 降级策略：
 * - paySuccess：记录日志，不抛异常（因为主要走MQ通知）
 * - getPayAmount：返回null，由调用方判断是否阻断（金额无法验证时必须阻断）
 * </p>
 */
@Slf4j
@Component
public class OrderFeignClientFallbackFactory implements FallbackFactory<OrderFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的OrderFeignClient实例
     */
    @Override
    public OrderFeignClient create(Throwable cause) {
        log.error("订单服务调用失败，触发降级", cause);
        return new OrderFeignClient() {
            @Override
            public Result<Void> paySuccess(String orderNo) {
                // 降级时只记录日志，不抛异常
                // 支付结果已经通过MQ通知了，Feign调用失败不影响主流程
                log.warn("通知订单服务支付成功失败（降级）: orderNo={}，已通过MQ通知", orderNo);
                return Result.success(null);
            }

            @Override
            public Result<BigDecimal> getPayAmount(String orderNo, Long userId) {
                // 金额反查降级：返回null，由调用方判断
                // 调用方在金额无法验证时必须拒绝创建支付，不能信任前端金额
                log.error("反查订单金额失败（降级）: orderNo={}, userId={}，金额无法验证将拒绝创建支付",
                        orderNo, userId);
                return Result.success(null);
            }
        };
    }
}
