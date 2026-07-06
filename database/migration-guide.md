# 数据库迁移操作指南

> 本文档介绍主流数据库迁移方式，并以 Flyway 为推荐方案，结合本项目（Spring Boot + MyBatis-Plus）给出实操步骤。
> 适用于：新环境初始化、版本升级表结构变更、多环境同步。

---

## 一、主流迁移方式对比

| 工具/方式 | 说明 | 适用场景 | 推荐度 |
|-----------|------|---------|--------|
| **Flyway** | SQL 脚本版本化管理，按版本号顺序执行，简单直观 | Spring Boot 项目，SQL 优先 | ⭐⭐⭐⭐⭐ 推荐 |
| **Liquibase** | XML/YAML/JSON 描述变更，跨数据库兼容，支持回滚 | 多数据库类型、需回滚 | ⭐⭐⭐⭐ |
| **MyBatis Migrations** | MyBatis 官方迁移工具，与 MyBatis 集成 | 纯 MyBatis 项目 | ⭐⭐⭐ |
| **手写 SQL 脚本** | 手动维护 SQL，手动执行 | 小项目、临时变更 | ⭐⭐ |
| **Docker init 脚本** | 容器首次启动自动执行 | 仅容器初始化 | ⭐⭐ |

### 选型建议
- **本项目推荐 Flyway**：与 Spring Boot 无缝集成、SQL 优先（DBA 友好）、版本化管理、社区成熟。
- 迁移脚本已存在于 `database/migrations/`，可直接被 Flyway 接管。

---

## 二、Flyway 集成步骤（推荐方案）

### 2.1 添加依赖

在各微服务的 `pom.xml` 添加（以 shop-order 为例）：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

> Spring Boot 3.x 已内置 Flyway 管理，无需指定版本号。

### 2.2 配置 application.yml

```yaml
spring:
  flyway:
    enabled: true
    # 迁移脚本位置（默认 classpath:db/migration，可改为外部目录）
    locations: classpath:db/migration
    # 基线版本（已有数据库首次接入 Flyway 时，从该版本开始管理）
    baseline-on-migrate: true
    baseline-version: 0
    # 校验已执行脚本是否被篡改
    validate-on-migrate: true
    # 表名（默认 flyway_schema_history）
    table: flyway_schema_history
```

### 2.3 脚本目录结构

```
shop-order/src/main/resources/db/migration/
├── V1.0.0__init_schema.sql      # 初始建表（从 database/migrations/ 拷贝）
├── V1.0.1__add_xxx_field.sql    # 后续增量变更
└── V1.0.2__add_xxx_table.sql
```

> **命名规则（必须严格遵守）**：
> - 前缀 `V` 表示版本化迁移（执行一次）。
> - 版本号用点分（如 `1.0.0`），按数字顺序执行。
> - 双下划线 `__` 分隔版本号与描述。
> - 描述用小写下划线（如 `add_user_phone_field`）。

### 2.4 执行流程

```
应用启动 → Flyway 自动扫描 db/migration → 对比 flyway_schema_history 表
→ 未执行的脚本按版本号顺序执行 → 记录到 flyway_schema_history
```

**首次接入已有数据库**：设置 `baseline-on-migrate: true`，Flyway 会标记当前数据库为基线（version=0），只执行之后新增的脚本。

### 2.5 编写迁移脚本规范

```sql
-- V1.0.1__add_order_remark_field.sql
-- 变更原因：订单需要支持用户填写备注（产品需求 PRD-001）
-- 变更内容：order_info 表新增 remark 字段
-- 影响范围：shop_order.order_info
-- 执行时间：2026-07-06
-- 回滚方式：ALTER TABLE order_info DROP COLUMN remark;

ALTER TABLE `order_info` ADD COLUMN `remark` VARCHAR(200) DEFAULT NULL COMMENT '订单备注' AFTER `cancel_reason`;
```

**规范要求**：
1. 头部注释：变更原因（why）、内容、影响范围、回滚方式。
2. 幂等性：DDL 用 `IF NOT EXISTS`/`IF EXISTS`（MySQL 8.0 支持），或通过 Flyway 版本控制保证只执行一次。
3. 一个脚本只做一件事（单一职责），便于回滚。
4. 避免 `DROP TABLE`/`TRUNCATE` 等破坏性操作。

---

## 三、迁移操作流程

### 3.1 新环境初始化

```
1. 创建空数据库（执行 database/schema.sql 的 CREATE DATABASE 部分）
2. 启动应用 → Flyway 自动执行 V1.0.0__init_schema.sql 建全表
3. （可选）执行测试数据脚本 docker/mysql/init/99-test-data.sql
```

### 3.2 已有数据库接入 Flyway

```
1. 备份现有数据库（mysqldump）
2. 配置 baseline-on-migrate: true
3. 将 database/migrations/V1.0.0__init_schema.sql 拷贝到 db/migration
4. 启动应用 → Flyway 标记基线（不执行 V1.0.0，因为已存在）
5. 后续变更通过新增 V1.0.1__xxx.sql 管理
```

### 3.3 日常变更流程（开发 → 测试 → 生产）

```
开发环境：
1. 改 database/schema.sql（设计先行）
2. 在 database/migrations/ 新增 V{版本}__{描述}.sql
3. 拷贝到对应服务的 src/main/resources/db/migration/
4. 启动应用，Flyway 自动执行
5. 验证表结构

测试/生产环境：
1. 合并代码到对应分支
2. 部署应用，Flyway 自动执行新增脚本
3. （生产建议）先在预发环境验证，再上生产
```

### 3.4 多微服务迁移策略

本项目每个微服务独立数据库，建议：
- 每个服务 `src/main/resources/db/migration/` 独立管理自己的迁移脚本。
- 公共建库脚本（CREATE DATABASE）仍由 `docker/mysql/init/01-init-databases.sql` 完成。
- 各服务只管自己的表结构变更。

```
shop-order/src/main/resources/db/migration/
  └── V1.0.0__init_order_tables.sql     # 仅 shop_order 库的表

shop-product/src/main/resources/db/migration/
  └── V1.0.0__init_product_tables.sql   # 仅 shop_product 库的表
```

---

## 四、常用命令（手动执行场景）

### 4.1 MySQL 命令行手动迁移

```bash
# 备份数据库
mysqldump -u root -p shop_order > backup_shop_order.sql

# 执行迁移脚本
mysql -u root -p shop_order < database/migrations/V1.0.1__add_xxx_field.sql

# 查看表结构
mysql -u root -p -e "DESC order_info;" shop_order
```

### 4.2 Docker 环境重置

```bash
# 停止并删除容器（会删除数据）
docker-compose down

# 删除数据卷（彻底重置）
docker volume rm shop_mysql-data

# 重新启动（自动执行 docker/mysql/init/ 脚本）
docker-compose up -d mysql
```

---

## 五、回滚策略

### 5.1 Flyway 回滚
Flyway 社区版不支持自动回滚，需手写回滚脚本：

```sql
-- U1.0.1__rollback_add_remark.sql（Undo 脚本，需 Flyway Teams 版本）
ALTER TABLE `order_info` DROP COLUMN `remark`;
```

> 社区版建议：写一个新版本脚本（V1.0.2）来撤销上一个变更（反向操作）。

### 5.2 备份恢复
```bash
# 恢复备份
mysql -u root -p shop_order < backup_shop_order.sql
```

---

## 六、注意事项

1. **生产环境变更必须备份**：执行前 `mysqldump` 备份。
2. **大表变更需注意锁表**：`ALTER TABLE` 大表会锁表，建议用 `pt-online-schema-change`（Percona Toolkit）。
3. **不要修改已执行的脚本**：Flyway 默认校验 checksum，修改已执行脚本会启动失败。如需修改，用新版本脚本。
4. **测试环境先验证**：所有迁移脚本先在测试环境验证通过，再上生产。
5. **schema.sql 与 db/migration 同步**：`database/schema.sql` 是全量快照，`db/migration/` 是增量记录，两者必须保持一致。

---

## 七、快速接入清单

- [ ] 各服务 pom.xml 添加 flyway 依赖
- [ ] application.yml 配置 spring.flyway
- [ ] 创建 src/main/resources/db/migration 目录
- [ ] 从 database/migrations/ 拷贝 V1.0.0 到各服务
- [ ] 设置 baseline-on-migrate: true（已有数据库）
- [ ] 启动验证 flyway_schema_history 表生成
- [ ] 后续变更遵循"改 schema.sql + 新增 V 版本脚本"流程
