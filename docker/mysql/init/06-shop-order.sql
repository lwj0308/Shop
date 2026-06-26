-- ============================================
-- Shop 商城项目 - 订单服务建表脚本
-- 数据库：shop_order
-- 说明：包含订单主表、明细、地址快照、物流、状态日志、退款单、Seata回滚日志等表
-- ============================================

USE shop_order;

-- 订单主表
-- 记录订单的核心信息，包括金额、状态、各时间节点等
-- 订单号使用雪花算法生成，保证全局唯一
CREATE TABLE IF NOT EXISTS `order_info` (
    `id` BIGINT NOT NULL COMMENT '订单ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（雪花算法生成）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `shop_id` BIGINT NOT NULL COMMENT '店铺ID',
    `total_amount` DECIMAL(12,2) NOT NULL COMMENT '订单总金额',
    `pay_amount` DECIMAL(12,2) NOT NULL COMMENT '实付金额',
    `freight` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
    `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠总金额（满减+优惠券）',
    `promotion_discount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '满减优惠金额',
    `order_type` TINYINT NOT NULL DEFAULT 1 COMMENT '订单类型：1普通订单 2秒杀订单',
    `seckill_id` BIGINT DEFAULT NULL COMMENT '秒杀活动ID（仅秒杀订单有值，普通订单为NULL）',
    `is_reviewed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已评价：0未评价 1已评价',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `delivery_time` DATETIME DEFAULT NULL COMMENT '发货时间',
    `receive_time` DATETIME DEFAULT NULL COMMENT '收货时间',
    `close_time` DATETIME DEFAULT NULL COMMENT '关闭时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- 订单明细表
-- 记录订单中每个商品的信息，价格和规格使用快照（下单时的价格，不受后续修改影响）
CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT NOT NULL COMMENT '订单明细ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID（SPU）',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
    `sku_spec` VARCHAR(200) DEFAULT NULL COMMENT '规格信息（快照）',
    `price` DECIMAL(10,2) NOT NULL COMMENT '商品单价（快照）',
    `quantity` INT NOT NULL COMMENT '购买数量',
    `image` VARCHAR(255) DEFAULT NULL COMMENT '商品图片（快照）',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

-- 订单地址快照表
-- 下单时复制用户的收货地址，防止用户后续修改地址影响已有订单
CREATE TABLE IF NOT EXISTS `order_address` (
    `id` BIGINT NOT NULL COMMENT '地址快照ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    `phone` VARCHAR(20) NOT NULL COMMENT '收货人手机号',
    `province` VARCHAR(20) NOT NULL COMMENT '省',
    `city` VARCHAR(20) NOT NULL COMMENT '市',
    `district` VARCHAR(20) NOT NULL COMMENT '区',
    `detail` VARCHAR(200) NOT NULL COMMENT '详细地址',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单地址快照';

-- 物流信息表
-- 记录订单的物流单号、物流公司和物流轨迹
CREATE TABLE IF NOT EXISTS `order_logistics` (
    `id` BIGINT NOT NULL COMMENT '物流ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `logistics_no` VARCHAR(50) DEFAULT NULL COMMENT '物流单号',
    `logistics_company` VARCHAR(50) DEFAULT NULL COMMENT '物流公司',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '物流状态：0待发货 1已发货 2运输中 3已签收',
    `detail` JSON DEFAULT NULL COMMENT '物流轨迹详情（JSON数组）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流信息';

-- 订单状态日志表
-- 记录订单每次状态变更，方便排查问题和追踪订单流转
CREATE TABLE IF NOT EXISTS `order_log` (
    `id` BIGINT NOT NULL COMMENT '日志ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `from_status` TINYINT DEFAULT NULL COMMENT '原状态',
    `to_status` TINYINT NOT NULL COMMENT '新状态',
    `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人',
    `note` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态日志';

-- 退款单表
-- 用户申请退款时创建退款单，商家审核通过后执行退款
CREATE TABLE IF NOT EXISTS `refund_order` (
    `id` BIGINT NOT NULL COMMENT '退款单ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
    `reason` VARCHAR(200) NOT NULL COMMENT '退款原因',
    `amount` DECIMAL(12,2) NOT NULL COMMENT '退款金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '退款状态：0待审核 1已同意 2已拒绝 3已退款',
    `audit_note` VARCHAR(200) DEFAULT NULL COMMENT '审核备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单';

-- Seata分布式事务回滚日志表（每个参与分布式事务的库都需要）
-- Seata AT 模式使用此表记录数据修改前后的快照，用于回滚
CREATE TABLE IF NOT EXISTS `undo_log` (
    `branch_id` BIGINT NOT NULL COMMENT '分支事务ID',
    `xid` VARCHAR(100) NOT NULL COMMENT '全局事务ID',
    `context` VARCHAR(128) NOT NULL COMMENT '上下文',
    `rollback_info` LONGBLOB NOT NULL COMMENT '回滚信息',
    `log_status` INT NOT NULL COMMENT '日志状态',
    `log_created` DATETIME NOT NULL COMMENT '创建时间',
    `log_modified` DATETIME NOT NULL COMMENT '修改时间',
    PRIMARY KEY (`branch_id`),
    KEY `idx_xid` (`xid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata分布式事务回滚日志';
