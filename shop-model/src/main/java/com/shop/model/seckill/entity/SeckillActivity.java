package com.shop.model.seckill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shop.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体
 * <p>
 * 对应数据库 seckill_activity 表，存储商家或平台创建的秒杀活动。
 * 秒杀活动指定一个 SKU 以秒杀价售卖，有独立的秒杀库存。
 * </p>
 * <p>
 * 活动创建时将 available_count 预热到 Redis（seckill:stock:{id}），
 * 用户抢购时通过 Lua 脚本原子扣减 Redis 库存，防止超卖。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_activity")
public class SeckillActivity extends BaseEntity {

    /** 商家ID（0表示平台活动） */
    private Long merchantId;

    /** 商品ID（SPU） */
    private Long productId;

    /** SKU ID（秒杀到规格级别） */
    private Long skuId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 原价（冗余展示用） */
    private BigDecimal originalPrice;

    /** 秒杀库存总数 */
    private Integer totalCount;

    /** 剩余库存（DB层，Redis为主） */
    private Integer availableCount;

    /** 每人限购数量 */
    private Integer limitCount;

    /** 秒杀开始时间 */
    private LocalDateTime startTime;

    /** 秒杀结束时间 */
    private LocalDateTime endTime;

    /** 状态：0待生效 1进行中 2已结束 3已下架 */
    private Integer status;

    /** 活动描述 */
    private String description;
}
