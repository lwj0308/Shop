-- ============================================
-- Shop 商城项目 - 商家服务建表脚本
-- 数据库：shop_merchant
-- 说明：包含商家信息、资质、结算账户、店铺等表
-- ============================================

USE shop_merchant;

-- 商家信息表
-- 商家的基本信息，入驻后需要审核才能正常经营
CREATE TABLE IF NOT EXISTS `merchant` (
    `id` BIGINT NOT NULL COMMENT '商家ID',
    `name` VARCHAR(100) NOT NULL COMMENT '商家名称',
    `logo` VARCHAR(255) DEFAULT NULL COMMENT '商家Logo',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '商家描述',
    `contact_phone` VARCHAR(20) NOT NULL COMMENT '联系电话（AES加密）',
    `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
    `password` VARCHAR(100) NOT NULL COMMENT '商家密码（BCrypt加密）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待审核 1已通过 2已拒绝 3已禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家信息';

-- 商家资质表
-- 商家入驻时需要提交营业执照等资质，审核通过后才能经营
CREATE TABLE IF NOT EXISTS `merchant_qualification` (
    `id` BIGINT NOT NULL COMMENT '资质ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `license_no` VARCHAR(50) NOT NULL COMMENT '营业执照号',
    `license_img` VARCHAR(255) NOT NULL COMMENT '营业执照图片',
    `legal_person` VARCHAR(50) NOT NULL COMMENT '法人姓名',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核 1已通过 2已拒绝',
    `audit_note` VARCHAR(200) DEFAULT NULL COMMENT '审核备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家资质';

-- 商家结算账户表
-- 商家的银行账户信息，用于订单结算时打款
-- balance 可用余额（可提现），frozen_amount 冻结金额（提现申请中）
CREATE TABLE IF NOT EXISTS `merchant_settlement` (
    `id` BIGINT NOT NULL COMMENT '结算账户ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `bank_name` VARCHAR(50) NOT NULL COMMENT '银行名称',
    `bank_account` VARCHAR(50) NOT NULL COMMENT '银行账号',
    `account_name` VARCHAR(50) NOT NULL COMMENT '账户名',
    `balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额（可提现金额）',
    `frozen_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额（提现申请中的金额）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家结算账户';

-- 店铺信息表
-- 一个商家可以拥有多个店铺，每个店铺独立运营
CREATE TABLE IF NOT EXISTS `shop` (
    `id` BIGINT NOT NULL COMMENT '店铺ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `name` VARCHAR(100) NOT NULL COMMENT '店铺名称',
    `logo` VARCHAR(255) DEFAULT NULL COMMENT '店铺Logo',
    `banner` VARCHAR(255) DEFAULT NULL COMMENT '店铺Banner',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '店铺描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0关闭 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺信息';

-- ============================================
-- 结算相关表（商家结算管理功能）
-- ============================================

-- 结算流水表
-- 记录每笔订单的结算信息：订单完成时生成，记录商家应得金额
-- settlement_amount = order_amount - commission_amount（商家应得 = 订单金额 - 平台抽成）
CREATE TABLE IF NOT EXISTS `settlement_record` (
    `id` BIGINT NOT NULL COMMENT '结算流水ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `order_amount` DECIMAL(12,2) NOT NULL COMMENT '订单金额（元）',
    `commission_rate` DECIMAL(5,4) NOT NULL DEFAULT 0.0000 COMMENT '平台抽成比例（如0.05表示5%）',
    `commission_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '平台抽成金额（元）',
    `settlement_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '商家应得金额（元）= 订单金额 - 平台抽成',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '结算状态：0-待结算 1-已结算 2-已退款',
    `settle_time` DATETIME DEFAULT NULL COMMENT '结算时间（订单完成时写入）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算流水（记录每笔订单的结算信息）';

-- 提现申请表
-- 商家申请提现的记录，需要管理员审核
-- 流程：商家申请（balance→frozen_amount冻结）→ 管理员审核通过（frozen_amount扣减）/ 拒绝（frozen_amount→balance解冻）
CREATE TABLE IF NOT EXISTS `withdraw_order` (
    `id` BIGINT NOT NULL COMMENT '提现申请ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `amount` DECIMAL(12,2) NOT NULL COMMENT '提现金额（元）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待审核 1-已通过 2-已拒绝 3-已打款',
    `bank_name` VARCHAR(50) NOT NULL COMMENT '银行名称（申请时快照）',
    `bank_account` VARCHAR(50) NOT NULL COMMENT '银行账号（申请时快照）',
    `account_name` VARCHAR(50) NOT NULL COMMENT '账户名（申请时快照）',
    `audit_remark` VARCHAR(200) DEFAULT NULL COMMENT '审核备注',
    `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_merchant_id` (`merchant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现申请（商家申请提现的记录）';

-- ============================================
-- 优惠券相关表
-- ============================================

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
