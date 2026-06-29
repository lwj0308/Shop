-- ============================================
-- Shop 商城项目 - 数据库初始化脚本
-- 说明：创建9个微服务的独立数据库，每个服务一个 schema 实现逻辑隔离
-- 执行顺序：MySQL 首次启动时自动按文件名顺序执行
-- ============================================

-- 用户服务数据库
CREATE DATABASE IF NOT EXISTS shop_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 商家服务数据库
CREATE DATABASE IF NOT EXISTS shop_merchant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 商品服务数据库
CREATE DATABASE IF NOT EXISTS shop_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 购物车服务数据库
CREATE DATABASE IF NOT EXISTS shop_cart DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 订单服务数据库
CREATE DATABASE IF NOT EXISTS shop_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 支付服务数据库
CREATE DATABASE IF NOT EXISTS shop_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 管理后台数据库（管理员、角色、权限、日志等）
CREATE DATABASE IF NOT EXISTS shop_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 营销服务数据库（优惠券、满减活动）
CREATE DATABASE IF NOT EXISTS shop_marketing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 秒杀服务数据库（秒杀活动）
CREATE DATABASE IF NOT EXISTS shop_seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Seata 分布式事务数据库（存储 Seata 的事务日志）
CREATE DATABASE IF NOT EXISTS shop_seata DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
