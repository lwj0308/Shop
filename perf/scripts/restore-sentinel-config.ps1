<#
.SYNOPSIS
恢复 Sentinel 配置到 Nacos 运行时环境
将本地 degrade 和 system 规则推送到 Nacos（dev 命名空间，SENTINEL_GROUP 分组）
#>

$nacosUrl = "http://localhost:8976/nacos/v1/cs/configs"
$namespace = "dev"
$group = "SENTINEL_GROUP"

# 需要恢复的配置文件列表
$files = @(
    "shop-product-degrade-rules.json",
    "shop-product-system-rules.json"
)

$configDir = "d:\workspace\business\Shop\docker\nacos\config"

Write-Host "=========================================="
Write-Host "  Restore Sentinel Config to Nacos"
Write-Host "=========================================="

$successCount = 0
$failCount = 0

foreach ($fileName in $files) {
    $filePath = Join-Path $configDir $fileName
    if (-not (Test-Path $filePath)) {
        Write-Host "[ERROR] File not found: $filePath"
        $failCount++
        continue
    }

    $content = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
    Write-Host ""
    Write-Host "Publishing: $fileName"
    Write-Host "Content: $content"

    $body = @{
        dataId  = $fileName
        group   = $group
        tenant  = $namespace
        type    = "json"
        content = $content
    }

    try {
        $response = Invoke-RestMethod -Uri $nacosUrl -Method Post -Body $body -ContentType "application/x-www-form-urlencoded; charset=utf-8"
        if ($response -eq "true") {
            Write-Host "[OK] $fileName published successfully"
            $successCount++
        }
        else {
            Write-Host "[FAIL] $fileName - Response: $response"
            $failCount++
        }
    }
    catch {
        Write-Host "[ERROR] $fileName - $($_.Exception.Message)"
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errorBody = $reader.ReadToEnd()
            Write-Host "Error Body: $errorBody"
        }
        $failCount++
    }
}

Write-Host ""
Write-Host "=========================================="
Write-Host "  Result: $successCount success, $failCount failed"
Write-Host "=========================================="
