-- ============================================================
-- 性能测试数据准备脚本
-- <p>
-- 用途：为 N-P-01~08 性能测试准备必要的测试数据。
-- 执行方式：mysql -uroot -p < perf-test-data.sql
-- 或在 MySQL 客户端中直接执行。
-- </p>
-- <p>
-- 包含内容：
-- 1. 热点商品（ID=10086，用于 N-P-02 热点参数限流验证）
-- 2. 大库存秒杀活动（ID=9999，库存 10000，用于 N-P-03 秒杀压测）
-- 3. 批量测试用户（ID=2001~3000，共 1000 个，用于高并发秒杀场景）
-- 4. 测试用户收货地址（每个用户 1 个默认地址，用于 N-P-05 下单测试）
-- 5. 测试用 SKU（用于下单测试）
-- </p>
-- <p>
-- 注意事项：
-- - 执行前请确保所有微服务已启动，MySQL/Redis/Nacos 已就绪
-- - 秒杀活动创建后需要调用预热接口，或直接用 redis-cli 写入库存
-- - 测试用户密码统一为 123456（BCrypt 哈希通过 Python bcrypt.hashpw 生成）
-- - 本脚本可重复执行（使用 INSERT IGNORE 或 ON DUPLICATE KEY UPDATE）
-- </p>
-- ============================================================

-- ============================================================
-- 1. 热点商品（ID=10086）
-- <p>
-- Sentinel 热点参数限流配置（shop-product-param-rules.json）中
-- 对 product:detail 资源的参数索引 0（商品ID）配置了：
-- - 默认 QPS 阈值 500
-- - 参数值 10086 的特殊阈值 2000
-- 因此必须在数据库中创建 ID=10086 的商品，否则接口返回 404 无法压测。
-- </p>
-- ============================================================
USE shop_product;

-- 热点商品 SPU（ID=10086，状态=1 上架）
INSERT IGNORE INTO `product` (
  `id`, `category_id`, `brand_id`, `shop_id`, `name`, `subtitle`, `main_image`,
  `images`, `detail`, `status`, `sales`, `view_count`, `create_time`, `update_time`, `deleted`
) VALUES (
  10086, 1, 1, 3001, '【热点测试商品】旗舰智能手机 Pro Max',
  '性能测试专用热点商品，用于验证 Sentinel 热点参数限流和 Caffeine 本地缓存降级',
  'https://dummyimage.com/600x600/333/fff&text=HotProduct',
  '["https://dummyimage.com/600x600/333/fff&text=HotProduct"]',
  '<p>性能测试专用热点商品详情</p>', 1, 99999, 888888,
  NOW(), NOW(), 0
);

-- 热点商品的 SKU（ID=100861，库存设大，避免下单时库存不足）
INSERT IGNORE INTO `product_sku` (
  `id`, `product_id`, `spec_values`, `price`, `original_price`, `stock`,
  `image`, `version`, `status`, `create_time`, `update_time`, `deleted`
) VALUES (
  100861, 10086, '{"版本":"标准版"}', 499900, 599900, 999999,
  'https://dummyimage.com/600x600/333/fff&text=HotProduct',
  0, 1, NOW(), NOW(), 0
);

-- ============================================================
-- 2. 大库存秒杀活动（ID=9999）
-- <p>
-- N-P-03 秒杀抢购目标 QPS 2000，持续 1 分钟，预计 120000 次请求。
-- 设置库存 100000 避免压测过程中库存耗尽影响结果。
-- 秒杀活动状态=1（进行中），时间窗口覆盖当前。
-- </p>
-- <p>
-- 注意：SQL 插入后还需要在 Redis 中预热库存，key 为 seckill:stock:9999
-- 预热方式见本文件末尾的说明，或调用 POST /api/seckill/admin/create 接口创建。
-- </p>
-- ============================================================
USE shop_seckill;

INSERT IGNORE INTO `seckill_activity` (
  `id`, `merchant_id`, `product_id`, `sku_id`, `seckill_price`, `original_price`,
  `total_count`, `available_count`, `limit_count`, `start_time`, `end_time`,
  `status`, `description`, `create_time`, `update_time`, `deleted`
) VALUES (
  9999, 2001, 10086, 100861, 399900, 499900,
  100000, 100000, 1,
  DATE_SUB(NOW(), INTERVAL 1 DAY),    -- 开始时间：1 天前
  DATE_ADD(NOW(), INTERVAL 7 DAY),    -- 结束时间：7 天后
  1,                                  -- 状态=1 进行中
  '【性能测试】大库存秒杀活动，用于 N-P-03 秒杀压测',
  NOW(), NOW(), 0
);

-- ============================================================
-- 3. 批量测试用户（ID=2001~3000，共 1000 个）
-- <p>
-- N-P-03 秒杀抢购需要 1000 并发用户。
-- 秒杀接口有 user-qps=3 限制（每用户每秒最多 3 次），
-- 要达到 2000 QPS 至少需要 667 个不同用户，这里准备 1000 个确保够用。
-- 密码统一为 123456（BCrypt 哈希与现有测试用户 1001 一致）。
-- </p>
-- <p>
-- 手机号规则：13900002001 ~ 13900003000
-- 昵称规则：perf_test_0001 ~ perf_test_1000
-- </p>
-- ============================================================
USE shop_user;

-- 使用存储过程批量插入用户（避免 1000 行 INSERT 太长）
DELIMITER $$
DROP PROCEDURE IF EXISTS batch_insert_perf_users$$
CREATE PROCEDURE batch_insert_perf_users()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 1000 DO
    INSERT IGNORE INTO `user` (
      `id`, `phone`, `password`, `nickname`, `avatar`, `status`,
      `create_time`, `update_time`, `deleted`
    ) VALUES (
      2000 + i,                              -- ID: 2001 ~ 3000
      CONCAT('1390000', LPAD(2000 + i, 4, '0')),  -- 手机号: 13900002001 ~ 13900003000
      '$2b$10$04BJm9vFIgq/w4YMPTvVtefEMfGw2.5QwryDhTE5aUN3E0w1PFICG',  -- 密码: 123456 (Python bcrypt.hashpw 生成)
      CONCAT('perf_test_', LPAD(i, 4, '0')),  -- 昵称: perf_test_0001 ~ perf_test_1000
      '',                                    -- 头像留空
      1,                                     -- 状态=1 正常
      NOW(), NOW(), 0
    );
    SET i = i + 1;
  END WHILE;
END$$
DELIMITER ;

CALL batch_insert_perf_users();
DROP PROCEDURE IF EXISTS batch_insert_perf_users;

-- ============================================================
-- 4. 测试用户收货地址（每个用户 1 个默认地址）
-- <p>
-- N-P-05 创建订单需要 addressId，为每个测试用户准备一个默认地址。
-- 地址 ID 范围：90001 ~ 91000（对应用户 2001~3000）
-- </p>
-- ============================================================
DELIMITER $$
DROP PROCEDURE IF EXISTS batch_insert_perf_addresses$$
CREATE PROCEDURE batch_insert_perf_addresses()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 1000 DO
    INSERT IGNORE INTO `user_address` (
      `id`, `user_id`, `name`, `phone`, `province`, `city`, `district`,
      `detail`, `is_default`, `create_time`, `update_time`, `deleted`
    ) VALUES (
      90000 + i,                             -- 地址 ID: 90001 ~ 91000
      2000 + i,                              -- 用户 ID: 2001 ~ 3000
      '测试用户',                             -- 收货人姓名
      CONCAT('1390000', LPAD(2000 + i, 4, '0')),  -- 手机号
      '北京市', '北京市', '朝阳区',
      '建国路88号性能测试大楼', 1,             -- is_default=1
      NOW(), NOW(), 0
    );
    SET i = i + 1;
  END WHILE;
END$$
DELIMITER ;

CALL batch_insert_perf_addresses();
DROP PROCEDURE IF EXISTS batch_insert_perf_addresses;

-- ============================================================
-- 5. 用于下单测试的普通商品和 SKU（如果 10086 已用作热点，再用 4002 做下单测试）
-- <p>
-- N-P-05 创建订单需要 SKU ID，使用现有测试数据中的 SKU 即可。
-- 现有 SKU 50001（iPhone 15 Pro Max）库存充足，适合下单压测。
-- 这里不需要额外创建，只是说明一下。
-- </p>
-- ============================================================

-- ============================================================
-- 6. Redis 秒杀库存预热说明
-- <p>
-- 秒杀活动创建后，必须把库存写入 Redis，key 格式：seckill:stock:{活动ID}
-- 否则秒杀抢购时会因 Redis 库存不存在而失败。
-- </p>
-- <p>
-- 预热方式二选一：
-- 方式1（推荐）：调用接口创建秒杀活动（会自动预热）
--   POST /api/seckill/admin/create
--   Body: {"productId":10086,"skuId":100861,"seckillPrice":3999,"originalPrice":4999,"totalCount":100000,"limitCount":1,"startTime":"...","endTime":"..."}
--   注意：需要商家登录态
-- </p>
-- <p>
-- 方式2（直接写 Redis）：
--   redis-cli SET seckill:stock:9999 100000
--   redis-cli SET seckill:limit:9999:2001 0  (初始化用户购买记录，可选)
-- </p>
-- <p>
-- 本 SQL 脚本执行后，请在终端运行以下命令预热 Redis：
--   docker exec -it shop-redis redis-cli SET seckill:stock:9999 100000
-- 或如果 Redis 在本机：
--   redis-cli SET seckill:stock:9999 100000
-- </p>
-- ============================================================

-- ============================================================
-- 执行完成提示
-- ============================================================
SELECT '性能测试数据准备完成！' AS message;
SELECT '热点商品 ID=10086 已创建' AS item;
SELECT '秒杀活动 ID=9999 已创建（请记得在 Redis 中预热库存）' AS item;
SELECT '测试用户 ID=2001~3000 已创建（密码: 123456）' AS item;
SELECT '收货地址 ID=90001~91000 已创建' AS item;
