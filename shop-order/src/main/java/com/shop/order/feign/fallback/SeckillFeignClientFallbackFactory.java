package com.shop.order.feign.fallback;

import com.shop.common.result.Result;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.order.feign.SeckillFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 秒杀活动服务Feign降级工厂
 * <p>
 * 当商家服务不可用（比如挂了、超时了）时，Feign会自动走这里的降级逻辑。
 * </p>
 * <p>
 * 降级策略：
 * - 查询类接口（getSeckillById）：降级返回null。
 *   因为查询失败时秒杀活动信息拿不到，无法继续抢购流程，
 *   调用方（SeckillService）拿到null后会校验并提示用户"秒杀活动信息获取失败"。
 * </p>
 */
@Slf4j
@Component
public class SeckillFeignClientFallbackFactory implements FallbackFactory<SeckillFeignClient> {

    /**
     * 创建降级实例
     *
     * @param cause 失败原因
     * @return 降级的SeckillFeignClient实例
     */
    @Override
    public SeckillFeignClient create(Throwable cause) {
        log.error("秒杀活动服务调用失败，触发降级", cause);
        return new SeckillFeignClient() {

            /**
             * 查询秒杀活动信息降级：返回null
             * <p>
             * 查询类接口降级返回null，调用方会校验返回值，
             * 如果为null则提示用户"秒杀活动信息获取失败"。
             * </p>
             */
            @Override
            public Result<SeckillActivity> getSeckillById(Long seckillId) {
                log.warn("查询秒杀活动信息降级: seckillId={}", seckillId);
                return Result.fail("秒杀活动服务暂时不可用，请稍后重试");
            }
        };
    }
}
