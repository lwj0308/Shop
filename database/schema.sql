-- ============================================================================
-- 项目名称：Shop 商城项目
-- 文件用途：全量建表脚本（事实来源 / Source of Truth）
-- 生成日期：2026-07-06
-- 字符集：utf8mb4 / utf8mb4_unicode_ci
-- 存储引擎：InnoDB
-- 规范摘要：
--   1. 每个微服务一个独立 schema，实现逻辑隔离
--   2. 所有表使用 CREATE TABLE IF NOT EXISTS，幂等可重复执行
--   3. 不使用物理外键（FOREIGN KEY），关联关系由应用层维护
--   4. 字符集统一 utf8mb4，支持 emoji 和特殊字符
--   5. 金额字段统一 DECIMAL，禁止使用 FLOAT/DOUBLE
--   6. 所有表都有 COMMENT 注释，字段都有中文说明
--   7. 本文件只包含表结构，不包含任何测试数据
-- 数据库清单（9个业务库 + 1个 Seata 库）：
--   shop_user / shop_merchant / shop_product / shop_cart / shop_order
--   shop_payment / shop_admin / shop_marketing / shop_seckill / shop_seata
-- ============================================================================

-- 确保连接字符集为 utf8mb4，防止中文乱码
SET NAMES utf8mb4;

-- ============================================================================
-- 一、创建数据库（9个业务库 + 1个 Seata 库）
-- ============================================================================

-- 用户服务数据库
CREATE DATABASE IF NOT EXISTS `shop_user` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 商家服务数据库
CREATE DATABASE IF NOT EXISTS `shop_merchant` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 商品服务数据库
CREATE DATABASE IF NOT EXISTS `shop_product` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 购物车服务数据库
CREATE DATABASE IF NOT EXISTS `shop_cart` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 订单服务数据库
CREATE DATABASE IF NOT EXISTS `shop_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 支付服务数据库
CREATE DATABASE IF NOT EXISTS `shop_payment` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 管理后台数据库（管理员、角色、权限、日志等）
CREATE DATABASE IF NOT EXISTS `shop_admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 营销服务数据库（优惠券、满减活动）
CREATE DATABASE IF NOT EXISTS `shop_marketing` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 秒杀服务数据库（秒杀活动）
CREATE DATABASE IF NOT EXISTS `shop_seckill` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Seata 分布式事务数据库（存储 Seata 的事务日志）
CREATE DATABASE IF NOT EXISTS `shop_seata` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- ============================================================================
-- 二、shop_user 用户服务库
-- 包含：用户基本信息、账户、地址、收藏、足迹、登录日志、消息通知、用户优惠券
-- ============================================================================

USE `shop_user`;

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


-- ============================================================================
-- 三、shop_merchant 商家服务库
-- 包含：商家信息、资质、结算账户、店铺、结算流水、提现申请
-- ============================================================================

USE `shop_merchant`;

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


-- ============================================================================
-- 四、shop_product 商品服务库
-- 包含：分类、品牌、SPU、SKU、规格、图片、评价
-- ============================================================================

USE `shop_product`;

-- 商品分类表（树形结构）
-- 支持多级分类，通过 parent_id 实现树形结构（如：手机→智能手机→5G手机）
CREATE TABLE IF NOT EXISTS `category` (
    `id` BIGINT NOT NULL COMMENT '分类ID',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID（0表示顶级）',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `icon` VARCHAR(255) DEFAULT NULL COMMENT '分类图标',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

-- 品牌表
-- 商品所属品牌，如苹果、华为、小米等
CREATE TABLE IF NOT EXISTS `brand` (
    `id` BIGINT NOT NULL COMMENT '品牌ID',
    `name` VARCHAR(50) NOT NULL COMMENT '品牌名称',
    `logo` VARCHAR(255) DEFAULT NULL COMMENT '品牌Logo',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '品牌描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌';

-- 商品SPU表
-- SPU（Standard Product Unit）是商品的标准单位，如"iPhone 15"
-- 一个 SPU 下可以有多个 SKU（不同颜色、不同存储等）
CREATE TABLE IF NOT EXISTS `product` (
    `id` BIGINT NOT NULL COMMENT '商品ID（SPU）',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `brand_id` BIGINT DEFAULT NULL COMMENT '品牌ID',
    `shop_id` BIGINT NOT NULL COMMENT '店铺ID',
    `name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `subtitle` VARCHAR(200) DEFAULT NULL COMMENT '商品副标题',
    `main_image` VARCHAR(255) DEFAULT NULL COMMENT '主图URL',
    `images` JSON DEFAULT NULL COMMENT '图片列表（JSON数组）',
    `detail` TEXT DEFAULT NULL COMMENT '商品详情（富文本HTML）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0下架 1上架',
    `sales` INT NOT NULL DEFAULT 0 COMMENT '销量（下单累加，用于热销推荐排序）',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量（查看商品详情累加，用于热度统计）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_brand_id` (`brand_id`),
    KEY `idx_shop_id` (`shop_id`),
    KEY `idx_sales` (`sales`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SPU';

-- 规格模板表
-- 定义商品的规格维度，如"颜色"、"尺码"、"存储容量"等
CREATE TABLE IF NOT EXISTS `product_spec` (
    `id` BIGINT NOT NULL COMMENT '规格ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `name` VARCHAR(50) NOT NULL COMMENT '规格名称（如：颜色、尺码）',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格模板';

-- 规格值表
-- 每个规格维度下的具体值，如颜色下的"红色"、"蓝色"等
CREATE TABLE IF NOT EXISTS `product_spec_value` (
    `id` BIGINT NOT NULL COMMENT '规格值ID',
    `spec_id` BIGINT NOT NULL COMMENT '规格ID',
    `value` VARCHAR(50) NOT NULL COMMENT '规格值（如：红色、XL）',
    PRIMARY KEY (`id`),
    KEY `idx_spec_id` (`spec_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格值';

-- 商品SKU表
-- SKU（Stock Keeping Unit）是最小库存单位，如"iPhone 15 红色 128G"
-- 每个 SKU 有独立的价格和库存，扣库存时使用乐观锁防止超卖
CREATE TABLE IF NOT EXISTS `product_sku` (
    `id` BIGINT NOT NULL COMMENT 'SKU ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID（SPU）',
    `spec_values` JSON NOT NULL COMMENT '规格值组合（如：{"颜色":"红色","尺码":"XL"}）',
    `price` DECIMAL(10,2) NOT NULL COMMENT '销售价格',
    `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '原价',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    `image` VARCHAR(255) DEFAULT NULL COMMENT 'SKU图片',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（扣库存时使用）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU';

-- 商品图片表
-- 存储商品的展示图片，按 sort 字段排序
CREATE TABLE IF NOT EXISTS `product_image` (
    `id` BIGINT NOT NULL COMMENT '图片ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `url` VARCHAR(255) NOT NULL COMMENT '图片URL',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品图片';

-- 商品评价表
-- 用户购买商品后可以发表评价，商家可以回复
CREATE TABLE IF NOT EXISTS `product_comment` (
    `id` BIGINT NOT NULL COMMENT '评价ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `content` VARCHAR(500) NOT NULL COMMENT '评价内容',
    `images` JSON DEFAULT NULL COMMENT '评价图片（JSON数组）',
    `score` TINYINT NOT NULL DEFAULT 5 COMMENT '评分：1-5分',
    `reply` VARCHAR(500) DEFAULT NULL COMMENT '商家回复',
    `is_anonymous` TINYINT NOT NULL DEFAULT 0 COMMENT '是否匿名评价：0否 1是',
    `comment_type` TINYINT NOT NULL DEFAULT 0 COMMENT '评价类型：0初始评价 1追评',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评价ID（追评时指向初始评价ID）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价';


-- ============================================================================
-- 五、shop_cart 购物车服务库
-- 包含：购物车项
-- ============================================================================

USE `shop_cart`;

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


-- ============================================================================
-- 六、shop_order 订单服务库
-- 包含：订单主表、明细、地址快照、物流、状态日志、退款单、Seata回滚日志
-- 注意：order_info / order_item 已对照实体类修正字段（详见迁移脚本说明）
-- ============================================================================

USE `shop_order`;

-- 订单主表
-- 记录订单的核心信息，包括金额、状态、各时间节点等
-- 订单号使用雪花算法生成，保证全局唯一
-- 【修正说明】对照 OrderInfo 实体类：
--   1. shop_id → merchant_id（商家ID），索引同步更名
--   2. freight → freight_amount（运费）
--   3. close_time → cancel_time（取消时间）
--   4. 新增 remark（订单备注）、cancel_reason（取消原因）
CREATE TABLE IF NOT EXISTS `order_info` (
    `id` BIGINT NOT NULL COMMENT '订单ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（雪花算法生成）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
    `total_amount` DECIMAL(12,2) NOT NULL COMMENT '订单总金额',
    `pay_amount` DECIMAL(12,2) NOT NULL COMMENT '实付金额',
    `freight_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '运费',
    `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠总金额（满减+优惠券）',
    `promotion_discount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '满减优惠金额',
    `order_type` TINYINT NOT NULL DEFAULT 1 COMMENT '订单类型：1普通订单 2秒杀订单',
    `seckill_id` BIGINT DEFAULT NULL COMMENT '秒杀活动ID（仅秒杀订单有值，普通订单为NULL）',
    `is_reviewed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已评价：0未评价 1已评价',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `delivery_time` DATETIME DEFAULT NULL COMMENT '发货时间',
    `receive_time` DATETIME DEFAULT NULL COMMENT '收货时间',
    `finish_time` DATETIME DEFAULT NULL COMMENT '完成时间（订单变为已完成的时间）',
    `cancel_time` DATETIME DEFAULT NULL COMMENT '取消时间',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '订单备注（用户下单时填写）',
    `cancel_reason` VARCHAR(200) DEFAULT NULL COMMENT '取消原因',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_merchant_id` (`merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

-- 订单明细表
-- 记录订单中每个商品的信息，价格和规格使用快照（下单时的价格，不受后续修改影响）
-- 【修正说明】对照 OrderItem 实体类：
--   1. image → product_image（商品图片快照）
--   2. 在 order_id 之后新增 order_no（订单号冗余，避免查询时 join order_info 表）
--   3. 在 quantity 之后新增 subtotal（小计金额 = 单价 × 数量）
CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT NOT NULL COMMENT '订单明细ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号（冗余，方便查询不用join order_info表）',
    `product_id` BIGINT NOT NULL COMMENT '商品ID（SPU）',
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
    `sku_spec` VARCHAR(200) DEFAULT NULL COMMENT '规格信息（快照）',
    `price` DECIMAL(10,2) NOT NULL COMMENT '商品单价（快照）',
    `quantity` INT NOT NULL COMMENT '购买数量',
    `subtotal` DECIMAL(12,2) NOT NULL COMMENT '小计金额（单价×数量）',
    `product_image` VARCHAR(255) DEFAULT NULL COMMENT '商品图片快照（下单时主图URL）',
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


-- ============================================================================
-- 七、shop_payment 支付服务库
-- 包含：支付记录、支付回调日志、Seata回滚日志
-- ============================================================================

USE `shop_payment`;

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


-- ============================================================================
-- 八、shop_admin 管理后台库
-- 包含：管理员、角色、权限、部门、操作日志、登录日志、安全事件、Banner、公告
-- ============================================================================

USE `shop_admin`;

-- 管理员信息表
-- 管理后台的操作人员，每个管理员可以分配多个角色
CREATE TABLE IF NOT EXISTS `admin_user` (
    `id` BIGINT NOT NULL COMMENT '管理员ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名（登录账号）',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称（显示名称）',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `dept_id` BIGINT DEFAULT NULL COMMENT '所属部门ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `password_change_time` DATETIME DEFAULT NULL COMMENT '密码最后修改时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员信息';

-- 角色表
-- 定义不同的角色，每个角色拥有不同的权限集合
CREATE TABLE IF NOT EXISTS `admin_role` (
    `id` BIGINT NOT NULL COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称（如：运营管理员）',
    `role_key` VARCHAR(50) NOT NULL COMMENT '角色标识（如：operator，用于代码中判断）',
    `data_scope` TINYINT NOT NULL DEFAULT 1 COMMENT '数据权限范围：1全部数据 2本部门 3本部门及下级 4仅本人',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色信息';

-- 权限/菜单表
-- 树形结构，包含目录、菜单、按钮三种类型，控制前端菜单显示和按钮权限
CREATE TABLE IF NOT EXISTS `admin_permission` (
    `id` BIGINT NOT NULL COMMENT '权限ID',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父级ID（0表示顶级）',
    `name` VARCHAR(50) NOT NULL COMMENT '权限名称（如：用户管理）',
    `type` TINYINT NOT NULL COMMENT '类型：1目录 2菜单 3按钮',
    `permission_key` VARCHAR(100) DEFAULT NULL COMMENT '权限标识（如user:list、user:edit）',
    `path` VARCHAR(200) DEFAULT NULL COMMENT '路由路径（前端菜单用）',
    `icon` VARCHAR(50) DEFAULT NULL COMMENT '菜单图标',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号（越小越靠前）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限/菜单';

-- 管理员-角色关联表
-- 一个管理员可以有多个角色，一个角色可以分配给多个管理员（多对多关系）
CREATE TABLE IF NOT EXISTS `admin_user_role` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '管理员ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员-角色关联';

-- 角色-权限关联表
-- 一个角色拥有多个权限，一个权限可以属于多个角色（多对多关系）
CREATE TABLE IF NOT EXISTS `admin_role_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联';

-- 部门表
-- 树形结构，用于数据权限控制（不同角色看到不同范围的数据）
CREATE TABLE IF NOT EXISTS `admin_dept` (
    `id` BIGINT NOT NULL COMMENT '部门ID',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父级部门ID（0表示顶级）',
    `name` VARCHAR(50) NOT NULL COMMENT '部门名称',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `leader` VARCHAR(50) DEFAULT NULL COMMENT '部门负责人',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门信息';

-- 操作日志表
-- 记录管理员的所有增删改操作，用于审计追踪
CREATE TABLE IF NOT EXISTS `admin_operation_log` (
    `id` BIGINT NOT NULL COMMENT '日志ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
    `module` VARCHAR(50) DEFAULT NULL COMMENT '操作模块（如：用户管理）',
    `operation_type` VARCHAR(20) DEFAULT NULL COMMENT '操作类型（新增/修改/删除/查询/导出）',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '操作描述',
    `request_url` VARCHAR(255) DEFAULT NULL COMMENT '请求URL',
    `request_method` VARCHAR(10) DEFAULT NULL COMMENT '请求方法（GET/POST/PUT/DELETE）',
    `request_params` TEXT DEFAULT NULL COMMENT '请求参数（JSON格式）',
    `response_result` TEXT DEFAULT NULL COMMENT '响应结果（JSON格式）',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP地址',
    `location` VARCHAR(100) DEFAULT NULL COMMENT '操作地点',
    `duration` INT DEFAULT NULL COMMENT '执行时长（毫秒）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '操作状态：0失败 1成功',
    `error_msg` TEXT DEFAULT NULL COMMENT '错误信息（失败时记录）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_module` (`module`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- 登录日志表
-- 记录管理员的登录成功/失败信息，用于安全审计
CREATE TABLE IF NOT EXISTS `admin_login_log` (
    `id` BIGINT NOT NULL COMMENT '日志ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '登录用户名',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT '登录IP地址',
    `location` VARCHAR(100) DEFAULT NULL COMMENT '登录地点',
    `browser` VARCHAR(50) DEFAULT NULL COMMENT '浏览器',
    `os` VARCHAR(50) DEFAULT NULL COMMENT '操作系统',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '登录状态：0失败 1成功',
    `fail_reason` VARCHAR(200) DEFAULT NULL COMMENT '失败原因',
    `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- 安全事件表
-- 记录异常安全事件，如频繁登录失败、异常IP访问、越权操作等
CREATE TABLE IF NOT EXISTS `admin_security_event` (
    `id` BIGINT NOT NULL COMMENT '事件ID',
    `event_type` VARCHAR(30) NOT NULL COMMENT '事件类型（频繁登录失败/异常IP/权限越权/敏感操作）',
    `user_id` BIGINT DEFAULT NULL COMMENT '相关用户ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '相关用户名',
    `detail` VARCHAR(500) DEFAULT NULL COMMENT '事件详情',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT '事件IP地址',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态：0未处理 1已处理 2已忽略',
    `handle_note` VARCHAR(200) DEFAULT NULL COMMENT '处理备注',
    `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_event_type` (`event_type`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件';

-- Banner管理表
-- 管理后台首页轮播图，用于展示促销活动、推荐商品等
CREATE TABLE IF NOT EXISTS `admin_banner` (
    `id` BIGINT NOT NULL COMMENT 'Banner ID',
    `title` VARCHAR(100) NOT NULL COMMENT 'Banner标题',
    `image` VARCHAR(255) NOT NULL COMMENT '图片地址',
    `link` VARCHAR(255) DEFAULT NULL COMMENT '跳转链接',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号（越小越靠前）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Banner管理';

-- 公告管理表
-- 系统公告，如通知公告、活动公告、维护公告等
CREATE TABLE IF NOT EXISTS `admin_notice` (
    `id` BIGINT NOT NULL COMMENT '公告ID',
    `title` VARCHAR(200) NOT NULL COMMENT '公告标题',
    `content` TEXT NOT NULL COMMENT '公告内容',
    `type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1通知 2活动 3维护',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告管理';


-- ============================================================================
-- 九、shop_marketing 营销服务库
-- 包含：优惠券模板、满减活动、满减活动商品关联
-- 从 shop-merchant 模块拆分而来，遵循单一职责原则
-- ============================================================================

USE `shop_marketing`;

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

-- 满减活动表 promotion
-- 商家或平台创建的满减活动（满X减Y），下单时自动计算优惠
-- scope_type=1全店（所有商品参与）, scope_type=2指定商品（仅关联的商品参与）
-- merchant_id=0 表示平台活动（管理员创建），>0 表示商家活动
-- 叠加规则：满减和优惠券可叠加，满减先算，优惠券基于满减后金额判断门槛
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

-- 满减活动商品关联表 promotion_product
-- 仅当 promotion.scope_type=2（指定商品）时才有数据
-- 记录哪些商品/SKU参与了指定的满减活动
-- sku_id 为 NULL 表示该商品的所有 SKU 都参与
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
-- 十、shop_seckill 秒杀服务库
-- 包含：秒杀活动
-- 从 shop-merchant 模块拆分而来，遵循单一职责原则
-- ============================================================================

USE `shop_seckill`;

-- 秒杀活动表 seckill_activity
-- 商家或平台创建的限时秒杀活动，指定 SKU 以秒杀价售卖
-- 独立秒杀库存（total_count/available_count），活动创建时预热到 Redis 防超卖
-- merchant_id=0 表示平台活动，>0 表示商家活动
-- 下单流程：Redis Lua 脚本原子扣减库存 → 发 MQ 消息异步创建订单
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


-- ============================================================================
-- 文件结束
-- 共创建 10 个数据库、48 张表
-- ============================================================================
