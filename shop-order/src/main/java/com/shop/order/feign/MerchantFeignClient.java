package com.shop.order.feign;

import com.shop.common.result.Result;
import com.shop.order.feign.fallback.MerchantFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * 商家服务Feign客户端
 * <p>
 * 通过Feign远程调用商家服务，用于订单确认收货后触发结算。
 * 调用的是商家服务的内部接口（/merchant/inner/**），不需要登录鉴权。
 * </p>
 * <p>
 * 降级策略：结算失败不影响确认收货的主流程（收货已成功），
 * 降级时只记录日志，可通过定时任务补偿结算。
 * </p>
 */
@FeignClient(name = "shop-merchant", path = "/merchant", fallbackFactory = MerchantFeignClientFallbackFactory.class)
public interface MerchantFeignClient {

    /**
     * 订单结算：生成结算记录并增加商家余额
     * <p>
     * 用户确认收货后调用，商家服务会：
     * 1. 生成一条结算流水记录（含平台抽成）
     * 2. 把商家应得金额加到可用余额
     * 幂等设计：同一订单号重复调用不会重复结算。
     * </p>
     *
     * @param merchantId  商家ID
     * @param orderNo     订单号
     * @param orderAmount 订单金额（元）
     * @return 操作结果
     */
    @PostMapping("/inner/settle")
    Result<Void> settleOrder(@RequestParam("merchantId") Long merchantId,
                              @RequestParam("orderNo") String orderNo,
                              @RequestParam("orderAmount") BigDecimal orderAmount);
}
