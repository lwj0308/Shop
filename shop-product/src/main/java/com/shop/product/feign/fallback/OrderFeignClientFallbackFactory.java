package com.shop.product.feign.fallback;

import com.shop.common.result.Result;
import com.shop.product.feign.OrderFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 订单服务Feign降级工厂
 * <p>
 * 当订单服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * 降级策略：
 * - checkOrderOwnership（校验订单归属）：降级返回false，校验失败时不允许评价，保证安全。
 *   就像门卫不在时，宁可不让陌生人进来，也不让可疑人员进入。
 * - markOrderReviewed（标记已评价）：降级仅记日志返回null，不影响评价本身。
 *   评价已经写入了，标记失败可以由对账任务补偿，不必阻断用户。
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

            /**
             * 校验订单归属降级：返回false
             * <p>
             * 校验类降级返回false，调用方（CommentServiceImpl）会判断false并抛异常拒绝评价，
             * 保证订单服务故障时不会被恶意利用评价接口。
             * </p>
             *
             * @param orderId 订单ID
             * @param userId  用户ID
             * @return 降级返回false（不允许评价）
             */
            @Override
            public Result<Boolean> checkOrderOwnership(Long orderId, Long userId) {
                log.warn("校验订单归属降级: orderId={}, userId={}", orderId, userId);
                return Result.success(false);
            }

            /**
             * 标记订单已评价降级：仅记日志返回null
             * <p>
             * 评价已经写入product_comment表，标记订单为已评价是辅助操作，
             * 失败时记日志由对账任务补偿，不阻塞评价主流程。
             * </p>
             *
             * @param orderId 订单ID
             * @return 降级返回null
             */
            @Override
            public Result<Void> markOrderReviewed(Long orderId) {
                log.warn("标记订单已评价降级: orderId={}", orderId);
                return null;
            }
        };
    }
}
