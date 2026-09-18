-- H2数据库建表脚本（测试专用）
-- 小白理解：这是给H2内存数据库用的建表语句，和MySQL的建表语句略有不同。
-- H2在MySQL兼容模式下支持大部分MySQL语法，但不支持COMMENT、ENGINE、CHARSET等。
-- 这个脚本只在测试运行时执行，创建表后就往里面插测试数据。

-- 订单主表
CREATE TABLE IF NOT EXISTS order_info (
    id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    pay_amount DECIMAL(12,2) NOT NULL,
    freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    promotion_discount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    order_type TINYINT NOT NULL DEFAULT 1,
    seckill_id BIGINT DEFAULT NULL,
    is_reviewed TINYINT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0,
    pay_time DATETIME DEFAULT NULL,
    delivery_time DATETIME DEFAULT NULL,
    receive_time DATETIME DEFAULT NULL,
    finish_time DATETIME DEFAULT NULL,
    cancel_time DATETIME DEFAULT NULL,
    remark VARCHAR(200) DEFAULT NULL,
    cancel_reason VARCHAR(200) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 测试环境允许 update_time 为 null：生产环境有 MetaObjectHandler 自动填充，
    -- 但 H2 切片测试没装配 Spring 容器，自动填充不生效，逻辑删除会传 null，所以这里放开约束
    update_time DATETIME DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE (order_no)
);

-- 订单明细表（B-I-01 集成测试新增）
CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    product_name VARCHAR(200) DEFAULT NULL,
    sku_spec VARCHAR(200) DEFAULT NULL,
    product_image VARCHAR(500) DEFAULT NULL,
    price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 订单地址快照表（B-I-01 集成测试新增）
CREATE TABLE IF NOT EXISTS order_address (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    province VARCHAR(50) DEFAULT NULL,
    city VARCHAR(50) DEFAULT NULL,
    district VARCHAR(50) DEFAULT NULL,
    detail VARCHAR(200) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 订单状态日志表（B-I-01 集成测试新增）
CREATE TABLE IF NOT EXISTS order_log (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    from_status TINYINT DEFAULT NULL,
    to_status TINYINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    operator_id BIGINT DEFAULT NULL,
    operator_type TINYINT DEFAULT NULL,
    note VARCHAR(200) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 物流信息表（B-I-01 集成测试新增）
-- detail 字段存 JSON 格式的物流轨迹，用 VARCHAR 存储即可
CREATE TABLE IF NOT EXISTS order_logistics (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    logistics_no VARCHAR(50) DEFAULT NULL,
    logistics_company VARCHAR(50) DEFAULT NULL,
    detail VARCHAR(2000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
