package com.shop.order.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 秒杀Redis库存工具
 * <p>
 * 秒杀库存放Redis（key：seckill:stock:{活动ID}），抢购时由Lua脚本原子扣减。
 * 抢购成功但后续下单链路失败时，需要把扣掉的库存加回去，否则库存会越扣越少。
 * 这个回退动作在秒杀下单服务和秒杀订单消费者里都要用，故收敛到此处。
 * </p>
 */
@Slf4j
public final class SeckillStockHelper {

    /** 秒杀库存的Redis Key前缀 */
    public static final String STOCK_KEY_PREFIX = "seckill:stock:";

    private SeckillStockHelper() {
    }

    /**
     * 回退Redis秒杀库存
     * <p>
     * 订单创建失败时，把抢购时扣减的Redis秒杀库存加回去，这样库存数量才能保持正确。
     * </p>
     *
     * @param stringRedisTemplate Redis模板
     * @param seckillId           秒杀活动ID
     */
    public static void rollbackRedisStock(StringRedisTemplate stringRedisTemplate, Long seckillId) {
        try {
            String stockKey = STOCK_KEY_PREFIX + seckillId;
            stringRedisTemplate.opsForValue().increment(stockKey);
            log.info("秒杀Redis库存回退成功: seckillId={}", seckillId);
        } catch (Exception e) {
            // 回退失败只能记录日志，后续可通过定时任务补偿
            log.error("秒杀Redis库存回退失败，需要人工处理: seckillId={}", seckillId, e);
        }
    }
}
