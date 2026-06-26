-- ============================================
-- Shop 商城项目 - 购物车服务建表脚本
-- 数据库：shop_cart
-- 说明：包含购物车项表
-- ============================================

USE shop_cart;

-- 购物车项表
-- 记录用户加入购物车的商品，同一 SKU 不能重复添加（通过唯一索引保证）
CREATE TABLE IF NOT EXISTS `cart_item` (
    `id` BIGINT NOT NULL COMMENT '购物车项ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID（SPU）',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
    `checked` TINYINT NOT NULL DEFAULT 1 COMMENT '是否勾选：0否 1是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车项';
