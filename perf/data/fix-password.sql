-- 批量更新所有测试用户（ID 1001~3000）的密码哈希为 123456 的正确 BCrypt 哈希
-- 原 SQL 中的哈希 $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH 不正确
-- 新哈希 $2b$10$04BJm9vFIgq/w4YMPTvVtefEMfGw2.5QwryDhTE5aUN3E0w1PFICG 是 123456 的正确哈希
-- 通过 Python bcrypt.hashpw(b'123456', bcrypt.gensalt(10)) 生成

UPDATE shop_user.user
SET password = '$2b$10$04BJm9vFIgq/w4YMPTvVtefEMfGw2.5QwryDhTE5aUN3E0w1PFICG'
WHERE id BETWEEN 1001 AND 3000;

-- 查看更新数量
SELECT COUNT(*) AS updated_count FROM shop_user.user
WHERE password = '$2b$10$04BJm9vFIgq/w4YMPTvVtefEMfGw2.5QwryDhTE5aUN3E0w1PFICG';

-- 同步更新商家账号密码（如果存在 shop_merchant.merchant_user 表）
UPDATE shop_merchant.merchant_user
SET password = '$2b$10$04BJm9vFIgq/w4YMPTvVtefEMfGw2.5QwryDhTE5aUN3E0w1PFICG'
WHERE id BETWEEN 2001 AND 3000;

-- 同步更新管理员账号密码（如果存在 shop_admin.admin_user 表）
UPDATE shop_admin.admin_user
SET password = '$2b$10$04BJm9vFIgq/w4YMPTvVtefEMfGw2.5QwryDhTE5aUN3E0w1PFICG'
WHERE username = 'admin';
