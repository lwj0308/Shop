-- ============================================
-- Shop 商城项目 - 用户服务建表脚本
-- 数据库：shop_user
-- 说明：包含用户基本信息、账户、地址、收藏、足迹、登录日志等表
-- ============================================

USE shop_user;

-- 用户基本信息表
-- 存储用户的核心信息，手机号和密码都做了加密处理
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL COMMENT '用户ID',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号（AES加密存储）',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基本信息';

-- 用户账户表（余额、积分）
-- 用户的钱包信息，使用乐观锁防止并发修改导致数据不一致
CREATE TABLE IF NOT EXISTS `user_account` (
    `id` BIGINT NOT NULL COMMENT '账户ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '余额',
    `points` INT NOT NULL DEFAULT 0 COMMENT '积分',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账户';

-- 收货地址表
-- 用户可以添加多个收货地址，其中一个设为默认
CREATE TABLE IF NOT EXISTS `user_address` (
    `id` BIGINT NOT NULL COMMENT '地址ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    `phone` VARCHAR(20) NOT NULL COMMENT '收货人手机号',
    `province` VARCHAR(20) NOT NULL COMMENT '省',
    `city` VARCHAR(20) NOT NULL COMMENT '市',
    `district` VARCHAR(20) NOT NULL COMMENT '区',
    `detail` VARCHAR(200) NOT NULL COMMENT '详细地址',
    `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认：0否 1是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址';

-- 收藏表
-- 用户收藏的商品，同一商品不能重复收藏
CREATE TABLE IF NOT EXISTS `user_favorite` (
    `id` BIGINT NOT NULL COMMENT '收藏ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏';

-- 浏览足迹表
-- 记录用户浏览过的商品，用于推荐和"猜你喜欢"功能
CREATE TABLE IF NOT EXISTS `user_footprint` (
    `id` BIGINT NOT NULL COMMENT '足迹ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `category_id` BIGINT DEFAULT NULL COMMENT '商品分类ID（冗余字段，记录浏览时的商品分类，用于猜你喜欢推荐）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览足迹';

-- 登录日志表
-- 记录用户每次登录的IP和设备信息，用于安全审计
CREATE TABLE IF NOT EXISTS `user_login_log` (
    `id` BIGINT NOT NULL COMMENT '日志ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `login_ip` VARCHAR(50) DEFAULT NULL COMMENT '登录IP',
    `login_device` VARCHAR(100) DEFAULT NULL COMMENT '登录设备',
    `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    `login_status` TINYINT NOT NULL DEFAULT 1 COMMENT '登录状态：0失败 1成功',
    `fail_reason` VARCHAR(200) DEFAULT NULL COMMENT '失败原因',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- 消息通知表
-- 统一存储用户/商家/管理员三类角色的站内通知，用 receiver_type 区分接收人
-- 例如：订单发货通知用户、商家入驻审核结果通知商家、提现审核结果通知商家等
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT NOT NULL COMMENT '通知ID',
    `receiver_type` TINYINT NOT NULL COMMENT '接收人类型：1用户 2商家 3管理员',
    `receiver_id` BIGINT NOT NULL COMMENT '接收人ID（用户ID/商家ID/管理员ID）',
    `type` TINYINT NOT NULL COMMENT '通知类型：1订单 2支付 3退款 4商家审核 5提现 6系统',
    `title` VARCHAR(100) NOT NULL COMMENT '通知标题',
    `content` VARCHAR(500) NOT NULL COMMENT '通知内容',
    `biz_type` VARCHAR(50) DEFAULT NULL COMMENT '关联业务类型（如order/withdraw/merchant）',
    `biz_id` VARCHAR(64) DEFAULT NULL COMMENT '关联业务ID（如订单号、提现单号）',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读：0未读 1已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_receiver` (`receiver_type`, `receiver_id`, `is_read`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知';

-- 用户优惠券表
-- 用户领取的优惠券记录，包含优惠券模板的冗余信息（名称/面额/门槛等），避免查询"我的优惠券"时跨服务调用
-- status=0未使用 1已使用 2已过期
-- 领取流程：用户调用 /user/coupon/receive → shop-user 通过 Feign 调用 shop-merchant 查询模板 → 校验后写入本表
-- 核销流程：shop-order 下单时通过 Feign 调用 shop-user 内部接口核销（标记已使用、记录订单号）
CREATE TABLE IF NOT EXISTS `user_coupon` (
    `id` BIGINT NOT NULL COMMENT '用户券ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `coupon_id` BIGINT NOT NULL COMMENT '优惠券模板ID',
    `merchant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '商家ID（冗余，0表示平台券）',
    `coupon_name` VARCHAR(100) NOT NULL COMMENT '优惠券名称（冗余）',
    `coupon_type` TINYINT NOT NULL COMMENT '优惠券类型（冗余）：1满减 2折扣 3立减',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '面额（冗余）',
    `threshold` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '使用门槛（冗余）',
    `valid_start_time` DATETIME NOT NULL COMMENT '有效期开始（冗余）',
    `valid_end_time` DATETIME NOT NULL COMMENT '有效期结束（冗余）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0未使用 1已使用 2已过期',
    `order_no` VARCHAR(32) DEFAULT NULL COMMENT '使用的订单号（核销时写入）',
    `get_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    `use_time` DATETIME DEFAULT NULL COMMENT '使用时间（核销时写入）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`, `status`),
    KEY `idx_coupon_id` (`coupon_id`),
    KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';
