-- ============================================
-- Shop 商城项目 - 秒杀服务建表脚本
-- 数据库：shop_seckill
-- 说明：包含秒杀活动表
-- 从 shop-merchant 模块拆分而来，遵循单一职责原则
-- ============================================

USE shop_seckill;

-- ============================================================================
-- 秒杀活动表 seckill_activity
-- 商家或平台创建的限时秒杀活动，指定 SKU 以秒杀价售卖
-- 独立秒杀库存（total_count/available_count），活动创建时预热到 Redis 防超卖
-- merchant_id=0 表示平台活动，>0 表示商家活动
-- 下单流程：Redis Lua 脚本原子扣减库存 → 发 MQ 消息异步创建订单
-- ============================================================================
CREATE TABLE IF NOT EXISTS `seckill_activity` (
    `id` BIGINT NOT NULL COMMENT '秒杀活动ID',
    `merchant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '商家ID（0表示平台活动）',
    `product_id` BIGINT NOT NULL COMMENT '商品ID（SPU）',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID（秒杀到规格级别）',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    `original_price` DECIMAL(10,2) NOT NULL COMMENT '原价（冗余展示用）',
    `total_count` INT NOT NULL COMMENT '秒杀库存总数',
    `available_count` INT NOT NULL COMMENT '剩余库存（DB层，Redis为主）',
    `limit_count` INT NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    `start_time` DATETIME NOT NULL COMMENT '秒杀开始时间',
    `end_time` DATETIME NOT NULL COMMENT '秒杀结束时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待生效 1进行中 2已结束 3已下架',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '活动描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';
