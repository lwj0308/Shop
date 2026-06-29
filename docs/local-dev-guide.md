# ShopMall 商城 - 本地开发手册（Windows）

## 目录

1. [环境要求](#1-环境要求)
2. [第一步：启动中间件](#2-第一步启动中间件)
3. [第二步：初始化Nacos配置](#3-第二步初始化nacos配置)
4. [第三步：启动后端微服务](#4-第三步启动后端微服务)
5. [第四步：启动前端应用](#5-第四步启动前端应用)
6. [验证全部跑通](#6-验证全部跑通)
7. [常用操作](#7-常用操作)
8. [常见问题](#8-常见问题)

---

## 1. 环境要求

| 软件 | 最低版本 | 验证命令 | 下载地址 |
|------|---------|---------|---------|
| JDK | 21 | `java -version` | https://adoptium.net/ |
| Maven | 3.9+ | `mvn -version` | https://maven.apache.org/download.cgi |
| Node.js | 18+ | `node -v` | https://nodejs.org/ |
| npm | 9+ | `npm -v` | 随Node.js安装 |
| Docker Desktop | 最新 | `docker -v` | https://www.docker.com/products/docker-desktop/ |
| Git | 2.x | `git --version` | https://git-scm.com/ |
| IDE | - | - | IntelliJ IDEA（推荐）或 VS Code |

### 环境变量检查

打开 PowerShell，依次执行：

```powershell
java -version       # 应显示 openjdk version "21.x.x"
mvn -version        # 应显示 Apache Maven 3.9.x
node -v             # 应显示 v18.x.x 或更高
docker -v           # 应显示 Docker version 2x.x.x
```

> **如果某个命令报错"不是内部命令"**，说明该软件没安装或没加入系统PATH，需要先安装。

---

## 2. 第一步：启动中间件

所有中间件（MySQL、Redis、Nacos、RocketMQ、MinIO、Elasticsearch）都通过 Docker Compose 一键启动。

### 2.1 了解 .env 配置文件

项目根目录的 `.env` 文件集中管理所有 Docker 容器的端口和密码配置，避免端口冲突：

`env
MYSQL_PORT=4306           # MySQL端口（避开本机3306）
MYSQL_ROOT_PASSWORD=root  # MySQL root密码
NACOS_PORT=8976           # Nacos HTTP端口（避开Hyper-V保留范围）
NACOS_GRPC_PORT=9976      # Nacos gRPC端口（= HTTP + 1000）
ROCKETMQ_NAMESRV_PORT=9977 # RocketMQ端口（避开Hyper-V保留范围）
ES_HTTP_PORT=9201         # ES端口（避开本机9200）
# ... 完整配置见 .env 文件
` 

> 修改端口后，需同步修改各微服务 `application.yml` 中的默认端口值。

### 2.2 启动 Docker Desktop

双击桌面上的 Docker Desktop 图标，等待左下角显示绿色 "Engine running"。

### 2.3 启动所有中间件

在项目根目录打开 PowerShell，执行：

```powershell
cd d:\workspace\business\Shop
docker-compose up -d
```

> 首次启动会拉取镜像，可能需要 10-20 分钟（取决于网速）。

### 2.4 检查启动状态

```powershell
docker-compose ps
```

应该看到以下服务都是 `Up` 状态：

| 容器名 | 端口 | 说明 |
|--------|------|------|
| shop-mysql | 4306 | MySQL数据库（4306避免和本地MySQL冲突） |
| shop-redis | 6379 | Redis缓存 |
| shop-nacos | 8976 | Nacos配置中心（8976避开Hyper-V保留端口） |
| shop-rocketmq-namesrv | 9977 | RocketMQ注册中心（9977避开Hyper-V保留端口） |
| shop-rocketmq-broker | 10911 | RocketMQ消息队列 |
| shop-rocketmq-dashboard | 8080 | RocketMQ控制台 |
| shop-minio | 9000/9001 | MinIO对象存储 |
| shop-elasticsearch | 9201 | Elasticsearch搜索引擎（9201避开本地ES冲突） |
| shop-kibana | 5601 | Kibana可视化 |

### 2.5 验证MySQL数据初始化

```powershell
docker exec -it shop-mysql mysql -uroot -proot -e "SHOW DATABASES;"
```

应该看到 8 个数据库：shop_user、shop_merchant、shop_product、shop_cart、shop_order、shop_payment、shop_admin、shop_seata。

验证测试数据：

```powershell
docker exec -it shop-mysql mysql -uroot -proot -e "USE shop_user; SELECT id, nickname, phone FROM user;"
```

应该看到 5 个用户（张三、李四、王五、赵六、孙七）。

### 2.6 各中间件控制台

| 服务 | 地址 | 账号/密码 |
|------|------|----------|
| Nacos | http://localhost:8976/nacos | nacos / nacos |
| MinIO | http://localhost:9001 | minioadmin / minioadmin |
| RocketMQ Dashboard | http://localhost:8080 | 无需登录 |
| Elasticsearch | http://localhost:9201 | 无需登录 |
| Kibana | http://localhost:5601 | 无需登录 |

---

## 3. 第二步：初始化Nacos配置

> **这一步很重要！** 微服务启动时会从Nacos读取数据库连接、Redis地址等配置，如果不配置，服务启动不了。

### 3.1 创建命名空间

1. 浏览器打开 http://localhost:8976/nacos
2. 用 nacos / nacos 登录
3. 左侧菜单 → **命名空间**
4. 点击 **新建命名空间**
5. 命名空间ID填 `dev`，命名空间名填 `开发环境`
6. 点击确定

### 3.2 导入配置文件

#### 方式一：API 脚本导入（最推荐，最可靠）

项目提供了跨平台脚本，通过 Nacos API 直接导入，不依赖 ZIP 格式：

**Windows (PowerShell):**
```powershell
.\docker\nacos\import-configs.ps1
```

**Linux / macOS (Bash):**
```bash
bash docker/nacos/import-configs.sh
```

脚本会自动读取所有 .yml 配置文件并导入到 `dev` 命名空间。

#### 方式二：ZIP 批量导入

项目已提供打包好的 ZIP 文件，可直接导入：

1. 左侧菜单 → **配置管理** → **配置列表**

2. 右上角切换到 `dev` 命名空间

3. 点击 **导入配置**，选择 `docker/nacos/config/nacos-config-import.zip`

4. 点击 **确定**，一键导入全部 11 个配置文件

> **注意**：Nacos 要求 ZIP 内必须有 `DEFAULT_GROUP` 文件夹，直接打包 .yml 文件会报错。项目已提供正确格式的 ZIP 文件。

#### 方式三：手动逐个创建（更清楚每个配置的内容）

按以下步骤逐个创建配置：

#### 3.2.1 公共配置 common.yml

- Data ID: `common.yml`
- Group: `DEFAULT_GROUP`
- 配置格式: `YAML`
- 配置内容：打开 `docker/nacos/config/common.yml` 文件，复制全部内容粘贴进去

#### 3.2.2 各服务配置

按同样的方式创建以下配置（Data ID = 服务名.yml）：

| Data ID | 对应文件 |
|---------|---------|
| shop-gateway.yml | docker/nacos/config/shop-gateway.yml |
| shop-user.yml | docker/nacos/config/shop-user.yml |
| shop-merchant.yml | docker/nacos/config/shop-merchant.yml |
| shop-product.yml | docker/nacos/config/shop-product.yml |
| shop-cart.yml | docker/nacos/config/shop-cart.yml |
| shop-order.yml | docker/nacos/config/shop-order.yml |
| shop-payment.yml | docker/nacos/config/shop-payment.yml |
| shop-admin.yml | docker/nacos/config/shop-admin.yml |
| shop-marketing.yml | docker/nacos/config/shop-marketing.yml |
| shop-seckill.yml | docker/nacos/config/shop-seckill.yml |

### 3.3 验证配置

在Nacos配置列表中，切换到 `dev` 命名空间，应该看到 11 个配置文件。

---

## 4. 第三步：启动后端微服务

### 4.1 编译项目

```powershell
cd d:\workspace\business\Shop
mvn clean install -DskipTests
```

> 首次编译会下载Maven依赖，可能需要 5-10 分钟。如果下载慢，可以配置阿里云Maven镜像。

### 4.2 启动顺序

微服务有依赖关系，需要按顺序启动：

| 顺序 | 服务 | 端口 | 说明 |
|------|------|------|------|
| 1 | shop-gateway | 8844 | API网关（必须第一个启动） |
| 2 | shop-user | 8845 | 用户服务 |
| 3 | shop-merchant | 8846 | 商家服务 |
| 4 | shop-product | 8847 | 商品服务 |
| 5 | shop-cart | 8848 | 购物车服务 |
| 6 | shop-order | 8849 | 订单服务 |
| 7 | shop-payment | 8850 | 支付服务 |
| 8 | shop-admin | 8851 | 管理后台服务 |

### 4.3 在IDEA中启动（推荐）

1. 用 IntelliJ IDEA 打开项目根目录 `d:\workspace\business\Shop`
2. 等待IDEA索引完成（右下角进度条走完）
3. 找到每个服务的 Application 启动类：
   - `shop-gateway/src/main/java/com/shop/gateway/GatewayApplication.java`
   - `shop-user/src/main/java/com/shop/user/UserApplication.java`
   - `shop-merchant/src/main/java/com/shop/merchant/MerchantApplication.java`
   - `shop-product/src/main/java/com/shop/product/ProductApplication.java`
   - `shop-cart/src/main/java/com/shop/cart/CartApplication.java`
   - `shop-order/src/main/java/com/shop/order/OrderApplication.java`
   - `shop-payment/src/main/java/com/shop/payment/PaymentApplication.java`
4. 右键每个Application类 → **Run**（或 Debug）
5. 按上面的顺序依次启动

### 4.4 用命令行启动

如果不用IDEA，可以用Maven命令：

```powershell
# 每个服务打开一个PowerShell窗口
cd d:\workspace\business\Shop

# 启动网关
mvn spring-boot:run -pl shop-gateway

# 启动用户服务（新窗口）
mvn spring-boot:run -pl shop-user

# 启动商家服务（新窗口）
mvn spring-boot:run -pl shop-merchant

# ... 其他服务同理
```

### 4.5 验证服务注册

打开 Nacos 控制台 → **服务管理** → **服务列表**，切换到 `dev` 命名空间，应该看到 7 个服务实例。

### 4.6 验证API网关

```powershell
# 测试网关是否正常
curl http://localhost:8844/api/user/auth/login

# 测试商品列表（公开接口）
curl http://localhost:8844/api/product/list
```

---

## 5. 第四步：启动前端应用

### 5.1 安装依赖

```powershell
cd d:\workspace\business\Shop\shop-frontend
npm install
```

> 首次安装会下载npm包，可能需要 3-5 分钟。

### 5.2 启动用户Web端

```powershell
cd d:\workspace\business\Shop\shop-frontend
npm run dev:user
```

浏览器打开 http://localhost:3000 （Vite会自动打开）

### 5.3 启动商家后台

新开一个PowerShell窗口：

```powershell
cd d:\workspace\business\Shop\shop-frontend
npm run dev:merchant
```

浏览器打开 http://localhost:3001

---

## 6. 验证全部跑通

### 6.1 用户端购物流程

1. 打开 http://localhost:3000
2. 点击右上角"我的" → 注册账号（或用测试账号登录：13800001111 / 123456）
3. 浏览首页商品
4. 点击商品进入详情页
5. 选择SKU规格 → 点击"加入购物车"
6. 进入购物车 → 勾选商品 → 点击"去结算"
7. 确认地址 → 提交订单
8. 模拟支付

### 6.2 商家后台流程

1. 打开 http://localhost:3001
2. 用测试商家账号登录：13900001111 / 123456
3. 查看首页概览数据
4. 进入商品管理 → 查看商品列表
5. 进入订单管理 → 查看订单列表

### 6.3 API文档

每个微服务启动后，可以访问 Swagger API 文档：

| 服务 | 文档地址 |
|------|---------|
| 用户服务 | http://localhost:8845/swagger-ui.html |
| 商家服务 | http://localhost:8846/swagger-ui.html |
| 商品服务 | http://localhost:8847/swagger-ui.html |
| 购物车服务 | http://localhost:8848/swagger-ui.html |
| 订单服务 | http://localhost:8849/swagger-ui.html |
| 支付服务 | http://localhost:8850/swagger-ui.html |
| 管理后台服务 | http://localhost:8851/swagger-ui.html |

---

## 7. 常用操作

### 7.1 启动/停止中间件

```powershell
# 启动所有中间件
docker-compose up -d

# 只启动MySQL和Redis（最小化启动）
docker-compose up -d mysql redis nacos

# 停止所有中间件
docker-compose down

# 停止并清除数据（慎用！会删除数据库数据）
docker-compose down -v

# 查看某个服务日志
docker-compose logs -f mysql
docker-compose logs -f nacos
```

### 7.2 重新初始化数据库

如果需要重置数据库数据：

```powershell
# 1. 停止MySQL
docker-compose down mysql

# 2. 删除MySQL数据
Remove-Item -Recurse -Force docker\mysql\data\*

# 3. 重新启动（会自动执行init目录下的SQL）
docker-compose up -d mysql
```

### 7.3 查看Redis数据

```powershell
# 进入Redis命令行
docker exec -it shop-redis redis-cli

# 查看所有key
KEYS *

# 查看某个key的值
GET token:xxx

# 退出
exit
```

### 7.4 查看MySQL数据

```powershell
# 进入MySQL命令行
docker exec -it shop-mysql mysql -uroot -proot

# 切换数据库
USE shop_user;

# 查询用户
SELECT * FROM user;

# 退出
exit
```

### 7.5 MinIO创建Bucket

1. 打开 http://localhost:9001
2. 用 minioadmin / minioadmin 登录
3. 点击 **Create Bucket**
4. 输入 Bucket Name: `shop`
5. 点击 **Create Bucket**
6. 点击进入 `shop` bucket → **Manage** → **Access Policy**
7. 设置为 **Public**（开发环境方便图片直接访问）

---

## 8. 常见问题

### Q1: Docker启动MySQL报错 "port is already allocated"

**原因**：3306端口被本机已安装的MySQL占用。

**解决**：项目已将Docker MySQL端口改为4306，在 `.env` 文件中：
```
MYSQL_PORT=4306
```
Nacos中的 `common.yml` 默认端口也已改为4306。如果还有冲突，可以改成其他端口（如4308）。

### Q2: 微服务启动报错 "Unable to connect to Nacos"

**原因**：Nacos还没启动完成，或者命名空间没创建。

**解决**：
1. 确认Nacos已启动：浏览器打开 http://localhost:8976/nacos
2. 确认 `dev` 命名空间已创建
3. 确认配置文件已导入

### Q3: 微服务启动报错 "Unable to connect to MySQL"

**原因**：MySQL还没启动完成，或密码不对。

**解决**：
1. 确认MySQL已启动：`docker-compose ps mysql`
2. 确认密码是 `shop123456`（和 `.env` 文件一致）
3. 确认Nacos中 `common.yml` 的数据库密码配置正确

### Q4: 前端npm install报错

**原因**：npm网络问题。

**解决**：切换到淘宝镜像：
```powershell
npm config set registry https://registry.npmmirror.com
npm install
```

### Q5: 前端页面打开空白

**原因**：后端服务没启动，API请求失败。

**解决**：
1. 确认网关服务已启动（8844端口）
2. 确认至少启动了用户服务和商品服务
3. 浏览器F12打开控制台，查看Network请求是否报错

### Q6: Elasticsearch启动报错 "max virtual memory areas vm.max_map_count"

**原因**：Windows Docker的ES需要调整系统参数。

**解决**：以管理员身份打开PowerShell：
```powershell
wsl -d docker-desktop
sysctl -w vm.max_map_count=262144
exit
```
然后重启ES：`docker-compose restart elasticsearch`

### Q7: 登录时提示密码错误

**原因**：测试数据的BCrypt密码哈希可能不匹配。

**解决**：重新生成密码哈希，或直接在数据库中更新：
```sql
-- 更新用户1001的密码为123456
UPDATE shop_user.user SET password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH' WHERE id = 1001;
```

### Q8: RocketMQ Broker启动失败

**原因**：broker.conf中的brokerIP1配置问题。

**解决**：编辑 `docker/rocketmq/broker/conf/broker.conf`，确保：
```
brokerIP1 = 127.0.0.1
```
然后重启：`docker-compose restart rocketmq-broker`

---

## 快速启动清单

一切就绪后，日常开发只需要：

```powershell
# 1. 启动中间件（如果Docker Desktop已运行）
cd d:\workspace\business\Shop
docker-compose up -d

# 2. 在IDEA中启动8个微服务（按顺序）

# 3. 启动前端
cd d:\workspace\business\Shop\shop-frontend
npm run dev:user      # 用户端 http://localhost:3000
npm run dev:merchant  # 商家端 http://localhost:3001
```

## 端口总览

| 端口 | 服务 | 说明 |
|------|------|------|
| 3000 | web-user | 用户Web端 |
| 3001 | web-merchant | 商家后台 |
| 4306 | MySQL | 数据库（4306避开本地MySQL冲突） |
| 5601 | Kibana | ES可视化 |
| 6379 | Redis | 缓存 |
| 8080 | RocketMQ Dashboard | MQ控制台 |
| 8844 | shop-gateway | API网关 |
| 8845 | shop-user | 用户服务 |
| 8846 | shop-merchant | 商家服务 |
| 8847 | shop-product | 商品服务 |
| 8848 | shop-cart | 购物车服务 |
| 8849 | shop-order | 订单服务 |
| 8850 | shop-payment | 支付服务 |
| 8851 | shop-admin | 管理后台服务 |
| 8976 | Nacos | 配置中心（8976避开Hyper-V保留端口） |
| 9000 | MinIO API | 对象存储API |
| 9001 | MinIO Console | 对象存储控制台 |
| 9201 | Elasticsearch | 搜索引擎（9201避开本地ES冲突） |
| 9977 | RocketMQ NameServer | MQ注册中心（9977避开Hyper-V保留端口） |
| 10911 | RocketMQ Broker | MQ消息服务 |

> 所有Docker中间件端口配置在项目根目录的 `.env` 文件中，可统一修改。
