<#
.SYNOPSIS
Nacos config batch import script
Import all .yml configs from config folder via Nacos OpenAPI
#>

# Base config
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$configDir = Join-Path $scriptPath "config"
$nacosUrl = "http://localhost:8976/nacos/v1/cs/configs"
$namespace = "dev"

Write-Host "=========================================="
Write-Host "  Nacos Config Batch Import Tool"
Write-Host "=========================================="
Write-Host "Config Dir: $configDir"
Write-Host "Nacos URL: $nacosUrl"
Write-Host "Namespace: $namespace"
Write-Host ""

# Health check
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8976/nacos/v1/console/health/liveness" -Method Get -TimeoutSec 5
    Write-Host "[OK] Nacos service is online"
}
catch {
    Write-Host "[ERROR] Cannot reach Nacos. Please run: docker-compose up -d nacos"
    Read-Host "Press Enter to exit"
    exit 1
}

# Load config files
$ymlFiles = Get-ChildItem -Path $configDir -Filter "*.yml" | Sort-Object Name
if (-not $ymlFiles) {
    Write-Host "[ERROR] No .yml config files found in config directory"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "Found $($ymlFiles.Count) config files"
Write-Host ""

$successCount = 0
$failCount = 0

# Import loop
foreach ($file in $ymlFiles) {
    $dataId = $file.Name
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)

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
    Write-Host "Switch to dev namespace to see all configs"
}

Read-Host "Press Enter to exit"