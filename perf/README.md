# 性能测试方案（N-P-01~08）

> 本目录存放 Shop 项目的 JMeter 性能测试脚本、测试数据和运行工具。
> 测试范围覆盖商品详情、秒杀、登录、下单、首页、稳定性、限流降级 8 个核心场景。

## 目录结构

```
perf/
├── jmx/                                    # JMeter 测试计划文件
│   ├── N-P-01-product-detail-normal.jmx    # 商品详情查询（普通）
│   ├── N-P-02-product-detail-hot.jmx       # 商品详情查询（热点）
│   ├── N-P-03-seckill.jmx                  # 秒杀抢购
│   ├── N-P-04-user-login.jmx               # 用户登录
│   ├── N-P-05-create-order.jmx             # 创建订单
│   ├── N-P-06-home-page.jmx                # 首页加载
│   ├── N-P-07-stability-24h.jmx            # 24h 稳定性测试
│   └── N-P-08-rate-limit-degrade.jmx       # 限流降级验证
├── data/                                   # 测试数据
│   ├── perf-test-data.sql                  # 测试数据准备 SQL
│   └── perf-users.csv                      # 1000 个测试用户（CSV）
├── scripts/                                # 运行脚本
│   └── run-perf.ps1                        # PowerShell 一键运行脚本
├── results/                                # 测试结果（运行时生成）
└── README.md                               # 本文档
```

## 测试场景一览

| 任务编号 | 场景 | 目标 QPS | 目标 P99 | 并发数 | 持续时间 | 优先级 |
|---------|------|---------|---------|--------|---------|--------|
| N-P-01 | 商品详情查询（普通） | 500 | 200ms | 100 | 5min | P0 |
| N-P-02 | 商品详情查询（热点 ID=10086） | 2000 | 500ms | 400 | 5min | P0 |
| N-P-03 | 秒杀抢购 | 2000 | 1s | 1000 | 1min | P0 |
| N-P-04 | 用户登录 | 200 | 300ms | 50 | 5min | P0 |
| N-P-05 | 创建订单 | 100 | 500ms | 50 | 5min | P0 |
| N-P-06 | 首页加载 | 300 | 300ms | 100 | 5min | P1 |
| N-P-07 | 24h 稳定性测试 | 100 | - | 50 | 24h | P1 |
| N-P-08 | 限流降级验证 | - | - | 500 | 1min | P0 |

## 前置准备

### 1. 环境要求

- **JMeter 5.6+**：已安装并配置 `JMETER_HOME` 环境变量，或将 `jmeter.bat` 加入 PATH
- **后端服务**：11 个微服务 + Redis + MySQL + RocketMQ + Nacos 全部启动
- **MySQL 客户端**：用于执行测试数据准备 SQL
- **Docker**：用于执行 Redis 命令（预热秒杀库存、清理黑名单）

### 2. 设置 JMeter 环境变量

```powershell
# 方式1：临时设置（当前终端有效）
$env:JMETER_HOME = "D:\apache-jmeter-5.6.3"
$env:PATH += ";$env:JMETER_HOME\bin"

# 方式2：永久设置（推荐）
[Environment]::SetEnvironmentVariable("JMETER_HOME", "D:\apache-jmeter-5.6.3", "User")
```

验证 JMeter 是否可用：
```powershell
jmeter --version
```

### 3. 准备测试数据

测试数据包括：
- 热点商品（ID=10086）
- 大库存秒杀活动（ID=9999，库存 100000）
- 1000 个测试用户（ID=2001~3000，密码 123456）
- 1000 个收货地址（ID=90001~91000）

**方式1：用一键脚本自动准备**（推荐）
```powershell
cd d:\workspace\business\Shop
.\perf\scripts\run-perf.ps1 -Scenario N-P-01 -SkipCleanup  # 会自动执行数据准备
```

**方式2：手动准备**
```powershell
# 1. 执行 SQL（会提示输入 MySQL root 密码）
mysql -uroot -p < perf\data\perf-test-data.sql

# 2. 预热 Redis 秒杀库存
docker exec shop-redis redis-cli SET seckill:stock:9999 100000
```

## 运行测试

### 一键运行（推荐）

```powershell
cd d:\workspace\business\Shop

# 运行所有 P0 场景（不含 24h 稳定性测试）
.\perf\scripts\run-perf.ps1 -Scenario all

# 只运行单个场景
.\perf\scripts\run-perf.ps1 -Scenario N-P-01

# 运行多个场景
.\perf\scripts\run-perf.ps1 -Scenario N-P-01,N-P-02,N-P-06

# 跳过数据准备（已准备过数据时）
.\perf\scripts\run-perf.ps1 -Scenario N-P-01 -SkipDataPrep

# 单独运行 24h 稳定性测试
.\perf\scripts\run-perf.ps1 -Scenario N-P-07 -SkipDataPrep
```

### 手动运行单个场景

```powershell
cd d:\workspace\business\Shop

# 运行 N-P-01 商品详情查询（普通）
jmeter -n -t perf/jmx/N-P-01-product-detail-normal.jmx `
       -l perf/results/N-P-01-result.jtl `
       -e -o perf/results/N-P-01-report

# 运行完成后查看 HTML 报告
start perf/results/N-P-01-report/index.html
```

### 参数说明

| 参数 | 说明 |
|------|------|
| `-n` | 非 GUI 模式运行（命令行模式，推荐） |
| `-t` | 指定测试计划文件（.jmx） |
| `-l` | 指定结果文件（.jtl） |
| `-e` | 测试结束后生成 HTML 报告 |
| `-o` | 指定 HTML 报告输出目录 |

## 测试结果解读

### 关键指标

JMeter HTML 报告中重点关注以下指标：

| 指标 | 说明 | 达标标准 |
|------|------|---------|
| **Throughput** | 吞吐量（QPS） | 达到目标 QPS |
| **90th pct** | 90% 请求的响应时间 | < 目标 P99 |
| **95th pct** | 95% 请求的响应时间 | < 目标 P99 |
| **99th pct** | 99% 请求的响应时间（P99） | < 目标 P99 |
| **Error Rate** | 错误率 | < 0.1% |

### HTML 报告位置

每个场景运行后，HTML 报告生成在：
```
perf/results/{场景名}-report/index.html
```

用浏览器打开即可查看详细的：
- 统计表格（平均/中位数/P90/P95/P99/最小/最大响应时间）
- 响应时间随时间变化曲线
- 吞吐量随时间变化曲线
- 错误率统计

## 各场景详细说明

### N-P-01 商品详情查询（普通）

- **接口**：`GET /api/product/4001`
- **测试点**：普通商品的查询性能，Sentinel 热点参数限流默认阈值 500 QPS
- **注意**：无需登录，直接压测

### N-P-02 商品详情查询（热点）

- **接口**：`GET /api/product/10086`
- **测试点**：热点商品（ID=10086）的查询性能，Sentinel 热点参数限流特殊阈值 2000 QPS
- **降级验证**：超过 2000 QPS 后，Sentinel blockHandler 返回 Caffeine 缓存数据（不是 429 错误）
- **注意**：需先执行 `perf-test-data.sql` 创建 ID=10086 的热点商品

### N-P-03 秒杀抢购

- **接口**：`POST /api/order/seckill/9999`
- **测试点**：秒杀抢购高并发性能，Redis 原子扣减库存防超卖
- **流程**：setUp 线程组先批量登录 1000 个用户获取 token → 主线程组用 token 抢购
- **注意**：
  - 需先预热 Redis 库存：`docker exec shop-redis redis-cli SET seckill:stock:9999 100000`
  - 秒杀接口限流：IP QPS=3, path QPS=1000, user QPS=3
  - 1000 并发用户避免单用户 QPS 限制

### N-P-04 用户登录

- **接口**：`POST /api/user/auth/login`
- **测试点**：登录接口性能，BCrypt 密码校验 + Sa-Token 生成
- **注意**：
  - 登录接口限流：IP QPS=5（sliding-window），50 并发可能触发限流
  - **建议运行前临时调高 IP QPS 到 200**（修改 Nacos 中 shop-gateway.yml 的 `/api/user/auth/**` 规则）
  - 或用分布式压测（多台机器不同 IP）

### N-P-05 创建订单

- **接口**：`POST /api/order`
- **测试点**：下单接口性能，包含幂等校验、库存扣减、优惠券核销等复杂逻辑
- **流程**：setUp 线程组先登录 50 个用户获取 token → 主线程组用 token 下单
- **注意**：
  - 下单接口限流：IP QPS=10, path QPS=100, user QPS=10
  - **建议运行前临时调高 IP QPS 到 100**（修改 Nacos 中 shop-gateway.yml 的 `/api/order/**` 规则）
  - 幂等键格式：`order_用户ID_时间戳_随机数`，每次请求都不同
  - SKU ID=50001（iPhone 15 Pro Max），库存充足

### N-P-06 首页加载

- **接口**：`GET /api/product/recommend/hot` + `GET /api/product/recommend/new` + `GET /api/product/category-tree`
- **测试点**：首页加载性能，模拟用户打开首页时同时请求多个接口
- **注意**：无需登录，3 个接口共享 300 QPS 吞吐量

### N-P-07 24h 稳定性测试

- **接口**：随机访问商品详情、热门推荐、商品搜索
- **测试点**：长时间运行稳定性，验证内存泄漏、连接泄漏
- **注意**：
  - 持续 24 小时（86400 秒），建议在后台运行
  - 运行时同时监控 JVM GC、Redis 连接数、MySQL 连接数、CPU/内存
  - 监控命令示例：
    - `jstat -gc <pid> 1000`（每秒打印 GC）
    - `jmap -heap <pid>`（观察堆内存）
    - `docker exec shop-redis redis-cli INFO clients`（Redis 连接数）

### N-P-08 限流降级验证

- **场景1**：500 并发 3000 QPS 压测热点商品 ID=10086，超过 2000 QPS 阈值
  - 预期：返回 Caffeine 缓存数据（HTTP 200），不是 429 错误
- **场景2**：单线程 100 QPS 高频请求普通商品，触发 IP 自动拉黑
  - 预期：超过 IP QPS=50 触发 429，累计 10 次后自动拉黑返回 403
- **注意**：
  - 测试会将本机 IP 自动拉黑 1 小时
  - 测试后清理黑名单：`docker exec shop-redis redis-cli DEL rate_limit:blacklist:ip`
  - 两个场景顺序执行（TestPlan serialize_threadgroups=true）

## 常见问题

### Q1: JMeter 报错 "Not enough memory"

**原因**：JMeter 堆内存不足，高并发场景（N-P-02/N-P-03）需要更多内存。

**解决**：编辑 `JMETER_HOME\bin\jmeter.bat`，增大堆内存：
```
set HEAP=-Xms1g -Xmx4g
```

### Q2: N-P-03 秒杀测试 token 文件为空

**原因**：setUp 线程组登录失败，可能是测试用户未创建或密码不匹配。

**解决**：
1. 确认已执行 `perf-test-data.sql` 创建测试用户
2. 手动验证登录：`curl -X POST http://localhost:8844/api/user/auth/login -H "Content-Type: application/json" -d '{"phone":"13900002001","password":"123456"}'`
3. 如果密码不匹配，更新数据库中的 BCrypt 哈希值

### Q3: N-P-04/N-P-05 错误率过高

**原因**：IP 级别限流（IP QPS=5/10）拒绝了大部分请求。

**解决**：临时调高 Nacos 中 `shop-gateway.yml` 的限流规则：
- `/api/user/auth/**` 的 `ip-qps` 改为 200
- `/api/order/**` 的 `ip-qps` 改为 100

测试完成后恢复原配置。

### Q4: N-P-08 测试后所有接口都返回 403

**原因**：本机 IP 被自动拉黑（黑名单有效期 1 小时）。

**解决**：
```powershell
docker exec shop-redis redis-cli DEL rate_limit:blacklist:ip
```

### Q5: 测试结果中 P99 远超目标

**可能原因**：
1. 后端服务资源不足（CPU/内存）
2. 数据库慢查询
3. Redis 连接池耗尽
4. 网络延迟

**排查步骤**：
1. 查看后端服务日志，是否有异常
2. 检查 MySQL 慢查询日志
3. 监控 Redis 连接数和命中率
4. 检查服务器 CPU/内存使用率

## 测试后清理

```powershell
# 1. 清理 Redis 黑名单（N-P-08 会拉黑本机 IP）
docker exec shop-redis redis-cli DEL rate_limit:blacklist:ip

# 2. 清理 Redis 秒杀库存（可选）
docker exec shop-redis redis-cli DEL seckill:stock:9999

# 3. 清理 token 文件（可选）
Remove-Item perf\data\tokens.csv -Force -ErrorAction SilentlyContinue
Remove-Item perf\data\order-tokens.csv -Force -ErrorAction SilentlyContinue

# 4. 恢复 Nacos 限流配置（如果临时调高了）
```
