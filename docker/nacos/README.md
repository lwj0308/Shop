# Nacos 配置导入说明

## 1. 启动 Nacos

先通过 docker-compose 启动 Nacos：
```bash
docker-compose up -d nacos
```

## 2. 访问 Nacos 控制台

浏览器打开 http://localhost:8976/nacos
默认账号密码：nacos / nacos

## 3. 创建命名空间

在"命名空间"页面创建 `dev` 命名空间

## 4. 导入配置

### 方式一：API 脚本导入（最推荐，最可靠）

项目提供了跨平台脚本，通过 Nacos API 直接导入，不依赖 ZIP 格式：

**Windows (PowerShell):**
```powershell
.\docker\nacos\import-configs.ps1
```

**Linux / macOS (Bash):**
```bash
bash docker/nacos/import-configs.sh
```

脚本会自动读取 `config/` 目录下所有 .yml 文件并导入到 `dev` 命名空间。

### 方式二：ZIP 批量导入

项目已提供打包好的 ZIP 文件：`docker/nacos/config/nacos-config-import.zip`

1. 在"配置管理"页面，选择 `dev` 命名空间
2. 点击 **导入配置**
3. 选择 `nacos-config-import.zip` 文件
4. 点击 **确定**，一键导入全部 11 个配置文件

> **ZIP 格式说明**：Nacos 要求 ZIP 内必须有一个以分组名命名的文件夹（`DEFAULT_GROUP`），配置文件放在该文件夹内。直接打包 `.yml` 文件会报"未读取到合法数据"。
>
> 如需重新打包，在 PowerShell 中执行：
> ```powershell
> cd d:\workspace\business\Shop\docker\nacos\config
> # 创建临时目录结构
> $tmp = "$env:TEMP\nacos-import"
> New-Item -ItemType Directory -Path "$tmp\DEFAULT_GROUP" -Force | Out-Null
> Copy-Item *.yml "$tmp\DEFAULT_GROUP\"
> # 打包
> Compress-Archive -Path "$tmp\DEFAULT_GROUP" -DestinationPath nacos-config-import.zip -Force
> Remove-Item $tmp -Recurse -Force
> ```

### 方式三：手动逐个创建

在"配置管理"页面，选择 `dev` 命名空间，逐个创建配置：

1. 先创建 `common.yml`（公共配置，Data ID = common.yml, Group = DEFAULT_GROUP）
2. 再创建各服务配置（Data ID = 服务名.yml, Group = DEFAULT_GROUP）

## 5. 验证

启动任意微服务，检查是否能从 Nacos 读取到配置
