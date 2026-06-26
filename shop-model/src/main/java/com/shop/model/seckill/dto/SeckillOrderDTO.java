package com.shop.model.seckill.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 秒杀订单 MQ 消息体 DTO
 * <p>
 * 用户抢购成功（Redis 库存扣减成功）后，shop-order 发送 MQ 消息，
 * 消息体就是这个 DTO，包含秒杀活动ID、用户ID。
 * </p>
 * <p>
 * MQ 消费者收到消息后，根据 seckillId 查询活动信息（含 skuId、秒杀价），
 * 然后异步创建秒杀订单。
 * </p>
 */
@Data
public class SeckillOrderDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 秒杀活动ID */
    private Long seckillId;

    /** 用户ID（抢购成功的用户） */
    private Long userId;
}
