# ShopMall 微服务电商平台

基于 Spring Cloud Alibaba 的全栈微服务电商平台，覆盖用户端、商家端、管理端三大前台，以及 10 个独立微服务后端。

## 项目概览

| 层级 | 技术栈 |
|------|--------|
| 后端框架 | Spring Boot 4.0 + Spring Cloud 2025.1 + Spring Cloud Alibaba 2025.1.0 |
| 语言 | Java 21 |
| 数据库 | MySQL 8.0 |
| ORM | MyBatis-Plus 3.5 |
| 缓存 | Redis 7 |
| 消息队列 | RocketMQ 5.3 |
| 搜索引擎 | Elasticsearch 8.15 + Kibana |
| 注册/配置中心 | Nacos 2.4 |
| 分布式事务 | Seata (AT 模式) |
| 认证鉴权 | Sa-Token 1.45 |
| 对象存储 | MinIO |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + Pinia |
| 包管理 | pnpm workspaces (monorepo) |
| 构建工具 | Maven 多模块 |

## 项目结构

```
Shop/
├── shop-common/         # 公共模块：统一返回、异常处理、上下文、工具类
├── shop-model/          # 数据模型：Entity/DTO/VO/Enum
├── shop-gateway/        # API网关 (8844)：路由、鉴权、CORS
├── shop-user/           # 用户服务 (8845)：注册登录、地址、收藏、足迹
├── shop-merchant/       # 商家服务 (8846)：入驻、店铺管理、结算提现
├── shop-product/        # 商品服务 (8847)：分类品牌、SPU/SKU、ES搜索、评价
├── shop-cart/           # 购物车服务 (8848)
├── shop-order/          # 订单服务 (8849)：下单、超时取消、退款、物流
├── shop-payment/        # 支付服务 (8850)：支付创建、回调处理
├── shop-admin/          # 管理服务 (8851)：RBAC权限、审计、Banner公告
├── shop-marketing/      # 营销服务 (8852)：优惠券、满减活动
├── shop-seckill/        # 秒杀服务 (8853)：库存预热、Lua脚本扣减
├── shop-frontend/       # 前端 monorepo
│   ├── packages/shared/      # 共享模块
│   ├── packages/web-user/    # 用户端 (消费者)
│   ├── packages/web-merchant/ # 商家端
│   └── packages/web-admin/   # 管理端
├── docker/              # Docker 配置：中间件编排、Nacos 配置、SQL 初始化
│   ├── mysql/init/      # 数据库初始化脚本 (10 个库)
│   ├── nacos/config/    # Nacos 配置文件
│   └── es/              # ES 索引映射
└── docs/                # 项目文档
```

## 中间件一览

| 中间件 | 端口 | 管理面板 |
|--------|------|----------|
| MySQL | 4306 | - |
| Redis | 6379 | - |
| Nacos | 8976 | http://localhost:8976/nacos (nacos/nacos) |
| RocketMQ NameServer | 9977 | - |
| RocketMQ Dashboard | 8080 | http://localhost:8080 |
| Elasticsearch | 9201 | http://localhost:9201 |
| Kibana | 5601 | http://localhost:5601 |
| MinIO | 9000 (API) / 9001 (Console) | http://localhost:9001 (minioadmin/minioadmin123) |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+
- Docker Desktop
- Node.js 18+ & pnpm 9+

### 1. 启动中间件

```bash
# 在项目根目录执行，一键启动所有中间件
docker-compose up -d

# 检查服务状态
docker-compose ps
```

### 2. 初始化 Nacos 配置

```bash
# 创建 dev 命名空间后，用脚本批量导入
# Windows (PowerShell):
.\docker\nacos\import-configs.ps1

# Linux / macOS (Bash):
bash docker/nacos/import-configs.sh
```

### 3. 编译并启动后端

```bash
# 编译全部微服务
mvn clean package -DskipTests

# 按顺序启动（先 gateway，再其他服务）
# 推荐在 IDEA 中分别运行各服务的 main 类
```

### 4. 启动前端

```bash
cd shop-frontend
pnpm install
pnpm dev:user       # 用户端
pnpm dev:merchant   # 商家端
pnpm dev:admin      # 管理端
```

## 核心业务链路

```
用户注册/登录 → 浏览商品(ES搜索) → 加入购物车 → 创建订单(幂等+分布式锁)
→ 库存扣减(Redis+Lua) → 支付处理 → 支付回调(RocketMQ) → 订单状态更新
→ 商家发货 → 物流跟踪 → 确认收货 → 评价商品
                        ↓ 超时未支付
                    (RocketMQ 延时消息 30 分钟自动取消 + 回滚库存)
```

## 架构亮点

- **微服务拆分**：按业务领域 10 个独立服务，Spring Cloud Gateway 统一入口
- **分布式事务**：Seata AT 模式保证跨服务下单扣库存的数据一致性
- **高性能库存扣减**：Redis + Lua 脚本原子执行，支持幂等防超卖
- **异步解耦**：RocketMQ 处理延时消息、商品 ES 同步、支付回调通知
- **搜索引擎**：ES + ik 中文分词，高亮/聚合/搜索建议
- **统一鉴权**：Sa-Token + Header 防伪造 + 白名单机制
- **RBAC 权限**：用户-角色-权限三层结构 + 数据权限控制
- **订单状态机**：严格的状态流转校验 + 全量变更日志
- **配置中心**：Nacos 集中管理 + 热更新，分层共享 (common.yml + 服务独立配置)
- **前后端分离**：pnpm monorepo 管理三个前端应用 + 共享模块

## 文档

| 文档 | 说明 |
|------|------|
| [本地开发指南](docs/local-dev-guide.md) | 环境搭建、中间件启动、常见问题 |
| [项目介绍文档](docs/项目介绍文档.md) | 完整的架构和功能说明 |
| [功能完善计划](docs/功能完善开发计划.md) | 各服务功能完成度评估 |
| [后端代码审查报告](docs/后端代码审查报告.md) | 代码质量分析 |

## License

MIT
