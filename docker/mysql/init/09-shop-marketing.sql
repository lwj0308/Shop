-- ============================================
-- Shop 商城项目 - 营销服务建表脚本
-- 数据库：shop_marketing
-- 说明：包含优惠券模板、满减活动、满减活动商品关联等表
-- 从 shop-merchant 模块拆分而来，遵循单一职责原则
-- ============================================

USE shop_marketing;

-- 优惠券模板表
-- 商家或平台创建的优惠券模板，定义优惠券的规则和发放量
-- type=1满减（满threshold元减amount元）, type=2折扣（打amount折，如0.85表示85折）, type=3立减（无门槛减amount元）
-- merchant_id=0 表示平台券（管理员创建），>0 表示商家券（对应商家创建）
-- 领取时间窗口：receive_start_time ~ receive_end_time
-- 使用时间窗口：valid_start_time ~ valid_end_time
CREATE TABLE IF NOT EXISTS `coupon` (
    `id` BIGINT NOT NULL COMMENT '优惠券ID',
    `merchant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '商家ID（0表示平台券）',
    `name` VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    `type` TINYINT NOT NULL COMMENT '类型：1满减 2折扣 3立减',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '面额（满减/立减为金额，折扣为折扣率如0.85）',
    `threshold` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛金额（满减用，满多少元可用）',
    `total_count` INT NOT NULL DEFAULT 0 COMMENT '发放总量（0表示不限量）',
    `received_count` INT NOT NULL DEFAULT 0 COMMENT '已领取数量',
    `used_count` INT NOT NULL DEFAULT 0 COMMENT '已使用数量',
    `per_limit` INT NOT NULL DEFAULT 1 COMMENT '每人限领数量',
    `receive_start_time` DATETIME NOT NULL COMMENT '领取开始时间',
    `receive_end_time` DATETIME NOT NULL COMMENT '领取结束时间',
    `valid_start_time` DATETIME NOT NULL COMMENT '有效期开始时间',
    `valid_end_time` DATETIME NOT NULL COMMENT '有效期结束时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待生效 1进行中 2已结束 3已下架',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '描述说明',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

-- ============================================================================
-- 满减活动表 promotion
-- 商家或平台创建的满减活动（满X减Y），下单时自动计算优惠
-- scope_type=1全店（所有商品参与）, scope_type=2指定商品（仅关联的商品参与）
-- merchant_id=0 表示平台活动（管理员创建），>0 表示商家活动
-- 叠加规则：满减和优惠券可叠加，满减先算，优惠券基于满减后金额判断门槛
-- ============================================================================
CREATE TABLE IF NOT EXISTS `promotion` (
    `id` BIGINT NOT NULL COMMENT '满减活动ID',
    `merchant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '商家ID（0表示平台活动）',
    `name` VARCHAR(100) NOT NULL COMMENT '活动名称',
    `threshold` DECIMAL(10,2) NOT NULL COMMENT '满减门槛金额（满多少元）',
    `discount_amount` DECIMAL(10,2) NOT NULL COMMENT '优惠金额（减多少元）',
    `scope_type` TINYINT NOT NULL DEFAULT 1 COMMENT '参与范围：1全店 2指定商品',
    `start_time` DATETIME NOT NULL COMMENT '活动开始时间',
    `end_time` DATETIME NOT NULL COMMENT '活动结束时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待生效 1进行中 2已结束 3已下架',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '活动描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满减活动';

-- ============================================================================
-- 满减活动商品关联表 promotion_product
-- 仅当 promotion.scope_type=2（指定商品）时才有数据
-- 记录哪些商品/SKU参与了指定的满减活动
-- sku_id 为 NULL 表示该商品的所有 SKU 都参与
-- ============================================================================
CREATE TABLE IF NOT EXISTS `promotion_product` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `promotion_id` BIGINT NOT NULL COMMENT '满减活动ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `sku_id` BIGINT DEFAULT NULL COMMENT 'SKU ID（NULL表示该商品所有SKU参与）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_promotion_id` (`promotion_id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='满减活动商品关联';
