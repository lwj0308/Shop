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
-- 说明：优惠券、满减活动、秒杀活动已迁移到独立模块
-- 优惠券/满减活动建表脚本：09-shop-marketing.sql（shop_marketing 库）
-- 秒杀活动建表脚本：10-shop-seckill.sql（shop_seckill 库）
-- ============================================
