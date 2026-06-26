-- ============================================
-- Shop 商城项目 - 测试数据初始化脚本（完整闭环版）
-- 说明：为各微服务数据库插入真实感的测试数据，覆盖全模块业务闭环
-- 注意：
--   1. 密码统一为 BCrypt 加密的 "123456"（用户/商家），管理员为 "admin123"
--   2. 时间使用 NOW() 相对计算，让数据看起来是最近产生的
--   3. 金额使用 DECIMAL，价格参考京东/淘宝真实商品
--   4. ID 使用简单递增数字模拟雪花算法风格
-- 执行顺序：在 01~07 建表脚本之后、09-shop-admin 之前执行
--
-- 业务闭环覆盖：
--   ① 用户闭环：用户→账户→地址→收藏→足迹→优惠券→通知
--   ② 商家闭环：商家→资质→结算账户→店铺→结算流水→提现
--   ③ 商品闭环：分类→品牌→商品→规格→SKU→图片→评价(含追评)
--   ④ 订单闭环：下单(满减+优惠券)→支付→发货→收货→评价→结算
--   ⑤ 秒杀闭环：秒杀活动→秒杀订单→支付
--   ⑥ 退款闭环：退款申请→审核→退款完成
--   ⑦ 营销闭环：满减活动→优惠券模板→用户领取→下单使用
--   ⑧ 统计闭环：商品销量/浏览量→推荐排序
-- ============================================

-- ============================================
-- 一、shop_user 库测试数据
-- ============================================
USE shop_user;

-- -------------------------------------------
-- 1.1 用户表 user（5个用户）
-- 4个正常用户 + 1个禁用用户，测试不同状态
-- -------------------------------------------
INSERT INTO `user` (`id`, `phone`, `password`, `nickname`, `avatar`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(1001, '13800001111', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '张三', 'https://dummyimage.com/100x100/333/fff&text=Zhang', 1, DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_SUB(NOW(), INTERVAL 90 DAY), 0),
(1002, '13800002222', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '李四', 'https://dummyimage.com/100x100/333/fff&text=Li', 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),
(1003, '13800003333', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '王五', 'https://dummyimage.com/100x100/333/fff&text=Wang', 1, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), 0),
(1004, '13800004444', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '赵六', 'https://dummyimage.com/100x100/333/fff&text=Zhao', 1, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY), 0),
(1005, '13800005555', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '孙七', 'https://dummyimage.com/100x100/333/fff&text=Sun', 0, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 0);

-- -------------------------------------------
-- 1.2 用户账户表 user_account（4个，对应前4个正常用户）
-- 余额1000-5000不等，积分200-2000不等
-- -------------------------------------------
INSERT INTO `user_account` (`id`, `user_id`, `balance`, `points`, `version`, `create_time`, `update_time`) VALUES
(5001, 1001, 3280.50, 1560, 0, DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(5002, 1002, 1520.00, 820, 0, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(5003, 1003, 4650.80, 2000, 0, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(5004, 1004, 1000.00, 200, 0, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY));

-- -------------------------------------------
-- 1.3 收货地址表 user_address（8条，每个正常用户2个地址）
-- 使用真实的省市区地址，每个用户一个默认地址
-- -------------------------------------------
INSERT INTO `user_address` (`id`, `user_id`, `name`, `phone`, `province`, `city`, `district`, `detail`, `is_default`, `create_time`, `update_time`, `deleted`) VALUES
-- 张三的2个地址
(6001, 1001, '张三', '13800001111', '北京市', '北京市', '朝阳区', '建国路88号SOHO现代城A座1208室', 1, DATE_SUB(NOW(), INTERVAL 85 DAY), DATE_SUB(NOW(), INTERVAL 85 DAY), 0),
(6002, 1001, '张三', '13800001111', '北京市', '北京市', '海淀区', '中关村大街27号中关村大厦5层', 0, DATE_SUB(NOW(), INTERVAL 80 DAY), DATE_SUB(NOW(), INTERVAL 80 DAY), 0),
-- 李四的2个地址
(6003, 1002, '李四', '13800002222', '上海市', '上海市', '浦东新区', '陆家嘴环路1000号恒生银行大厦15F', 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),
(6004, 1002, '李四', '13800002222', '上海市', '上海市', '徐汇区', '漕溪北路398号东方商厦3栋2201', 0, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),
-- 王五的2个地址
(6005, 1003, '王五', '13800003333', '广东省', '深圳市', '南山区', '科技园南路16号创维半导体大厦8楼', 1, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), 0),
(6006, 1003, '王五', '13800003333', '广东省', '广州市', '天河区', '天河路385号太古汇一座3206', 0, DATE_SUB(NOW(), INTERVAL 38 DAY), DATE_SUB(NOW(), INTERVAL 38 DAY), 0),
-- 赵六的2个地址
(6007, 1004, '赵六', '13800004444', '浙江省', '杭州市', '西湖区', '文三路478号华星科技大厦9层', 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(6008, 1004, '赵六', '13800004444', '浙江省', '杭州市', '余杭区', '文一西路969号阿里巴巴西溪园区A区', 0, DATE_SUB(NOW(), INTERVAL 22 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY), 0);

-- -------------------------------------------
-- 1.4 收藏表 user_favorite（10条）
-- 用户1001收藏5个商品，用户1002收藏3个，用户1003收藏2个
-- -------------------------------------------
INSERT INTO `user_favorite` (`id`, `user_id`, `product_id`, `create_time`) VALUES
-- 张三收藏了5个商品
(7001, 1001, 4001, DATE_SUB(NOW(), INTERVAL 20 DAY)),
(7002, 1001, 4004, DATE_SUB(NOW(), INTERVAL 15 DAY)),
(7003, 1001, 4011, DATE_SUB(NOW(), INTERVAL 10 DAY)),
(7004, 1001, 4006, DATE_SUB(NOW(), INTERVAL 7 DAY)),
(7005, 1001, 4012, DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 李四收藏了3个商品
(7006, 1002, 4002, DATE_SUB(NOW(), INTERVAL 12 DAY)),
(7007, 1002, 4008, DATE_SUB(NOW(), INTERVAL 8 DAY)),
(7008, 1002, 4010, DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- 王五收藏了2个商品
(7009, 1003, 4003, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(7010, 1003, 4005, DATE_SUB(NOW(), INTERVAL 4 DAY));

-- -------------------------------------------
-- 1.5 浏览足迹表 user_footprint（15条）
-- 最近7天内的浏览记录，包含 category_id 用于"猜你喜欢"推荐
-- -------------------------------------------
INSERT INTO `user_footprint` (`id`, `user_id`, `product_id`, `category_id`, `create_time`) VALUES
-- 张三浏览了手机数码、笔记本类商品（用于猜你喜欢推荐同类商品）
(8001, 1001, 4001, 111, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8002, 1001, 4002, 111, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8003, 1001, 4004, 211, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(8004, 1001, 4012, 12,  DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 李四浏览了手机、服饰类商品
(8005, 1002, 4004, 211, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8006, 1002, 4005, 212, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(8007, 1002, 4011, 512, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(8008, 1002, 4008, 411, DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- 王五浏览了手机、家电类商品
(8009, 1003, 4003, 111, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8010, 1003, 4006, 311, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(8011, 1003, 4007, 312, DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 赵六浏览了服饰、运动类商品
(8012, 1004, 4008, 411, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8013, 1004, 4010, 43,  DATE_SUB(NOW(), INTERVAL 2 DAY)),
(8014, 1004, 4015, 81,  DATE_SUB(NOW(), INTERVAL 4 DAY)),
(8015, 1004, 4011, 512, DATE_SUB(NOW(), INTERVAL 6 DAY));

-- -------------------------------------------
-- 1.6 登录日志表 user_login_log（12条）
-- 最近的登录记录，包含成功和失败两种情况
-- -------------------------------------------
INSERT INTO `user_login_log` (`id`, `user_id`, `login_ip`, `login_device`, `login_time`, `login_status`, `fail_reason`) VALUES
-- 成功登录记录
(9001, 1001, '116.23.45.67', 'iPhone 15 Pro / iOS 17.2 / Safari',         DATE_SUB(NOW(), INTERVAL 1 HOUR), 1, NULL),
(9002, 1001, '116.23.45.67', 'Windows 11 / Chrome 120',                    DATE_SUB(NOW(), INTERVAL 2 DAY),  1, NULL),
(9003, 1002, '202.96.128.86', 'MacBook Pro / macOS 14.2 / Chrome 120',     DATE_SUB(NOW(), INTERVAL 3 HOUR), 1, NULL),
(9004, 1002, '202.96.128.86', 'iPhone 14 / iOS 17.1 / Safari',             DATE_SUB(NOW(), INTERVAL 1 DAY),  1, NULL),
(9005, 1003, '183.6.112.33', 'Xiaomi 14 / Android 14 / Chrome',            DATE_SUB(NOW(), INTERVAL 5 HOUR), 1, NULL),
(9006, 1003, '183.6.112.33', 'Windows 10 / Edge 120',                       DATE_SUB(NOW(), INTERVAL 3 DAY),  1, NULL),
(9007, 1004, '115.236.77.22', 'Huawei Mate 60 / HarmonyOS 4.0',             DATE_SUB(NOW(), INTERVAL 8 HOUR), 1, NULL),
(9008, 1004, '115.236.77.22', 'iPad Air 5 / iPadOS 17.2 / Safari',          DATE_SUB(NOW(), INTERVAL 2 DAY),  1, NULL),
(9009, 1001, '116.23.45.68', 'Windows 11 / Firefox 121',                    DATE_SUB(NOW(), INTERVAL 5 DAY),  1, NULL),
(9010, 1002, '202.96.128.87', 'MacBook Air / macOS 14.2 / Safari',          DATE_SUB(NOW(), INTERVAL 6 DAY),  1, NULL),
-- 失败登录记录（测试失败日志展示）
(9011, 1001, '116.23.45.67', 'iPhone 15 Pro / iOS 17.2 / Safari',         DATE_SUB(NOW(), INTERVAL 1 HOUR), 0, '密码错误'),
(9012, 1005, '116.23.45.67', 'iPhone 15 Pro / iOS 17.2 / Safari',         DATE_SUB(NOW(), INTERVAL 10 DAY), 0, '账号已禁用');

-- -------------------------------------------
-- 1.7 消息通知表 notification（8条）
-- 覆盖用户/商家/管理员三类接收人，覆盖订单/支付/退款/审核/提现等通知类型
-- -------------------------------------------
INSERT INTO `notification` (`id`, `receiver_type`, `receiver_id`, `type`, `title`, `content`, `biz_type`, `biz_id`, `is_read`, `create_time`, `update_time`) VALUES
-- 用户通知（receiver_type=1）
(10001, 1, 1001, 1, '订单已发货', '您的订单 2024061500010001 已发货，物流单号：SF1234567890，请注意查收。', 'order', '2024061500010001', 1, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY)),
(10002, 1, 1001, 1, '订单已完成', '您的订单 2024061500010001 已完成，感谢您的惠顾，欢迎再次光临！', 'order', '2024061500010001', 1, DATE_SUB(NOW(), INTERVAL 3 DAY),  DATE_SUB(NOW(), INTERVAL 3 DAY)),
(10003, 1, 1001, 2, '优惠券领取成功', '您已成功领取「平台满1000减100券」，有效期至下月月底，请尽快使用。', 'coupon', '5001', 0, DATE_SUB(NOW(), INTERVAL 7 DAY),  DATE_SUB(NOW(), INTERVAL 7 DAY)),
(10004, 1, 1002, 3, '退款已完成', '您的订单 2024061900010005 退款已处理完成，退款金额 399.00 元已原路退回，请查收。', 'refund', '2024061900010005', 1, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
(10005, 1, 1003, 1, '秒杀订单已发货', '您的秒杀订单 2024062000010006 已发货，物流单号：ZT1111222233，请留意签收。', 'order', '2024062000010006', 0, DATE_SUB(NOW(), INTERVAL 1 DAY),  DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 商家通知（receiver_type=2）
(10006, 2, 2001, 1, '您有新订单', '您收到一个新订单 2024061500010001，金额 14198.00 元，请及时处理。', 'order', '2024061500010001', 1, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY)),
(10007, 2, 2001, 5, '提现审核通过', '您的提现申请 5000.00 元已审核通过，将在1-3个工作日内到账。', 'withdraw', '16001', 1, DATE_SUB(NOW(), INTERVAL 5 DAY),  DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- 管理员通知（receiver_type=3）
(10008, 3, 1, 4, '商品审核申请', '商家「家居生活馆」提交了新商品「美的破壁机」的上架申请，请前往后台审核。', 'product', '4014', 0, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY));

-- -------------------------------------------
-- 1.8 用户优惠券表 user_coupon（6条）
-- 闭环：用户领取优惠券 → 下单使用 → 状态变更
-- status=0未使用 1已使用 2已过期
-- -------------------------------------------
INSERT INTO `user_coupon` (`id`, `user_id`, `coupon_id`, `merchant_id`, `coupon_name`, `coupon_type`, `amount`, `threshold`, `valid_start_time`, `valid_end_time`, `status`, `order_no`, `get_time`, `use_time`, `create_time`, `update_time`) VALUES
-- 张三领取的平台满减券（已用于订单10001，闭环：领取→使用→订单关联）
(5001, 1001, 6001, 0,    '平台满1000减100券', 1, 100.00, 1000.00, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 1, '2024061500010001', DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY)),
-- 张三领取的平台立减券（未使用，可用于下单测试）
(5002, 1001, 6002, 0,    '平台立减50元券',    3, 50.00,  0.00,    DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY), 0, NULL,                  DATE_SUB(NOW(), INTERVAL 20 DAY), NULL,                  DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY)),
-- 张三领取的商家2001满减券（未使用）
(5003, 1001, 6003, 2001, '数码店满5000减300', 1, 300.00, 5000.00, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 45 DAY), 0, NULL,                  DATE_SUB(NOW(), INTERVAL 15 DAY), NULL,                  DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
-- 李四领取的商家2002满减券（未使用）
(5004, 1002, 6004, 2002, '服饰店满200减30',   1, 30.00,  200.00,  DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 48 DAY), 0, NULL,                  DATE_SUB(NOW(), INTERVAL 12 DAY), NULL,                  DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
-- 王五领取的平台满减券（未使用）
(5005, 1003, 6001, 0,    '平台满1000减100券', 1, 100.00, 1000.00, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 50 DAY), 0, NULL,                  DATE_SUB(NOW(), INTERVAL 10 DAY), NULL,                  DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
-- 赵六领取的平台立减券（已过期，测试过期状态展示）
(5006, 1004, 6005, 0,    '平台满500减50券',   1, 50.00,  500.00,  DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 2, NULL,                  DATE_SUB(NOW(), INTERVAL 60 DAY), NULL,                  DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY));


-- ============================================
-- 二、shop_merchant 库测试数据
-- ============================================
USE shop_merchant;

-- -------------------------------------------
-- 2.1 商家表 merchant（3个商家，全部已审核通过）
-- -------------------------------------------
INSERT INTO `merchant` (`id`, `name`, `logo`, `description`, `contact_phone`, `user_id`, `password`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(2001, '数码旗舰店', 'https://dummyimage.com/800x800/E4393C/fff&text=Digital', '专注正品数码产品，Apple、华为、小米官方授权经销商，7天无理由退换', '13900001111', 1001, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 115 DAY), 0),
(2002, '时尚服饰专营店', 'https://dummyimage.com/800x800/E4393C/fff&text=Fashion', '国际知名服饰品牌官方授权，优衣库、耐克正品保证，假一赔十', '13900002222', 1002, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, DATE_SUB(NOW(), INTERVAL 100 DAY), DATE_SUB(NOW(), INTERVAL 95 DAY), 0),
(2003, '家居生活馆', 'https://dummyimage.com/800x800/E4393C/fff&text=Home', '品质家居生活一站式购物，美的、海尔、宜家精选好物', '13900003333', 1003, '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_SUB(NOW(), INTERVAL 85 DAY), 0);

-- -------------------------------------------
-- 2.2 商家资质表 merchant_qualification（3条，对应3个已通过的商家）
-- 营业执照号15位格式，法人姓名
-- -------------------------------------------
INSERT INTO `merchant_qualification` (`id`, `merchant_id`, `license_no`, `license_img`, `legal_person`, `status`, `audit_note`, `create_time`, `update_time`) VALUES
(3001, 2001, '91110105MA01B1CX3T', 'https://dummyimage.com/800x600/333/fff&text=License+Digital', '陈建国', 1, '资质审核通过，材料齐全', DATE_SUB(NOW(), INTERVAL 118 DAY), DATE_SUB(NOW(), INTERVAL 115 DAY)),
(3002, 2002, '91310115MA1H8K2P5X', 'https://dummyimage.com/800x600/333/fff&text=License+Fashion', '林美华', 1, '资质审核通过，材料齐全', DATE_SUB(NOW(), INTERVAL 98 DAY),  DATE_SUB(NOW(), INTERVAL 95 DAY)),
(3003, 2003, '91440106MA5D8K3P7X', 'https://dummyimage.com/800x600/333/fff&text=License+Home',    '周志强', 1, '资质审核通过，材料齐全', DATE_SUB(NOW(), INTERVAL 88 DAY),  DATE_SUB(NOW(), INTERVAL 85 DAY));

-- -------------------------------------------
-- 2.3 商家结算账户表 merchant_settlement（3条）
-- 闭环：订单完成→余额增加→提现冻结→提现打款扣减
-- balance=可用余额，frozen_amount=冻结金额（提现申请中）
-- -------------------------------------------
INSERT INTO `merchant_settlement` (`id`, `merchant_id`, `bank_name`, `bank_account`, `account_name`, `balance`, `frozen_amount`, `create_time`, `update_time`, `deleted`) VALUES
-- 商家2001：可用余额8488.10（来自订单10001结算13488.10 - 提现5000），冻结3000（待审核提现）
(4001, 2001, '中国工商银行', '6222021001234567890', '北京数码旗舰科技有限公司', 8488.10, 3000.00, DATE_SUB(NOW(), INTERVAL 115 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
-- 商家2002：可用余额2000（初始运营资金），无冻结
(4002, 2002, '中国建设银行', '6227001234560123456', '上海时尚服饰贸易有限公司', 2000.00, 0.00,    DATE_SUB(NOW(), INTERVAL 95 DAY), DATE_SUB(NOW(), INTERVAL 95 DAY), 0),
-- 商家2003：可用余额0（订单10005已退款，无结算收入），无冻结
(4003, 2003, '中国农业银行', '6228480123456789012', '广州家居生活贸易有限公司', 0.00,    0.00,    DATE_SUB(NOW(), INTERVAL 85 DAY), DATE_SUB(NOW(), INTERVAL 85 DAY), 0);

-- -------------------------------------------
-- 2.4 店铺表 shop（3个店铺）
-- 每个商家一个店铺
-- -------------------------------------------
INSERT INTO `shop` (`id`, `merchant_id`, `name`, `logo`, `banner`, `description`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(3001, 2001, 'Apple授权专卖店', 'https://dummyimage.com/800x800/E4393C/fff&text=Apple', 'https://dummyimage.com/1200x400/E4393C/fff&text=Apple+Banner', 'Apple官方授权，正品保障。iPhone、MacBook、iPad全系产品，享官方保修服务。', 1, DATE_SUB(NOW(), INTERVAL 110 DAY), DATE_SUB(NOW(), INTERVAL 110 DAY), 0),
(3002, 2002, '优衣库官方旗舰店', 'https://dummyimage.com/800x800/E4393C/fff&text=UNIQLO', 'https://dummyimage.com/1200x400/E4393C/fff&text=UNIQLO+Banner', '优衣库官方旗舰店，LifeWear服适人生。高品质基础款，舒适百搭。', 1, DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_SUB(NOW(), INTERVAL 90 DAY), 0),
(3003, 2003, '宜家家居官方店', 'https://dummyimage.com/800x800/E4393C/fff&text=IKEA', 'https://dummyimage.com/1200x400/E4393C/fff&text=IKEA+Banner', '宜家家居官方店，为大众创造更美好的日常家居生活。', 1, DATE_SUB(NOW(), INTERVAL 80 DAY), DATE_SUB(NOW(), INTERVAL 80 DAY), 0);

-- -------------------------------------------
-- 2.5 结算流水表 settlement_record（2条）
-- 闭环：订单完成→生成结算流水→商家余额增加
-- status: 0待结算 1已结算 2已退款
-- -------------------------------------------
INSERT INTO `settlement_record` (`id`, `merchant_id`, `order_no`, `order_amount`, `commission_rate`, `commission_amount`, `settlement_amount`, `status`, `settle_time`, `create_time`, `update_time`, `deleted`) VALUES
-- 订单10001已完成：金额14198，抽成5%，商家应得13488.10（已结算到余额）
(15001, 2001, '2024061500010001', 14198.00, 0.0500, 709.90, 13488.10, 1, DATE_SUB(NOW(), INTERVAL 3 DAY),  DATE_SUB(NOW(), INTERVAL 3 DAY),  DATE_SUB(NOW(), INTERVAL 3 DAY),  0),
-- 订单10005已退款：金额399，状态=已退款（不结算）
(15002, 2003, '2024061900010005', 399.00,   0.0500, 19.95,  379.05,   2, NULL,                         DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), 0);

-- -------------------------------------------
-- 2.6 提现申请表 withdraw_order（2条）
-- 闭环：商家申请提现→余额转冻结→管理员审核→打款扣冻结/拒绝退余额
-- status: 0待审核 1已通过 2已拒绝 3已打款
-- -------------------------------------------
INSERT INTO `withdraw_order` (`id`, `merchant_id`, `amount`, `status`, `bank_name`, `bank_account`, `account_name`, `audit_remark`, `audit_time`, `create_time`, `update_time`, `deleted`) VALUES
-- 商家2001的已打款提现（5000元，已从冻结扣减）
(16001, 2001, 5000.00, 3, '中国工商银行', '6222021001234567890', '北京数码旗舰科技有限公司', '审核通过，已打款', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
-- 商家2001的待审核提现（3000元，冻结在frozen_amount中）
(16002, 2001, 3000.00, 0, '中国工商银行', '6222021001234567890', '北京数码旗舰科技有限公司', NULL,                  NULL,                         DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0);

-- -------------------------------------------
-- 2.7 优惠券模板表 coupon（5个优惠券模板）
-- 闭环：商家/平台创建模板→用户领取→下单使用→统计数量
-- type: 1满减 2折扣 3立减
-- merchant_id=0 表示平台券，>0 表示商家券
-- -------------------------------------------
INSERT INTO `coupon` (`id`, `merchant_id`, `name`, `type`, `amount`, `threshold`, `total_count`, `received_count`, `used_count`, `per_limit`, `receive_start_time`, `receive_end_time`, `valid_start_time`, `valid_end_time`, `status`, `description`, `create_time`, `update_time`, `deleted`) VALUES
-- 平台满减券：满1000减100（进行中，被订单10001使用过1次）
(6001, 0,    '平台满1000减100券',  1, 100.00, 1000.00, 1000, 156, 12, 1, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),  DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),  1, '全场通用，满1000元可用',            DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),  0),
-- 平台立减券：立减50（进行中，无门槛）
(6002, 0,    '平台立减50元券',     3, 50.00,  0.00,    500,  89,  5,  1, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY),  DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY),  1, '无门槛立减，全场可用',              DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), 0),
-- 商家2001满减券：满5000减300（进行中，数码店专用）
(6003, 2001, '数码店满5000减300',  1, 300.00, 5000.00, 200,  45,  3,  1, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 45 DAY),  DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 45 DAY),  1, '数码旗舰店专用，满5000元可用',      DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), 0),
-- 商家2002满减券：满200减30（进行中，服饰店专用）
(6004, 2002, '服饰店满200减30',    1, 30.00,  200.00,  300,  78,  8,  1, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 48 DAY),  DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 48 DAY),  1, '时尚服饰专营店专用，满200元可用',    DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), 0),
-- 平台满减券：满500减50（已结束，测试过期状态）
(6005, 0,    '平台满500减50券',    1, 50.00,  500.00,  100,  100, 60, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY),  DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY),  2, '已结束的活动券',                    DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 0);

-- -------------------------------------------
-- 2.8 满减活动表 promotion（4个满减活动）
-- 闭环：创建满减活动→下单自动计算优惠→订单记录promotion_discount
-- scope_type: 1全店 2指定商品
-- status: 0待生效 1进行中 2已结束 3已下架
-- -------------------------------------------
INSERT INTO `promotion` (`id`, `merchant_id`, `name`, `threshold`, `discount_amount`, `scope_type`, `start_time`, `end_time`, `status`, `description`, `create_time`, `update_time`, `deleted`) VALUES
-- 平台活动：满10000减500（进行中，全店，被订单10001使用）
(7001, 0,    '全场满10000减500', 10000.00, 500.00, 1, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),  1, '全场商品参与，满10000元立减500',           DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY), 0),
-- 商家2001活动：满5000减200（进行中，指定商品）
(7002, 2001, '数码店满5000减200', 5000.00, 200.00, 2, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY),  1, '数码店指定商品参与，满5000元立减200',       DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), 0),
-- 商家2002活动：满200减30（进行中，全店）
(7003, 2002, '服饰店满200减30',    200.00,  30.00,  1, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 45 DAY),  1, '服饰店全店商品参与，满200元立减30',         DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), 0),
-- 平台活动：满300减50（已结束）
(7004, 0,    '全场满300减50',      300.00,  50.00,  1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY),  2, '已结束的满减活动',                          DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 0);

-- -------------------------------------------
-- 2.9 满减活动商品关联表 promotion_product
-- 仅当 promotion.scope_type=2（指定商品）时才有数据
-- promotion 7002（数码店满5000减200）关联 iPhone 和 MacBook
-- -------------------------------------------
INSERT INTO `promotion_product` (`id`, `promotion_id`, `product_id`, `sku_id`, `create_time`) VALUES
(8001, 7002, 4001, NULL, DATE_SUB(NOW(), INTERVAL 20 DAY)),
(8002, 7002, 4004, NULL, DATE_SUB(NOW(), INTERVAL 20 DAY));

-- -------------------------------------------
-- 2.10 秒杀活动表 seckill_activity（2个秒杀活动）
-- 闭环：创建秒杀活动→Redis预热库存→抢购→异步创建秒杀订单
-- status: 0待生效 1进行中 2已结束 3已下架
-- -------------------------------------------
INSERT INTO `seckill_activity` (`id`, `merchant_id`, `product_id`, `sku_id`, `seckill_price`, `original_price`, `total_count`, `available_count`, `limit_count`, `start_time`, `end_time`, `status`, `description`, `create_time`, `update_time`, `deleted`) VALUES
-- 小米14 Ultra 秒杀活动（进行中，已被订单10006使用1个库存）
(9001, 2001, 4003, 50019, 3999.00, 5999.00, 50, 49, 1, DATE_SUB(NOW(), INTERVAL 2 DAY),  DATE_ADD(NOW(), INTERVAL 5 DAY),  1, '小米14 Ultra 限时秒杀，直降2000元，限量50台', DATE_SUB(NOW(), INTERVAL 2 DAY),  DATE_SUB(NOW(), INTERVAL 1 DAY),  0),
-- iPhone 15 Pro Max 秒杀活动（待生效，测试状态展示）
(9002, 2001, 4001, 50001, 8999.00, 9999.00, 20, 20, 1, DATE_ADD(NOW(), INTERVAL 3 DAY),  DATE_ADD(NOW(), INTERVAL 10 DAY), 0, 'iPhone 15 Pro Max 秒杀预热，敬请期待',        DATE_SUB(NOW(), INTERVAL 1 DAY),  DATE_SUB(NOW(), INTERVAL 1 DAY),  0);


-- ============================================
-- 三、shop_product 库测试数据
-- ============================================
USE shop_product;

-- -------------------------------------------
-- 3.1 分类表 category（三级分类树，共36个分类）
-- 一级8个 → 二级18个 → 三级10个
-- -------------------------------------------

-- 一级分类（8个）
INSERT INTO `category` (`id`, `parent_id`, `name`, `icon`, `sort`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(1,  0, '手机数码', 'https://dummyimage.com/100x100/333/fff&text=Phone', 1, 1, DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), 0),
(2,  0, '电脑办公', 'https://dummyimage.com/100x100/333/fff&text=PC', 2, 1, DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), 0),
(3,  0, '家用电器', 'https://dummyimage.com/100x100/333/fff&text=Appliance', 3, 1, DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), 0),
(4,  0, '服饰鞋包', 'https://dummyimage.com/100x100/333/fff&text=Clothes', 4, 1, DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), 0),
(5,  0, '美妆护肤', 'https://dummyimage.com/100x100/333/fff&text=Beauty', 5, 1, DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), 0),
(6,  0, '食品生鲜', 'https://dummyimage.com/100x100/333/fff&text=Food', 6, 1, DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), 0),
(7,  0, '家居家装', 'https://dummyimage.com/100x100/333/fff&text=Home', 7, 1, DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), 0),
(8,  0, '运动户外', 'https://dummyimage.com/100x100/333/fff&text=Sport', 8, 1, DATE_SUB(NOW(), INTERVAL 180 DAY), DATE_SUB(NOW(), INTERVAL 180 DAY), 0);

-- 二级分类（18个）
INSERT INTO `category` (`id`, `parent_id`, `name`, `icon`, `sort`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(11, 1, '手机', 'https://dummyimage.com/100x100/333/fff&text=Mobile', 1, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(12, 1, '平板电脑', 'https://dummyimage.com/100x100/333/fff&text=Tablet', 2, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(13, 1, '智能穿戴', 'https://dummyimage.com/100x100/333/fff&text=Wearable', 3, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(21, 2, '笔记本', 'https://dummyimage.com/100x100/333/fff&text=Laptop', 1, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(22, 2, '台式机', 'https://dummyimage.com/100x100/333/fff&text=Desktop', 2, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(23, 2, '外设', 'https://dummyimage.com/100x100/333/fff&text=Peripheral', 3, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(31, 3, '大家电', 'https://dummyimage.com/100x100/333/fff&text=MajorApp', 1, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(32, 3, '厨房电器', 'https://dummyimage.com/100x100/333/fff&text=KitchenApp', 2, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(41, 4, '男装', 'https://dummyimage.com/100x100/333/fff&text=Men', 1, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(42, 4, '女装', 'https://dummyimage.com/100x100/333/fff&text=Women', 2, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(43, 4, '鞋靴', 'https://dummyimage.com/100x100/333/fff&text=Shoes', 3, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(51, 5, '面部护肤', 'https://dummyimage.com/100x100/333/fff&text=Skincare', 1, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(52, 5, '彩妆', 'https://dummyimage.com/100x100/333/fff&text=Makeup', 2, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(61, 6, '生鲜水果', 'https://dummyimage.com/100x100/333/fff&text=Fruit', 1, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(62, 6, '休闲零食', 'https://dummyimage.com/100x100/333/fff&text=Snack', 2, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(71, 7, '家具', 'https://dummyimage.com/100x100/333/fff&text=Furniture', 1, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(72, 7, '家纺', 'https://dummyimage.com/100x100/333/fff&text=Textile', 2, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(81, 8, '运动鞋服', 'https://dummyimage.com/100x100/333/fff&text=SportWear', 1, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0),
(82, 8, '户外装备', 'https://dummyimage.com/100x100/333/fff&text=Outdoor', 2, 1, DATE_SUB(NOW(), INTERVAL 175 DAY), DATE_SUB(NOW(), INTERVAL 175 DAY), 0);

-- 三级分类（10个）
INSERT INTO `category` (`id`, `parent_id`, `name`, `icon`, `sort`, `status`, `create_time`, `update_time`, `deleted`) VALUES
(111, 11, '5G手机', 'https://dummyimage.com/100x100/333/fff&text=5G', 1, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(112, 11, '4G手机', 'https://dummyimage.com/100x100/333/fff&text=4G', 2, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(211, 21, '游戏本', 'https://dummyimage.com/100x100/333/fff&text=Gaming', 1, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(212, 21, '轻薄本', 'https://dummyimage.com/100x100/333/fff&text=Ultrabook', 2, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(311, 31, '空调', 'https://dummyimage.com/100x100/333/fff&text=AC', 1, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(312, 31, '冰箱', 'https://dummyimage.com/100x100/333/fff&text=Fridge', 2, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(313, 31, '洗衣机', 'https://dummyimage.com/100x100/333/fff&text=Washer', 3, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(411, 41, 'T恤', 'https://dummyimage.com/100x100/333/fff&text=Tshirt', 1, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(412, 41, '外套', 'https://dummyimage.com/100x100/333/fff&text=Jacket', 2, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(511, 51, '面霜', 'https://dummyimage.com/100x100/333/fff&text=Cream', 1, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0),
(512, 51, '精华', 'https://dummyimage.com/100x100/333&text=Serum', 2, 1, DATE_SUB(NOW(), INTERVAL 170 DAY), DATE_SUB(NOW(), INTERVAL 170 DAY), 0);

-- -------------------------------------------
-- 3.2 品牌表 brand（10个品牌）
-- -------------------------------------------
INSERT INTO `brand` (`id`, `name`, `logo`, `description`, `create_time`, `update_time`, `deleted`) VALUES
(101, 'Apple', 'https://dummyimage.com/100x100/333/fff&text=Apple', 'Apple Inc.，全球领先的科技公司，以创新设计和卓越体验著称', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(102, '华为(HUAWEI)', 'https://dummyimage.com/100x100/333/fff&text=HUAWEI', '华为技术有限公司，全球领先的ICT基础设施和智能终端提供商', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(103, '小米(Xiaomi)', 'https://dummyimage.com/100x100/333/fff&text=Xiaomi', '小米科技有限责任公司，始终坚持做感动人心、价格厚道的好产品', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(104, '联想(Lenovo)', 'https://dummyimage.com/100x100/333/fff&text=Lenovo', '联想集团，全球PC领导品牌，为用户提供创新智能设备', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(105, '戴尔(Dell)', 'https://dummyimage.com/100x100/333/fff&text=Dell', '戴尔科技集团，全球领先的IT解决方案提供商', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(106, '美的(Midea)', 'https://dummyimage.com/100x100/333/fff&text=Midea', '美的集团，全球领先的消费电器、暖通空调、机器人与自动化系统科技集团', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(107, '海尔(Haier)', 'https://dummyimage.com/100x100/333/fff&text=Haier', '海尔集团，全球大型家电品牌，致力于为用户打造智慧家居体验', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(108, '优衣库(UNIQLO)', 'https://dummyimage.com/100x100/333/fff&text=UNIQLO', '优衣库，日本快时尚品牌，LifeWear服适人生理念', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(109, '耐克(NIKE)', 'https://dummyimage.com/100x100/333/fff&text=NIKE', 'Nike Inc.，全球领先的运动品牌，Just Do It', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0),
(110, '兰蔻(Lancôme)', 'https://dummyimage.com/100x100/333/fff&text=Lancome', '兰蔻，法国高端美妆品牌，源于法国的优雅与奢华', DATE_SUB(NOW(), INTERVAL 200 DAY), DATE_SUB(NOW(), INTERVAL 200 DAY), 0);

-- -------------------------------------------
-- 3.3 商品SPU表 product（15个商品）
-- 覆盖各分类，包含真实的副标题、主图、图片列表、详情
-- sales=销量（下单累加），view_count=浏览量（查看详情累加）
-- 闭环：下单→sales+1；查看详情→view_count+1；用于热销推荐排序
-- -------------------------------------------
INSERT INTO `product` (`id`, `category_id`, `brand_id`, `shop_id`, `name`, `subtitle`, `main_image`, `images`, `detail`, `status`, `sales`, `view_count`, `create_time`, `update_time`, `deleted`) VALUES
(4001, 111, 101, 3001, 'Apple iPhone 15 Pro Max 256GB 原色钛金属',
 'A17 Pro芯片 | 钛金属设计 | 4800万像素 | 超长续航',
 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15ProMax',
 '["https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-1","https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-2","https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-3","https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-4"]',
 '<h3>Apple iPhone 15 Pro Max</h3><p>全新A17 Pro芯片，带来突破性的性能表现。钛金属设计，轻盈坚固。4800万像素主摄，捕捉每一个精彩瞬间。超长续航，陪伴你一整天。</p><ul><li>A17 Pro芯片，6核CPU+6核GPU</li><li>6.7英寸超视网膜XDR显示屏</li><li>钛金属边框，陶瓷盾面板</li><li>4800万像素主摄+1200万超广角+1200万长焦</li><li>USB-C接口，支持USB 3</li></ul>',
 1, 156, 2380, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),

(4002, 111, 102, 3001, '华为 Mate 60 Pro 512GB 雅丹黑',
 '麒麟芯片回归 | 卫星通话 | 昆仑玻璃 | 超可靠通信',
 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60Pro',
 '["https://dummyimage.com/800x800/E4393C/fff&text=Mate60-1","https://dummyimage.com/800x800/E4393C/fff&text=Mate60-2","https://dummyimage.com/800x800/E4393C/fff&text=Mate60-3","https://dummyimage.com/800x800/E4393C/fff&text=Mate60-4"]',
 '<h3>华为 Mate 60 Pro</h3><p>麒麟芯片强势回归，带来旗舰级性能体验。全球首发卫星通话功能，无信号也能通信。第二代昆仑玻璃，十倍耐摔。</p><ul><li>麒麟9000S芯片</li><li>6.82英寸OLED曲面屏</li><li>5000万像素超光变摄像头</li><li>卫星通话功能</li><li>5000mAh大电池，88W快充</li></ul>',
 1, 132, 1860, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),

(4003, 111, 103, 3001, '小米14 Ultra 16GB+512GB 黑色',
 '徕卡Summilux光学镜头 | 骁龙8 Gen3 | 2K超视感屏',
 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14Ultra',
 '["https://dummyimage.com/800x800/E4393C/fff&text=Mi14-1","https://dummyimage.com/800x800/E4393C/fff&text=Mi14-2","https://dummyimage.com/800x800/E4393C/fff&text=Mi14-3","https://dummyimage.com/800x800/E4393C/fff&text=Mi14-4","https://dummyimage.com/800x800/E4393C/fff&text=Mi14-5"]',
 '<h3>小米14 Ultra</h3><p>徕卡Summilux光学镜头，移动影像新巅峰。骁龙8 Gen3旗舰芯片，性能全面跃升。2K超视感屏，视觉体验震撼。</p><ul><li>骁龙8 Gen3处理器</li><li>6.73英寸2K AMOLED屏</li><li>徕卡四摄：5000万主摄+5000万超广角+5000万长焦+5000万超长焦</li><li>5000mAh电池，90W有线+50W无线快充</li></ul>',
 1, 98, 1520, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),

(4004, 211, 101, 3001, 'Apple MacBook Pro 14英寸 M3 Pro芯片',
 'M3 Pro芯片 | 18GB统一内存 | Liquid视网膜XDR屏 | 超长续航',
 'https://dummyimage.com/800x800/E4393C/fff&text=MacBookPro14',
 '["https://dummyimage.com/800x800/E4393C/fff&text=MBP-1","https://dummyimage.com/800x800/E4393C/fff&text=MBP-2","https://dummyimage.com/800x800/E4393C/fff&text=MBP-3"]',
 '<h3>Apple MacBook Pro 14英寸</h3><p>M3 Pro芯片带来惊人的性能和能效，18GB统一内存轻松应对专业工作流。Liquid视网膜XDR显示屏，画面细腻生动。</p><ul><li>M3 Pro芯片（11核CPU+14核GPU）</li><li>14.2英寸Liquid视网膜XDR屏</li><li>18GB统一内存，512GB SSD</li><li>长达17小时续航</li><li>MagSafe 3、HDMI、SD卡槽、三个雷雳4接口</li></ul>',
 1, 76, 980, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),

(4005, 212, 104, 3001, '联想 小新Pro16 2024 酷睿Ultra7',
 '酷睿Ultra7处理器 | 2.5K 120Hz屏 | 32GB大内存 | 轻薄商务',
 'https://dummyimage.com/800x800/E4393C/fff&text=XiaoXinPro16',
 '["https://dummyimage.com/800x800/E4393C/fff&text=XiaoXin-1","https://dummyimage.com/800x800/E4393C/fff&text=XiaoXin-2","https://dummyimage.com/800x800/E4393C/fff&text=XiaoXin-3"]',
 '<h3>联想 小新Pro16 2024</h3><p>全新酷睿Ultra7处理器，AI性能大幅提升。2.5K 120Hz高刷屏，视觉流畅。32GB大内存，多任务无压力。</p><ul><li>酷睿Ultra7 155H处理器</li><li>16英寸2.5K IPS屏，120Hz刷新率</li><li>32GB LPDDR5x内存，1TB SSD</li><li>75Wh大电池，100W PD快充</li><li>1.95kg轻薄机身</li></ul>',
 1, 54, 720, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 0),

(4006, 311, 106, 3003, '美的 酷省电 3匹新一级能效 空调柜机',
 '新一级能效 | 一键酷省电 | 56℃高温除菌 | 智能自清洁',
 'https://dummyimage.com/800x800/E4393C/fff&text=MideaAC',
 '["https://dummyimage.com/800x800/E4393C/fff&text=MideaAC-1","https://dummyimage.com/800x800/E4393C/fff&text=MideaAC-2","https://dummyimage.com/800x800/E4393C/fff&text=MideaAC-3","https://dummyimage.com/800x800/E4393C/fff&text=MideaAC-4"]',
 '<h3>美的 酷省电 3匹空调柜机</h3><p>新一级能效，省电更省心。一键酷省电模式，节能高达37%。56℃高温除菌，守护呼吸健康。智能自清洁，持久清新。</p><ul><li>3匹柜机，适用30-40㎡</li><li>新一级能效，APF值4.5</li><li>一键酷省电，ECO节能模式</li><li>56℃高温除菌</li><li>智能WiFi控制</li></ul>',
 1, 43, 650, DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 0),

(4007, 312, 107, 3003, '海尔 BCD-470WDPG 470升十字对开门冰箱',
 '470升大容量 | 干湿分储 | 智能双变频 | 一级能效',
 'https://dummyimage.com/800x800/E4393C/fff&text=HaierFridge',
 '["https://dummyimage.com/800x800/E4393C/fff&text=HaierFridge-1","https://dummyimage.com/800x800/E4393C/fff&text=HaierFridge-2","https://dummyimage.com/800x800/E4393C/fff&text=HaierFridge-3"]',
 '<h3>海尔 470升十字对开门冰箱</h3><p>470升大容量，全家食材轻松收纳。干湿分储设计，保鲜更专业。智能双变频，一级能效更省电。</p><ul><li>470升总容量（冷藏311L+冷冻159L）</li><li>十字对开门设计</li><li>干湿分储，精控保鲜</li><li>双变频压缩机，一级能效</li><li>DEO净味系统</li></ul>',
 1, 38, 580, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), 0),

(4008, 411, 108, 3002, '优衣库 男装 圆领T恤(短袖)',
 '纯棉舒适 | 亲肤透气 | 百搭基础款 | 多色可选',
 'https://dummyimage.com/800x800/E4393C/fff&text=UNIQLOTshirt',
 '["https://dummyimage.com/800x800/E4393C/fff&text=UT-1","https://dummyimage.com/800x800/E4393C/fff&text=UT-2","https://dummyimage.com/800x800/E4393C/fff&text=UT-3"]',
 '<h3>优衣库 圆领T恤(短袖)</h3><p>经典圆领T恤，100%纯棉面料，亲肤舒适。简约百搭设计，日常穿搭必备基础款。多色可选，满足不同风格需求。</p><ul><li>100%棉面料</li><li>圆领设计，穿着舒适</li><li>多色可选：白色/黑色/灰色</li><li>尺码：S/M/L/XL/XXL</li><li>机洗可，方便打理</li></ul>',
 1, 320, 450, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),

(4009, 42, 108, 3002, '优衣库 女装 羊毛混纺针织开衫',
 '羊毛混纺 | 柔软亲肤 | 优雅百搭 | 秋冬必备',
 'https://dummyimage.com/800x800/E4393C/fff&text=UNIQLOCardigan',
 '["https://dummyimage.com/800x800/E4393C/fff&text=UC-1","https://dummyimage.com/800x800/E4393C/fff&text=UC-2","https://dummyimage.com/800x800/E4393C/fff&text=UC-3"]',
 '<h3>优衣库 羊毛混纺针织开衫</h3><p>优质羊毛混纺面料，柔软亲肤，保暖舒适。经典开衫设计，优雅百搭，秋冬穿搭必备单品。</p><ul><li>羊毛混纺面料</li><li>开衫设计，穿脱方便</li><li>多色可选</li><li>尺码：XS/S/M/L/XL</li><li>手洗推荐</li></ul>',
 1, 87, 380, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 0),

(4010, 43, 109, 3002, '耐克 Air Max 270 气垫运动鞋 男款',
 '270度Max Air气垫 | 轻质缓震 | 透气网面 | 经典百搭',
 'https://dummyimage.com/800x800/E4393C/fff&text=NikeAirMax270',
 '["https://dummyimage.com/800x800/E4393C/fff&text=NAM-1","https://dummyimage.com/800x800/E4393C/fff&text=NAM-2","https://dummyimage.com/800x800/E4393C/fff&text=NAM-3","https://dummyimage.com/800x800/E4393C/fff&text=NAM-4"]',
 '<h3>耐克 Air Max 270 气垫运动鞋</h3><p>270度Max Air气垫，带来极致缓震体验。轻质鞋面设计，透气舒适。经典百搭造型，运动休闲两相宜。</p><ul><li>270度可视Max Air气垫</li><li>网面鞋面，透气舒适</li><li>橡胶华夫外底，抓地力强</li><li>多色可选：黑/白/红</li><li>尺码：39-44</li></ul>',
 1, 145, 890, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),

(4011, 512, 110, 3002, '兰蔻 小黑瓶精华液 50ml',
 '修护强韧 | 淡化细纹 | 提亮肤色 | 经典精华',
 'https://dummyimage.com/800x800/E4393C/fff&text=LancomeSerum',
 '["https://dummyimage.com/800x800/E4393C/fff&text=LS-1","https://dummyimage.com/800x800/E4393C/fff&text=LS-2","https://dummyimage.com/800x800/E4393C/fff&text=LS-3"]',
 '<h3>兰蔻 小黑瓶精华液</h3><p>兰蔻明星产品，修护强韧肌底。富含二裂酵母发酵产物溶胞物，淡化细纹，提亮肤色。轻盈质地，快速吸收。</p><ul><li>50ml容量</li><li>二裂酵母发酵产物溶胞物</li><li>修护肌底，强韧屏障</li><li>淡化细纹，提亮肤色</li><li>轻盈质地，快速吸收</li></ul>',
 1, 210, 670, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),

(4012, 12, 101, 3001, 'Apple iPad Air 5 256GB 星光色',
 'M1芯片 | 10.9英寸液态视网膜屏 | 全天候续航 | Apple Pencil适配',
 'https://dummyimage.com/800x800/E4393C/fff&text=iPadAir5',
 '["https://dummyimage.com/800x800/E4393C/fff&text=iPad-1","https://dummyimage.com/800x800/E4393C/fff&text=iPad-2","https://dummyimage.com/800x800/E4393C/fff&text=iPad-3"]',
 '<h3>Apple iPad Air 5</h3><p>M1芯片加持，性能飞跃。10.9英寸液态视网膜显示屏，色彩绚丽。支持Apple Pencil和妙控键盘，创作无界限。</p><ul><li>M1芯片</li><li>10.9英寸液态视网膜屏</li><li>256GB存储容量</li><li>支持Apple Pencil（第二代）</li><li>全天候电池续航</li></ul>',
 1, 89, 540, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),

(4013, 23, 105, 3001, '戴尔 U2723QE 27英寸4K显示器',
 '4K IPS Black面板 | 98% DCI-P3 | Type-C 90W | 专业设计',
 'https://dummyimage.com/800x800/E4393C/fff&text=DellU2723QE',
 '["https://dummyimage.com/800x800/E4393C/fff&text=Dell-1","https://dummyimage.com/800x800/E4393C/fff&text=Dell-2","https://dummyimage.com/800x800/E4393C/fff&text=Dell-3"]',
 '<h3>戴尔 U2723QE 4K显示器</h3><p>全新IPS Black面板技术，对比度翻倍。4K UHD分辨率，98% DCI-P3色域覆盖。Type-C 90W供电，一线连接。</p><ul><li>27英寸4K UHD（3840x2160）</li><li>IPS Black面板，2000:1对比度</li><li>98% DCI-P3色域</li><li>USB-C 90W供电</li><li>旋转升降底座</li></ul>',
 1, 32, 410, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 0),

(4014, 32, 106, 3003, '美的 破壁机 静音免滤 智能预约',
 '高速破壁 | 静音降噪 | 免滤免泡 | 12小时智能预约',
 'https://dummyimage.com/800x800/E4393C/fff&text=MideaBlender',
 '["https://dummyimage.com/800x800/E4393C/fff&text=MB-1","https://dummyimage.com/800x800/E4393C/fff&text=MB-2","https://dummyimage.com/800x800/E4393C/fff&text=MB-3"]',
 '<h3>美的 破壁机</h3><p>40000转/分高速破壁，细腻免滤。多重降噪技术，清晨不扰眠。12小时智能预约，早起即享热饮。</p><ul><li>40000转/分高速电机</li><li>8叶精钢破壁刀</li><li>多重降噪，低至45dB</li><li>12小时智能预约</li><li>1.75L大容量</li></ul>',
 0, 12, 180, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),

(4015, 81, 109, 3002, '耐克 DRI-FIT 速干运动T恤 男款',
 'DRI-FIT速干科技 | 轻盈透气 | 运动必备 | 多色可选',
 'https://dummyimage.com/800x800/E4393C/fff&text=NikeDriFit',
 '["https://dummyimage.com/800x800/E4393C/fff&text=NDT-1","https://dummyimage.com/800x800/E4393C/fff&text=NDT-2","https://dummyimage.com/800x800/E4393C/fff&text=NDT-3"]',
 '<h3>耐克 DRI-FIT 速干运动T恤</h3><p>DRI-FIT速干科技面料，快速排汗保持干爽。轻盈透气设计，运动更舒适。经典运动版型，训练休闲两相宜。</p><ul><li>DRI-FIT速干科技</li><li>100%聚酯纤维</li><li>透气网面拼接</li><li>多色可选</li><li>尺码：S/M/L/XL/XXL</li></ul>',
 1, 76, 320, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0);

-- -------------------------------------------
-- 3.4 规格模板表 product_spec
-- 为6个商品创建规格维度
-- -------------------------------------------
INSERT INTO `product_spec` (`id`, `product_id`, `name`) VALUES
-- 商品4001 iPhone 15 Pro Max: 颜色 + 存储
(10001, 4001, '颜色'),
(10002, 4001, '存储'),
-- 商品4002 Mate 60 Pro: 颜色 + 存储
(10003, 4002, '颜色'),
(10004, 4002, '存储'),
-- 商品4003 小米14 Ultra: 颜色 + 存储
(10005, 4003, '颜色'),
(10006, 4003, '存储'),
-- 商品4004 MacBook Pro: 芯片 + 内存
(10007, 4004, '芯片'),
(10008, 4004, '内存'),
-- 商品4008 优衣库T恤: 颜色 + 尺码
(10009, 4008, '颜色'),
(10010, 4008, '尺码'),
-- 商品4010 耐克Air Max 270: 颜色 + 尺码
(10011, 4010, '颜色'),
(10012, 4010, '尺码');

-- -------------------------------------------
-- 3.5 规格值表 product_spec_value
-- 每个规格维度下的具体值
-- -------------------------------------------
INSERT INTO `product_spec_value` (`id`, `spec_id`, `value`) VALUES
-- 商品4001 iPhone 15 Pro Max 颜色值
(20001, 10001, '原色钛金属'),
(20002, 10001, '蓝色钛金属'),
(20003, 10001, '白色钛金属'),
-- 商品4001 iPhone 15 Pro Max 存储值
(20004, 10002, '256GB'),
(20005, 10002, '512GB'),
(20006, 10002, '1TB'),
-- 商品4002 Mate 60 Pro 颜色值
(20007, 10003, '雅丹黑'),
(20008, 10003, '白沙银'),
(20009, 10003, '南糯紫'),
-- 商品4002 Mate 60 Pro 存储值
(20010, 10004, '256GB'),
(20011, 10004, '512GB'),
(20012, 10004, '1TB'),
-- 商品4003 小米14 Ultra 颜色值
(20013, 10005, '黑色'),
(20014, 10005, '白色'),
(20015, 10005, '岩石灰'),
-- 商品4003 小米14 Ultra 存储值
(20016, 10006, '12+256GB'),
(20017, 10006, '16+512GB'),
(20018, 10006, '16+1TB'),
-- 商品4004 MacBook Pro 芯片值
(20019, 10007, 'M3 Pro'),
(20020, 10007, 'M3 Max'),
-- 商品4004 MacBook Pro 内存值
(20021, 10008, '18GB'),
(20022, 10008, '36GB'),
-- 商品4008 优衣库T恤 颜色值
(20023, 10009, '白色'),
(20024, 10009, '黑色'),
(20025, 10009, '灰色'),
-- 商品4008 优衣库T恤 尺码值
(20026, 10010, 'S'),
(20027, 10010, 'M'),
(20028, 10010, 'L'),
(20029, 10010, 'XL'),
(20030, 10010, 'XXL'),
-- 商品4010 耐克Air Max 270 颜色值
(20031, 10011, '黑'),
(20032, 10011, '白'),
(20033, 10011, '红'),
-- 商品4010 耐克Air Max 270 尺码值
(20034, 10012, '39'),
(20035, 10012, '40'),
(20036, 10012, '41'),
(20037, 10012, '42'),
(20038, 10012, '43'),
(20039, 10012, '44');

-- -------------------------------------------
-- 3.6 商品SKU表 product_sku（笛卡尔积SKU，共37个）
-- 每个SKU有真实的价格、原价、库存
-- -------------------------------------------
INSERT INTO `product_sku` (`id`, `product_id`, `spec_values`, `price`, `original_price`, `stock`, `image`, `version`, `status`, `create_time`, `update_time`, `deleted`) VALUES
-- ===== 商品4001 iPhone 15 Pro Max：3色 × 3存储 = 9个SKU =====
(50001, 4001, '{"颜色":"原色钛金属","存储":"256GB"}', 9999.00, 10999.00, 199, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-Natural-256', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(50002, 4001, '{"颜色":"原色钛金属","存储":"512GB"}', 11499.00, 12499.00, 120, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-Natural-512', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),
(50003, 4001, '{"颜色":"原色钛金属","存储":"1TB"}', 12999.00, 13999.00, 50, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-Natural-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),
(50004, 4001, '{"颜色":"蓝色钛金属","存储":"256GB"}', 9999.00, 10999.00, 180, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-Blue-256', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),
(50005, 4001, '{"颜色":"蓝色钛金属","存储":"512GB"}', 11499.00, 12499.00, 100, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-Blue-512', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),
(50006, 4001, '{"颜色":"蓝色钛金属","存储":"1TB"}', 12999.00, 13999.00, 60, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-Blue-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),
(50007, 4001, '{"颜色":"白色钛金属","存储":"256GB"}', 9999.00, 10999.00, 150, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-White-256', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),
(50008, 4001, '{"颜色":"白色钛金属","存储":"512GB"}', 11499.00, 12499.00, 80, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-White-512', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),
(50009, 4001, '{"颜色":"白色钛金属","存储":"1TB"}', 12999.00, 13999.00, 40, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-White-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), 0),

-- ===== 商品4002 Mate 60 Pro：3色 × 3存储 = 9个SKU =====
(50010, 4002, '{"颜色":"雅丹黑","存储":"256GB"}', 6499.00, 6999.00, 299, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Black-256', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(50011, 4002, '{"颜色":"雅丹黑","存储":"512GB"}', 7499.00, 7999.00, 200, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Black-512', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),
(50012, 4002, '{"颜色":"雅丹黑","存储":"1TB"}', 8499.00, 8999.00, 80, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Black-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),
(50013, 4002, '{"颜色":"白沙银","存储":"256GB"}', 6499.00, 6999.00, 250, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Silver-256', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),
(50014, 4002, '{"颜色":"白沙银","存储":"512GB"}', 7499.00, 7999.00, 150, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Silver-512', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),
(50015, 4002, '{"颜色":"白沙银","存储":"1TB"}', 8499.00, 8999.00, 60, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Silver-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),
(50016, 4002, '{"颜色":"南糯紫","存储":"256GB"}', 6499.00, 6999.00, 180, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Purple-256', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),
(50017, 4002, '{"颜色":"南糯紫","存储":"512GB"}', 7499.00, 7999.00, 100, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Purple-512', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),
(50018, 4002, '{"颜色":"南糯紫","存储":"1TB"}', 8499.00, 8999.00, 50, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-Purple-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 55 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY), 0),

-- ===== 商品4003 小米14 Ultra：3色 × 3存储 = 9个SKU =====
-- 注意：SKU 50019 已被秒杀活动9001扣减1个库存（原350→349），秒杀独立库存不占用此库存
(50019, 4003, '{"颜色":"黑色","存储":"12+256GB"}', 5999.00, 6499.00, 349, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-Black-256', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(50020, 4003, '{"颜色":"黑色","存储":"16+512GB"}', 6499.00, 6999.00, 200, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-Black-512', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),
(50021, 4003, '{"颜色":"黑色","存储":"16+1TB"}', 7499.00, 7999.00, 80, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-Black-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),
(50022, 4003, '{"颜色":"白色","存储":"12+256GB"}', 5999.00, 6499.00, 280, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-White-256', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),
(50023, 4003, '{"颜色":"白色","存储":"16+512GB"}', 6499.00, 6999.00, 160, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-White-512', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),
(50024, 4003, '{"颜色":"白色","存储":"16+1TB"}', 7499.00, 7999.00, 60, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-White-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),
(50025, 4003, '{"颜色":"岩石灰","存储":"12+256GB"}', 5999.00, 6499.00, 200, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-Gray-256', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),
(50026, 4003, '{"颜色":"岩石灰","存储":"16+512GB"}', 6499.00, 6999.00, 120, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-Gray-512', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),
(50027, 4003, '{"颜色":"岩石灰","存储":"16+1TB"}', 7499.00, 7999.00, 50, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-Gray-1TB', 0, 1, DATE_SUB(NOW(), INTERVAL 50 DAY), DATE_SUB(NOW(), INTERVAL 50 DAY), 0),

-- ===== 商品4004 MacBook Pro：2芯片 × 2内存 = 4个SKU =====
-- SKU 50028 已被订单10002扣减1个库存（原101→100）
(50028, 4004, '{"芯片":"M3 Pro","内存":"18GB"}', 14999.00, 16499.00, 99, 'https://dummyimage.com/800x800/E4393C/fff&text=MBP-M3Pro-18G', 0, 1, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
(50029, 4004, '{"芯片":"M3 Pro","内存":"36GB"}', 17999.00, 19499.00, 50, 'https://dummyimage.com/800x800/E4393C/fff&text=MBP-M3Pro-36G', 0, 1, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), 0),
(50030, 4004, '{"芯片":"M3 Max","内存":"18GB"}', 19999.00, 21499.00, 30, 'https://dummyimage.com/800x800/E4393C/fff&text=MBP-M3Max-18G', 0, 1, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), 0),
(50031, 4004, '{"芯片":"M3 Max","内存":"36GB"}', 22999.00, 24499.00, 20, 'https://dummyimage.com/800x800/E4393C/fff&text=MBP-M3Max-36G', 0, 1, DATE_SUB(NOW(), INTERVAL 45 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), 0),

-- ===== 商品4008 优衣库T恤：3色 × 5尺码 = 15个SKU =====
-- SKU 50033 已被订单10003扣减3个库存（原803→800）
(50032, 4008, '{"颜色":"白色","尺码":"S"}', 59.00, 79.00, 500, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-White-S', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50033, 4008, '{"颜色":"白色","尺码":"M"}', 59.00, 79.00, 797, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-White-M', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
(50034, 4008, '{"颜色":"白色","尺码":"L"}', 59.00, 79.00, 600, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-White-L', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50035, 4008, '{"颜色":"白色","尺码":"XL"}', 59.00, 79.00, 400, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-White-XL', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50036, 4008, '{"颜色":"白色","尺码":"XXL"}', 59.00, 79.00, 200, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-White-XXL', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50037, 4008, '{"颜色":"黑色","尺码":"S"}', 59.00, 79.00, 450, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Black-S', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50038, 4008, '{"颜色":"黑色","尺码":"M"}', 59.00, 79.00, 700, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Black-M', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50039, 4008, '{"颜色":"黑色","尺码":"L"}', 59.00, 79.00, 550, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Black-L', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50040, 4008, '{"颜色":"黑色","尺码":"XL"}', 59.00, 79.00, 350, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Black-XL', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50041, 4008, '{"颜色":"黑色","尺码":"XXL"}', 59.00, 79.00, 180, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Black-XXL', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50042, 4008, '{"颜色":"灰色","尺码":"S"}', 59.00, 79.00, 400, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Gray-S', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50043, 4008, '{"颜色":"灰色","尺码":"M"}', 59.00, 79.00, 650, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Gray-M', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50044, 4008, '{"颜色":"灰色","尺码":"L"}', 59.00, 79.00, 500, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Gray-L', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50045, 4008, '{"颜色":"灰色","尺码":"XL"}', 59.00, 79.00, 300, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Gray-XL', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),
(50046, 4008, '{"颜色":"灰色","尺码":"XXL"}', 59.00, 79.00, 150, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-Gray-XXL', 0, 1, DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), 0),

-- ===== 商品4010 耐克Air Max 270：3色 × 6尺码 = 18个SKU =====
(50047, 4010, '{"颜色":"黑","尺码":"39"}', 899.00, 1099.00, 120, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Black-39', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50048, 4010, '{"颜色":"黑","尺码":"40"}', 899.00, 1099.00, 150, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Black-40', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50049, 4010, '{"颜色":"黑","尺码":"41"}', 899.00, 1099.00, 200, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Black-41', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50050, 4010, '{"颜色":"黑","尺码":"42"}', 899.00, 1099.00, 180, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Black-42', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50051, 4010, '{"颜色":"黑","尺码":"43"}', 899.00, 1099.00, 100, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Black-43', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50052, 4010, '{"颜色":"黑","尺码":"44"}', 899.00, 1099.00, 80, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Black-44', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50053, 4010, '{"颜色":"白","尺码":"39"}', 899.00, 1099.00, 100, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-White-39', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50054, 4010, '{"颜色":"白","尺码":"40"}', 899.00, 1099.00, 130, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-White-40', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50055, 4010, '{"颜色":"白","尺码":"41"}', 899.00, 1099.00, 160, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-White-41', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50056, 4010, '{"颜色":"白","尺码":"42"}', 899.00, 1099.00, 140, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-White-42', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50057, 4010, '{"颜色":"白","尺码":"43"}', 899.00, 1099.00, 90, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-White-43', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50058, 4010, '{"颜色":"白","尺码":"44"}', 899.00, 1099.00, 70, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-White-44', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50059, 4010, '{"颜色":"红","尺码":"39"}', 899.00, 1099.00, 80, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Red-39', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50060, 4010, '{"颜色":"红","尺码":"40"}', 899.00, 1099.00, 100, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Red-40', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50061, 4010, '{"颜色":"红","尺码":"41"}', 899.00, 1099.00, 120, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Red-41', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50062, 4010, '{"颜色":"红","尺码":"42"}', 899.00, 1099.00, 110, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Red-42', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50063, 4010, '{"颜色":"红","尺码":"43"}', 899.00, 1099.00, 60, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Red-43', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),
(50064, 4010, '{"颜色":"红","尺码":"44"}', 899.00, 1099.00, 50, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-Red-44', 0, 1, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0),

-- ===== 无规格商品：每个商品1个默认SKU =====
-- SKU 50065 已被订单10001扣减1个库存（原201→200）；SKU 50069 已被订单10001扣减1个库存（原151→150）
(50065, 4005, '{"配置":"酷睿Ultra7/32GB/1TB"}', 5999.00, 6799.00, 79, 'https://dummyimage.com/800x800/E4393C/fff&text=XiaoXinPro16', 0, 1, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 0),
(50066, 4006, '{"规格":"3匹柜机/新一级能效"}', 5999.00, 7999.00, 120, 'https://dummyimage.com/800x800/E4393C/fff&text=MideaAC', 0, 1, DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY), 0),
(50067, 4007, '{"规格":"470升/十字对开门"}', 4299.00, 5299.00, 90, 'https://dummyimage.com/800x800/E4393C/fff&text=HaierFridge', 0, 1, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY), 0),
(50068, 4009, '{"颜色":"米色","尺码":"M"}', 299.00, 399.00, 300, 'https://dummyimage.com/800x800/E4393C/fff&text=UNIQLOCardigan', 0, 1, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), 0),
(50069, 4011, '{"规格":"50ml"}', 980.00, 1080.00, 499, 'https://dummyimage.com/800x800/E4393C/fff&text=LancomeSerum', 0, 1, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
-- SKU 50070 已被订单10001扣减1个库存（原151→150）
(50070, 4012, '{"颜色":"星光色","存储":"256GB"}', 4799.00, 5299.00, 149, 'https://dummyimage.com/800x800/E4393C/fff&text=iPadAir5', 0, 1, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY), 0),
-- SKU 50071 已被订单10002扣减1个库存（原61→60）
(50071, 4013, '{"规格":"27英寸/4K/Type-C"}', 3999.00, 4599.00, 59, 'https://dummyimage.com/800x800/E4393C/fff&text=DellU2723QE', 0, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 0),
-- SKU 50072 已被订单10005退款，库存已回滚（原199→200）
(50072, 4014, '{"规格":"1.75L/静音免滤"}', 399.00, 599.00, 200, 'https://dummyimage.com/800x800/E4393C/fff&text=MideaBlender', 0, 1, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), 0),
(50073, 4015, '{"颜色":"黑","尺码":"L"}', 259.00, 349.00, 400, 'https://dummyimage.com/800x800/E4393C/fff&text=NikeDriFit', 0, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 0);

-- -------------------------------------------
-- 3.7 商品图片表 product_image（每个商品3-5张图，共60条）
-- -------------------------------------------
INSERT INTO `product_image` (`id`, `product_id`, `url`, `sort`) VALUES
-- 商品4001 iPhone 15 Pro Max（5张图）
(60001, 4001, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-1', 1),
(60002, 4001, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-2', 2),
(60003, 4001, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-3', 3),
(60004, 4001, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-4', 4),
(60005, 4001, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15-5', 5),
-- 商品4002 Mate 60 Pro（4张图）
(60006, 4002, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-1', 1),
(60007, 4002, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-2', 2),
(60008, 4002, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-3', 3),
(60009, 4002, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60-4', 4),
-- 商品4003 小米14 Ultra（5张图）
(60010, 4003, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-1', 1),
(60011, 4003, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-2', 2),
(60012, 4003, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-3', 3),
(60013, 4003, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-4', 4),
(60014, 4003, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14-5', 5),
-- 商品4004 MacBook Pro（4张图）
(60015, 4004, 'https://dummyimage.com/800x800/E4393C/fff&text=MBP-1', 1),
(60016, 4004, 'https://dummyimage.com/800x800/E4393C/fff&text=MBP-2', 2),
(60017, 4004, 'https://dummyimage.com/800x800/E4393C/fff&text=MBP-3', 3),
(60018, 4004, 'https://dummyimage.com/800x800/E4393C/fff&text=MBP-4', 4),
-- 商品4005 联想小新Pro16（4张图）
(60019, 4005, 'https://dummyimage.com/800x800/E4393C/fff&text=XiaoXin-1', 1),
(60020, 4005, 'https://dummyimage.com/800x800/E4393C/fff&text=XiaoXin-2', 2),
(60021, 4005, 'https://dummyimage.com/800x800/E4393C/fff&text=XiaoXin-3', 3),
(60022, 4005, 'https://dummyimage.com/800x800/E4393C/fff&text=XiaoXin-4', 4),
-- 商品4006 美的空调（4张图）
(60023, 4006, 'https://dummyimage.com/800x800/E4393C/fff&text=MideaAC-1', 1),
(60024, 4006, 'https://dummyimage.com/800x800/E4393C/fff&text=MideaAC-2', 2),
(60025, 4006, 'https://dummyimage.com/800x800/E4393C/fff&text=MideaAC-3', 3),
(60026, 4006, 'https://dummyimage.com/800x800/E4393C/fff&text=MideaAC-4', 4),
-- 商品4007 海尔冰箱（3张图）
(60027, 4007, 'https://dummyimage.com/800x800/E4393C/fff&text=HaierFridge-1', 1),
(60028, 4007, 'https://dummyimage.com/800x800/E4393C/fff&text=HaierFridge-2', 2),
(60029, 4007, 'https://dummyimage.com/800x800/E4393C/fff&text=HaierFridge-3', 3),
-- 商品4008 优衣库T恤（3张图）
(60030, 4008, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-1', 1),
(60031, 4008, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-2', 2),
(60032, 4008, 'https://dummyimage.com/800x800/E4393C/fff&text=UT-3', 3),
-- 商品4009 优衣库针织开衫（3张图）
(60033, 4009, 'https://dummyimage.com/800x800/E4393C/fff&text=UC-1', 1),
(60034, 4009, 'https://dummyimage.com/800x800/E4393C/fff&text=UC-2', 2),
(60035, 4009, 'https://dummyimage.com/800x800/E4393C/fff&text=UC-3', 3),
-- 商品4010 耐克Air Max 270（4张图）
(60036, 4010, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-1', 1),
(60037, 4010, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-2', 2),
(60038, 4010, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-3', 3),
(60039, 4010, 'https://dummyimage.com/800x800/E4393C/fff&text=NAM-4', 4),
-- 商品4011 兰蔻小黑瓶（3张图）
(60040, 4011, 'https://dummyimage.com/800x800/E4393C/fff&text=LS-1', 1),
(60041, 4011, 'https://dummyimage.com/800x800/E4393C/fff&text=LS-2', 2),
(60042, 4011, 'https://dummyimage.com/800x800/E4393C/fff&text=LS-3', 3),
-- 商品4012 iPad Air 5（4张图）
(60043, 4012, 'https://dummyimage.com/800x800/E4393C/fff&text=iPad-1', 1),
(60044, 4012, 'https://dummyimage.com/800x800/E4393C/fff&text=iPad-2', 2),
(60045, 4012, 'https://dummyimage.com/800x800/E4393C/fff&text=iPad-3', 3),
(60046, 4012, 'https://dummyimage.com/800x800/E4393C/fff&text=iPad-4', 4),
-- 商品4013 戴尔4K显示器（4张图）
(60047, 4013, 'https://dummyimage.com/800x800/E4393C/fff&text=Dell-1', 1),
(60048, 4013, 'https://dummyimage.com/800x800/E4393C/fff&text=Dell-2', 2),
(60049, 4013, 'https://dummyimage.com/800x800/E4393C/fff&text=Dell-3', 3),
(60050, 4013, 'https://dummyimage.com/800x800/E4393C/fff&text=Dell-4', 4),
-- 商品4014 美的破壁机（3张图）
(60051, 4014, 'https://dummyimage.com/800x800/E4393C/fff&text=MB-1', 1),
(60052, 4014, 'https://dummyimage.com/800x800/E4393C/fff&text=MB-2', 2),
(60053, 4014, 'https://dummyimage.com/800x800/E4393C/fff&text=MB-3', 3),
-- 商品4015 耐克速干T恤（3张图）
(60054, 4015, 'https://dummyimage.com/800x800/E4393C/fff&text=NDT-1', 1),
(60055, 4015, 'https://dummyimage.com/800x800/E4393C/fff&text=NDT-2', 2),
(60056, 4015, 'https://dummyimage.com/800x800/E4393C/fff&text=NDT-3', 3);

-- -------------------------------------------
-- 3.8 商品评价表 product_comment（12条评价，含追评和匿名评价）
-- 闭环：订单完成(is_reviewed=1)→初始评价(comment_type=0)→追评(comment_type=1, parent_id关联)
-- score: 4-5=好评 3=中评 1-2=差评
-- is_anonymous: 0否 1是（匿名显示"匿名用户"）
-- comment_type: 0初始评价 1追评（parent_id指向初始评价ID）
-- -------------------------------------------
INSERT INTO `product_comment` (`id`, `product_id`, `order_item_id`, `user_id`, `content`, `images`, `score`, `reply`, `is_anonymous`, `comment_type`, `parent_id`, `create_time`, `update_time`, `deleted`) VALUES
-- ===== 订单10001的评价（订单已完成，is_reviewed=1） =====
-- 商品4001 iPhone 15 Pro Max 初始评价（好评）
(7001, 4001, 8001, 1001, '手机用了一周了，钛金属质感真的很好，拍照效果也很棒，A17 Pro芯片运行非常流畅，非常满意！', '["https://dummyimage.com/100x100/333/fff&text=Review1-1","https://dummyimage.com/100x100/333/fff&text=Review1-2"]', 5, '感谢您的支持与认可！祝您使用愉快~', 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),
-- 商品4001 iPhone 15 Pro Max 追评（关联初始评价7001）
(7002, 4001, 8001, 1001, '用了一个月后来追评：电池续航真的很给力，重度使用一天没问题。相机夜景模式提升明显，非常推荐！', NULL, 5, NULL, 0, 1, 7001, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),
-- 商品4012 iPad Air 5 初始评价（好评，匿名评价）
(7003, 4012, 8006, 1001, 'iPad Air 5配Apple Pencil画画很流畅，M1芯片性能足够，日常记笔记看视频都很棒。', NULL, 5, NULL, 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),

-- ===== 订单10002的评价（订单运输中，暂不能评价） =====

-- ===== 订单10006秒杀订单的评价（订单已完成，is_reviewed=1） =====
-- 商品4003 小米14 Ultra 初始评价（好评）
(7004, 4003, 8008, 1003, '秒杀价买到小米14 Ultra真的太划算了！徕卡镜头拍照确实好看，色彩很有质感。系统流畅度也不错。', '["https://dummyimage.com/100x100/333/fff&text=Review4-1"]', 5, '感谢您参与秒杀活动！小米14 Ultra确实性价比很高~', 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),

-- ===== 其他历史评价（丰富评价数据，用于商品详情页评价展示） =====
-- 说明：这些评价的 order_item_id 为虚拟值（8009-8016），不关联实际订单
--       跨库无外键约束，仅用于商品详情页评价列表展示，不影响新评价提交流程
(7005, 4002, 8009, 1002, '华为回归之作，卫星通话功能太实用了！信号比之前用的手机好很多，昆仑玻璃也很耐摔。', '["https://dummyimage.com/100x100/333/fff&text=Review5-1"]', 5, '感谢您对华为产品的认可！', 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), 0),
(7006, 4004, 8010, 1002, 'MacBook Pro性能没得说，M3 Pro芯片剪辑4K视频毫无压力，屏幕色彩也很准确，设计工作必备。', NULL, 5, NULL, 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 0),
(7007, 4008, 8011, 1001, '纯棉T恤穿着很舒服，洗了几次没有变形褪色，性价比很高，又回购了两件。', '["https://dummyimage.com/100x100/333/fff&text=Review7-1","https://dummyimage.com/100x100/333/fff&text=Review7-2","https://dummyimage.com/100x100/333/fff&text=Review7-3"]', 5, '感谢您的回购支持！优衣库品质值得信赖~', 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),
(7008, 4010, 8012, 1003, '鞋子穿着很舒服，气垫缓震效果明显，跑步和日常穿都很合适。就是白色款容易脏。', NULL, 4, NULL, 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), 0),
(7009, 4011, 8013, 1004, '兰蔻小黑瓶用了两周，皮肤确实变细腻了，吸收很快不油腻，会继续回购。', '["https://dummyimage.com/100x100/333/fff&text=Review9-1"]', 5, '感谢您的分享！兰蔻小黑瓶是经典好物~', 1, 0, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), 0),
(7010, 4005, 8014, 1004, '笔记本轻薄便携，性能也不错，日常办公和轻度剪辑完全够用。就是风扇在高负载时声音有点大。', NULL, 4, NULL, 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), 0),
(7011, 4006, 8015, 1002, '空调制冷效果很好，运行声音很小，节能省电模式很实用，APP远程控制方便。', NULL, 5, '感谢您的评价！美的空调品质有保障~', 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 0),
(7012, 4015, 8016, 1004, '速干面料确实有效，运动出汗后很快干爽，版型也不错，值得购买。', NULL, 4, NULL, 0, 0, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0);


-- ============================================
-- 四、shop_cart 库测试数据
-- ============================================
USE shop_cart;

-- -------------------------------------------
-- 4.1 购物车项表 cart_item（5条）
-- 用户1001: 2个商品, 用户1002: 2个商品, 用户1003: 1个商品
-- -------------------------------------------
INSERT INTO `cart_item` (`id`, `user_id`, `product_id`, `sku_id`, `quantity`, `checked`, `create_time`, `update_time`) VALUES
-- 张三的购物车：iPhone 15 Pro Max + 兰蔻小黑瓶
(9001, 1001, 4001, 50001, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9002, 1001, 4011, 50069, 2, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 李四的购物车：华为Mate 60 Pro + 优衣库T恤
(9003, 1002, 4002, 50010, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9004, 1002, 4008, 50033, 3, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 王五的购物车：小米14 Ultra
(9005, 1003, 4003, 50019, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));


-- ============================================
-- 五、shop_order 库测试数据
-- ============================================
USE shop_order;

-- -------------------------------------------
-- 5.1 订单主表 order_info（6个订单，覆盖不同状态和类型）
-- 状态：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款
-- order_type: 1普通订单 2秒杀订单
-- 闭环：满减(promotion_discount) + 优惠券(discount_amount - promotion_discount) → pay_amount
-- 金额计算规则：商品总价 = total_amount；优惠 = discount_amount；实付 = pay_amount
--   - promotion_discount = 满减优惠金额
--   - 优惠券优惠 = discount_amount - promotion_discount
--   - pay_amount = total_amount - discount_amount
-- -------------------------------------------
INSERT INTO `order_info` (`id`, `order_no`, `user_id`, `shop_id`, `total_amount`, `pay_amount`, `freight`, `discount_amount`, `promotion_discount`, `order_type`, `seckill_id`, `is_reviewed`, `status`, `pay_time`, `delivery_time`, `receive_time`, `close_time`, `create_time`, `update_time`, `deleted`) VALUES
-- 订单1：已完成（用户1001，店铺3001，iPhone+iPad）
-- 闭环：满减活动7001(满10000减500) + 优惠券5001(满1000减100)
-- 计算：总价9999+4799=14798 → 满减500 → 券后门槛14298>=1000 → 券减100 → 实付14198
(10001, '2024061500010001', 1001, 3001, 14798.00, 14198.00, 0.00, 600.00, 500.00, 1, NULL, 1, 5,
 DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), NULL,
 DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), 0),

-- 订单2：运输中（用户1002，店铺3001，MacBook+Dell显示器）
-- 闭环：无满减无优惠券（MacBook不在满减指定商品范围，总价不满10000）
-- 计算：总价14999+3999=18998 → 无优惠 → 实付18998
(10002, '2024061600010002', 1002, 3001, 18998.00, 18998.00, 0.00, 0.00, 0.00, 1, NULL, 0, 3,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),

-- 订单3：待发货（用户1001，店铺3002，优衣库T恤×3）
-- 闭环：满减活动7003(满200减30) 无优惠券
-- 计算：总价59×3=177 → 不满200，无优惠 → 实付177
(10003, '2024061700010003', 1001, 3002, 177.00, 177.00, 0.00, 0.00, 0.00, 1, NULL, 0, 2,
 DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0),

-- 订单4：待付款（用户1003，店铺3001，华为Mate60）
-- 闭环：无优惠，等待支付
(10004, '2024061800010004', 1003, 3001, 6499.00, 6499.00, 0.00, 0.00, 0.00, 1, NULL, 0, 0,
 NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), 0),

-- 订单5：已退款（用户1002，店铺3003，美的破壁机）
-- 闭环：退款流程 → 状态7已退款
(10005, '2024061900010005', 1002, 3003, 399.00, 399.00, 0.00, 0.00, 0.00, 1, NULL, 0, 7,
 DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), NULL,
 DATE_SUB(NOW(), INTERVAL 21 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), 0),

-- 订单6：已完成（用户1003，店铺3001，小米14 Ultra秒杀订单）
-- 闭环：秒杀活动9001(秒杀价3999，原价5999) → order_type=2，seckill_id=9001
-- 计算：秒杀价3999 → 实付3999（节省2000）
(10006, '2024062000010006', 1003, 3001, 3999.00, 3999.00, 0.00, 2000.00, 0.00, 2, 9001, 1, 5,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0);

-- -------------------------------------------
-- 5.2 订单明细表 order_item（8条，每个订单1-2个商品）
-- 价格和规格使用快照（下单时的价格，不受后续修改影响）
-- -------------------------------------------
INSERT INTO `order_item` (`id`, `order_id`, `product_id`, `sku_id`, `product_name`, `sku_spec`, `price`, `quantity`, `image`) VALUES
-- 订单1明细：iPhone 15 Pro Max + iPad Air 5
(8001, 10001, 4001, 50001, 'Apple iPhone 15 Pro Max 256GB 原色钛金属', '颜色:原色钛金属, 存储:256GB', 9999.00, 1, 'https://dummyimage.com/800x800/E4393C/fff&text=iPhone15ProMax'),
(8002, 10001, 4012, 50070, 'Apple iPad Air 5 256GB 星光色', '颜色:星光色, 存储:256GB', 4799.00, 1, 'https://dummyimage.com/800x800/E4393C/fff&text=iPadAir5'),
-- 订单2明细：MacBook Pro + Dell显示器
(8003, 10002, 4004, 50028, 'Apple MacBook Pro 14英寸 M3 Pro芯片', '芯片:M3 Pro, 内存:18GB', 14999.00, 1, 'https://dummyimage.com/800x800/E4393C/fff&text=MacBookPro14'),
(8004, 10002, 4013, 50071, '戴尔 U2723QE 27英寸4K显示器', '规格:27英寸/4K/Type-C', 3999.00, 1, 'https://dummyimage.com/800x800/E4393C/fff&text=DellU2723QE'),
-- 订单3明细：优衣库T恤 × 3
(8005, 10003, 4008, 50033, '优衣库 男装 圆领T恤(短袖)', '颜色:白色, 尺码:M', 59.00, 3, 'https://dummyimage.com/800x800/E4393C/fff&text=UNIQLOTshirt'),
-- 订单4明细：华为Mate 60 Pro
(8006, 10004, 4002, 50010, '华为 Mate 60 Pro 512GB 雅丹黑', '颜色:雅丹黑, 存储:256GB', 6499.00, 1, 'https://dummyimage.com/800x800/E4393C/fff&text=Mate60Pro'),
-- 订单5明细：美的破壁机
(8007, 10005, 4014, 50072, '美的 破壁机 静音免滤 智能预约', '规格:1.75L/静音免滤', 399.00, 1, 'https://dummyimage.com/800x800/E4393C/fff&text=MideaBlender'),
-- 订单6明细：小米14 Ultra（秒杀价）
(8008, 10006, 4003, 50019, '小米14 Ultra 16GB+512GB 黑色', '颜色:黑色, 存储:12+256GB', 3999.00, 1, 'https://dummyimage.com/800x800/E4393C/fff&text=Mi14Ultra');

-- -------------------------------------------
-- 5.3 订单地址快照表 order_address（6条）
-- 下单时复制用户的默认收货地址
-- -------------------------------------------
INSERT INTO `order_address` (`id`, `order_id`, `name`, `phone`, `province`, `city`, `district`, `detail`) VALUES
(11001, 10001, '张三', '13800001111', '北京市', '北京市', '朝阳区', '建国路88号SOHO现代城A座1208室'),
(11002, 10002, '李四', '13800002222', '上海市', '上海市', '浦东新区', '陆家嘴环路1000号恒生银行大厦15F'),
(11003, 10003, '张三', '13800001111', '北京市', '北京市', '朝阳区', '建国路88号SOHO现代城A座1208室'),
(11004, 10004, '王五', '13800003333', '广东省', '深圳市', '南山区', '科技园南路16号创维半导体大厦8楼'),
(11005, 10005, '李四', '13800002222', '上海市', '上海市', '浦东新区', '陆家嘴环路1000号恒生银行大厦15F'),
(11006, 10006, '王五', '13800003333', '广东省', '深圳市', '南山区', '科技园南路16号创维半导体大厦8楼');

-- -------------------------------------------
-- 5.4 物流信息表 order_logistics（3条）
-- 运输中和已完成的订单有物流信息
-- -------------------------------------------
INSERT INTO `order_logistics` (`id`, `order_id`, `logistics_no`, `logistics_company`, `status`, `detail`, `create_time`, `update_time`) VALUES
-- 订单1的物流（已签收）
(12001, 10001, 'SF1234567890', '顺丰速运', 3,
 '[{"time":"2024-06-02 10:30:00","desc":"快件已签收，签收人：本人签收"},{"time":"2024-06-02 08:15:00","desc":"快件正在派送中，快递员：王师傅 13800008888"},{"time":"2024-06-01 22:00:00","desc":"快件已到达【北京朝阳营业部】"},{"time":"2024-06-01 08:00:00","desc":"快件已从【深圳中转站】发出"},{"time":"2024-05-31 18:00:00","desc":"顺丰速运已收取快件"}]',
 DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
-- 订单2的物流（运输中）
(12002, 10002, 'JD9876543210', '京东物流', 2,
 '[{"time":"2024-06-15 14:00:00","desc":"快件已到达【上海浦东营业部】，即将派送"},{"time":"2024-06-15 06:00:00","desc":"快件已从【上海中转站】发出"},{"time":"2024-06-14 20:00:00","desc":"快件已到达【上海中转站】"},{"time":"2024-06-14 10:00:00","desc":"快件已从【北京仓库】发出"}]',
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 订单6秒杀订单的物流（运输中，已签收）
(12003, 10006, 'ZT1111222233', '中通快递', 3,
 '[{"time":"2024-06-21 16:00:00","desc":"快件已签收，签收人：本人签收"},{"time":"2024-06-21 09:00:00","desc":"快件正在派送中"},{"time":"2024-06-20 20:00:00","desc":"快件已到达【深圳南山营业部】"}]',
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- -------------------------------------------
-- 5.5 订单状态日志表 order_log（16条）
-- 记录每个订单的状态流转，方便排查订单问题
-- 状态：0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款
-- -------------------------------------------
INSERT INTO `order_log` (`id`, `order_id`, `from_status`, `to_status`, `operator`, `note`, `create_time`) VALUES
-- 订单10001完整闭环：待付款→待发货→运输中→已收货→已完成
(13001, 10001, 0, 2, 'user:1001',  '用户支付成功，等待商家发货',     DATE_SUB(NOW(), INTERVAL 15 DAY)),
(13002, 10001, 2, 3, 'merchant:2001','商家已发货，物流单号SF1234567890', DATE_SUB(NOW(), INTERVAL 14 DAY)),
(13003, 10001, 3, 4, 'user:1001',  '用户确认收货',                   DATE_SUB(NOW(), INTERVAL 10 DAY)),
(13004, 10001, 4, 5, 'system',      '7天自动确认收货，订单完成',       DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 订单10002：待付款→待发货→运输中（进行中）
(13005, 10002, 0, 2, 'user:1002',  '用户支付成功，等待商家发货',     DATE_SUB(NOW(), INTERVAL 3 DAY)),
(13006, 10002, 2, 3, 'merchant:2002','商家已发货，物流单号JD9876543210', DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- 订单10003：待付款→待发货（等待发货）
(13007, 10003, 0, 2, 'user:1001',  '用户支付成功，等待商家发货',     DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 订单10004：待付款（无状态流转日志，等待用户支付）
-- 订单10005退款闭环：待付款→待发货→运输中→已收货→退款中→已退款
(13009, 10005, 0, 2, 'user:1002',  '用户支付成功，等待商家发货',     DATE_SUB(NOW(), INTERVAL 20 DAY)),
(13010, 10005, 2, 3, 'merchant:2001','商家已发货',                     DATE_SUB(NOW(), INTERVAL 19 DAY)),
(13011, 10005, 3, 4, 'user:1002',  '用户确认收货',                   DATE_SUB(NOW(), INTERVAL 15 DAY)),
(13012, 10005, 4, 6, 'user:1002',  '用户申请退款：商品质量问题',     DATE_SUB(NOW(), INTERVAL 12 DAY)),
(13013, 10005, 6, 7, 'admin:1',    '退款审核通过，退款金额399.00元', DATE_SUB(NOW(), INTERVAL 12 DAY)),
-- 订单10006秒杀订单完整闭环：待付款→待发货→运输中→已收货→已完成
(13014, 10006, 0, 2, 'user:1003',  '秒杀订单支付成功',               DATE_SUB(NOW(), INTERVAL 2 DAY)),
(13015, 10006, 2, 3, 'merchant:2001','商家已发货，物流单号ZT1111222233', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(13016, 10006, 3, 4, 'user:1003',  '用户确认收货',                   DATE_SUB(NOW(), INTERVAL 1 DAY)),
(13017, 10006, 4, 5, 'system',      '秒杀订单自动确认收货，订单完成', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- -------------------------------------------
-- 5.6 退款单表 refund_order（1条）
-- 闭环：用户申请退款 → 管理员审核通过 → 退款完成
-- -------------------------------------------
INSERT INTO `refund_order` (`id`, `order_id`, `order_item_id`, `reason`, `amount`, `status`, `audit_note`, `create_time`, `update_time`) VALUES
(14001, 10005, 8007, '商品质量问题：破壁机使用时异响严重，影响正常使用', 399.00, 3, '核实为商品质量问题，同意退款，已原路退回', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY));

-- -------------------------------------------
-- 5.7 undo_log 表（Seata回滚日志，正常运行时无数据）
-- 说明：仅在建表脚本中创建结构，测试数据不需要预置回滚日志
-- -------------------------------------------


-- ============================================
-- 六、shop_payment 库测试数据
-- ============================================
USE shop_payment;

-- -------------------------------------------
-- 6.1 支付记录表 payment_info（6条）
-- 闭环：订单创建→支付记录生成→支付成功/退款
-- pay_status：0待支付 1已支付 2已关闭 3已退款
-- -------------------------------------------
INSERT INTO `payment_info` (`id`, `payment_no`, `order_no`, `user_id`, `amount`, `pay_type`, `pay_status`, `pay_time`, `callback_time`, `create_time`, `update_time`) VALUES
-- 订单1支付：已支付（模拟支付），金额14198（满减+优惠券后实付）
(15001, 'PAY2024061500010001', '2024061500010001', 1001, 14198.00, 1, 1,
 DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY),
 DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
-- 订单2支付：已支付（支付宝），金额18998
(15002, 'PAY2024061600010002', '2024061600010002', 1002, 18998.00, 3, 1,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY),
 DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 订单3支付：已支付（微信），金额177
(15003, 'PAY2024061700010003', '2024061700010003', 1001, 177.00, 2, 1,
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 订单4支付：待支付（用户未完成支付），金额6499
(15004, 'PAY2024061800010004', '2024061800010004', 1003, 6499.00, 1, 0,
 NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR)),
-- 订单5支付：已退款（先支付后退款），金额399
(15005, 'PAY2024061900010005', '2024061900010005', 1002, 399.00, 1, 3,
 DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY),
 DATE_SUB(NOW(), INTERVAL 21 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
-- 订单6秒杀订单支付：已支付（模拟支付），金额3999（秒杀价）
(15006, 'PAY2024062000010006', '2024062000010006', 1003, 3999.00, 1, 1,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- -------------------------------------------
-- 6.2 支付回调日志表 payment_callback（5条）
-- 记录第三方支付平台的回调数据，待支付的订单4无回调
-- out_trade_no 为第三方交易号，唯一索引保证幂等
-- -------------------------------------------
INSERT INTO `payment_callback` (`id`, `payment_id`, `channel`, `callback_data`, `out_trade_no`, `create_time`) VALUES
-- 订单1支付回调（模拟支付渠道）
(16001, 15001, 'mock',
 '{"out_trade_no":"MOCK20240615001","trade_no":"TRADE202406150001","total_amount":"14198.00","trade_status":"TRADE_SUCCESS","buyer_id":"BUYER_1001"}',
 'MOCK20240615001', DATE_SUB(NOW(), INTERVAL 15 DAY)),
-- 订单2支付回调（支付宝）
(16002, 15002, 'alipay',
 '{"out_trade_no":"ALI20240616002","trade_no":"2024061622001406150002","total_amount":"18998.00","trade_status":"TRADE_SUCCESS","buyer_logon_id":"138****2222"}',
 'ALI20240616002', DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 订单3支付回调（微信）
(16003, 15003, 'wechat',
 '{"out_trade_no":"WX20240617003","transaction_id":"4200002124202406170033","total_fee":"17700","trade_type":"JSAPI","trade_state":"SUCCESS"}',
 'WX20240617003', DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 订单5退款回调（模拟支付渠道，退款成功）
(16004, 15005, 'mock',
 '{"out_trade_no":"MOCK20240619005","trade_no":"TRADE202406190005","refund_no":"REF20240619005","total_amount":"399.00","refund_amount":"399.00","trade_status":"REFUND_SUCCESS"}',
 'MOCK20240619005', DATE_SUB(NOW(), INTERVAL 12 DAY)),
-- 订单6秒杀订单支付回调（模拟支付渠道）
(16005, 15006, 'mock',
 '{"out_trade_no":"MOCK20240620006","trade_no":"TRADE202406200006","total_amount":"3999.00","trade_status":"TRADE_SUCCESS","buyer_id":"BUYER_1003"}',
 'MOCK20240620006', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- -------------------------------------------
-- 6.3 undo_log 表（Seata回滚日志，正常运行时无数据）
-- 说明：仅在建表脚本中创建结构，测试数据不需要预置回滚日志
-- -------------------------------------------

-- ============================================
-- 测试数据初始化完成
-- 业务闭环验证清单：
--   1. 用户闭环：5个用户→4个账户→8个地址→10个收藏→15个足迹→6张优惠券→8条通知
--   2. 商家闭环：3个商家→3个资质→3个结算账户→3个店铺→2条结算流水→2条提现
--   3. 商品闭环：3级分类→10个品牌→15个商品→规格/SKU/图片→12条评价(含追评和匿名)
--   4. 订单闭环：6个订单(普通/秒杀/退款)→8条明细→6个地址快照→3条物流→16条状态日志
--   5. 秒杀闭环：2个秒杀活动→订单10006使用秒杀价3999→支付完成
--   6. 退款闭环：订单10005申请退款→管理员审核通过→退款完成→支付记录状态3
--   7. 营销闭环：4个满减活动→5个优惠券模板→6张用户优惠券→订单10001使用满减+优惠券
--   8. 统计闭环：商品sales/view_count字段→推荐系统排序依据
-- ============================================
