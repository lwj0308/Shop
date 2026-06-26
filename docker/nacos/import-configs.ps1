# Nacos 配置批量导入脚本
# 用途：通过 Nacos API 批量导入 docker/nacos/config/ 目录下的所有 .yml 配置文件
# 使用方法：在项目根目录执行 .\docker\nacos\import-configs.ps1
#
# 为什么用 API 而不是 ZIP？
# Nacos 的 ZIP 导入功能对目录结构有严格要求（必须有 DEFAULT_GROUP 文件夹），
# 容易报"未读取到合法数据"错误。用 API 直接导入更稳定可靠。

# 配置参数
$configDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$configDir = Join-Path $configDir "config"
$nacosUrl = "http://localhost:8976/nacos/v1/cs/configs"
$namespace = "dev"

Write-Host "=========================================="
Write-Host "  Nacos 配置批量导入工具"
Write-Host "=========================================="
Write-Host "配置目录: $configDir"
Write-Host "Nacos地址: $nacosUrl"
Write-Host "命名空间: $namespace"
Write-Host ""

# 检查 Nacos 是否在线
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8976/nacos/v1/console/health/liveness" -Method Get -TimeoutSec 5
    Write-Host "[OK] Nacos 服务在线"
} catch {
    Write-Host "[ERROR] Nacos 服务无法访问，请先启动：docker-compose up -d nacos"
    exit 1
}

# 获取所有 .yml 配置文件
$ymlFiles = Get-ChildItem $configDir -Filter "*.yml" | Sort-Object Name
Write-Host "找到 $($ymlFiles.Count) 个配置文件"
Write-Host ""

$success = 0
$fail = 0

foreach ($file in $ymlFiles) {
    $dataId = $file.Name
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)

    # 用 Hashtable 构建请求体，PowerShell 会自动处理 URL 编码
    # 避免 & 字符在 PowerShell 中被解析为调用运算符
    $body = @{
        dataId  = $dataId
        group   = "DEFAULT_GROUP"
        tenant  = $namespace
        type    = "yaml"
        content = $content
    }

    try {
        $response = Invoke-RestMethod -Uri $nacosUrl -Method Post -Body $body -ContentType "application/x-www-form-urlencoded; charset=utf-8"
        if ($response -eq "true") {
            Write-Host "  [OK]   $dataId"
            $success++
        } else {
            Write-Host "  [FAIL] $dataId - 返回: $response"
            $fail++
        }
    } catch {
        Write-Host "  [ERROR] $dataId - $($_.Exception.Message)"
        $fail++
    }
}

Write-Host ""
Write-Host "=========================================="
Write-Host "  导入完成: $success 成功, $fail 失败"
Write-Host "=========================================="

if ($fail -eq 0) {
    Write-Host ""
    Write-Host "验证：打开 http://localhost:8976/nacos"
    Write-Host "切换到 dev 命名空间，应该看到 $success 个配置文件"
}
