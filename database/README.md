# Shop 商城项目 - 数据库设计文档

> 本目录是项目数据库设计的**事实来源（Single Source of Truth）**。
> 任何表结构变更必须先改本目录下的 `schema.sql`，说明变更原因，再生成迁移脚本并执行。
> 原有 `docker/mysql/init/` 仅用于容器首次初始化，以本目录 `schema.sql` 为准同步。

## 目录结构

```
database/
├── README.md                  # 本文件（目录索引与规范摘要）
├── design.md                  # 数据库设计文档（业务对象/关系/建模/范式校验/实施计划/差异校验报告）
├── schema.sql                 # 全量建表脚本（事实来源，每次变更同步更新）
├── er-diagram.md              # ER 关系图（Mermaid 描述表关系与关联基数）
└── migrations/
    ├── V1.0.0__init_schema.sql # 初始全量建表迁移脚本
    └── V1.0.1__*.sql           # 后续增量变更迁移（按版本号顺序执行）
```

## 规范摘要（详见 `.trae/skills/database-design/SKILL.md`）

1. **不使用物理外键**：表间关系通过"逻辑外键 + 索引"维护。
2. **软删除**：业务表必须有 `deleted TINYINT NOT NULL DEFAULT 0`（配合 `@TableLogic`）。
3. **密码加密**：BCrypt 存储，禁止明文。
4. **金额类型**：统一 `DECIMAL(10,2)` / `DECIMAL(12,2)`，货币 CNY，2 位小数，禁止浮点数。
5. **主键策略**：雪花算法 BIGINT（`IdType.ASSIGN_ID`）。
6. **公共字段**：`id / create_time / update_time / deleted`（BaseEntity 公共基类）。
7. **字符集**：utf8mb4 / utf8mb4_unicode_ci。
8. **状态字段**：TINYINT，必须注释所有取值含义。

## 变更流程（强制）

```
1. 改 schema.sql（设计先行）
2. 写明变更原因（why）
3. 生成迁移脚本 migrations/V{版本}__{描述}.sql
4. 执行迁移 + 验证
```

## 数据库架构概览

- **主库**：MySQL 8.0，按微服务拆分为 9 个独立 schema + 1 个 Seata 库
- **缓存**：Redis（秒杀库存/限流计数/热点缓存/幂等键）
- **搜索**：Elasticsearch（商品索引）
- **分布式事务**：Seata AT 模式（每库 `undo_log` 表）

详见 [design.md](./design.md)。
