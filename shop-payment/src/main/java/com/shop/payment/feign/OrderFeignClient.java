package com.shop.payment.feign;

import com.shop.common.result.Result;
import com.shop.payment.feign.fallback.OrderFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 订单服务远程调用接口
 * <p>
 * 支付服务需要调用订单服务通知支付结果。
 * 这是备用方案：主要用RocketMQ异步通知，但如果MQ不可用，
 * 可以通过Feign直接调用订单服务来保证支付结果能送达。
 * </p>
 * <p>
 * 同时提供订单金额反查能力：创建支付记录时必须反查订单实付金额，
 * 不能信任前端传入的金额，防止金额篡改攻击。
 * </p>
 * <p>
 * 降级策略：
 * - paySuccess 降级时记录日志，不抛异常（因为主要走MQ）
 * - getPayAmount 降级时返回 null，由调用方决定是否阻断（金额无法验证时必须阻断）
 * </p>
 */
@FeignClient(name = "shop-order", path = "/order",
        fallbackFactory = OrderFeignClientFallbackFactory.class)
public interface OrderFeignClient {

    /**
     * 通知订单服务支付成功
     * <p>
     * 支付成功后调用此接口，让订单服务把订单状态更新为"已支付"。
     * 这是备用方案，主要还是通过RocketMQ异步通知。
     * </p>
     *
     * @param orderNo 订单号
     * @return 操作结果
     */
    @PostMapping("/pay-success")
    Result<Void> paySuccess(@RequestParam("orderNo") String orderNo);

    /**
     * 根据订单号反查实付金额（支付安全核心）
     * <p>
     * 创建支付记录时调用，用订单服务的真实金额覆盖前端传入的金额，
     * 防止前端篡改金额（比如把100元改成0.01元）。
     * 同时校验订单归属：传入userId确保只能给自己的订单创建支付。
     * </p>
     *
     * @param orderNo 订单号
     * @param userId  当前用户ID（用于归属校验）
     * @return 订单实付金额（降级时返回null）
     */
    @GetMapping("/inner/pay-amount")
    Result<BigDecimal> getPayAmount(@RequestParam("orderNo") String orderNo,
                                    @RequestParam("userId") Long userId);
}
