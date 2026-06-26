package com.shop.order.feign.fallback;

import com.shop.common.result.Result;
import com.shop.order.feign.MerchantFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 商家服务Feign降级工厂
 * <p>
 * 当商家服务不可用时，Feign会自动走这里的降级逻辑。
 * </p>
 * <p>
 * 降级策略：
 * - 结算接口：降级时只记录日志，不抛异常。
 *   因为确认收货已经成功，结算失败不应该影响收货主流程。
 *   可以通过定时任务扫描"已收货但未结算"的订单进行补偿。
 * </p>
 */
@Slf4j
@Component
public class MerchantFeignClientFallbackFactory implements FallbackFactory<MerchantFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的MerchantFeignClient实例
     */
    @Override
    public MerchantFeignClient create(Throwable cause) {
        log.error("商家服务调用失败，触发降级", cause);
        return new MerchantFeignClient() {

            /**
             * 订单结算降级：记录日志，不抛异常
             * <p>
             * 确认收货已经成功，结算失败不影响收货。
             * 记录订单号方便后续通过定时任务补偿结算。
             * </p>
             */
            @Override
            public Result<Void> settleOrder(Long merchantId, String orderNo, BigDecimal orderAmount) {
                log.error("订单结算降级，需要通过定时任务补偿: merchantId={}, orderNo={}, orderAmount={}",
                        merchantId, orderNo, orderAmount);
                return Result.fail("商家服务暂时不可用，结算将延迟处理");
            }
        };
    }
}
