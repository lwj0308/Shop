# Shop 商城项目 - 数据库设计文档

> 本文档是数据库设计的核心文档，遵循 `database-design` skill 规范。
> 生成依据：项目页面 UI、用户流程、核心功能 + 现有 SQL 建表脚本 + 实体类交叉校验。

---

## 一、业务对象梳理

根据项目页面 UI（web-user / web-merchant / web-admin）、用户流程、核心功能，梳理出 8 大业务域、30+ 核心对象。

| 业务域 | 业务对象 | 业务场景 | 所在库 |
|--------|---------|---------|--------|
| **用户域** | user（用户） | 注册/登录/个人中心/下单/评价 | shop_user |
| | user_account（用户账户） | 余额/积分管理，钱包充值消费 | shop_user |
| | user_address（收货地址） | 下单选择地址，多地址管理 | shop_user |
| | user_favorite（收藏） | 收藏商品，商品列表红心 | shop_user |
| | user_footprint（浏览足迹） | 记录浏览历史，猜你喜欢推荐 | shop_user |
| | user_login_log（登录日志） | 登录安全审计 | shop_user |
| | notification（消息通知） | 订单/支付/退款/审核通知（统一三端） | shop_user |
| | user_coupon（用户优惠券） | 我的优惠券，下单核销 | shop_user |
| **商家域** | merchant（商家） | 商家入驻/登录/经营 | shop_merchant |
| | merchant_qualification（资质） | 入驻审核，营业执照 | shop_merchant |
| | merchant_settlement（结算账户） | 银行卡/余额/冻结资金，提现 | shop_merchant |
| | shop（店铺） | 一商家多店铺，店铺主页 | shop_merchant |
| | settlement_record（结算流水） | 订单完成结算，平台抽成 | shop_merchant |
| | withdraw_order（提现申请） | 商家提现，管理员审核 | shop_merchant |
| **商品域** | category（分类） | 商品分类树，分类页筛选 | shop_product |
| | brand（品牌） | 品牌筛选，品牌专区 | shop_product |
| | product（商品SPU） | 商品详情，列表展示 | shop_product |
| | product_spec（规格维度） | 颜色/尺码等规格定义 | shop_product |
| | product_spec_value（规格值） | 红色/XL等具体值 | shop_product |
| | product_sku（商品SKU） | 最小库存单位，购买/扣库存 | shop_product |
| | product_image（商品图片） | 商品图集展示 | shop_product |
| | product_comment（商品评价） | 评价/追评/商家回复 | shop_product |
| **购物车域** | cart_item（购物车项） | 加购物车，结算勾选 | shop_cart |
| **订单域** | order_info（订单主表） | 下单/支付/发货/收货/完成 | shop_order |
| | order_item（订单明细） | 订单商品快照 | shop_order |
| | order_address（地址快照） | 下单时地址固化 | shop_order |
| | order_logistics（物流） | 物流单号/轨迹 | shop_order |
| | order_log（状态日志） | 订单状态流转追踪 | shop_order |
| | refund_order（退款单） | 退款申请/审核/退款 | shop_order |
| **支付域** | payment_info（支付记录） | 发起支付/支付回调 | shop_payment |
| | payment_callback（回调日志） | 第三方回调留痕，幂等 | shop_payment |
| **营销域** | coupon（优惠券模板） | 创建优惠券/领取/核销 | shop_marketing |
| | promotion（满减活动） | 满减优惠，下单计算 | shop_marketing |
| | promotion_product（活动商品关联） | 指定商品参与满减 | shop_marketing |
| **秒杀域** | seckill_activity（秒杀活动） | 限时秒杀，Redis原子扣库存 | shop_seckill |
| **管理后台域** | admin_user（管理员） | 后台登录/操作 | shop_admin |
| | admin_role（角色） | RBAC角色定义 | shop_admin |
| | admin_permission（权限菜单） | 菜单/按钮权限树 | shop_admin |
| | admin_user_role（管理员角色） | N:N中间表 | shop_admin |
| | admin_role_permission（角色权限） | N:N中间表 | shop_admin |
| | admin_dept（部门） | 数据权限范围控制 | shop_admin |
| | admin_operation_log（操作日志） | 增删改审计 | shop_admin |
| | admin_login_log（登录日志） | 后台登录审计 | shop_admin |
| | admin_security_event（安全事件） | 异常安全事件追踪 | shop_admin |
| | admin_banner（Banner） | 首页轮播图 | shop_admin |
| | admin_notice（公告） | 系统公告 | shop_admin |

---

## 二、对象关系确认

### 2.1 同库关系（物理同库，逻辑外键）

| 关系 | 基数 | 关联字段 | 所在表 |
|------|------|---------|--------|
| user ↔ user_account | 1:1 | user_id | user_account |
| user → user_address | 1:N | user_id | user_address |
| user → user_favorite | 1:N | user_id | user_favorite |
| user → user_footprint | 1:N | user_id | user_footprint |
| user → user_login_log | 1:N | user_id | user_login_log |
| user → user_coupon | 1:N | user_id | user_coupon |
| merchant → shop | 1:N | merchant_id | shop |
| merchant → merchant_qualification | 1:N | merchant_id | merchant_qualification |
| merchant ↔ merchant_settlement | 1:1 | merchant_id | merchant_settlement |
| merchant → settlement_record | 1:N | merchant_id | settlement_record |
| merchant → withdraw_order | 1:N | merchant_id | withdraw_order |
| category → category | 1:N（自关联） | parent_id | category |
| shop → product | 1:N | shop_id | product |
| brand → product | 1:N | brand_id | product |
| category → product | 1:N | category_id | product |
| product → product_sku | 1:N | product_id | product_sku |
| product → product_spec | 1:N | product_id | product_spec |
| product_spec → product_spec_value | 1:N | spec_id | product_spec_value |
| product → product_image | 1:N | product_id | product_image |
| product → product_comment | 1:N | product_id | product_comment |
| order_info → order_item | 1:N | order_id | order_item |
| order_info ↔ order_address | 1:1 | order_id | order_address |
| order_info ↔ order_logistics | 1:1 | order_id | order_logistics |
| order_info → order_log | 1:N | order_id | order_log |
| order_info → refund_order | 1:N | order_id | refund_order |
| payment_info → payment_callback | 1:N | payment_id | payment_callback |
| promotion → promotion_product | 1:N | promotion_id | promotion_product |
| admin_user ↔ admin_role | N:N | - | admin_user_role |
| admin_role ↔ admin_permission | N:N | - | admin_role_permission |
| admin_dept → admin_user | 1:N | dept_id | admin_user |
| admin_dept → admin_dept | 1:N（自关联） | parent_id | admin_dept |
| admin_permission → admin_permission | 1:N（自关联） | parent_id | admin_permission |

### 2.2 跨库关系（逻辑关联，存 ID + 索引，无物理外键）

| 源表 → 目标表 | 关联字段 | 说明 |
|--------------|---------|------|
| cart_item → product / product_sku | product_id / sku_id | shop_cart↔shop_product |
| order_item → product / product_sku | product_id / sku_id | shop_order↔shop_product |
| order_info → shop | shop_id | shop_order↔shop_merchant |
| payment_info → order_info | order_no | shop_payment↔shop_order |
| user_coupon → coupon | coupon_id | shop_user↔shop_marketing（含冗余字段） |
| product_comment → order_item | order_item_id | shop_product↔shop_order |
| user_footprint → product | product_id | shop_user↔shop_product |
| seckill_activity → product_sku | sku_id | shop_seckill↔shop_product |
| order_info → seckill_activity | seckill_id | shop_order↔shop_seckill（秒杀订单） |

---

## 三、建模确认（表/关系/基数/关联字段位置）

> 详见 [er-diagram.md](./er-diagram.md)。关联字段位置原则：
> - **1:1**：外键放任一端，加 UNIQUE 约束。
> - **1:N**：外键放 N 端（多的一方），加普通索引。
> - **N:N**：建中间表，中间表含两端 ID。

---

## 四、核心设计理由

### 4.1 为什么按微服务拆库（每服务独立 schema）
- **数据隔离**：服务边界即数据边界，避免数据耦合，符合微服务单一职责。
- **独立扩展**：订单库可单独扩容/分库分表，不影响商品库。
- **故障隔离**：单库故障不影响其他服务。

### 4.2 为什么不用物理外键
- 微服务跨库无法用物理外键。
- 物理外键降低写入性能、增加锁竞争、阻碍分库分表。
- 用"逻辑外键 + 索引"维护关系，应用层保证一致性。

### 4.3 为什么订单明细用快照字段（product_name/price/sku_spec）
- 商品信息可能被商家修改，订单数据必须固化历史价格。
- 符合"不可变事实"原则，保证财务对账准确。

### 4.4 为什么用户优惠券表冗余模板信息
- shop_user 库查询"我的优惠券"时，若不冗余需跨服务调 shop_marketing。
- 冗余 coupon_name/amount/threshold 等字段，单库查询完成，提升性能（反范式，见第六节）。

### 4.5 为什么秒杀库存独立于商品库存
- 秒杀是营销活动，不能消耗正常商品库存。
- 秒杀库存预热到 Redis，Lua 脚本原子扣减防超卖。

### 4.6 为什么订单地址用快照表
- 用户可能修改地址，订单地址必须固化下单时的收货信息。

---

## 五、数据库选型与理由

| 类型 | 选型 | 理由 |
|------|------|------|
| **主数据库** | MySQL 8.0 (InnoDB) | 支持事务/行锁/MVCC，满足电商强一致性需求；社区成熟，运维成本低 |
| **缓存数据库** | Redis 6 | 秒杀库存原子扣减（Lua）、接口限流计数（滑动窗口）、热点商品缓存、幂等键（SETNX） |
| **搜索引擎** | Elasticsearch 7 | 商品搜索、分词检索、聚合统计，弥补 MySQL LIKE 性能不足 |
| **消息队列** | RocketMQ | 秒杀订单异步创建、订单超时取消延迟消息、削峰填谷 |
| **分布式事务** | Seata AT | 跨服务事务（下单扣库存、退款回滚），每库 undo_log 表 |

---

## 六、三大范式校验

### 6.1 第一范式（1NF）- 字段原子性
> 每个字段不可再分。

- **合规**：所有表字段均为原子值（如 province/city/district 拆分而非合并存储）。
- **反范式例外**：
  - `product.images`（JSON数组）、`product_comment.images`（JSON数组）、`order_logistics.detail`（JSON轨迹）、`product_sku.spec_values`（JSON）。
  - **理由**：JSON 存储多值图片/规格，避免拆分子表带来多次 JOIN，提升读取性能；这些字段整体读写，无需按元素查询。

### 6.2 第二范式（2NF）- 完全依赖主键
> 非主键字段必须完全依赖主键（非部分依赖）。

- **合规**：所有表均使用单列主键 `id`，无联合主键，不存在部分依赖。

### 6.3 第三范式（3NF）- 直接依赖主键
> 非主键字段必须直接依赖主键（非传递依赖）。

- **反范式设计（有意为之，理由：提升查询性能）**：

| 表 | 冗余字段 | 依赖的表 | 反范式理由 |
|----|---------|---------|-----------|
| user_coupon | coupon_name / coupon_type / amount / threshold / valid_start_time / valid_end_time / merchant_id | coupon | 避免"我的优惠券"列表跨服务调 shop_marketing，单库完成查询 |
| order_item | product_name / sku_spec / price / image | product / product_sku | 订单快照，固化下单时商品信息，避免商品改名影响历史订单 |
| order_address | name / phone / province / city / district / detail | user_address | 地址快照，固化下单时地址，避免用户改地址影响订单 |
| withdraw_order | bank_name / bank_account / account_name | merchant_settlement | 提现申请时快照银行卡，避免后续改卡影响历史记录 |
| user_footprint | category_id | product | 记录浏览时分类，用于猜你喜欢推荐，避免回查商品表 |
| seckill_activity | original_price | product_sku | 冗余原价用于展示，避免详情页再查 SKU |
| settlement_record | order_amount | order_info | 结算流水冗余订单金额，便于对账查询 |
| admin_operation_log | username | admin_user | 冗余操作人用户名，避免联表查询日志 |

> **总结**：所有反范式均围绕"快照"与"避免跨库/跨表查询"，符合电商高读取性能诉求。冗余字段在写入时固化，不接受源表变更回写。

---

## 七、字段规范

### 7.1 核心公共字段（BaseEntity）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | BIGINT | 是 | 主键，雪花算法（IdType.ASSIGN_ID） |
| create_time | DATETIME | 是 | 创建时间，默认 CURRENT_TIMESTAMP，INSERT 自动填充 |
| update_time | DATETIME | 是 | 更新时间，ON UPDATE CURRENT_TIMESTAMP，INSERT/UPDATE 自动填充 |
| deleted | TINYINT | 是 | 软删除：0未删除 1已删除，默认0，@TableLogic |

> 例外：日志类表（user_login_log / order_log / payment_callback / admin_operation_log / admin_login_log / admin_security_event）和关联表（admin_user_role / admin_role_permission / promotion_product）及 product_spec / product_spec_value / product_image 不含 deleted 字段（只增不删）；user_coupon 不含 deleted（用 status 管理生命周期）。

### 7.2 字段命名规范

| 规范 | 示例 |
|------|------|
| 主键统一 `id` | id |
| 外键引用 `<对象>_id` | user_id / product_id / order_id / merchant_id |
| 时间字段 `_time` 后缀 | create_time / pay_time / delivery_time |
| 布尔语义 `is_` / `has_` 前缀 | is_default / is_anonymous / is_reviewed |
| 状态字段 `status` | status (TINYINT) |
| 唯一索引 `uk_` 前缀 | uk_phone / uk_order_no |
| 普通索引 `idx_` 前缀 | idx_user_id |
| 命名 snake_case | order_no / pay_amount |

### 7.3 字段类型规范

| 数据类型 | 规范 | 示例 |
|---------|------|------|
| 主键 | BIGINT | id |
| 金额 | DECIMAL(10,2) 或 DECIMAL(12,2)，CNY，2位小数 | price / amount / pay_amount |
| 比率 | DECIMAL(5,4) | commission_rate（0.05表示5%） |
| 状态 | TINYINT，注释全部取值 | status |
| 是否类 | TINYINT(0/1) | is_default / deleted |
| 数量类 | INT | stock / quantity / sales |
| 手机号 | VARCHAR(20)，AES加密存储 | phone |
| 密码 | VARCHAR(100)，BCrypt加密 | password |
| 订单号 | VARCHAR(32)，雪花算法 | order_no |
| 时间 | DATETIME | create_time |
| 富文本/JSON | TEXT / JSON | detail / images |
| 图片URL | VARCHAR(255) | main_image |

### 7.4 状态字段取值定义（全量）

| 表 | 字段 | 取值 |
|----|------|------|
| user | status | 0禁用 1正常 |
| merchant | status | 0待审核 1已通过 2已拒绝 3已禁用 |
| shop | status | 0关闭 1正常 |
| category | status | 0禁用 1启用 |
| product | status | 0下架 1上架 |
| product_sku | status | 0禁用 1启用 |
| order_info | status | 0待付款 1已取消 2待发货 3运输中 4已收货 5已完成 6退款中 7已退款 |
| order_info | order_type | 1普通订单 2秒杀订单 |
| order_info | is_reviewed | 0未评价 1已评价 |
| payment_info | pay_status | 0待支付 1已支付 2已关闭 3已退款 |
| payment_info | pay_type | 1模拟支付 2微信 3支付宝 |
| order_logistics | status | 0待发货 1已发货 2运输中 3已签收 |
| refund_order | status | 0待审核 1已同意 2已拒绝 3已退款 |
| coupon | status | 0待生效 1进行中 2已结束 3已下架 |
| coupon | type | 1满减 2折扣 3立减 |
| user_coupon | status | 0未使用 1已使用 2已过期 |
| promotion | status | 0待生效 1进行中 2已结束 3已下架 |
| promotion | scope_type | 1全店 2指定商品 |
| seckill_activity | status | 0待生效 1进行中 2已结束 3已下架 |
| product_comment | comment_type | 0初始评价 1追评 |
| product_comment | is_anonymous | 0否 1是 |
| settlement_record | status | 0待结算 1已结算 2已退款 |
| withdraw_order | status | 0待审核 1已通过 2已拒绝 3已打款 |
| notification | receiver_type | 1用户 2商家 3管理员 |
| notification | type | 1订单 2支付 3退款 4商家审核 5提现 6系统 |
| admin_user | status | 0禁用 1正常 |
| admin_role | status | 0禁用 1正常 |
| admin_role | data_scope | 1全部 2本部门 3本部门及下级 4仅本人 |
| admin_permission | type | 1目录 2菜单 3按钮 |
| admin_security_event | status | 0未处理 1已处理 2已忽略 |

---

## 八、硬性约束校验

| 约束 | 校验结果 |
|------|---------|
| 不使用物理外键 | ✅ 全部使用逻辑外键 + 索引，无 FOREIGN KEY |
| 软删除 | ✅ 业务表均有 deleted 字段（日志/关联表除外，见7.1） |
| 密码加密 | ✅ user / merchant / admin_user 密码均 BCrypt（VARCHAR(100)） |
| 金额类型 | ✅ 全部 DECIMAL，无 FLOAT/DOUBLE |
| 货币定义 | CNY（人民币），单位元，2位小数 |
| 手机号 | VARCHAR(20)，AES加密，国内11位（预留国际化长度） |
| 多币种 | 否，单一币种 CNY |
| 字符集 | utf8mb4 / utf8mb4_unicode_ci |

---

## 九、不可物理删除的数据

以下数据属于业务凭证/审计记录，禁止物理删除，仅可标记状态或软删除：

| 表 | 理由 |
|----|------|
| order_info / order_item / order_address | 订单交易凭证，财务/税务要求留存 |
| payment_info / payment_callback | 支付凭证，对账/审计留存 |
| settlement_record / withdraw_order | 结算/提现凭证，财务合规 |
| refund_order | 退款凭证 |
| user_login_log / admin_login_log / admin_operation_log / admin_security_event | 安全审计日志 |
| order_log | 订单状态流转记录，追溯用 |
| product_comment | 评价记录，已关联订单 |
| user_account | 账户余额，资金数据 |

---

## 十、校验报告：实体类与 SQL 脚本一致性

> 对照 `shop-model` 实体类与 `docker/mysql/init/` SQL 脚本校验结果。

### 10.1 一致表（无需调整）

user / user_account / user_address / user_favorite / user_footprint / user_login_log / notification / user_coupon / merchant / merchant_qualification / merchant_settlement / shop / settlement_record / withdraw_order / category / brand / product / product_spec / product_spec_value / product_sku / product_image / product_comment / cart_item / payment_info / payment_callback / admin_user / admin_role / admin_permission / admin_user_role / admin_role_permission / admin_dept / admin_operation_log / admin_login_log / admin_security_event / admin_banner / admin_notice / coupon / promotion / promotion_product / seckill_activity

### 10.2 不一致表（需修正）⚠️

#### ① order_info 表（实体类 OrderInfo.java 与 SQL 不一致）

| SQL 字段 | 实体类字段 | 问题 | 处理方案 |
|---------|-----------|------|---------|
| shop_id | merchantId | 字段名不一致（SQL=shop_id，实体=merchantId→merchant_id） | 以实体类为准，schema.sql 改为 merchant_id（代码运行依赖实体类映射） |
| freight | freightAmount | 字段名不一致（SQL=freight，实体→freight_amount） | schema.sql 改为 freight_amount |
| close_time | finishTime / cancelTime | 实体类拆分为完成时间与取消时间，SQL 仅有 close_time | schema.sql 将 close_time 拆为 finish_time + cancel_time |
| - | remark | 实体类有，SQL 缺失 | schema.sql 补充 remark 字段 |
| - | cancelReason | 实体类有，SQL 缺失 | schema.sql 补充 cancel_reason 字段 |

#### ② order_item 表（实体类 OrderItem.java 与 SQL 不一致）

| SQL 字段 | 实体类字段 | 问题 | 处理方案 |
|---------|-----------|------|---------|
| - | orderNo | 实体类有 orderNo，SQL 缺失 order_no | schema.sql 补充 order_no 字段 |
| image | productImage | 字段名不一致（SQL=image，实体→product_image） | schema.sql 改为 product_image |
| - | subtotal | 实体类有，SQL 缺失 | schema.sql 补充 subtotal 字段 |

> **结论**：`docker/mysql/init/06-shop-order.sql` 滞后于代码，实际运行数据库可能已被手动 ALTER 更新。本设计文档的 `schema.sql` 以**实体类为准**（代码即真相），已修正上述字段，保证 schema 与代码一致。

---

## 十一、实施计划

### 11.1 业务对象清单
见第一节（8 大业务域、40+ 对象）。

### 11.2 对象关系
见第二节（同库 + 跨库关系）。

### 11.3 主数据库
MySQL 8.0，按微服务拆 9 个 schema + 1 个 Seata 库。

### 11.4 缓存数据库
Redis（秒杀库存/限流/热点缓存/幂等键）。

### 11.5 核心表清单与职责

| 库 | 表 | 职责 |
|----|----|------|
| shop_user | user | 用户基本信息 |
| | user_account | 用户钱包（余额/积分） |
| | user_address | 收货地址 |
| | user_favorite | 商品收藏 |
| | user_footprint | 浏览足迹 |
| | user_login_log | 登录日志 |
| | notification | 三端消息通知 |
| | user_coupon | 用户优惠券（含模板冗余） |
| shop_merchant | merchant | 商家信息 |
| | merchant_qualification | 商家资质 |
| | merchant_settlement | 商家结算账户 |
| | shop | 店铺信息 |
| | settlement_record | 结算流水 |
| | withdraw_order | 提现申请 |
| shop_product | category | 商品分类（树形） |
| | brand | 品牌 |
| | product | 商品SPU |
| | product_spec | 规格维度 |
| | product_spec_value | 规格值 |
| | product_sku | 商品SKU |
| | product_image | 商品图片 |
| | product_comment | 商品评价 |
| shop_cart | cart_item | 购物车项 |
| shop_order | order_info | 订单主表 |
| | order_item | 订单明细 |
| | order_address | 订单地址快照 |
| | order_logistics | 物流信息 |
| | order_log | 订单状态日志 |
| | refund_order | 退款单 |
| | undo_log | Seata回滚日志 |
| shop_payment | payment_info | 支付记录 |
| | payment_callback | 支付回调日志 |
| | undo_log | Seata回滚日志 |
| shop_admin | admin_user | 管理员 |
| | admin_role | 角色 |
| | admin_permission | 权限菜单 |
| | admin_user_role | 管理员-角色 |
| | admin_role_permission | 角色-权限 |
| | admin_dept | 部门 |
| | admin_operation_log | 操作日志 |
| | admin_login_log | 登录日志 |
| | admin_security_event | 安全事件 |
| | admin_banner | Banner |
| | admin_notice | 公告 |
| shop_marketing | coupon | 优惠券模板 |
| | promotion | 满减活动 |
| | promotion_product | 满减活动商品关联 |
| shop_seckill | seckill_activity | 秒杀活动 |

### 11.6 字段明细（字段名/类型/必填/唯一/可空）

> 完整字段明细见 `schema.sql`（每张表字段均含注释标明 NOT NULL/UNIQUE/DEFAULT）。
> 此处列出核心表样例：

**order_info（订单主表）**

| 字段 | 类型 | 必填 | 唯一 | 可空 | 默认 | 说明 |
|------|------|------|------|------|------|------|
| id | BIGINT | 是 | 是(主键) | 否 | - | 雪花ID |
| order_no | VARCHAR(32) | 是 | 是(uk) | 否 | - | 订单号 |
| user_id | BIGINT | 是 | 否 | 否 | - | 用户ID |
| merchant_id | BIGINT | 是 | 否 | 否 | - | 商家ID |
| total_amount | DECIMAL(12,2) | 是 | 否 | 否 | - | 订单总额 |
| pay_amount | DECIMAL(12,2) | 是 | 否 | 否 | - | 实付金额 |
| freight_amount | DECIMAL(10,2) | 是 | 否 | 否 | 0.00 | 运费 |
| discount_amount | DECIMAL(10,2) | 是 | 否 | 否 | 0.00 | 优惠总额 |
| promotion_discount | DECIMAL(10,2) | 是 | 否 | 否 | 0.00 | 满减优惠 |
| order_type | TINYINT | 是 | 否 | 否 | 1 | 1普通2秒杀 |
| seckill_id | BIGINT | 否 | 否 | 是 | NULL | 秒杀活动ID |
| is_reviewed | TINYINT | 是 | 否 | 否 | 0 | 是否已评价 |
| status | TINYINT | 是 | 否 | 否 | 0 | 订单状态 |
| pay_time | DATETIME | 否 | 否 | 是 | NULL | 支付时间 |
| delivery_time | DATETIME | 否 | 否 | 是 | NULL | 发货时间 |
| receive_time | DATETIME | 否 | 否 | 是 | NULL | 收货时间 |
| finish_time | DATETIME | 否 | 否 | 是 | NULL | 完成时间 |
| cancel_time | DATETIME | 否 | 否 | 是 | NULL | 取消时间 |
| remark | VARCHAR(200) | 否 | 否 | 是 | NULL | 订单备注 |
| cancel_reason | VARCHAR(200) | 否 | 否 | 是 | NULL | 取消原因 |
| create_time | DATETIME | 是 | 否 | 否 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | 否 | 否 | CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT | 是 | 否 | 否 | 0 | 软删除 |

> 其余表字段明细请直接查阅 `schema.sql`，每行均含 COMMENT 注明约束。

### 11.7 表关联方式
- 同库：逻辑外键（字段存 ID）+ 普通索引/唯一索引。
- 跨库：逻辑关联（存 ID），无任何物理约束，应用层（Feign）保证一致性。

### 11.8 三大范式符合性
见第六节，反范式均已说明理由。

### 11.9 验收标准

| 验收项 | 标准 |
|--------|------|
| 建表成功 | schema.sql 全量执行无报错 |
| 字段类型 | 金额全 DECIMAL，密码 VARCHAR(100)，主键 BIGINT |
| 索引生效 | 所有外键字段有索引，唯一约束生效 |
| 状态取值 | 每个 status 字段 COMMENT 含完整取值说明 |
| 软删除 | 业务表 deleted 字段存在，默认0 |
| 无外键 | 全表无 FOREIGN KEY 约束 |
| 字符集 | utf8mb4 / utf8mb4_unicode_ci |
| 实体一致 | schema.sql 与 shop-model 实体类字段完全对应 |
| 迁移可重放 | 迁移脚本幂等（IF NOT EXISTS） |

---

## 十二、变更记录

| 版本 | 日期 | 变更内容 | 变更原因 |
|------|------|---------|---------|
| 1.0.0 | 2026-07-06 | 初始生成数据库设计文档与 schema.sql | 项目缺少数据库设计文档，对照实体类校验并补全 |
| 1.0.0 | 2026-07-06 | 修正 order_info 字段：shop_id→merchant_id、freight→freight_amount、close_time 拆分为 finish_time+cancel_time，新增 remark/cancel_reason | 与 OrderInfo 实体类保持一致（代码即真相） |
| 1.0.0 | 2026-07-06 | 修正 order_item 字段：image→product_image，新增 order_no/subtotal | 与 OrderItem 实体类保持一致 |
