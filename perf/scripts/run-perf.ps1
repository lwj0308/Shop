# ============================================================
# 性能测试一键运行脚本（PowerShell 版）
# <p>
# 用途：一键执行 N-P-01~08 性能测试场景
# 使用方式：
#   .\perf\scripts\run-perf.ps1 -Scenario all        # 运行所有场景
#   .\perf\scripts\run-perf.ps1 -Scenario N-P-01     # 只运行 N-P-01
#   .\perf\scripts\run-perf.ps1 -Scenario N-P-01,N-P-02  # 运行多个场景
#   .\perf\scripts\run-perf.ps1 -Scenario all -SkipDataPrep  # 跳过数据准备
# </p>
# ============================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$Scenario,

    [switch]$SkipDataPrep = $false,

    [switch]$SkipCleanup = $false
)

# ==================== 配置区 ====================
$PROJECT_ROOT = "d:\workspace\business\Shop"
$JMETER_HOME = $env:JMETER_HOME  # 从环境变量读取 JMeter 安装路径
$PERF_DIR = Join-Path $PROJECT_ROOT "perf"
$JMX_DIR = Join-Path $PERF_DIR "jmx"
$RESULTS_DIR = Join-Path $PERF_DIR "results"
$DATA_DIR = Join-Path $PERF_DIR "data"

# 场景与脚本映射
$ScenarioMap = @{
    "N-P-01" = "N-P-01-product-detail-normal.jmx"
    "N-P-02" = "N-P-02-product-detail-hot.jmx"
    "N-P-03" = "N-P-03-seckill.jmx"
    "N-P-04" = "N-P-04-user-login.jmx"
    "N-P-05" = "N-P-05-create-order.jmx"
    "N-P-06" = "N-P-06-home-page.jmx"
    "N-P-07" = "N-P-07-stability-24h.jmx"
    "N-P-08" = "N-P-08-rate-limit-degrade.jmx"
}

# ==================== 工具函数 ====================

/**
 * 打印带颜色的日志信息
 * 小白理解：让输出更好看，成功绿色、警告黄色、错误红色
 */
function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $color = switch ($Level) {
        "INFO"    { "White" }
        "SUCCESS" { "Green" }
        "WARN"    { "Yellow" }
        "ERROR"   { "Red" }
        default   { "White" }
    }
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$timestamp] [$Level] $Message" -ForegroundColor $color
}

/**
 * 检查 JMeter 是否可用
 */
function Test-JMeter {
    $jmeterCmd = Get-Command jmeter -ErrorAction SilentlyContinue
    if ($jmeterCmd) {
        return $jmeterCmd.Source
    }
    if ($JMETER_HOME -and (Test-Path (Join-Path $JMETER_HOME "bin\jmeter.bat"))) {
        return Join-Path $JMETER_HOME "bin\jmeter.bat"
    }
    return $null
}

/**
 * 执行单个性能测试场景
 */
function Invoke-PerfScenario {
    param([string]$ScenarioName)

    $jmxFile = Join-Path $JMX_DIR $ScenarioMap[$ScenarioName]
    $resultFile = Join-Path $RESULTS_DIR "$ScenarioName-result.jtl"
    $reportDir = Join-Path $RESULTS_DIR "$ScenarioName-report"

    if (-not (Test-Path $jmxFile)) {
        Write-Log "测试脚本不存在: $jmxFile" "ERROR"
        return $false
    }

    # 清理旧结果
    if (Test-Path $resultFile) { Remove-Item $resultFile -Force }
    if (Test-Path $reportDir) { Remove-Item $reportDir -Recurse -Force }

    Write-Log "=========================================="
    Write-Log "开始执行: $ScenarioName"
    Write-Log "脚本: $jmxFile"
    Write-Log "结果: $resultFile"
    Write-Log "报告: $reportDir"
    Write-Log "=========================================="

    $startTime = Get-Date

    try {
        & $jmeterCmd -n -t $jmxFile -l $resultFile -e -o $reportDir
        $exitCode = $LASTEXITCODE
        $duration = (Get-Date) - $startTime

        if ($exitCode -eq 0) {
            Write-Log "$ScenarioName 执行完成（耗时: $($duration.ToString('hh\:mm\:ss'))）" "SUCCESS"
            Write-Log "HTML 报告: $reportDir\index.html" "INFO"
            return $true
        } else {
            Write-Log "$ScenarioName 执行失败（退出码: $exitCode）" "ERROR"
            return $false
        }
    } catch {
        Write-Log "$ScenarioName 执行异常: $_" "ERROR"
        return $false
    }
}

/**
 * 准备测试数据
 * 小白理解：性能测试需要先在数据库里准备好测试用户、热点商品、秒杀活动等数据
 */
function Invoke-DataPrep {
    Write-Log "开始准备性能测试数据..." "INFO"

    # 1. 执行 SQL 脚本
    $sqlFile = Join-Path $DATA_DIR "perf-test-data.sql"
    Write-Log "执行 SQL: $sqlFile" "INFO"
    Write-Log "请输入 MySQL root 密码:" "WARN"
    $mysqlResult = mysql -uroot -p < $sqlFile 2>&1
    Write-Log "SQL 执行完成" "SUCCESS"

    # 2. 预热 Redis 秒杀库存
    Write-Log "预热 Redis 秒杀库存..." "INFO"
    $redisResult = docker exec shop-redis redis-cli SET seckill:stock:9999 100000 2>&1
    Write-Log "Redis 预热结果: $redisResult" "SUCCESS"

    # 3. 清理旧的 token 文件
    $tokensFile = Join-Path $DATA_DIR "tokens.csv"
    $orderTokensFile = Join-Path $DATA_DIR "order-tokens.csv"
    if (Test-Path $tokensFile) { Remove-Item $tokensFile -Force }
    if (Test-Path $orderTokensFile) { Remove-Item $orderTokensFile -Force }
    Write-Log "已清理旧的 token 文件" "INFO"
}

/**
 * 清理测试后的黑名单（N-P-08 会拉黑本机 IP）
 */
function Invoke-Cleanup {
    Write-Log "清理 Redis 黑名单..." "INFO"
    # 小白理解：BlacklistFilter.java 中定义的 IP 黑名单 Redis Key 是 "blacklist:ip"
    # N-P-08 会自动拉黑本机 IP 1 小时，测试完成后需手动清除，否则后续测试都会被 403 拦截
    $blacklistResult = docker exec shop-redis redis-cli DEL blacklist:ip 2>&1
    Write-Log "黑名单清理结果: $blacklistResult" "SUCCESS"
}

# ==================== 主流程 ====================

Write-Log "Shop 性能测试工具 v1.0" "INFO"
Write-Log "项目路径: $PROJECT_ROOT" "INFO"

# 检查 JMeter
$jmeterCmd = Test-JMeter
if (-not $jmeterCmd) {
    Write-Log "未找到 JMeter！请设置 JMETER_HOME 环境变量或将 jmeter 加入 PATH" "ERROR"
    Write-Log "例如: `$env:JMETER_HOME = 'D:\apache-jmeter-5.6.3'" "WARN"
    exit 1
}
Write-Log "JMeter 路径: $jmeterCmd" "INFO"

# 创建结果目录
if (-not (Test-Path $RESULTS_DIR)) {
    New-Item -ItemType Directory -Path $RESULTS_DIR -Force | Out-Null
    Write-Log "创建结果目录: $RESULTS_DIR" "INFO"
}

# 数据准备
if (-not $SkipDataPrep) {
    Invoke-DataPrep
} else {
    Write-Log "跳过数据准备（-SkipDataPrep）" "WARN"
}

# 解析场景列表
if ($Scenario -eq "all") {
    $scenarios = @("N-P-01", "N-P-02", "N-P-03", "N-P-04", "N-P-05", "N-P-06", "N-P-08")
    # 注意：N-P-07 是 24h 稳定性测试，不包含在 all 中，需单独运行
    Write-Log "注意：N-P-07（24h 稳定性测试）未包含在 all 中，需单独运行" "WARN"
} else {
    $scenarios = $Scenario -split ","
}

Write-Log "待执行场景: $($scenarios -join ', ')" "INFO"

# 执行场景
$results = @{}
foreach ($scn in $scenarios) {
    $scn = $scn.Trim()
    if ($ScenarioMap.ContainsKey($scn)) {
        $success = Invoke-PerfScenario -ScenarioName $scn
        $results[$scn] = $success
    } else {
        Write-Log "未知场景: $scn，跳过" "WARN"
    }
}

# 清理
if (-not $SkipCleanup) {
    Invoke-Cleanup
}

# 汇总
Write-Log "==========================================" "INFO"
Write-Log "性能测试汇总" "INFO"
Write-Log "==========================================" "INFO"
foreach ($scn in $results.Keys) {
    $status = if ($results[$scn]) { "成功" } else { "失败" }
    $color = if ($results[$scn]) { "Green" } else { "Red" }
    Write-Host "  $scn : $status" -ForegroundColor $color
}

$successCount = ($results.Values | Where-Object { $_ }).Count
$failCount = ($results.Values | Where-Object { -not $_ }).Count
Write-Log "总计: $successCount 成功, $failCount 失败" "INFO"
Write-Log "HTML 报告目录: $RESULTS_DIR" "INFO"
