param(
    [string]$Package = "com.aiquota.mobile",
    [string]$ApkPath = "android/app/build/outputs/apk/debug/app-debug.apk",
    [int]$TotalMinutes = 120,
    [int]$IntervalMinutes = 10,
    [int]$ReinstallAfterMinutes = 90,
    [int]$FreshnessLimitMinutes = 25
)

$ErrorActionPreference = "Continue"
$providers = @(
    "claude",
    "codex",
    "glm",
    "opencode",
    "gemini",
    "copilot",
    "cursor",
    "antigravity"
)

$evidenceDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$summaryPath = Join-Path $evidenceDir "session-monitor-$runId.jsonl"
$logPath = Join-Path $evidenceDir "session-monitor-$runId.log"
$failurePath = Join-Path $evidenceDir "session-monitor-$runId-failure.txt"
$logcatPath = Join-Path $evidenceDir "session-monitor-$runId-logcat.txt"

function Write-RunLog {
    param([string]$Message)
    $line = "{0} {1}" -f (Get-Date -Format "o"), $Message
    $line | Tee-Object -FilePath $logPath -Append
}

function Invoke-AdbText {
    param([string[]]$AdbArgs)
    try {
        return (& adb @AdbArgs 2>&1 | Out-String)
    } catch {
        return $_.Exception.Message
    }
}

function Get-Snapshot {
    param([string]$Provider)
    $xml = Invoke-AdbText @("shell", "run-as", $Package, "cat", "shared_prefs/usage_data_$Provider.xml")
    if ($xml -match "No such file|Permission denied|run-as:") {
        return [pscustomobject]@{
            provider = $Provider
            ok = $false
            error = $xml.Trim()
        }
    }

    $m = [regex]::Match($xml, '<string name="snapshot">(.*?)</string>', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $m.Success) {
        return [pscustomobject]@{
            provider = $Provider
            ok = $false
            error = "snapshot string missing"
        }
    }

    $decoded = [System.Net.WebUtility]::HtmlDecode($m.Groups[1].Value)
    try {
        $json = $decoded | ConvertFrom-Json
        $providerSnapshot = @($json.providers)[0]
        $updated = [DateTimeOffset]::Parse($providerSnapshot.updatedAt).UtcDateTime
        $ageMin = ((Get-Date).ToUniversalTime() - $updated).TotalMinutes
        return [pscustomobject]@{
            provider = $Provider
            ok = $true
            connectionState = $providerSnapshot.connectionState
            refreshState = $providerSnapshot.refreshState
            planLabel = $providerSnapshot.planLabel
            updatedAt = $providerSnapshot.updatedAt
            statusUpdatedAt = $providerSnapshot.statusUpdatedAt
            ageMinutes = [math]::Round($ageMin, 2)
            message = $providerSnapshot.message
            lineCount = @($providerSnapshot.lines).Count
            sources = (@($providerSnapshot.lines) | ForEach-Object { $_.source } | Sort-Object -Unique) -join ","
            firstLine = if (@($providerSnapshot.lines).Count -gt 0) { @($providerSnapshot.lines)[0].label } else { $null }
        }
    } catch {
        return [pscustomobject]@{
            provider = $Provider
            ok = $false
            error = $_.Exception.Message
        }
    }
}

function Get-RecentRefreshLog {
    param([string]$Provider)
    $pattern = "(?i)$Provider|ProviderBackgroundRefresh|GlmIsolatedWebSession|login|auth|TIMEOUT|DISCONNECTED|usage payload"
    $raw = Invoke-AdbText @("logcat", "-d", "-v", "time", "-t", "700")
    return (($raw -split "`r?`n") | Where-Object { $_ -match $pattern } | Select-Object -Last 80) -join "`n"
}

function Save-Failure {
    param(
        [string]$Reason,
        [object[]]$Rows,
        [int]$ElapsedMinutes
    )
    $allLogs = Invoke-AdbText @("logcat", "-d", "-v", "time")
    $allLogs | Set-Content -LiteralPath $logcatPath -Encoding UTF8
    $rowsJson = $Rows | ConvertTo-Json -Depth 8
    $failure = @"
reason=$Reason
elapsedMinutes=$ElapsedMinutes
failureAt=$(Get-Date -Format o)
summaryPath=$summaryPath
logPath=$logPath
logcatPath=$logcatPath

rows:
$rowsJson
"@
    $failure | Set-Content -LiteralPath $failurePath -Encoding UTF8
    Write-RunLog "FAIL reason=$Reason failurePath=$failurePath"
}

function Install-Apk {
    if (-not (Test-Path -LiteralPath $ApkPath)) {
        throw "APK not found: $ApkPath"
    }
    Write-RunLog "APK reinstall started apk=$ApkPath"
    $output = Invoke-AdbText @("install", "-r", $ApkPath)
    Write-RunLog ("APK reinstall output=" + ($output.Trim() -replace "\s+", " "))
    if ($output -notmatch "Success") {
        throw "APK reinstall failed: $output"
    }
    Start-Sleep -Seconds 120
}

Write-RunLog "monitor started total=${TotalMinutes}m interval=${IntervalMinutes}m reinstallAfter=${ReinstallAfterMinutes}m freshnessLimit=${FreshnessLimitMinutes}m"
Invoke-AdbText @("logcat", "-c") | Out-Null

$checks = [math]::Floor($TotalMinutes / $IntervalMinutes)
for ($i = 0; $i -le $checks; $i++) {
    $elapsed = $i * $IntervalMinutes
    if ($i -gt 0) {
        Start-Sleep -Seconds ($IntervalMinutes * 60)
    }

    if ($elapsed -ge $ReinstallAfterMinutes) {
        try {
            Install-Apk
        } catch {
            $rows = @()
            Save-Failure -Reason ("apk_reinstall_failed: " + $_.Exception.Message) -Rows $rows -ElapsedMinutes $elapsed
            exit 2
        }
    }

    $rows = foreach ($p in $providers) { Get-Snapshot -Provider $p }
    $record = [pscustomobject]@{
        checkedAt = (Get-Date).ToUniversalTime().ToString("o")
        elapsedMinutes = $elapsed
        rows = $rows
    }
    $record | ConvertTo-Json -Depth 8 -Compress | Add-Content -LiteralPath $summaryPath -Encoding UTF8

    $briefRows = $rows | ForEach-Object {
        if ($_.ok) {
            "{0}:{1}/{2}/age={3}m/lines={4}" -f $_.provider, $_.connectionState, $_.refreshState, $_.ageMinutes, $_.lineCount
        } else {
            "{0}:ERROR={1}" -f $_.provider, $_.error
        }
    }
    Write-RunLog ("CHECK elapsed=${elapsed}m " + ($briefRows -join " | "))

    foreach ($row in $rows) {
        if (-not $row.ok) {
            Save-Failure -Reason ("snapshot_read_failed provider={0} error={1}" -f $row.provider, $row.error) -Rows $rows -ElapsedMinutes $elapsed
            exit 2
        }
        $message = [string]$row.message
        $authLike = $message -match "(?i)login|sign.?in|auth|reauth|permission|401|403"
        if ($row.connectionState -eq "DISCONNECTED" -or $authLike) {
            $recent = Get-RecentRefreshLog -Provider $row.provider
            $recent | Set-Content -LiteralPath (Join-Path $evidenceDir "session-monitor-$runId-$($row.provider)-recent-log.txt") -Encoding UTF8
            Save-Failure -Reason ("session_expired provider={0} state={1} message={2}" -f $row.provider, $row.connectionState, $message) -Rows $rows -ElapsedMinutes $elapsed
            exit 2
        }
        if ($row.ageMinutes -gt $FreshnessLimitMinutes) {
            $recent = Get-RecentRefreshLog -Provider $row.provider
            $recentPath = Join-Path $evidenceDir "session-monitor-$runId-$($row.provider)-recent-log.txt"
            $recent | Set-Content -LiteralPath $recentPath -Encoding UTF8
            Save-Failure -Reason ("snapshot_stale provider={0} ageMin={1} recentLog={2}" -f $row.provider, $row.ageMinutes, $recentPath) -Rows $rows -ElapsedMinutes $elapsed
            exit 2
        }
    }
}

$final = "PASS summaryPath=$summaryPath logPath=$logPath logcatPath=$logcatPath completedAt=$(Get-Date -Format o)"
$final | Set-Content -LiteralPath (Join-Path $evidenceDir "session-monitor-$runId-pass.txt") -Encoding UTF8
Write-RunLog $final
exit 0
