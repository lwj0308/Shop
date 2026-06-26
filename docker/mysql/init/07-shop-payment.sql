-- ============================================
-- Shop 商城项目 - 支付服务建表脚本
-- 数据库：shop_payment
-- 说明：包含支付记录、支付回调日志、Seata回滚日志等表
-- ============================================

USE shop_payment;

-- 支付记录表
-- 记录每笔支付的详细信息，包括支付方式、金额、状态等
CREATE TABLE IF NOT EXISTS `payment_info` (
    `id` BIGINT NOT NULL COMMENT '支付ID',
    `payment_no` VARCHAR(32) NOT NULL COMMENT '支付单号',
    `order_no` VARCHAR(32) NOT NULL COMMENT '关联订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `amount` DECIMAL(12,2) NOT NULL COMMENT '支付金额',
    `pay_type` TINYINT NOT NULL COMMENT '支付方式：1模拟支付 2微信 3支付宝',
    `pay_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态：0待支付 1已支付 2已关闭 3已退款',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `callback_time` DATETIME DEFAULT NULL COMMENT '回调时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录';

-- 支付回调日志表
-- 记录第三方支付平台的回调数据，用于对账和排查问题
-- out_trade_no 作为幂等唯一索引，防止重复处理同一笔回调
CREATE TABLE IF NOT EXISTS `payment_callback` (
    `id` BIGINT NOT NULL COMMENT '回调ID',
    `payment_id` BIGINT NOT NULL COMMENT '支付ID',
    `channel` VARCHAR(20) NOT NULL COMMENT '回调渠道',
    `callback_data` TEXT NOT NULL COMMENT '回调数据（JSON）',
    `out_trade_no` VARCHAR(64) NOT NULL COMMENT '第三方交易号（幂等唯一索引）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
    KEY `idx_payment_id` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志';

-- Seata分布式事务回滚日志表
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
