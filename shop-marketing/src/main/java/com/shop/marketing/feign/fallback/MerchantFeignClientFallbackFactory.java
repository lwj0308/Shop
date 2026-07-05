package com.shop.marketing.feign.fallback;

import com.shop.common.result.Result;
import com.shop.marketing.feign.MerchantFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 商家服务 Feign 降级工厂（shop-marketing 内部使用）
 * <p>
 * 当 shop-merchant 服务不可用时，Feign 会自动走这里的降级逻辑，
 * 避免营销服务因为商家服务挂了而跟着崩溃（防止级联雪崩）。
 * </p>
 * <p>
 * 小白理解：降级就像"打电话没人接，先留个言"，
 * 不是让营销服务直接报错，而是返回一个友好的失败提示，让上层逻辑决定怎么处理。
 * </p>
 */
@Slf4j
@Component
public class MerchantFeignClientFallbackFactory implements FallbackFactory<MerchantFeignClient> {

    /**
     * 创建降级实例
     * <p>
     * 当 Feign 调用 shop-merchant 失败时（超时、连接拒绝、服务宕机等），
     * Sentinel 会触发这个方法，返回一个"降级版"的 MerchantFeignClient。
     * </p>
     *
     * @param cause 失败原因（异常对象，包含堆栈信息，方便排查问题）
     * @return 降级的 MerchantFeignClient 实例
     */
    @Override
    public MerchantFeignClient create(Throwable cause) {
        log.error("商家服务调用失败，触发降级", cause);
        return new MerchantFeignClient() {

            /**
             * 根据 userId 获取 merchantId 的降级方法
             * <p>
             * 商家身份校验是强依赖：如果查不到 merchantId，就不能创建/管理优惠券。
             * 降级策略：返回失败结果，让上层逻辑感知到商家服务不可用，
             * 而不是返回 null 导致权限校验被绕过。
             * </p>
             *
             * @param userId 用户ID（原方法参数，降级方法必须保持一致）
             * @return 失败结果，提示商家服务暂时不可用
             */
            @Override
            public Result<Long> getMerchantIdByUserId(Long userId) {
                log.warn("获取商家ID降级，userId={}，商家服务暂时不可用", userId);
                return Result.fail("商家服务暂时不可用，请稍后重试");
            }
        };
    }
}
