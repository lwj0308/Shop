-- ============================================
-- Shop 商城项目 - 商品服务建表脚本
-- 数据库：shop_product
-- 说明：包含分类、品牌、SPU、SKU、规格、图片、评价等表
-- ============================================

USE shop_product;

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
