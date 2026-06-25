# Android Provider Refresh Session Recovery

## TL;DR
> Summary:      Restore real background refresh collection for long-lived provider sessions, preserving existing provider behavior and the current path+query+fragment route-key fix for Codex hash-route injection.
> Deliverables:
> - Session-preserving refresh behavior for Codex and Gemini, with GLM/OpenCode no longer diagnostic-pass skipped if currently present.
> - Focused unit coverage for route-key reinjection, refresh scheduling, failure/session policy, timestamp integrity, and no fake usage.
> - Debug APK assembled, emulator relaunched, app installed/launched, real refresh verified by logcat and sanitized snapshots for Codex and Gemini minimum.
> - ANR absence evidence after the refresh verification window.
> Effort:       Large
> Risk:         High - live provider sessions depend on third-party WebView state, real auth cookies, and provider pages that can change without compile-time signal.

## Scope
### Must have
- Preserve the existing dirty route-key behavior in `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:544-569`, `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:611-631`, and `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt:333-360`, where collector injection keys use `routeKeyOf(url)` and `routeKeyOf` includes path, query, and fragment at `ProviderBackgroundRefreshService.kt:965-974`, `WebLoginActivity.kt:829-838`, and `BackgroundProviderWebCollector.kt:533-542`.
- Keep WebView sessions alive across ordinary refresh and login retries; only explicit disconnect/session reset may clear provider-scoped cookies/storage as described by `ProviderWebSessionClearPolicy.kt:7-17` and `ProviderWebSessionCleaner.kt:13-59`.
- Make real refresh attempt collection for connected providers through `ProviderRefreshPlan.automaticJobsFor` and `manualCycleJobsFor` in `ProviderRefreshPlan.kt:56-86`; do not leave GLM/OpenCode diagnostic pass-through in `ProviderBackgroundRefreshService.refreshProvider` at `ProviderBackgroundRefreshService.kt:268-344`.
- Preserve native-token expiration semantics: native-token providers may clear credentials on manual auth failure through `ProviderRefreshSessionPolicy.kt:5-8` and `ProviderBackgroundRefreshService.kt:360-376`, while automatic auth-like refresh failures keep previous state refreshable.
- Keep timestamp integrity: transient collection, no-payload, unavailable, and collecting states must not fake fresh `updatedAt` when previous usage lines exist; follow `ProviderModels.kt:155-188`, `ProviderModels.kt:208-252`, and `LocalUsageRepository.kt:129-200`.
- Verify Codex and Gemini at minimum with real emulator refresh, logcat, and sanitized snapshot inspection from `usage_data_codex.xml`, `usage_data_gemini.xml`, `ai_quota_local_usage.xml`, and `ai_quota_widget_cache.xml`.
- Confirm no new ANR trace appears after the verification start time; current known traces are `/data/anr/anr_2026-06-21-11-20-26-047` and `/data/anr/anr_2026-06-21-11-26-25-806`.

### Must NOT have (guardrails, anti-slop, scope boundaries)
- Do not use CodeGraph.
- Do not fake usage rows, payloads, `updatedAt`, `statusUpdatedAt`, reset times, account IDs, or successful provider states.
- Do not classify `codex_usage_unavailable`, generic no-payload, timeout, stale usage, or recoverable Google usage failure as confirmed auth failure without explicit login redirect, main-frame `401/403`, or collector-auth evidence.
- Do not add non-production Codex fallbacks such as post-auth root redirects, settings-usage fallbacks, route-application-error fallbacks, collector run IDs, or stale-collector bypasses; existing tests already reject these at `ProviderBackgroundRefreshServicePolicyTest.kt:169-294`.
- Do not broaden session clearing beyond explicit disconnect/session reset; do not call `removeAllCookies`, `removeSessionCookies`, `deleteAllData`, `clearCache(true)`, `clearHistory()`, or `clearFormData()`, as guarded by `WebViewSessionPersistenceTest.kt:8-30`.
- Do not change unrelated provider collectors unless required by shared refresh/session contracts and covered by focused tests.
- Do not log secrets, cookies, OAuth codes, bearer tokens, emails, or raw payloads; maintain redacted logging expectations in `ProviderBackgroundRefreshServicePolicyTest.kt:369-381`.

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- Test decision: TDD + Android/JUnit unit tests via Gradle `:app:testDebugUnitTest`; real-device QA via `adb`, logcat, SharedPreferences inspection, and ANR trace listing.
- QA policy: every task has agent-executed scenarios
- Evidence: `.omo/evidence/task-<N>-<slug>.<ext>`

## Execution strategy
### Parallel execution waves
> Target 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks to maximize parallelism.

Wave 1 (no dependencies):
- Task 1: Baseline dirty worktree, toolchain, emulator, snapshot, and ANR evidence
- Task 2: Add/lock TDD guardrails for route-key reinjection and session preservation
- Task 3: Add/lock TDD guardrails for refresh scheduling, GLM/OpenCode real collection, and timestamp integrity
- Task 4: Add/lock log redaction and provider failure classification guardrails

Wave 2 (after Wave 1):
- Task 5: depends [2, 3, 4] - Implement minimal session-preserving refresh recovery
- Task 6: depends [5] - Run targeted and full unit test/build gates
- Task 7: depends [6] - Relaunch emulator, install debug APK, and launch app

Wave 3 (after Wave 2):
- Task 8: depends [7] - Verify real Codex and Gemini refresh with logcat and sanitized snapshots
- Task 9: depends [7, 8] - Run GLM/OpenCode risk sweep and ANR absence check

Critical path: Task 1 -> Task 2/3/4 -> Task 5 -> Task 6 -> Task 7 -> Task 8 -> Task 9

### Dependency matrix
| Task | Depends on | Blocks | Can parallelize with |
|------|------------|--------|----------------------|
| 1    | none       | 5, 7, 8, 9 | 2, 3, 4 |
| 2    | none       | 5, 6 | 1, 3, 4 |
| 3    | none       | 5, 6 | 1, 2, 4 |
| 4    | none       | 5, 6 | 1, 2, 3 |
| 5    | 2, 3, 4   | 6 | none |
| 6    | 5          | 7 | none |
| 7    | 6          | 8, 9 | none |
| 8    | 7          | 9 | none |
| 9    | 7, 8       | Final verification | none |

## Todos
> Implementation + Test = ONE task. Never separate.
> Every task MUST have: References + Acceptance Criteria + QA Scenarios + Commit.

- [ ] 1. Baseline dirty worktree, toolchain, emulator, snapshot, and ANR evidence

  What to do: Capture the current dirty worktree, local Gradle/ADB/emulator availability, installed package, active emulator state, current provider snapshot summaries, and `/data/anr` state before implementation. Create `.omo/evidence/` and treat any missing tool/session as a blocker with evidence.
  Must NOT do: Do not edit product files. Do not delete app data, cookies, ANR files, or provider snapshots. Do not log raw secrets outside sanitized evidence.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [5, 7, 8, 9] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/build.gradle.kts:81-180` - package, SDK, dependency, and unit-test framework facts.
  - Pattern:  `android/app/src/main/AndroidManifest.xml:71-82` - foreground refresh service registration.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderScriptProviders.kt:102-107` - provider scoped SharedPreferences names.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderScopedStateRepository.kt:15-42` - scoped snapshot, account, script metadata persistence.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/widget/WidgetSnapshotCache.kt:16-31` - widget cache keys to inspect.
  - External: `https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs` - ANR diagnosis and execute-service timeout guidance.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force .omo/evidence | Out-Null; git status --short | Tee-Object .omo/evidence/task-1-dirty-worktree.txt"` exits 0 and records existing dirty files without reverting them.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradles=@('C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat','C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat','C:\Users\datell1357\Tools\gradle\gradle-9.6.0\bin\gradle.bat'); $gradles | ForEach-Object { [pscustomobject]@{path=$_; exists=(Test-Path $_)} } | ConvertTo-Json | Tee-Object .omo/evidence/task-1-gradle-tools.json; if (-not ($gradles | Where-Object { Test-Path $_ } | Select-Object -First 1)) { exit 1 }"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; & $adb devices | Tee-Object .omo/evidence/task-1-adb-devices.txt; & $env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe -list-avds | Tee-Object .omo/evidence/task-1-avds.txt; & $adb -s emulator-5554 shell getprop ro.build.version.release | Tee-Object .omo/evidence/task-1-device-android.txt; & $adb -s emulator-5554 shell getprop ro.product.model | Tee-Object .omo/evidence/task-1-device-model.txt"` exits 0 and shows `emulator-5554` or records the selected replacement serial.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; & $adb -s emulator-5554 shell ls -la /data/anr 2>&1 | Tee-Object .omo/evidence/task-1-anr-before.txt; & $adb -s emulator-5554 shell run-as com.aiquota.mobile ls shared_prefs 2>&1 | Tee-Object .omo/evidence/task-1-shared-prefs.txt"` exits 0 or records a concrete app-install/debuggable blocker.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Baseline evidence captured
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$files=@('.omo/evidence/task-1-dirty-worktree.txt','.omo/evidence/task-1-gradle-tools.json','.omo/evidence/task-1-adb-devices.txt','.omo/evidence/task-1-anr-before.txt'); $missing=$files | Where-Object { -not (Test-Path $_) }; if ($missing) { $missing | Tee-Object .omo/evidence/task-1-baseline-check.txt; exit 1 }; 'PASS baseline evidence present' | Tee-Object .omo/evidence/task-1-baseline-check.txt"
    Expected: Exit code 0; evidence files exist.
    Evidence: .omo/evidence/task-1-baseline-check.txt

  Scenario: Missing tool/session is explicit blocker
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$missing = -not (Test-Path 'C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'); if ($missing) { 'BLOCKED: adb missing' | Tee-Object .omo/evidence/task-1-blocker.txt; exit 1 }; 'adb present' | Tee-Object .omo/evidence/task-1-blocker-check.txt"
    Expected: Exit code 0 when adb is present; otherwise `.omo/evidence/task-1-blocker.txt` contains the blocker.
    Evidence: .omo/evidence/task-1-blocker-check.txt
  ```

  Commit: NO | Message: `chore(evidence): capture provider refresh baseline` | Files: [.omo/evidence/*]

- [ ] 2. Add/lock TDD guardrails for route-key reinjection and session preservation

  What to do: Add or strengthen tests that fail if route-key injection becomes path-only, if Codex hash route reinjection is blocked, if login/refresh clears cookies outside the explicit disconnect policy, or if destructive WebView/session calls reappear. Preserve existing route-key changes if they already satisfy the tests.
  Must NOT do: Do not add production collector run IDs, forced Codex settings fallbacks, or raw URL/query/fragment logging that could expose secrets.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [5, 6] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:544-569` - service injection key and reinjection log.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:965-974` - service `routeKeyOf` path+query+fragment behavior.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:611-631` - login injection key and interactive wait behavior.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:829-838` - login `routeKeyOf`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt:333-360` - composable background collector injection key.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt:533-542` - composable `routeKeyOf`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:964-975` - Codex collector targets `https://chatgpt.com/codex/cloud/settings/analytics#usage`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionClearPolicy.kt:7-17` - clear-before-login is only Cursor interactive auth; disconnect clear is explicit.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:169-294` - non-production Codex fallback and reinjection policy source assertions.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebSessionClearPolicyTest.kt:20-97` - session-clear behavior and provider-scoped URLs.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/WebViewSessionPersistenceTest.kt:8-52` - destructive session cleanup guardrails.
  - External: `https://developer.android.com/reference/android/webkit/CookieManager` - `setCookie` is asynchronous and `setAcceptThirdPartyCookies` is per-WebView policy.

  Acceptance criteria (agent-executable only):
  - [ ] Add a failing-first unit/source test proving all three `routeKeyOf` implementations append both query and fragment, using concrete examples `https://chatgpt.com/codex/cloud/settings/analytics#usage` and `https://chatgpt.com/codex/cloud/settings/analytics?account_id=a#usage`.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest --tests com.aiquota.mobile.providers.GoogleProviderLoginRoutingTest --tests com.aiquota.mobile.providers.ProviderWebSessionClearPolicyTest --tests com.aiquota.mobile.providers.WebViewSessionPersistenceTest 2>&1 | Tee-Object .omo/evidence/task-2-route-session-tests.txt"` exits 0.
  - [ ] Test output or source asserts no path-only key pattern such as `:${pathOf(url)}` is used for collector injection in the three touched collector surfaces.
  - [ ] Tests still reject `removeAllCookies`, `removeSessionCookies`, `deleteAllData`, `clearCache(true)`, `clearHistory()`, and `clearFormData()` outside explicit disconnect cleanup.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Route key includes hash route and query
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$files=@('android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt','android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt','android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt'); foreach ($f in $files) { $s=Get-Content $f -Raw; if ($s -notmatch 'rawQuery' -or $s -notmatch 'rawFragment' -or $s -notmatch 'routeKeyOf\(url\)') { Write-Host FAIL:$f; exit 1 } }; 'PASS route-key query+fragment on all collector surfaces' | Tee-Object .omo/evidence/task-2-route-key-scan.txt"
    Expected: Exit code 0 and PASS line present.
    Evidence: .omo/evidence/task-2-route-key-scan.txt

  Scenario: Destructive session clearing remains forbidden
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.WebViewSessionPersistenceTest --tests com.aiquota.mobile.providers.ProviderWebSessionClearPolicyTest 2>&1 | Tee-Object .omo/evidence/task-2-session-negative-tests.txt"
    Expected: Exit code 0; tests fail if broad cookie/storage clearing is introduced.
    Evidence: .omo/evidence/task-2-session-negative-tests.txt
  ```

  Commit: YES | Message: `test(android): lock provider route keys and session persistence` | Files: [android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt, android/app/src/test/java/com/aiquota/mobile/providers/GoogleProviderLoginRoutingTest.kt, android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebSessionClearPolicyTest.kt, android/app/src/test/java/com/aiquota/mobile/providers/WebViewSessionPersistenceTest.kt]

- [ ] 3. Add/lock TDD guardrails for refresh scheduling, GLM/OpenCode real collection, and timestamp integrity

  What to do: Add or strengthen tests proving automatic/manual refresh still schedules connected providers, retries recoverable Codex/Gemini states, does not diagnostic-pass GLM/OpenCode, resolves GLM Web OAuth refresh to the usage page, reuses OpenCode's trusted `/workspace/{id}/go` URL when known, and preserves `updatedAt` on transient failures with previous usage.
  Must NOT do: Do not make automatic refresh disconnect providers after repeated collector misses. Do not turn GLM Web OAuth into a broad login-start loop. Do not update usage timestamps without a fresh trusted provider payload.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [5, 6] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt:22-74` - refresh interval, timeouts, and automatic job selection.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt:88-125` - collection mode and hidden collector URLs.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:268-358` - provider refresh pipeline, OpenCode URL reuse, GLM runtime resolver.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GlmUsagePageRoutes.kt:14-22` - GLM Web OAuth runtime job resolves to Web usage URL.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/OpenCodeUsagePageRoutes.kt:6-36` - canonical OpenCode `/workspace/{id}/go` usage URL.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt:155-188` - collecting/connected-without-usage timestamp behavior.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt:208-252` - failed-keeping-previous and `snapshotUpdatedAtForStatusTransition`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt:129-200` - collecting, pending, and fail-keeping-previous repository behavior.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderRefreshPlanTest.kt:95-356` - automatic/manual refresh scheduling and retry tests.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:129-148` - GLM/OpenCode diagnostic-pass and GLM runtime resolver guards.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:151-166` - OpenCode background redirect guard.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/local/ProviderUsageSnapshotTest.kt:40-130` - previous usage/timestamp preservation.

  Acceptance criteria (agent-executable only):
  - [ ] Add failing-first tests for any current GLM/OpenCode diagnostic skip/pass-through behavior, then make them pass by removing only the diagnostic skip path.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderRefreshPlanTest --tests com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest --tests com.aiquota.mobile.local.ProviderUsageSnapshotTest --tests com.aiquota.mobile.local.LocalUsageRepositoryTest 2>&1 | Tee-Object .omo/evidence/task-3-refresh-plan-tests.txt"` exits 0.
  - [ ] Source scan confirms `shouldPassRefreshForDiagnostics`, `reason=diagnostic_pass`, `MAX_STALE_REFRESH_FAILURES`, and `staleRefreshFailureCounts` are absent.
  - [ ] Tests assert transient Codex/Gemini refresh failures with prior usage preserve `updatedAt`, preserve lines, set `statusUpdatedAt`, and remain refreshable.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Real collection paths are scheduled
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderRefreshPlanTest --tests com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest 2>&1 | Tee-Object .omo/evidence/task-3-scheduling-green.txt"
    Expected: Exit code 0; output includes the two test classes and no failed tests.
    Evidence: .omo/evidence/task-3-scheduling-green.txt

  Scenario: Diagnostic-pass and fake timestamp regressions are absent
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$bad = Select-String -Path android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt,android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt,android/app/src/main/java/com/aiquota/mobile/local/*.kt -Pattern 'shouldPassRefreshForDiagnostics|diagnostic_pass|fake|hardcoded|MAX_STALE_REFRESH_FAILURES|staleRefreshFailureCounts' -Quiet; if ($bad) { exit 1 }; 'PASS no diagnostic pass or fake timestamp marker' | Tee-Object .omo/evidence/task-3-negative-scan.txt"
    Expected: Exit code 0 and PASS line present.
    Evidence: .omo/evidence/task-3-negative-scan.txt
  ```

  Commit: YES | Message: `test(android): lock real provider refresh scheduling` | Files: [android/app/src/test/java/com/aiquota/mobile/providers/ProviderRefreshPlanTest.kt, android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt, android/app/src/test/java/com/aiquota/mobile/local/ProviderUsageSnapshotTest.kt, android/app/src/test/java/com/aiquota/mobile/local/LocalUsageRepositoryTest.kt]

- [ ] 4. Add/lock log redaction and provider failure classification guardrails

  What to do: Add or strengthen tests that reject raw payload/cookie/token logging, require redacted diagnostic logs, and preserve existing failure classification: explicit auth evidence only becomes `INTERACTIVE_AUTH_REQUIRED`; Codex `codex_usage_unavailable` remains no-trusted-payload/retryable; recoverable Google no-payload failures become pending/retryable rather than fake success or hard auth failure.
  Must NOT do: Do not suppress useful provider IDs, line counts, host/path diagnostics, or status codes needed for QA. Do not log full URLs with OAuth codes/tokens or raw payload bodies.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [5, 6] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderCollectorErrorPolicy.kt:6-29` - collector error to refresh failure mapping.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderCollectorErrorPolicy.kt:53-74` - explicit auth errors and Codex retry policy.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshFailure.kt:5-35` - failure kinds and auth classifier.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:313-338` - failure handling and hidden WebView retention.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:837-887` - bridge payload/error handling and retry.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:738-785` - recoverable Google pending and no fake success.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:369-381` - log redaction source assertions.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderCollectorErrorPolicyTest.kt` - collector error policy test surface.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/local/LocalUsageRepositoryTest.kt:300-350` - interactive auth recovery/session-expired distinctions.
  - External: `https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs` - keep service start path fast and diagnosable without raw sensitive logs.

  Acceptance criteria (agent-executable only):
  - [ ] Add or update tests proving `codex_usage_unavailable` does not become `INTERACTIVE_AUTH_REQUIRED`, and explicit auth errors still do.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderCollectorErrorPolicyTest --tests com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest --tests com.aiquota.mobile.local.LocalUsageRepositoryTest 2>&1 | Tee-Object .omo/evidence/task-4-failure-redaction-tests.txt"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$files=Get-ChildItem android/app/src/main/java/com/aiquota/mobile -Recurse -Filter *.kt; $bad=Select-String -Path $files.FullName -Pattern 'Log\.[dwie]\([^)]*rawPayload|Log\.[dwie]\([^)]*cookiesFor|Bearer\s+\$|access_token=\$|refresh_token=\$|code=\$' -Quiet; if ($bad) { exit 1 }; 'PASS redaction source scan' | Tee-Object .omo/evidence/task-4-redaction-scan.txt"` exits 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Failure classification preserves retryable no-payload
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderCollectorErrorPolicyTest 2>&1 | Tee-Object .omo/evidence/task-4-classifier-green.txt"
    Expected: Exit code 0; tests fail if `codex_usage_unavailable` is classified as auth-required.
    Evidence: .omo/evidence/task-4-classifier-green.txt

  Scenario: Sensitive log patterns are rejected
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$files=Get-ChildItem android/app/src/main/java/com/aiquota/mobile -Recurse -Filter *.kt; Select-String -Path $files.FullName -Pattern 'rawPayload|cookie|Bearer|access_token|refresh_token|oauth' | ForEach-Object { $_.Line } | Tee-Object .omo/evidence/task-4-log-risk-lines.txt; $bad=Select-String -Path .omo/evidence/task-4-log-risk-lines.txt -Pattern 'Log\..*rawPayload|Log\..*CookieManager|getCookie\(.*Log|Bearer\s+[A-Za-z0-9]' -Quiet; if ($bad) { exit 1 }"
    Expected: Exit code 0; evidence contains reviewed risk lines but no disallowed raw secret logging.
    Evidence: .omo/evidence/task-4-log-risk-lines.txt
  ```

  Commit: YES | Message: `test(android): lock provider refresh failure safety` | Files: [android/app/src/test/java/com/aiquota/mobile/providers/ProviderCollectorErrorPolicyTest.kt, android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt, android/app/src/test/java/com/aiquota/mobile/local/LocalUsageRepositoryTest.kt]

- [ ] 5. Implement minimal session-preserving refresh recovery

  What to do: Make the smallest production-code changes required for Tasks 2-4 to pass. Expected edits are limited to provider refresh/session files: preserve path+query+fragment route keys, remove any GLM/OpenCode diagnostic pass-through, keep hidden WebViews retained on timeout/collector miss where existing policy says so, keep WebView-profile sessions intact, and ensure failure paths preserve previous usage without fake freshness. If the current dirty code already implements a requirement, leave it intact and only adjust tests or narrow bugs.
  Must NOT do: Do not refactor provider definitions, collectors, widgets, Firebase/OAuth flows, or unrelated UI. Do not change unrelated providers' start URLs or auth store kinds. Do not clear all WebView cookies/storage. Do not add arbitrary sleeps or retries outside existing provider-specific policies.

  Parallelization: Can parallel: NO | Wave 2 | Blocks: [6] | Blocked by: [2, 3, 4]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:236-344` - cycle/job execution and outcome handling.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:346-358` - runtime GLM/OpenCode refresh resolution.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:451-500` - WebView collection request lifecycle.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:572-607` - Gemini/OpenCode refresh redirects.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:714-835` - WebViewClient main-frame/resource/page-finished handling.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt:51-142` - Codex/GLM/OpenCode/Gemini definitions and collection kinds.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:117-209` - collector eligibility and provider reinjection policy.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt:16-90` - read/save snapshot normalization and persistence.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:69-354` - source-level refresh service guardrails.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderRefreshPlanTest.kt:95-356` - refresh plan scheduling.

  Acceptance criteria (agent-executable only):
  - [ ] The implementation touches only the minimal provider/session files required by failing tests. `powershell -NoProfile -ExecutionPolicy Bypass -Command "git diff --name-only | Tee-Object .omo/evidence/task-5-files.txt; $allowed=@('android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt','android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt','android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt','android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt','android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionClearPolicy.kt','android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt','android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt','android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt'); $changed=git diff --name-only | Where-Object { $_ -like 'android/app/src/main/*' }; $extra=$changed | Where-Object { $_ -notin $allowed }; if ($extra) { $extra | Tee-Object .omo/evidence/task-5-extra-files.txt; exit 1 }"` exits 0 unless a new scoped helper/test-backed file is justified in `.omo/evidence/task-5-extra-files.txt`.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest --tests com.aiquota.mobile.providers.ProviderRefreshPlanTest --tests com.aiquota.mobile.providers.ProviderWebSessionClearPolicyTest --tests com.aiquota.mobile.providers.WebViewSessionPersistenceTest --tests com.aiquota.mobile.providers.GoogleProviderLoginRoutingTest --tests com.aiquota.mobile.local.ProviderUsageSnapshotTest --tests com.aiquota.mobile.local.LocalUsageRepositoryTest 2>&1 | Tee-Object .omo/evidence/task-5-targeted-green.txt"` exits 0.
  - [ ] Source scan confirms no fake values: `powershell -NoProfile -ExecutionPolicy Bypass -Command "$bad=Select-String -Path android/app/src/main/java/com/aiquota/mobile/**/*.kt -Pattern 'fake usage|hardcoded usage|diagnostic_pass|shouldPassRefreshForDiagnostics|codexSettingsUsageFallback|maybeRedirectCodexPostAuthToUsage' -Quiet; if ($bad) { exit 1 }; 'PASS no fake or non-production refresh paths' | Tee-Object .omo/evidence/task-5-anti-slop-scan.txt"` exits 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Targeted provider/session suite passes
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest --tests com.aiquota.mobile.providers.ProviderRefreshPlanTest --tests com.aiquota.mobile.providers.ProviderWebSessionClearPolicyTest --tests com.aiquota.mobile.providers.WebViewSessionPersistenceTest --tests com.aiquota.mobile.local.ProviderUsageSnapshotTest 2>&1 | Tee-Object .omo/evidence/task-5-provider-session-suite.txt"
    Expected: Exit code 0; no failed tests.
    Evidence: .omo/evidence/task-5-provider-session-suite.txt

  Scenario: Existing dirty worktree is preserved
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "git diff -- android/app/src/main/java/com/aiquota/mobile/MainActivity.kt android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshHealthScheduler.kt android/app/src/main/java/com/aiquota/mobile/update/AppUpdateCheckScheduler.kt android/app/src/test/java/com/aiquota/mobile/update/AppUpdateCheckTest.kt | Tee-Object .omo/evidence/task-5-unrelated-diff-check.patch; 'review unrelated diff separately; provider task must not revert it' | Tee-Object -Append .omo/evidence/task-5-unrelated-diff-check.patch"
    Expected: Evidence exists; executor did not revert or normalize unrelated dirty files.
    Evidence: .omo/evidence/task-5-unrelated-diff-check.patch
  ```

  Commit: YES | Message: `fix(android): restore provider session refresh collection` | Files: [android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt, android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt, android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt, android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt, android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionClearPolicy.kt, android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt, android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt, android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt, android/app/src/test/java/com/aiquota/mobile/providers/*.kt, android/app/src/test/java/com/aiquota/mobile/local/*.kt]

- [ ] 6. Run targeted and full unit test/build gates

  What to do: Run the full debug unit test suite and assemble the debug APK. Capture complete logs. Treat Gradle/toolchain failures as blockers, not implementation success.
  Must NOT do: Do not skip tests because they are slow. Do not run release signing or release upload tasks. Do not mutate provider data.

  Parallelization: Can parallel: NO | Wave 2 | Blocks: [7] | Blocked by: [5]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/build.gradle.kts:81-152` - Android debug build configuration.
  - Pattern:  `android/app/build.gradle.kts:162-180` - dependencies and test framework.
  - Pattern:  `android/app/src/main/AndroidManifest.xml:71-82` - foreground service must compile into debug APK.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:8-392` - refresh service regression suite.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/ProviderWebCollectorScriptsTest.kt` - collector runtime script tests with Node assumptions.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest 2>&1 | Tee-Object .omo/evidence/task-6-all-unit-tests.txt"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:assembleDebug 2>&1 | Tee-Object .omo/evidence/task-6-assemble-debug.txt; if (-not (Test-Path 'android/app/build/outputs/apk/debug/app-debug.apk')) { exit 1 }"` exits 0 and the APK exists.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Item android/app/build/outputs/apk/debug/app-debug.apk | Select-Object FullName,Length,LastWriteTime | ConvertTo-Json | Tee-Object .omo/evidence/task-6-apk-metadata.json"` exits 0 and `Length` is greater than 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Full unit suite passes
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$gradle='C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\a04bxjujx95o3nb99gddekhwo\gradle-8.10.2\bin\gradle.bat'; & $gradle -p android :app:testDebugUnitTest 2>&1 | Tee-Object .omo/evidence/task-6-full-unit-qa.txt"
    Expected: Exit code 0; Gradle reports `BUILD SUCCESSFUL`.
    Evidence: .omo/evidence/task-6-full-unit-qa.txt

  Scenario: Debug build artifact exists
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$apk='android/app/build/outputs/apk/debug/app-debug.apk'; if (-not (Test-Path $apk)) { exit 1 }; Get-FileHash $apk -Algorithm SHA256 | Tee-Object .omo/evidence/task-6-apk-sha256.txt"
    Expected: Exit code 0; SHA-256 hash is captured.
    Evidence: .omo/evidence/task-6-apk-sha256.txt
  ```

  Commit: NO | Message: `test(android): verify provider refresh build` | Files: [.omo/evidence/task-6-*]

- [ ] 7. Relaunch emulator, install debug APK, and launch app

  What to do: Relaunch the emulator, install the newly assembled debug APK, launch the app, and capture package/version/process state. Use `Pixel_6a` unless Task 1 selected a different available AVD. Preserve existing provider sessions where possible by using `adb install -r` rather than clearing app data.
  Must NOT do: Do not uninstall the app or run `pm clear` unless a blocker requires a clean-room reproduction and the user explicitly approves. Do not erase the AVD. Do not wipe WebView/cookie data.

  Parallelization: Can parallel: NO | Wave 2 | Blocks: [8, 9] | Blocked by: [6]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/build.gradle.kts:85-96` - application ID `com.aiquota.mobile` and build config.
  - Pattern:  `android/app/src/main/AndroidManifest.xml:63-70` - `.MainActivity` launcher component.
  - Pattern:  `android/app/src/main/AndroidManifest.xml:71-82` - `.providers.ProviderBackgroundRefreshService` foreground service.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:999-1016` - refresh service actions and manual refresh intent extras.
  - External: `https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work` - WorkManager/unique work guidance and stopped-worker cleanup risk context.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; $emu=Join-Path $env:LOCALAPPDATA 'Android\Sdk\emulator\emulator.exe'; & $adb -s emulator-5554 emu kill 2>$null; Start-Sleep -Seconds 5; Start-Process -FilePath $emu -ArgumentList @('-avd','Pixel_6a','-netdelay','none','-netspeed','full') -WindowStyle Hidden; & $adb wait-for-device; for($i=0;$i -lt 60;$i++){ $boot=(& $adb -s emulator-5554 shell getprop sys.boot_completed).Trim(); if($boot -eq '1'){ break }; Start-Sleep -Seconds 2 }; & $adb -s emulator-5554 shell getprop sys.boot_completed | Tee-Object .omo/evidence/task-7-boot-completed.txt"` exits 0 and evidence contains `1`.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; & $adb -s emulator-5554 install -r android/app/build/outputs/apk/debug/app-debug.apk 2>&1 | Tee-Object .omo/evidence/task-7-install.txt; & $adb -s emulator-5554 shell am start -n com.aiquota.mobile/.MainActivity 2>&1 | Tee-Object .omo/evidence/task-7-launch.txt; Start-Sleep -Seconds 3; & $adb -s emulator-5554 shell dumpsys package com.aiquota.mobile | Select-String -Pattern 'Package \\[com.aiquota.mobile\\]|versionCode=|versionName=' | Tee-Object .omo/evidence/task-7-package.txt; & $adb -s emulator-5554 shell pidof com.aiquota.mobile | Tee-Object .omo/evidence/task-7-pid.txt"` exits 0 and captures package plus PID state.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; & $adb -s emulator-5554 shell run-as com.aiquota.mobile ls shared_prefs 2>&1 | Tee-Object .omo/evidence/task-7-shared-prefs-after-install.txt"` exits 0 and still lists provider scoped prefs if they existed before install.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Emulator relaunch and app install/launch succeed
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$okBoot=Select-String -Path .omo/evidence/task-7-boot-completed.txt -Pattern '^1$' -Quiet; $okInstall=Select-String -Path .omo/evidence/task-7-install.txt -Pattern 'Success' -Quiet; $okPackage=Select-String -Path .omo/evidence/task-7-package.txt -Pattern 'Package \\[com.aiquota.mobile\\]' -Quiet; [pscustomobject]@{boot=$okBoot; install=$okInstall; package=$okPackage} | ConvertTo-Json | Tee-Object .omo/evidence/task-7-install-launch-check.txt; if (-not ($okBoot -and $okInstall -and $okPackage)) { exit 1 }"
    Expected: Exit code 0; boot, install, and package evidence are present.
    Evidence: .omo/evidence/task-7-install-launch-check.txt

  Scenario: Session-preserving install did not clear scoped prefs
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$after=Get-Content .omo/evidence/task-7-shared-prefs-after-install.txt -Raw; if ($after -notmatch 'ai_quota_local_usage.xml') { exit 1 }; 'PASS app shared prefs accessible after install - no pm clear used' | Tee-Object .omo/evidence/task-7-no-clear-check.txt"
    Expected: Exit code 0; evidence confirms app data is still accessible.
    Evidence: .omo/evidence/task-7-no-clear-check.txt
  ```

  Commit: NO | Message: `test(android): relaunch emulator and install debug app` | Files: [.omo/evidence/task-7-*]

- [ ] 8. Verify real Codex and Gemini refresh with logcat and sanitized snapshots

  What to do: Run manual refresh for Codex and Gemini on the relaunched emulator. Capture redacted logcat for each provider, inspect provider-scoped and local/widget snapshots, and prove either fresh trusted usage was collected or a concrete no-fake/blocker state was preserved. This task is only complete when Codex and Gemini have real provider evidence; if the emulator lacks real sessions, stop with blocker evidence instead of fabricating success.
  Must NOT do: Do not seed fake snapshots. Do not edit SharedPreferences by hand. Do not clear provider sessions. Do not infer auth failure from no-payload alone.

  Parallelization: Can parallel: NO | Wave 3 | Blocks: [9] | Blocked by: [7]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:255-344` - cycle start, mark collecting, outcome save/failure behavior.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:451-475` - WebView collection timeout.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:544-569` - injection/reinjection logs.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:572-607` - Gemini/OpenCode redirect behavior.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:837-887` - bridge payload/error completion and Codex retry.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt:65-99` - normalized collection logs for login/collection service path.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderSnapshotCodec.kt:37-70` - snapshot JSON shape for sanitized inspection.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt:218-230` - display/widget export.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/widget/WidgetSnapshotCache.kt:43-58` - widget cache read state.
  - External: `https://developer.android.com/reference/android/webkit/CookieManager` - cookie persistence/flush behavior relevant to WebView session collection.

  Acceptance criteria (agent-executable only):
  - [ ] Codex logcat evidence contains `cycleStart` with `codex`, `load provider=codex`, and at least one `inject provider=codex` or `reinject provider=codex`; it must not contain raw emails, bearer tokens, OAuth codes, cookie values, or raw payload JSON.
  - [ ] Gemini logcat evidence contains `cycleStart` with `gemini`, `load provider=gemini`, and one of: `inject provider=gemini`, `reinject provider=gemini`, `redirectUsage provider=gemini`, `googleWebSessionFallback provider=gemini`, or `outcome provider=gemini type=Snapshot`; it must not contain raw secrets.
  - [ ] Sanitized Codex snapshot summary shows `providerId=codex`, `refreshState=IDLE`, `lineCount > 0`, and `updatedAtAfter` greater than `updatedAtBefore`; OR blocker evidence shows no real Codex session and no fake `updatedAt`/lines were written.
  - [ ] Sanitized Gemini snapshot summary shows `providerId=gemini`, `refreshState=IDLE`, and either `lineCount > 0` with fresh trusted payload or a recoverable pending/no-payload state with `updatedAt` preserved when previous lines existed.
  - [ ] Widget/local cache evidence is sanitized and shows no provider has synthetic usage values.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Codex real manual refresh
    Tool:     powershell(adb)
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; $serial='emulator-5554'; & $adb -s $serial logcat -c; $before=(& $adb -s $serial exec-out run-as com.aiquota.mobile cat shared_prefs/usage_data_codex.xml 2>$null); $before | Set-Content .omo/evidence/task-8-codex-before.xml; & $adb -s $serial shell am start-foreground-service -n com.aiquota.mobile/.providers.ProviderBackgroundRefreshService -a com.aiquota.mobile.action.REFRESH --es com.aiquota.mobile.widget.extra.PROVIDER_ID codex | Tee-Object .omo/evidence/task-8-codex-refresh-command.txt; Start-Sleep -Seconds 80; & $adb -s $serial logcat -d -v time | Select-String -Pattern 'AIQuotaBgRefreshService|AIQuotaCollector|AIQuotaBgCollector|provider=codex|cycleStart|inject|reinject|retryCollector|dropPayload|codex_usage_unavailable|Background refresh|ANR' | ForEach-Object { $_.Line -replace 'Bearer\\s+\\S+','Bearer <REDACTED>' -replace 'code=[^&\\s]+','code=<REDACTED>' -replace '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+','<REDACTED_EMAIL>' -replace 'account_id=[^&\\s]+','account_id=<REDACTED>' } | Tee-Object .omo/evidence/task-8-codex-logcat-redacted.txt; & $adb -s $serial exec-out run-as com.aiquota.mobile cat shared_prefs/usage_data_codex.xml | Set-Content .omo/evidence/task-8-codex-after.xml; & $adb -s $serial exec-out run-as com.aiquota.mobile cat shared_prefs/ai_quota_local_usage.xml | Set-Content .omo/evidence/task-8-local-usage-after-codex.xml"
    Expected: Exit code 0; logcat contains Codex cycle/load/inject evidence and sanitized snapshots are captured.
    Evidence: .omo/evidence/task-8-codex-logcat-redacted.txt

  Scenario: Gemini real manual refresh
    Tool:     powershell(adb)
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; $serial='emulator-5554'; & $adb -s $serial logcat -c; & $adb -s $serial exec-out run-as com.aiquota.mobile cat shared_prefs/usage_data_gemini.xml 2>$null | Set-Content .omo/evidence/task-8-gemini-before.xml; & $adb -s $serial shell am start-foreground-service -n com.aiquota.mobile/.providers.ProviderBackgroundRefreshService -a com.aiquota.mobile.action.REFRESH --es com.aiquota.mobile.widget.extra.PROVIDER_ID gemini | Tee-Object .omo/evidence/task-8-gemini-refresh-command.txt; Start-Sleep -Seconds 90; & $adb -s $serial logcat -d -v time | Select-String -Pattern 'AIQuotaBgRefreshService|AIQuotaCollector|AIQuotaBgCollector|AIQuotaGoogleWeb|provider=gemini|cycleStart|inject|reinject|redirectUsage|googleWebSessionFallback|terminalCheck|interactiveSignInRequired|Background refresh|ANR' | ForEach-Object { $_.Line -replace 'Bearer\\s+\\S+','Bearer <REDACTED>' -replace 'code=[^&\\s]+','code=<REDACTED>' -replace '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+','<REDACTED_EMAIL>' -replace 'Authorization: [^\\s]+','Authorization: <REDACTED>' } | Tee-Object .omo/evidence/task-8-gemini-logcat-redacted.txt; & $adb -s $serial exec-out run-as com.aiquota.mobile cat shared_prefs/usage_data_gemini.xml | Set-Content .omo/evidence/task-8-gemini-after.xml; & $adb -s $serial exec-out run-as com.aiquota.mobile cat shared_prefs/ai_quota_widget_cache.xml | Set-Content .omo/evidence/task-8-widget-cache-after-gemini.xml"
    Expected: Exit code 0; logcat contains Gemini cycle/load plus collection, redirect, fallback, or explicit pending evidence; snapshots captured.
    Evidence: .omo/evidence/task-8-gemini-logcat-redacted.txt
  ```

  Commit: NO | Message: `test(android): verify codex and gemini real refresh` | Files: [.omo/evidence/task-8-*]

- [ ] 9. Run GLM/OpenCode risk sweep and ANR absence check

  What to do: Verify GLM/OpenCode are no longer silently diagnostic-passed, then run an ANR absence check after the Codex/Gemini verification window. If GLM/OpenCode sessions are not configured, the pass condition is scheduled/attempted collection or explicit no-session state without diagnostic pass. Confirm no ANR file newer than the verification start time and no logcat ANR in the refresh window.
  Must NOT do: Do not change GLM/OpenCode login/credential flows beyond the minimal refresh-session fix. Do not delete old ANR files. Do not hide ANR evidence by truncating logs before capture.

  Parallelization: Can parallel: NO | Wave 3 | Blocks: [Final verification] | Blocked by: [7, 8]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt:53-117` - GLM and OpenCode auth/collection definitions.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:346-358` - GLM/OpenCode runtime resolution.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:598-607` - OpenCode refresh redirect to `/go`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GlmUsagePageRoutes.kt:14-22` - GLM Web OAuth hidden collector job.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/OpenCodeUsagePageRoutes.kt:6-36` - OpenCode canonical usage URL.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt:35-61` - widget/notification surface refresh runs off debounced background scope.
  - Pattern:  `android/app/src/test/java/com/aiquota/mobile/providers/UsageSurfaceRefresherTest.kt:14-68` - surface refresh non-blocking/widget update expectations.
  - External: `https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs` - execute-service ANR guidance: keep service `onCreate`, `onStartCommand`, and related main-thread startup paths fast.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$bad=Select-String -Path android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt -Pattern 'shouldPassRefreshForDiagnostics|diagnostic_pass|reason=diagnostic_pass' -Quiet; if ($bad) { exit 1 }; 'PASS GLM/OpenCode diagnostic pass absent' | Tee-Object .omo/evidence/task-9-diagnostic-pass-scan.txt"` exits 0.
  - [ ] GLM manual refresh log evidence contains `provider=glm` with native or hidden WebView collection attempt, or a concrete no-credential/no-subscription outcome; it must not contain `diagnostic_pass`.
  - [ ] OpenCode manual refresh log evidence contains `provider=opencode` with load/redirect/inject/payload/error evidence, or a concrete no-session outcome; it must not contain `diagnostic_pass`.
  - [ ] `/data/anr` after evidence has no file timestamp newer than the Task 8 verification start and no `ANR in com.aiquota.mobile` appears in post-refresh logcat.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: GLM and OpenCode are attempted instead of diagnostic-passed
    Tool:     powershell(adb)
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; $serial='emulator-5554'; & $adb -s $serial logcat -c; foreach($provider in @('glm','opencode')) { & $adb -s $serial shell am start-foreground-service -n com.aiquota.mobile/.providers.ProviderBackgroundRefreshService -a com.aiquota.mobile.action.REFRESH --es com.aiquota.mobile.widget.extra.PROVIDER_ID $provider | Out-File .omo/evidence/task-9-$provider-command.txt; Start-Sleep -Seconds 35 }; & $adb -s $serial logcat -d -v time | Select-String -Pattern 'AIQuotaBgRefreshService|AIQuotaCollector|AIQuotaBgCollector|provider=glm|provider=opencode|cycleStart|load provider=|redirectUsage|inject|reinject|diagnostic_pass|Background refresh' | ForEach-Object { $_.Line -replace 'Bearer\\s+\\S+','Bearer <REDACTED>' -replace '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+','<REDACTED_EMAIL>' } | Tee-Object .omo/evidence/task-9-glm-opencode-logcat-redacted.txt; if (Select-String -Path .omo/evidence/task-9-glm-opencode-logcat-redacted.txt -Pattern 'diagnostic_pass' -Quiet) { exit 1 }"
    Expected: Exit code 0; evidence shows attempted GLM/OpenCode refresh or explicit no-session outcome, and no diagnostic pass.
    Evidence: .omo/evidence/task-9-glm-opencode-logcat-redacted.txt

  Scenario: No new ANR after refresh verification
    Tool:     powershell(adb)
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$adb='C:\Users\datell1357\Tools\android-reversing\android-sdk\platform-tools\adb.exe'; $serial='emulator-5554'; & $adb -s $serial shell ls -la /data/anr 2>&1 | Tee-Object .omo/evidence/task-9-anr-after.txt; & $adb -s $serial logcat -d -v time | Select-String -Pattern 'ANR in com.aiquota.mobile|Input dispatching timed out|SystemJobService|onStartJob' | Tee-Object .omo/evidence/task-9-anr-logcat.txt; $newAnr = Select-String -Path .omo/evidence/task-9-anr-after.txt -Pattern '2026-06-21 11:(3[8-9]|[4-5][0-9])|2026-06-21 12:|2026-06-21 13:' -Quiet; $logAnr = Select-String -Path .omo/evidence/task-9-anr-logcat.txt -Pattern 'ANR in com.aiquota.mobile' -Quiet; if ($newAnr -or $logAnr) { exit 1 }; 'PASS no new app ANR after verification window' | Tee-Object .omo/evidence/task-9-anr-pass.txt"
    Expected: Exit code 0; no ANR files newer than the known 11:20/11:26 traces and no app ANR logcat line.
    Evidence: .omo/evidence/task-9-anr-pass.txt
  ```

  Commit: NO | Message: `test(android): verify provider refresh runtime evidence` | Files: [.omo/evidence/task-9-*]

## Final verification wave (MANDATORY - after all implementation tasks)
> Runs in PARALLEL. ALL must APPROVE. Surface results to the caller and wait for an explicit "okay" before declaring complete.
- [ ] F1. Plan compliance audit - every task done, every acceptance criterion met
- [ ] F2. Code quality review - diagnostics clean, idioms match, no dead code
- [ ] F3. Real manual QA - every QA scenario executed with evidence captured
- [ ] F4. Scope fidelity - nothing extra shipped beyond Must-Have, nothing Must-NOT-Have introduced

## Commit strategy
- One logical change per commit. Conventional Commits (`<type>(<scope>): <subject>` body + footer).
- Atomic: every commit builds and passes tests on its own.
- No "WIP" / "fix typo squash later" commits on the final branch - clean up before merge.
- Reference the plan file path in the final commit footer: `Plan: .omo/plans/provider-refresh-session-recovery-ulw-loop.md`.

## Success criteria
- All Must-Have shipped; all QA scenarios pass with captured evidence; F1-F4 approved; commit history clean.
