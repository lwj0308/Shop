package com.shop.order.feign;

import com.shop.common.result.Result;
import com.shop.model.seckill.entity.SeckillActivity;
import com.shop.order.feign.fallback.SeckillFeignClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 秒杀活动Feign客户端
 * <p>
 * 通过Feign远程调用秒杀服务（shop-seckill），查询秒杀活动信息。
 * 调用的是秒杀服务的内部接口（/seckill/inner/**），不需要登录鉴权。
 * </p>
 * <p>
 * 小白讲解：秒杀活动是在 shop-seckill 服务里创建的，
 * 订单服务要下单时，需要远程调用秒杀服务拿到秒杀价、限购数量、时间窗口等信息。
 * </p>
 */
@FeignClient(name = "shop-seckill", fallbackFactory = SeckillFeignClientFallbackFactory.class)
public interface SeckillFeignClient {

    /**
     * 查询秒杀活动信息（内部接口，不鉴权）
     * <p>
     * 下单时通过Feign调用秒杀服务的内部接口，拿到秒杀活动的完整信息，
     * 包括秒杀价、限购数量、开始/结束时间、SKU ID等。
     * </p>
     *
     * @param seckillId 秒杀活动ID
     * @return 秒杀活动实体（包含秒杀价、限购、时间窗口等）
     */
    @GetMapping("/seckill/inner/{seckillId}")
    Result<SeckillActivity> getSeckillById(@PathVariable("seckillId") Long seckillId);
}
