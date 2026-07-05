<#
.SYNOPSIS
Sentinel 规则批量导入脚本（RL-05 引入）
把 config 目录下所有 *-flow-rules.json 和 *-degrade-rules.json 导入到 Nacos 的 SENTINEL_GROUP 分组

说明：
  - 限流/熔断规则使用 JSON 格式存储在 Nacos
  - 分组统一使用 SENTINEL_GROUP（与 application.yml 中 sentinel.datasource.*.nacos.group-id 对应）
  - namespace 使用 dev（开发环境）
  - 服务启动时通过 sentinel-datasource-nacos 自动拉取规则

使用方法：
  PowerShell 中执行：.\docker\nacos\import-sentinel-rules.ps1
#>

# 基础配置
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$configDir = Join-Path $scriptPath "config"
$nacosUrl = "http://localhost:8976/nacos/v1/cs/configs"
$namespace = "dev"
$group = "SENTINEL_GROUP"

Write-Host "=========================================="
Write-Host "  Sentinel Rules Batch Import Tool"
Write-Host "=========================================="
Write-Host "Config Dir: $configDir"
Write-Host "Nacos URL:  $nacosUrl"
Write-Host "Namespace:  $namespace"
Write-Host "Group:      $group"
Write-Host ""

# 健康检查
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8976/nacos/v1/console/health/liveness" -Method Get -TimeoutSec 5
    Write-Host "[OK] Nacos service is online"
}
catch {
    Write-Host "[ERROR] Cannot reach Nacos. Please run: docker-compose up -d nacos"
    Read-Host "Press Enter to exit"
    exit 1
}

# 加载所有 *-rules.json 文件
$jsonFiles = Get-ChildItem -Path $configDir -Filter "*-rules.json" | Sort-Object Name
if (-not $jsonFiles) {
    Write-Host "[ERROR] No *-rules.json config files found in config directory"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "Found $($jsonFiles.Count) Sentinel rule files"
Write-Host ""

$successCount = 0
$failCount = 0

# 导入循环
foreach ($file in $jsonFiles) {
    $dataId = $file.Name
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)

    $body = @{
        dataId  = $dataId
        group   = $group
        tenant  = $namespace
        type    = "json"
        content = $content
    }

    try {
        $response = Invoke-RestMethod -Uri $nacosUrl -Method Post -Body $body -ContentType "application/x-www-form-urlencoded; charset=utf-8"
        if ($response -eq "true") {
            Write-Host "  [OK]   $dataId"
            $successCount++
        }
        else {
            Write-Host "  [FAIL] $dataId - Response: $response"
            $failCount++
        }
    }
    catch {
        Write-Host "  [ERROR] $dataId - $($_.Exception.Message)"
        $failCount++
    }
}

Write-Host ""
Write-Host "=========================================="
Write-Host "  Import finished: $successCount success, $failCount failed"
Write-Host "=========================================="

if ($failCount -eq 0) {
    Write-Host ""
    Write-Host "Verify: open http://localhost:8976/nacos"
    Write-Host "Switch to dev namespace, SENTINEL_GROUP group to see Sentinel rules"
}

Read-Host "Press Enter to exit"
