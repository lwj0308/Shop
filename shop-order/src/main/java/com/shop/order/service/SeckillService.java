package com.shop.order.service;

import com.shop.common.result.Result;

/**
 * 秒杀服务接口
 * <p>
 * 定义秒杀抢购相关的业务方法。
 * 秒杀是高并发场景，核心是：Redis原子扣减库存 + MQ异步下单。
 * </p>
 */
public interface SeckillService {

    /**
     * 执行秒杀抢购
     * <p>
     * 核心流程：
     * 1. 通过Feign查询秒杀活动信息（校验活动是否存在、状态是否进行中、是否在时间窗口内）
     * 2. 执行Lua脚本原子扣减Redis秒杀库存（防止超卖、防止超过限购）
     * 3. Lua脚本返回成功后，发送MQ消息异步创建秒杀订单
     * 4. 返回"抢购成功，正在创建订单"提示
     * </p>
     * <p>
     * 小白讲解：秒杀和普通下单不一样，秒杀是先在Redis里"抢"一个名额，
     * 抢到了再异步去创建订单，这样能扛住高并发，不会把数据库压垮。
     * </p>
     *
     * @param userId     用户ID
     * @param seckillId  秒杀活动ID
     * @return 抢购结果提示信息
     */
    Result<String> executeSeckill(Long userId, Long seckillId);
}
