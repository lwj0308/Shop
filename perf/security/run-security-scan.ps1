<#
.SYNOPSIS
    Shop Project Security Test Script (N-S-01 ~ N-S-09)
.DESCRIPTION
    Since OWASP ZAP cannot be downloaded (GitHub unreachable), this script implements
    OWASP Top 10 core security detection logic via PowerShell for API security testing.
#>

param(
    [string]$GatewayUrl = "http://localhost:8844",
    [string]$ReportDir = "d:\workspace\business\Shop\perf\security\results"
)

$ErrorActionPreference = "Continue"
if (-not (Test-Path $ReportDir)) { New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$script:AllResults = @()

function Add-Result {
    param([string]$TestId, [string]$TestName, [string]$Status, [string]$Details, [string]$Evidence = "")
    $script:AllResults += [PSCustomObject]@{
        TestId=$TestId; TestName=$TestName; Status=$Status; Details=$Details; Evidence=$Evidence
        Timestamp=(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
    }
    $color = switch ($Status) { "PASS"{"Green"}; "FAIL"{"Red"}; "WARN"{"Yellow"}; default{"Gray"} }
    Write-Host "[$Status] $TestId $TestName - $Details" -ForegroundColor $color
}

function Send-Request {
    param([string]$Url, [string]$Method="GET", [hashtable]$Headers=@{}, [string]$Body=$null, [int]$TimeoutSec=10)
    try {
        $params = @{ Uri=$Url; Method=$Method; UseBasicParsing=$true; TimeoutSec=$TimeoutSec; ErrorAction="Stop" }
        if ($Headers.Count -gt 0) { $params.Headers = $Headers }
        if ($Body) {
            $params.Body = $Body
            if (-not $Headers.ContainsKey("Content-Type")) {
                $h2 = $Headers.Clone(); $h2["Content-Type"]="application/json"; $params.Headers = $h2
            }
        }
        $resp = Invoke-WebRequest @params
        return @{ Success=$true; StatusCode=$resp.StatusCode; Headers=$resp.Headers; Body=$resp.Content }
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $body = ""
        if ($_.Exception.Response) {
            try { $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $body = $reader.ReadToEnd() } catch {}
        }
        return @{ Success=$false; StatusCode=$code; Headers=@{}; Body=$body; Error=$_.Exception.Message }
    }
}

function Get-UserToken {
    $r = Send-Request -Url "$GatewayUrl/api/user/auth/login" -Method POST -Body '{"phone":"13800001111","password":"123456"}'
    if ($r.Success -and $r.Body -match '"accessToken":"([^"]+)"') { return $matches[1] }
    return $null
}

# N-S-01: Baseline scan (HTTP security headers)
function Test-NS01-Baseline {
    Write-Host "`n========== N-S-01: Baseline Scan (HTTP Security Headers) ==========" -ForegroundColor Cyan
    $r = Send-Request -Url "$GatewayUrl/api/product/4001" -Method GET
    $headers = $r.Headers
    $securityHeaders = @("X-Content-Type-Options","X-Frame-Options","Strict-Transport-Security","X-XSS-Protection")
    foreach ($h in $securityHeaders) {
        if ($headers.ContainsKey($h)) {
            Add-Result -TestId "N-S-01" -TestName "Header: $h" -Status "PASS" -Details "Set: $($headers[$h])"
        } else {
            Add-Result -TestId "N-S-01" -TestName "Header: $h" -Status "WARN" -Details "Not set (recommend adding at gateway)"
        }
    }
    if ($headers.ContainsKey("Server")) {
        $serverVal = $headers["Server"]
        if ($serverVal -match "\d") {
            Add-Result -TestId "N-S-01" -TestName "Server version leak" -Status "FAIL" -Details "Server header has version: $serverVal"
        } else {
            Add-Result -TestId "N-S-01" -TestName "Server version leak" -Status "PASS" -Details "No version: $serverVal"
        }
    } else {
        Add-Result -TestId "N-S-01" -TestName "Server version leak" -Status "PASS" -Details "No Server header"
    }
    $corsR = Send-Request -Url "$GatewayUrl/api/product/4001" -Method GET -Headers @{ "Origin"="http://evil.com" }
    if ($corsR.Headers.ContainsKey("Access-Control-Allow-Origin")) {
        $corsVal = $corsR.Headers["Access-Control-Allow-Origin"]
        if ($corsVal -eq "*" -or $corsVal -eq "http://evil.com") {
            Add-Result -TestId "N-S-01" -TestName "CORS config" -Status "FAIL" -Details "CORS allows any origin: $corsVal"
        } else {
            Add-Result -TestId "N-S-01" -TestName "CORS config" -Status "PASS" -Details "CORS restricted: $corsVal"
        }
    } else {
        Add-Result -TestId "N-S-01" -TestName "CORS config" -Status "PASS" -Details "No CORS header (cross-origin denied)"
    }
}

# N-S-02: SQL Injection
function Test-NS02-SqlInjection {
    Write-Host "`n========== N-S-02: SQL Injection Test ==========" -ForegroundColor Cyan
    $payloads = @("' OR '1'='1","'; DROP TABLE product; --","' UNION SELECT * FROM user--","1' OR 1=1#","admin'--")
    foreach ($payload in $payloads) {
        $encodedPayload = [uri]::EscapeDataString($payload)
        $r = Send-Request -Url "$GatewayUrl/api/product/search?keyword=$encodedPayload" -Method GET
        if ($r.Body -match "SQL|SQLException|mysql|syntax error|ORA-|PostgreSQL") {
            Add-Result -TestId "N-S-02" -TestName "Search SQLi" -Status "FAIL" -Details "Payload: $payload leaked SQL error" -Evidence $r.Body.Substring(0,[math]::Min(200,$r.Body.Length))
        } elseif ($r.StatusCode -eq 500) {
            Add-Result -TestId "N-S-02" -TestName "Search SQLi" -Status "WARN" -Details "Payload: $payload caused 500 (may have injection)" -Evidence $r.Body
        } else {
            Add-Result -TestId "N-S-02" -TestName "Search SQLi" -Status "PASS" -Details "Payload: $payload no anomaly (HTTP $($r.StatusCode))"
        }
    }
    foreach ($payload in $payloads) {
        $body = "{`"phone`":`"$payload`",`"password`":`"123456`"}"
        $r = Send-Request -Url "$GatewayUrl/api/user/auth/login" -Method POST -Body $body
        if ($r.Body -match "SQL|SQLException|mysql|syntax error") {
            Add-Result -TestId "N-S-02" -TestName "Login SQLi" -Status "FAIL" -Details "Payload: $payload leaked SQL error" -Evidence $r.Body
        } elseif ($r.Body -match '"accessToken":"([^"]+)"') {
            Add-Result -TestId "N-S-02" -TestName "Login SQLi" -Status "FAIL" -Details "Payload: $payload bypassed auth!" -Evidence $r.Body
        } else {
            Add-Result -TestId "N-S-02" -TestName "Login SQLi" -Status "PASS" -Details "Payload: $payload rejected"
        }
    }
}

# N-S-03: XSS
function Test-NS03-Xss {
    Write-Host "`n========== N-S-03: XSS Test ==========" -ForegroundColor Cyan
    $xssPayloads = @('<script>alert(1)</script>','"><script>alert(1)</script>','<img src=x onerror=alert(1)>','javascript:alert(1)','<svg onload=alert(1)>')
    foreach ($payload in $xssPayloads) {
        $encodedPayload = [uri]::EscapeDataString($payload)
        $r = Send-Request -Url "$GatewayUrl/api/product/search?keyword=$encodedPayload" -Method GET
        if ($r.Body -and $r.Body.Contains($payload)) {
            Add-Result -TestId "N-S-03" -TestName "Search XSS" -Status "FAIL" -Details "Payload not escaped: $payload" -Evidence $r.Body.Substring(0,[math]::Min(200,$r.Body.Length))
        } else {
            Add-Result -TestId "N-S-03" -TestName "Search XSS" -Status "PASS" -Details "Payload escaped/filtered: $payload"
        }
    }
    $token = Get-UserToken
    if ($token) {
        foreach ($payload in $xssPayloads) {
            $escapedPayload = $payload -replace '"','\"' -replace '\\','\\\\'
            $body = "{`"productId`":4001,`"content`":`"$escapedPayload`",`"score`":5}"
            $r = Send-Request -Url "$GatewayUrl/api/product/comment/create" -Method POST -Headers @{"Authorization"="Bearer $token"} -Body $body
            if ($r.Body -and $r.Body.Contains($payload)) {
                Add-Result -TestId "N-S-03" -TestName "Comment XSS" -Status "FAIL" -Details "Comment not escaped: $payload" -Evidence $r.Body
            } elseif ($r.StatusCode -eq 200) {
                Add-Result -TestId "N-S-03" -TestName "Comment XSS" -Status "WARN" -Details "Comment accepted, check storage escaping: $payload"
            } else {
                Add-Result -TestId "N-S-03" -TestName "Comment XSS" -Status "PASS" -Details "Comment rejected/escaped: $payload (HTTP $($r.StatusCode))"
            }
        }
    } else {
        Add-Result -TestId "N-S-03" -TestName "Comment XSS" -Status "WARN" -Details "No token, skipped"
    }
}

# N-S-04: Auth & Authorization
function Test-NS04-Auth {
    Write-Host "`n========== N-S-04: Auth & Authorization Test ==========" -ForegroundColor Cyan
    $protectedApis = @(
        @{Url="$GatewayUrl/api/order/list"; Name="OrderList"},
        @{Url="$GatewayUrl/api/cart/list"; Name="CartList"},
        @{Url="$GatewayUrl/api/user/profile"; Name="UserProfile"},
        @{Url="$GatewayUrl/api/admin/user/list"; Name="AdminUserList"}
    )
    foreach ($api in $protectedApis) {
        $r = Send-Request -Url $api.Url -Method GET
        if ($r.StatusCode -eq 401 -or $r.StatusCode -eq 403) {
            Add-Result -TestId "N-S-04" -TestName "Unauth access $($api.Name)" -Status "PASS" -Details "Correctly denied (HTTP $($r.StatusCode))"
        } elseif ($r.StatusCode -eq 200) {
            Add-Result -TestId "N-S-04" -TestName "Unauth access $($api.Name)" -Status "FAIL" -Details "Accessible without login!" -Evidence $r.Body.Substring(0,[math]::Min(200,$r.Body.Length))
        } else {
            Add-Result -TestId "N-S-04" -TestName "Unauth access $($api.Name)" -Status "WARN" -Details "HTTP $($r.StatusCode)"
        }
    }
    $fakeToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjk5OTksInVzZXJuYW1lIjoiYWRtaW4ifQ.fakeSignature"
    $r = Send-Request -Url "$GatewayUrl/api/order/list" -Method GET -Headers @{"Authorization"="Bearer $fakeToken"}
    if ($r.StatusCode -eq 401 -or $r.StatusCode -eq 403) {
        Add-Result -TestId "N-S-04" -TestName "Fake token access" -Status "PASS" -Details "Fake token denied (HTTP $($r.StatusCode))"
    } else {
        Add-Result -TestId "N-S-04" -TestName "Fake token access" -Status "FAIL" -Details "Fake token works!" -Evidence $r.Body
    }
    $userToken = Get-UserToken
    if ($userToken) {
        $r = Send-Request -Url "$GatewayUrl/api/admin/user/list" -Method GET -Headers @{"Authorization"="Bearer $userToken"}
        if ($r.StatusCode -eq 401 -or $r.StatusCode -eq 403) {
            Add-Result -TestId "N-S-04" -TestName "User access admin API" -Status "PASS" -Details "Correctly denied (HTTP $($r.StatusCode))"
        } else {
            Add-Result -TestId "N-S-04" -TestName "User access admin API" -Status "FAIL" -Details "Privilege escalation!" -Evidence $r.Body
        }
    }
}

# N-S-05: Information Leak
function Test-NS05-InfoLeak {
    Write-Host "`n========== N-S-05: Information Leak Test ==========" -ForegroundColor Cyan
    $actuatorEndpoints = @("/actuator","/actuator/env","/actuator/health","/actuator/heapdump","/actuator/configprops","/actuator/beans","/actuator/mappings")
    foreach ($ep in $actuatorEndpoints) {
        $r = Send-Request -Url "$GatewayUrl$ep" -Method GET
        if ($r.StatusCode -eq 200) {
            if ($ep -eq "/actuator/health") {
                Add-Result -TestId "N-S-05" -TestName "Actuator: $ep" -Status "PASS" -Details "health endpoint open (normal)"
            } elseif ($r.Body.Length -gt 100 -and ($r.Body -match "password|secret|key|credential")) {
                Add-Result -TestId "N-S-05" -TestName "Actuator: $ep" -Status "FAIL" -Details "Endpoint leaks sensitive info!" -Evidence $r.Body.Substring(0,[math]::Min(300,$r.Body.Length))
            } else {
                Add-Result -TestId "N-S-05" -TestName "Actuator: $ep" -Status "WARN" -Details "Endpoint accessible (close in prod)"
            }
        } else {
            Add-Result -TestId "N-S-05" -TestName "Actuator: $ep" -Status "PASS" -Details "Closed (HTTP $($r.StatusCode))"
        }
    }
    $docEndpoints = @("/v2/api-docs","/v3/api-docs","/swagger-ui.html","/swagger-resources")
    foreach ($ep in $docEndpoints) {
        $r = Send-Request -Url "$GatewayUrl$ep" -Method GET
        if ($r.StatusCode -eq 200 -and $r.Body.Length -gt 100) {
            Add-Result -TestId "N-S-05" -TestName "API doc: $ep" -Status "WARN" -Details "Doc endpoint accessible (close in prod)"
        } else {
            Add-Result -TestId "N-S-05" -TestName "API doc: $ep" -Status "PASS" -Details "Closed (HTTP $($r.StatusCode))"
        }
    }
    $r = Send-Request -Url "$GatewayUrl/api/user/auth/login" -Method POST -Body "{invalid json}"
    if ($r.Body -match "at com\.|at org\.|at java\.|StackTrace|Caused by") {
        Add-Result -TestId "N-S-05" -TestName "Stacktrace leak" -Status "FAIL" -Details "Error response contains Java stacktrace" -Evidence $r.Body.Substring(0,[math]::Min(300,$r.Body.Length))
    } else {
        Add-Result -TestId "N-S-05" -TestName "Stacktrace leak" -Status "PASS" -Details "No stacktrace in error"
    }
}

# N-S-06: CSRF
function Test-NS06-Csrf {
    Write-Host "`n========== N-S-06: CSRF Test ==========" -ForegroundColor Cyan
    $token = Get-UserToken
    if ($token) {
        $orderBody = '{"items":[{"skuId":4001,"quantity":1}],"addressId":1}'
        $r3 = Send-Request -Url "$GatewayUrl/api/order/create" -Method POST -Headers @{"Authorization"="Bearer $token";"Origin"="http://evil.com"} -Body $orderBody
        if ($r3.StatusCode -eq 403 -or $r3.Body -match "csrf|origin|forbidden") {
            Add-Result -TestId "N-S-06" -TestName "CSRF Origin check" -Status "PASS" -Details "Malicious origin denied (HTTP $($r3.StatusCode))"
        } elseif ($r3.StatusCode -eq 200 -or $r3.StatusCode -eq 201) {
            Add-Result -TestId "N-S-06" -TestName "CSRF Origin check" -Status "WARN" -Details "Malicious origin accepted (API mode CSRF risk low, relies on token)" -Evidence $r3.Body
        } else {
            Add-Result -TestId "N-S-06" -TestName "CSRF Origin check" -Status "PASS" -Details "HTTP $($r3.StatusCode) (likely business validation failure, not CSRF)"
        }
    } else {
        Add-Result -TestId "N-S-06" -TestName "CSRF Origin check" -Status "WARN" -Details "No token, skipped"
    }
    Add-Result -TestId "N-S-06" -TestName "CSRF Token mechanism" -Status "INFO" -Details "API mode uses Bearer token, no cookie, CSRF risk inherently low"
}

# N-S-07: Session Management
function Test-NS07-Session {
    Write-Host "`n========== N-S-07: Session Management Test ==========" -ForegroundColor Cyan
    $token = Get-UserToken
    if ($token) {
        $r1 = Send-Request -Url "$GatewayUrl/api/user/profile" -Method GET -Headers @{"Authorization"="Bearer $token"}
        $r2 = Send-Request -Url "$GatewayUrl/api/user/profile" -Method GET -Headers @{"Authorization"="Bearer $token"}
        if ($r1.StatusCode -eq 200 -and $r2.StatusCode -eq 200) {
            Add-Result -TestId "N-S-07" -TestName "Token reuse" -Status "PASS" -Details "Same token works multiple times (stateless token normal)"
        } else {
            Add-Result -TestId "N-S-07" -TestName "Token reuse" -Status "WARN" -Details "Token reuse abnormal: 1st=$($r1.StatusCode), 2nd=$($r2.StatusCode)"
        }
        $tamperedToken = $token.Substring(0,[math]::Min($token.Length - 5,50)) + "XXXXX"
        $r3 = Send-Request -Url "$GatewayUrl/api/user/profile" -Method GET -Headers @{"Authorization"="Bearer $tamperedToken"}
        if ($r3.StatusCode -eq 401 -or $r3.StatusCode -eq 403) {
            Add-Result -TestId "N-S-07" -TestName "Token tamper detection" -Status "PASS" -Details "Tampered token denied (HTTP $($r3.StatusCode))"
        } else {
            Add-Result -TestId "N-S-07" -TestName "Token tamper detection" -Status "FAIL" -Details "Tampered token works!" -Evidence $r3.Body
        }
        $r4 = Send-Request -Url "$GatewayUrl/api/user/profile" -Method GET -Headers @{"Authorization"="Bearer "}
        if ($r4.StatusCode -eq 401) {
            Add-Result -TestId "N-S-07" -TestName "Empty token denied" -Status "PASS" -Details "Empty token denied (HTTP 401)"
        } else {
            Add-Result -TestId "N-S-07" -TestName "Empty token denied" -Status "FAIL" -Details "Empty token returned HTTP $($r4.StatusCode)"
        }
    } else {
        Add-Result -TestId "N-S-07" -TestName "Session mgmt" -Status "WARN" -Details "No token, skipped"
    }
}

# N-S-08: Rate Limit & Brute Force
function Test-NS08-RateLimit {
    Write-Host "`n========== N-S-08: Rate Limit & Brute Force Test ==========" -ForegroundColor Cyan
    Write-Host "  Sending 10 wrong login requests..."
    $blocked = $false; $blockCount = 0
    for ($i = 1; $i -le 10; $i++) {
        $body = '{"phone":"13800001111","password":"wrongpassword' + $i + '"}'
        $r = Send-Request -Url "$GatewayUrl/api/user/auth/login" -Method POST -Body $body -TimeoutSec 5
        if ($r.StatusCode -eq 429) { $blocked = $true; $blockCount++; Write-Host "  Request $i rate-limited (429)" }
        elseif ($r.Body -match "locked|frequent|too many") { $blocked = $true; $blockCount++; Write-Host "  Request $i locked" }
        Start-Sleep -Milliseconds 100
    }
    if ($blocked) {
        Add-Result -TestId "N-S-08" -TestName "Login rate limit" -Status "PASS" -Details "$blockCount of 10 requests rate-limited/locked"
    } else {
        Add-Result -TestId "N-S-08" -TestName "Login rate limit" -Status "FAIL" -Details "All 10 login requests not rate-limited (QPS=5 may not work)"
    }
    Write-Host "  Sending 5 consecutive wrong logins (test lock mechanism)..."
    $tempPhone = "13800002222"
    for ($i = 1; $i -le 5; $i++) {
        $body = "{`"phone`":`"$tempPhone`",`"password`":`"wrong$i`"}"
        $r = Send-Request -Url "$GatewayUrl/api/user/auth/login" -Method POST -Body $body -TimeoutSec 5
        Write-Host "  Wrong login $i`: HTTP $($r.StatusCode)"
        Start-Sleep -Milliseconds 200
    }
    $body = "{`"phone`":`"$tempPhone`",`"password`":`"123456`"}"
    $r = Send-Request -Url "$GatewayUrl/api/user/auth/login" -Method POST -Body $body -TimeoutSec 5
    if ($r.StatusCode -eq 429 -or $r.Body -match "locked|frequent|too many") {
        Add-Result -TestId "N-S-08" -TestName "Login fail lock" -Status "PASS" -Details "Account locked after failures (HTTP $($r.StatusCode))"
    } else {
        Add-Result -TestId "N-S-08" -TestName "Login fail lock" -Status "WARN" -Details "6th login HTTP $($r.StatusCode) (may need more failures)" -Evidence $r.Body
    }
    Write-Host "  Waiting 5s for rate limit window..."
    Start-Sleep -Seconds 5
}

# N-S-09: API Security (idempotent key, privilege escalation)
function Test-NS09-ApiSecurity {
    Write-Host "`n========== N-S-09: API Security Test ==========" -ForegroundColor Cyan
    $token = Get-UserToken
    if (-not $token) {
        Add-Result -TestId "N-S-09" -TestName "API security" -Status "WARN" -Details "No token, skipped"
        return
    }
    $headers = @{ "Authorization" = "Bearer $token" }
    $orderBody = '{"items":[{"skuId":4001,"quantity":1}],"addressId":1}'
    $r = Send-Request -Url "$GatewayUrl/api/order/create" -Method POST -Headers $headers -Body $orderBody
    if ($r.StatusCode -eq 400 -or $r.Body -match "idempotent|X-Idempotent-Key") {
        Add-Result -TestId "N-S-09" -TestName "Idempotent key check" -Status "PASS" -Details "Missing idempotent key rejected (HTTP $($r.StatusCode))"
    } else {
        Add-Result -TestId "N-S-09" -TestName "Idempotent key check" -Status "WARN" -Details "Missing idempotent key HTTP $($r.StatusCode) (may not be enforced)"
    }
    $idempotentKey = "test-idempotent-$(Get-Date -Format 'yyyyMMddHHmmss')"
    $headers2 = @{ "Authorization" = "Bearer $token"; "X-Idempotent-Key" = $idempotentKey }
    $r1 = Send-Request -Url "$GatewayUrl/api/order/create" -Method POST -Headers $headers2 -Body $orderBody
    $r2 = Send-Request -Url "$GatewayUrl/api/order/create" -Method POST -Headers $headers2 -Body $orderBody
    if ($r1.Body -match '"orderNo":"([^"]+)"' -and $r2.Body -match '"orderNo":"([^"]+)"') {
        $orderNo1 = $r1.Body -replace '.*"orderNo":"([^"]+)".*','$1'
        $orderNo2 = $r2.Body -replace '.*"orderNo":"([^"]+)".*','$1'
        if ($orderNo1 -eq $orderNo2) {
            Add-Result -TestId "N-S-09" -TestName "Idempotent dedup" -Status "PASS" -Details "Same key returns same order: $orderNo1"
        } else {
            Add-Result -TestId "N-S-09" -TestName "Idempotent dedup" -Status "FAIL" -Details "Same key created different orders!" -Evidence "1st: $($r1.Body), 2nd: $($r2.Body)"
        }
    } else {
        Add-Result -TestId "N-S-09" -TestName "Idempotent dedup" -Status "WARN" -Details "Order creation failed, cannot verify" -Evidence $r1.Body
    }
    $r = Send-Request -Url "$GatewayUrl/api/order/999999" -Method GET -Headers @{"Authorization"="Bearer $token"}
    if ($r.StatusCode -eq 403 -or $r.StatusCode -eq 404) {
        Add-Result -TestId "N-S-09" -TestName "Horizontal privilege" -Status "PASS" -Details "Access to others order denied (HTTP $($r.StatusCode))"
    } elseif ($r.StatusCode -eq 200) {
        Add-Result -TestId "N-S-09" -TestName "Horizontal privilege" -Status "FAIL" -Details "Can access others order!" -Evidence $r.Body
    } else {
        Add-Result -TestId "N-S-09" -TestName "Horizontal privilege" -Status "PASS" -Details "HTTP $($r.StatusCode) (non-200, safe)"
    }
}

# Main
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Shop Project Security Test (N-S-01 ~ N-S-09)" -ForegroundColor Cyan
Write-Host "Target: $GatewayUrl" -ForegroundColor Cyan
Write-Host "Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

$healthR = Send-Request -Url "$GatewayUrl/actuator/health" -Method GET
if (-not $healthR.Success) {
    Write-Host "Gateway unreachable, abort" -ForegroundColor Red
    exit 1
}
Write-Host "Gateway reachable" -ForegroundColor Green

Test-NS01-Baseline
Test-NS02-SqlInjection
Test-NS03-Xss
Test-NS04-Auth
Test-NS05-InfoLeak
Test-NS06-Csrf
Test-NS07-Session
Test-NS08-RateLimit
Test-NS09-ApiSecurity

Write-Host "`n================================================" -ForegroundColor Cyan
Write-Host "Security test done, generating report" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

$pass = ($AllResults | Where-Object { $_.Status -eq "PASS" }).Count
$fail = ($AllResults | Where-Object { $_.Status -eq "FAIL" }).Count
$warn = ($AllResults | Where-Object { $_.Status -eq "WARN" }).Count
$info = ($AllResults | Where-Object { $_.Status -eq "INFO" }).Count
$total = $AllResults.Count
Write-Host "Total: $total | PASS: $pass | FAIL: $fail | WARN: $warn | INFO: $info" -ForegroundColor Cyan

$jsonPath = "$ReportDir\security-scan-$Timestamp.json"
$AllResults | ConvertTo-Json -Depth 5 | Out-File -FilePath $jsonPath -Encoding utf8
Write-Host "JSON report: $jsonPath" -ForegroundColor Green

$mdPath = "$ReportDir\security-scan-$Timestamp.md"
$md = @()
$md += "# Shop Project Security Test Report"
$md += ""
$md += "- Time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$md += "- Target: $GatewayUrl"
$md += "- Tool: PowerShell script (OWASP ZAP fallback)"
$md += ""
$md += "## Statistics"
$md += ""
$md += "| Status | Count |"
$md += "|--------|-------|"
$md += "| PASS | $pass |"
$md += "| FAIL | $fail |"
$md += "| WARN | $warn |"
$md += "| INFO | $info |"
$md += "| Total | $total |"
$md += ""
$md += "## Details"
$md += ""
$md += "| TestId | TestName | Status | Details |"
$md += "|--------|----------|--------|---------|"
foreach ($r in $AllResults) {
    $details = $r.Details -replace "\|","\\|"
    $md += "| $($r.TestId) | $($r.TestName) | $($r.Status) | $details |"
}
$md += ""
$md | Out-File -FilePath $mdPath -Encoding utf8
Write-Host "Markdown report: $mdPath" -ForegroundColor Green

if ($fail -gt 0) {
    Write-Host "`nFAILED items:" -ForegroundColor Red
    $AllResults | Where-Object { $_.Status -eq "FAIL" } | ForEach-Object {
        Write-Host "  - $($_.TestId) $($_.TestName): $($_.Details)" -ForegroundColor Red
    }
}
Write-Host "`nTest complete." -ForegroundColor Green
