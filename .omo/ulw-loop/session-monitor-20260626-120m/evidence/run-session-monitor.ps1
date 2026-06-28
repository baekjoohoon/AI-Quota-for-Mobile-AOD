param(
    [string]$Package = "com.aiquota.mobile",
    [string]$ApkPath = "android/app/build/outputs/apk/debug/app-debug.apk",
    [int]$TotalMinutes = 120,
    [int]$IntervalMinutes = 10,
    [int]$ReinstallAfterMinutes = 90,
    [int]$FreshnessLimitMinutes = 25
)

$ErrorActionPreference = "Continue"
$providers = @("claude", "codex", "glm", "opencode", "gemini", "copilot", "cursor", "antigravity")
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

function Stop-HeavyAdbDumps {
    $patterns = @(
        "shell\s+dumpsys\s+package\s+com\.google\.android\.gms",
        "shell\s+dumpsys\s+meminfo(\s|$)"
    )
    try {
        Get-CimInstance Win32_Process -Filter "Name = 'adb.exe'" |
            Where-Object {
                $commandLine = [string]$_.CommandLine
                $patterns | Where-Object { $commandLine -match $_ }
            } |
            ForEach-Object {
                Write-RunLog "stopping heavy adb dump pid=$($_.ProcessId)"
                Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
            }
    } catch {
        Write-RunLog ("heavy adb dump cleanup skipped: " + $_.Exception.Message)
    }
}

function Get-Snapshot {
    param([string]$Provider)
    $xml = Invoke-AdbText @("shell", "run-as", $Package, "cat", "shared_prefs/usage_data_$Provider.xml")
    if ($xml -match "No such file|Permission denied|run-as:") {
        return [pscustomobject]@{ provider = $Provider; ok = $false; error = $xml.Trim() }
    }
    $m = [regex]::Match($xml, '<string name="snapshot">(.*?)</string>', [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $m.Success) {
        return [pscustomobject]@{ provider = $Provider; ok = $false; error = "snapshot string missing" }
    }
    try {
        $json = [System.Net.WebUtility]::HtmlDecode($m.Groups[1].Value) | ConvertFrom-Json
        $providerSnapshot = @($json.providers)[0]
        $updated = [DateTimeOffset]::Parse($providerSnapshot.updatedAt).UtcDateTime
        $statusUpdated = [DateTimeOffset]::Parse($providerSnapshot.statusUpdatedAt).UtcDateTime
        $ageMin = ((Get-Date).ToUniversalTime() - $updated).TotalMinutes
        $statusAgeMin = ((Get-Date).ToUniversalTime() - $statusUpdated).TotalMinutes
        return [pscustomobject]@{
            provider = $Provider
            ok = $true
            connectionState = $providerSnapshot.connectionState
            refreshState = $providerSnapshot.refreshState
            planLabel = $providerSnapshot.planLabel
            updatedAt = $providerSnapshot.updatedAt
            statusUpdatedAt = $providerSnapshot.statusUpdatedAt
            ageMinutes = [math]::Round($ageMin, 2)
            statusAgeMinutes = [math]::Round($statusAgeMin, 2)
            message = $providerSnapshot.message
            lineCount = @($providerSnapshot.lines).Count
            signature = ((@($providerSnapshot.lines) | ForEach-Object { "$($_.key):$($_.remainingText):$($_.resetText):$($_.resetsAt)" }) -join "|")
        }
    } catch {
        return [pscustomobject]@{ provider = $Provider; ok = $false; error = $_.Exception.Message }
    }
}

function Get-Snapshots {
    $rows = foreach ($p in $providers) { Get-Snapshot -Provider $p }
    $refreshing = @($rows | Where-Object { $_.ok -and ($_.connectionState -eq "COLLECTING" -or $_.refreshState -eq "REFRESHING") })
    if ($refreshing.Count -gt 0) {
        Write-RunLog ("refreshing observed; recheck in 30s providers=" + (($refreshing | ForEach-Object { $_.provider }) -join ",")) | Out-Null
        Start-Sleep -Seconds 30
        $rows = foreach ($p in $providers) { Get-Snapshot -Provider $p }
    }
    return @($rows | Where-Object { $_ -is [pscustomobject] -and $_.provider })
}

function Save-Failure {
    param([string]$Reason, [object[]]$Rows, [int]$ElapsedMinutes)
    Invoke-AdbText @("logcat", "-d", "-v", "time") | Set-Content -LiteralPath $logcatPath -Encoding UTF8
    $rowsJson = $Rows | ConvertTo-Json -Depth 8
    @"
reason=$Reason
elapsedMinutes=$ElapsedMinutes
failureAt=$(Get-Date -Format o)
summaryPath=$summaryPath
logPath=$logPath
logcatPath=$logcatPath

rows:
$rowsJson
"@ | Set-Content -LiteralPath $failurePath -Encoding UTF8
    Write-RunLog "FAIL reason=$Reason failurePath=$failurePath"
}

function Install-Apk {
    if (-not (Test-Path -Path $ApkPath)) { throw "APK not found: $ApkPath" }
    Write-RunLog "APK reinstall started apk=$ApkPath"
    $output = Invoke-AdbText @("install", "-r", "--no-streaming", $ApkPath)
    Write-RunLog ("APK reinstall output=" + ($output.Trim() -replace "\s+", " "))
    if ($output -notmatch "Success") { throw "APK reinstall failed: $output" }
    Start-Sleep -Seconds 120
}

function Test-Failure {
    param([object[]]$Rows, [int]$ElapsedMinutes)
    $recentLog = Invoke-AdbText @("logcat", "-d", "-v", "time", "-t", "2500")
    if ($recentLog -match "ANR in com\.aiquota\.mobile|Input dispatching timed out.*com\.aiquota\.mobile") {
        Save-Failure -Reason "app_anr_detected" -Rows $Rows -ElapsedMinutes $ElapsedMinutes
        return $true
    }
    foreach ($row in $Rows) {
        if (-not $row.ok) {
            Save-Failure -Reason ("snapshot_read_failed provider={0} error={1}" -f $row.provider, $row.error) -Rows $Rows -ElapsedMinutes $ElapsedMinutes
            return $true
        }
        $message = [string]$row.message
        $authLike = $message -match "(?i)login|sign.?in|auth|reauth|permission|401|403"
        $collecting = $row.connectionState -eq "COLLECTING" -or $row.refreshState -eq "REFRESHING"
        if ($collecting -and $row.statusAgeMinutes -gt 3) {
            Save-Failure -Reason ("refresh_stuck provider={0} state={1} refresh={2} statusAgeMin={3} message={4}" -f $row.provider, $row.connectionState, $row.refreshState, $row.statusAgeMinutes, $message) -Rows $Rows -ElapsedMinutes $ElapsedMinutes
            return $true
        }
        if ((-not $collecting -and $row.connectionState -ne "CONNECTED") -or $authLike) {
            Save-Failure -Reason ("session_or_state_regressed provider={0} state={1} refresh={2} message={3}" -f $row.provider, $row.connectionState, $row.refreshState, $message) -Rows $Rows -ElapsedMinutes $ElapsedMinutes
            return $true
        }
        if ($row.lineCount -le 0) {
            Save-Failure -Reason ("usage_lines_missing provider={0}" -f $row.provider) -Rows $Rows -ElapsedMinutes $ElapsedMinutes
            return $true
        }
        if ($row.ageMinutes -gt $FreshnessLimitMinutes) {
            Save-Failure -Reason ("snapshot_stale provider={0} ageMin={1}" -f $row.provider, $row.ageMinutes) -Rows $Rows -ElapsedMinutes $ElapsedMinutes
            return $true
        }
    }
    return $false
}

Write-RunLog "monitor started total=${TotalMinutes}m interval=${IntervalMinutes}m reinstallAfter=${ReinstallAfterMinutes}m freshnessLimit=${FreshnessLimitMinutes}m"
Stop-HeavyAdbDumps
Invoke-AdbText @("logcat", "-c") | Out-Null

$checks = [math]::Floor($TotalMinutes / $IntervalMinutes)
for ($i = 0; $i -le $checks; $i++) {
    $elapsed = $i * $IntervalMinutes
    if ($i -gt 0) { Start-Sleep -Seconds ($IntervalMinutes * 60) }
    Stop-HeavyAdbDumps
    if ($elapsed -ge $ReinstallAfterMinutes) {
        try {
            Install-Apk
        } catch {
            Save-Failure -Reason ("apk_reinstall_failed: " + $_.Exception.Message) -Rows @() -ElapsedMinutes $elapsed
            exit 2
        }
    }
    $rows = Get-Snapshots
    [pscustomobject]@{
        checkedAt = (Get-Date).ToUniversalTime().ToString("o")
        elapsedMinutes = $elapsed
        rows = $rows
    } | ConvertTo-Json -Depth 8 -Compress | Add-Content -LiteralPath $summaryPath -Encoding UTF8
    $briefRows = $rows | ForEach-Object {
        if ($_.ok) { "{0}:{1}/{2}/age={3}m/lines={4}" -f $_.provider, $_.connectionState, $_.refreshState, $_.ageMinutes, $_.lineCount }
        else { "{0}:ERROR={1}" -f $_.provider, $_.error }
    }
    Write-RunLog ("CHECK elapsed=${elapsed}m " + ($briefRows -join " | "))
    if (Test-Failure -Rows $rows -ElapsedMinutes $elapsed) { exit 2 }
}

$passPath = Join-Path $evidenceDir "session-monitor-$runId-pass.txt"
"PASS summaryPath=$summaryPath logPath=$logPath logcatPath=$logcatPath completedAt=$(Get-Date -Format o)" | Set-Content -LiteralPath $passPath -Encoding UTF8
Write-RunLog "PASS passPath=$passPath"
exit 0
