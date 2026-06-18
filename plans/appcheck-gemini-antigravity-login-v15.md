# Fix Firebase App Check Login For Gemini And Antigravity v15

## TL;DR
> Summary:      Fix v15 Gemini CLI and Antigravity login start failures by proving and correcting App Check token validity for debug and Play internal-testing release builds while keeping callable enforcement enabled. Source edits are limited to redacted diagnostics and regression tests only if runtime evidence shows the app hides or misclassifies the App Check rejection.
> Deliverables:
> - Debug App Check token registered and verified against `com-aiquota-mobile`.
> - Release/internal-testing Play Integrity, Play app signing SHA-256, and Firebase App Check registration verified or corrected.
> - Gemini/Antigravity login flows reach Google OAuth before any user account action in debug and release.
> - App-side diagnostics classify App Check/Auth rejection without exposing tokens, cookies, OAuth codes, emails, or provider HTML.
> - Regression tests prove App Check remains enforced.
> Effort:       Medium
> Risk:         Medium - External Firebase/Play Console state can block release verification even when repo code is correct.

## Scope
### Must have
- Keep `enforceAppCheck: true` for `startGeminiCliOAuth` and `startAntigravityOAuth`; source currently wires this in `functions/src/index.js:26-31` and `functions/src/index.js:67-72`.
- Preserve the existing debug/release provider split: debug installs `DebugAppCheckProviderFactory` in `android/app/src/debug/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt:10-15`; release installs `PlayIntegrityAppCheckProviderFactory` in `android/app/src/release/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt:10-15`.
- Prove debug failures with logcat evidence and register the exact SDK-generated debug UUID using Firebase App Check debug token APIs or Firebase Console automation.
- Prove release/internal-testing installs came from Google Play before evaluating release App Check: installer package, versionCode `15`, package `com.aiquota.mobile`, and delivered signing SHA-256.
- Verify Play Integrity API linkage, Play app signing SHA-256, Firebase App Check Play Integrity registration, and advanced settings for a Google Play-only distribution.
- If source changes are needed, use TDD: a characterization test first, a failing test that captures the bug, minimal implementation, then regression.
- Keep provider diagnostics redacted only: status, provider, callable name, Firebase Functions code, app-check rejection classification, timeout/retry state. No raw secrets or page content.

### Must NOT have (guardrails, anti-slop, scope boundaries)
- Do not disable App Check, do not set `enforceAppCheck: false`, and do not weaken release-hardening tests.
- Do not treat the release/internal-testing failure as sideload-only unless `adb shell dumpsys package com.aiquota.mobile` proves the installer is not `com.android.vending`.
- Do not embed OAuth client secrets in Android. Existing tests guard this in `android/app/src/test/java/com/aiquota/mobile/providers/GeminiCliFirebaseGatewayTest.kt:10-25`.
- Do not store debug tokens, App Check tokens, OAuth codes, cookies, auth headers, emails, usernames, or provider HTML in repo files or evidence.
- Do not add broad refactors to Firebase gateways, provider repositories, dashboard UI, or unrelated collectors.
- Do not mark completion from tests alone; the actual Android login start scenario must be exercised through adb/logcat and UI state.

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- Test decision: TDD + Node `node --test`, repo source tests, and Android JVM tests.
- QA policy: every task has agent-executed scenarios
- Evidence: `evidence/task-<N>-<slug>.<ext>`

## Execution strategy
### Parallel execution waves
> Target 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks to maximize parallelism.

Wave 1 (no dependencies):
- Task 1: Baseline source/test contract and current failure matrix
- Task 2: Debug App Check token capture and registration
- Task 3: Firebase App Check and Functions enforcement inventory
- Task 4: Play internal-testing identity and signing proof
- Task 5: Hypothesis matrix and decision gate

Wave 2 (after Wave 1):
- Task 6: depends [1, 5]
- Task 7: depends [1, 3, 5]
- Task 8: depends [2, 5]
- Task 9: depends [3, 4, 5]

Wave 3 (after Wave 2):
- Task 10: depends [6, 7, 8, 9]
- Task 11: depends [10]
- Task 12: depends [10]

Critical path: Task 1 -> Task 5 -> Task 6 -> Task 10 -> Task 12

### Dependency matrix
| Task | Depends on | Blocks | Can parallelize with |
|------|------------|--------|----------------------|
| 1    | none       | 5, 6, 7 | 2, 3, 4              |
| 2    | none       | 5, 8    | 1, 3, 4              |
| 3    | none       | 5, 7, 9 | 1, 2, 4              |
| 4    | none       | 5, 9    | 1, 2, 3              |
| 5    | 1, 2, 3, 4 | 6, 7, 8, 9 | none              |
| 6    | 1, 5       | 10      | 7, 8, 9              |
| 7    | 1, 3, 5    | 10      | 6, 8, 9              |
| 8    | 2, 5       | 10      | 6, 7, 9              |
| 9    | 3, 4, 5    | 10      | 6, 7, 8              |
| 10   | 6, 7, 8, 9 | 11, 12  | none                 |
| 11   | 10         | final   | 12                   |
| 12   | 10         | final   | 11                   |

## Todos
> Implementation + Test = ONE task. Never separate.
> Every task MUST have: References + Acceptance Criteria + QA Scenarios + Commit.

- [ ] 1. Baseline source/test contract and current failure matrix

  What to do: Capture the current repo contract before changing anything. Create `evidence/`, save `git status`, run the existing source and unit tests that assert Firebase App Check wiring, and capture the current debug/release login-start failure evidence if devices are already connected. Record whether the observed 403 is before Google OAuth for Gemini, Antigravity, or both.
  Must NOT do: Do not edit source files, do not register tokens, do not deploy Functions, and do not infer release sideloading without installer evidence.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [5, 6, 7] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `package.json:7-11` - root `npm.cmd test`, `test:mobile`, and `test:functions` commands.
  - Pattern:  `README.md:68-88` - documented npm, Gradle unit test, debug build, and release bundle commands.
  - Pattern:  `test/release-hardening.test.js:21-27` - asserts callable gateways keep `enforceAppCheck: true`.
  - Test:     `test/mobile-source.test.js:159-201` - asserts Firebase Auth, Functions, App Check providers, callable names, and no Android client secrets.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt:55-71` - asserts Firebase Auth and App Check bootstrap.
  - External: `https://firebase.google.com/docs/functions/callable` - callable requests include Auth/App Check tokens when available and need Firebase Android SDK 22.1.1+.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -Command "New-Item -ItemType Directory -Force evidence | Out-Null; git status --short | Tee-Object evidence/task-1-git-status.txt"` completes and the evidence file exists.
  - [ ] `powershell -NoProfile -Command "npm.cmd test 2>&1 | Tee-Object evidence/task-1-npm-test.txt"` exits 0.
  - [ ] `powershell -NoProfile -Command "& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest 2>&1 | Tee-Object evidence/task-1-android-gateway-tests.txt"` exits 0.
  - [ ] `powershell -NoProfile -Command "$found = Select-String -Path functions/src/index.js -Pattern 'enforceAppCheck: false' -Quiet; if ($found) { exit 1 } else { 'no enforceAppCheck false' | Set-Content evidence/task-1-no-disable.txt }"` records no App Check disablement.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: baseline contracts pass
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force evidence | Out-Null; npm.cmd test; & '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest"
    Expected: Exit code 0; stdout includes Node test pass output and Gradle `BUILD SUCCESSFUL`.
    Evidence: evidence/task-1-baseline-tests.txt

  Scenario: forbidden App Check disablement is absent
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$bad = Select-String -Path functions/src/index.js,functions/src/*.js -Pattern 'enforceAppCheck:\s*false'; if ($bad) { $bad | Tee-Object evidence/task-1-disablement-error.txt; exit 1 } else { 'PASS no enforceAppCheck false' | Tee-Object evidence/task-1-disablement-error.txt }"
    Expected: Exit code 0; evidence says `PASS no enforceAppCheck false`.
    Evidence: evidence/task-1-disablement-error.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-1-*.txt]

- [ ] 2. Debug App Check token capture and registration

  What to do: On a debug build, trigger a Firebase backend call before Google OAuth, capture the `DebugAppCheckProvider` UUID from logcat, register that UUID for the Firebase Android app, and verify the same debug build no longer gets App Check 403 on `startGeminiCliOAuth` or `startAntigravityOAuth`. Use the App Check REST API when possible; if REST auth is unavailable, drive Firebase Console in Chrome and capture a screenshot with the UUID redacted.
  Must NOT do: Do not commit the debug token. Do not store the raw token in evidence. Do not switch debug builds to Play Integrity.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [5, 8] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/debug/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt:3-15` - debug build installs `DebugAppCheckProviderFactory`.
  - Pattern:  `android/app/build.gradle.kts:125-129` - Firebase BoM plus App Check Play Integrity and debug dependencies.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt:16-24` - `startGeminiCliOAuth` callable path.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt:17-25` - `startAntigravityOAuth` callable path.
  - External: `https://firebase.google.com/docs/app-check/android/debug-provider` - debug provider logs a UUID to register, then backend services accept it as valid.
  - External: `https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.apps.debugTokens` - debug token resource requires UUID4 token and display name; token is input-only.
  - External: `https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.apps.debugTokens/create` - REST create endpoint is `POST /v1/{parent=projects/*/apps/*}/debugTokens`.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -Command "adb devices | Tee-Object evidence/task-2-adb-devices.txt"` shows at least one `device`.
  - [ ] `powershell -NoProfile -Command "& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:installDebug 2>&1 | Tee-Object evidence/task-2-install-debug.txt"` exits 0.
  - [ ] `powershell -NoProfile -Command "adb logcat -d -s DebugAppCheckProvider FirebaseAppCheck AIQuotaGemini AIQuotaAntigravity AIQuotaFirebaseGateway | Out-String | Set-Content evidence/task-2-debug-logcat-redacted.txt"` captures a redacted `DebugAppCheckProvider` registration hint or proves a previously registered token path.
  - [ ] `powershell -NoProfile -Command "$raw='evidence/task-2-debug-logcat-raw.tmp'; adb logcat -d -s DebugAppCheckProvider FirebaseAppCheck | Set-Content $raw; $match=Select-String -Path $raw -Pattern '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}' | Select-Object -First 1; if (-not $match) { exit 1 }; $uuid=[regex]::Match($match.Line,'[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}').Value; Set-Content evidence/task-2-debug-token.tmp $uuid; (Get-Content $raw) -replace '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}','<REDACTED_DEBUG_UUID>' | Set-Content evidence/task-2-debug-logcat-redacted.txt; Remove-Item $raw -ErrorAction SilentlyContinue"` extracts the debug UUID only into a temporary file and redacts evidence.
  - [ ] `powershell -NoProfile -Command "$project='com-aiquota-mobile'; $projectNumber=(gcloud.cmd projects describe $project --format='value(projectNumber)'); $apps=(firebase.cmd apps:list --project $project --json | ConvertFrom-Json).result; $app=$apps | Where-Object { $_.platform -eq 'ANDROID' -and $_.packageName -eq 'com.aiquota.mobile' } | Select-Object -First 1; if (-not $app) { exit 1 }; $token=(Get-Content .\evidence\task-2-debug-token.tmp); $body=@{ displayName=('AI Quota v15 debug ' + (Get-Date -Format yyyyMMddHHmmss)); token=$token } | ConvertTo-Json; $auth=gcloud.cmd auth print-access-token; Invoke-RestMethod -Method Post -Headers @{Authorization='Bearer ' + $auth; 'Content-Type'='application/json'} -Uri ('https://firebaseappcheck.googleapis.com/v1/projects/' + $projectNumber + '/apps/' + $app.appId + '/debugTokens') -Body $body | ConvertTo-Json -Depth 5 | ForEach-Object { $_ -replace '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}', '<REDACTED_DEBUG_UUID>' } | Set-Content evidence/task-2-debug-token-registration.txt; Remove-Item .\evidence\task-2-debug-token.tmp -ErrorAction SilentlyContinue"` exits 0 or records a Chrome-driven console fallback evidence file.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: debug token allows Gemini and Antigravity callable start
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb logcat -c; adb shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 3; adb logcat -d -s DebugAppCheckProvider FirebaseAppCheck AIQuotaGemini AIQuotaAntigravity AIQuotaFirebaseGateway GoogleOAuth AndroidRuntime | Tee-Object evidence/task-2-debug-callable-start.txt"
    Expected: Evidence contains no `App attestation failed`, no `PERMISSION_DENIED`, and no `FirebaseFunctionsException: Unauthenticated` for `startGeminiCliOAuth` or `startAntigravityOAuth`; UI/logs reach Google OAuth preparation or WebView load.
    Evidence: evidence/task-2-debug-callable-start.txt

  Scenario: unregistered fake debug token is still rejected
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$project='com-aiquota-mobile'; $projectNumber=(gcloud.cmd projects describe $project --format='value(projectNumber)'); $apps=(firebase.cmd apps:list --project $project --json | ConvertFrom-Json).result; $app=$apps | Where-Object { $_.platform -eq 'ANDROID' -and $_.packageName -eq 'com.aiquota.mobile' } | Select-Object -First 1; $auth=gcloud.cmd auth print-access-token; try { Invoke-RestMethod -Method Post -Headers @{Authorization='Bearer ' + $auth; 'Content-Type'='application/json'} -Uri ('https://firebaseappcheck.googleapis.com/v1/projects/' + $projectNumber + '/apps/' + $app.appId + ':exchangeDebugToken') -Body (@{ debugToken='00000000-0000-4000-8000-000000000000' } | ConvertTo-Json) -ErrorAction Stop; 'UNEXPECTED_SUCCESS' | Tee-Object evidence/task-2-fake-token-error.txt; exit 1 } catch { $_.Exception.Message | Tee-Object evidence/task-2-fake-token-error.txt }"
    Expected: Exit code 0; evidence records an expected rejection for the fake UUID, proving enforcement still exists.
    Evidence: evidence/task-2-fake-token-error.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-2-*.txt]

- [ ] 3. Firebase App Check and Functions enforcement inventory

  What to do: Inventory the Firebase project `com-aiquota-mobile`, Android app registration for package `com.aiquota.mobile`, App Check debug token display names, Play Integrity config, App Check service enforcement for Cloud Functions, and recent Functions logs. Save only redacted, non-secret outputs.
  Must NOT do: Do not change Firebase config in this task. Do not paste raw access tokens into evidence.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [5, 7, 9] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `.firebaserc:1-5` - default Firebase project is `com-aiquota-mobile`.
  - Pattern:  `firebase.json:15-18` - Functions source is `functions`, runtime is Node.js 22.
  - Pattern:  `functions/package.json:7-13` - Functions package uses Node 22 and `firebase-functions` `^6.0.0`.
  - Pattern:  `functions/src/index.js:94-102` - exported callable names include Gemini and Antigravity start/complete/refresh functions.
  - External: `https://firebase.google.com/docs/app-check/cloud-functions` - `enforceAppCheck: true` rejects missing or invalid App Check tokens.
  - External: `https://firebase.google.com/docs/reference/appcheck/rest` - REST resources include debug tokens, Play Integrity config, and service enforcement configs.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -Command "firebase.cmd apps:list --project com-aiquota-mobile --json | Tee-Object evidence/task-3-firebase-apps.json"` exits 0 and includes Android package `com.aiquota.mobile`.
  - [ ] `powershell -NoProfile -Command "gcloud.cmd services list --enabled --project com-aiquota-mobile --filter='NAME:firebaseappcheck.googleapis.com OR NAME:playintegrity.googleapis.com OR NAME:cloudfunctions.googleapis.com' --format=json | Tee-Object evidence/task-3-enabled-services.json"` exits 0 and includes App Check, Play Integrity, and Cloud Functions services.
  - [ ] `powershell -NoProfile -Command "firebase.cmd functions:log --project com-aiquota-mobile --only startGeminiCliOAuth,startAntigravityOAuth --limit 50 2>&1 | Tee-Object evidence/task-3-functions-log-redacted.txt"` exits 0 or records permission failure; any tokens/cookies are absent.
  - [ ] REST reads for `playIntegrityConfig` and `debugTokens` are saved to `evidence/task-3-appcheck-rest-redacted.json` with all UUIDs and tokens redacted.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Firebase project has the expected Android app and services
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$apps = firebase.cmd apps:list --project com-aiquota-mobile --json | Tee-Object evidence/task-3-firebase-apps.json | ConvertFrom-Json; $android = $apps.result | Where-Object { $_.platform -eq 'ANDROID' -and $_.packageName -eq 'com.aiquota.mobile' }; if (-not $android) { exit 1 }; gcloud.cmd services list --enabled --project com-aiquota-mobile --format=json | Tee-Object evidence/task-3-services.json"
    Expected: Exit code 0; app list includes Android package `com.aiquota.mobile`; services include Firebase App Check and Play Integrity.
    Evidence: evidence/task-3-firebase-apps.json

  Scenario: wrong project cannot satisfy the app check
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "firebase.cmd apps:list --project demo-ai-quota-mobile --json 2>&1 | Tee-Object evidence/task-3-wrong-project-error.txt; if (Select-String -Path evidence/task-3-wrong-project-error.txt -Pattern 'com.aiquota.mobile' -Quiet) { exit 1 }"
    Expected: Exit code 0; wrong project evidence does not contain `com.aiquota.mobile`, or CLI reports project not found/unauthorized.
    Evidence: evidence/task-3-wrong-project-error.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-3-*.json, evidence/task-3-*.txt]

- [ ] 4. Play internal-testing identity and signing proof

  What to do: On the device containing the Play Store internal-testing v15 install, prove the app is Play-delivered and signed with the Play app signing certificate. Compare that delivered certificate SHA-256 with Firebase App Check Play Integrity registration and the Play Console App integrity page. This is the task that prevents the sideload false diagnosis.
  Must NOT do: Do not use an emulator or locally installed APK as release evidence. Do not assume upload-key SHA equals delivered signing SHA.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [5, 9] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `README.md:13-19` - current upload artifact is the release AAB for Google Play internal testing.
  - Pattern:  `README.md:42-50` - Google Play package is `com.aiquota.mobile`.
  - Pattern:  `android/app/build.gradle.kts:49-54` - applicationId is `com.aiquota.mobile`, versionCode is `15`.
  - Pattern:  `android/app/build.gradle.kts:71-90` - release build uses release signing config before Play re-signs delivered APKs.
  - External: `https://firebase.google.com/docs/app-check/android/play-integrity-provider` - App Check Play Integrity setup requires the same Firebase project link and the app signing certificate SHA-256.
  - External: `https://support.google.com/googleplay/android-developer/answer/9842756` - Google signs final APKs, so API providers need the Google-held app signing key fingerprint, not only the upload key.
  - External: `https://developer.android.com/google/play/integrity/setup` - Play Integrity requires a linked Google Cloud project for Play-distributed apps and exposes verdicts such as `PLAY_RECOGNIZED` and `LICENSED`.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -Command "adb shell dumpsys package com.aiquota.mobile | Tee-Object evidence/task-4-package-dumpsys.txt"` includes `versionCode=15` and `installerPackageName=com.android.vending`.
  - [ ] `powershell -NoProfile -Command "$path=(adb shell pm path com.aiquota.mobile | Select-Object -First 1).Replace('package:','').Trim(); adb pull $path evidence/task-4-play-delivered-base.apk; & $env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat verify --print-certs evidence/task-4-play-delivered-base.apk | Tee-Object evidence/task-4-apksigner.txt"` exits 0 and records the delivered signer SHA-256.
  - [ ] Chrome/Play Console automation captures the App integrity page showing the Play app signing SHA-256; screenshot saved to `evidence/task-4-play-console-app-integrity.png` with any account/email redacted.
  - [ ] The delivered signer SHA-256 equals the Play app signing SHA-256 and is present in Firebase App Check Play Integrity registration evidence.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: release install is genuinely Play-delivered
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb shell dumpsys package com.aiquota.mobile | Tee-Object evidence/task-4-play-install.txt; if (-not (Select-String -Path evidence/task-4-play-install.txt -Pattern 'installerPackageName=com.android.vending' -Quiet)) { exit 1 }; if (-not (Select-String -Path evidence/task-4-play-install.txt -Pattern 'versionCode=15' -Quiet)) { exit 1 }"
    Expected: Exit code 0; installer is `com.android.vending`; versionCode is `15`.
    Evidence: evidence/task-4-play-install.txt

  Scenario: sideloaded release is rejected as release evidence
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$installer = (adb shell dumpsys package com.aiquota.mobile | Select-String 'installerPackageName=' | Select-Object -First 1).ToString(); if ($installer -notmatch 'com.android.vending') { 'REJECT_AS_RELEASE_EVIDENCE ' + $installer | Tee-Object evidence/task-4-sideload-edge.txt; exit 0 } else { 'PLAY_INSTALL_OK ' + $installer | Tee-Object evidence/task-4-sideload-edge.txt }"
    Expected: Exit code 0; evidence either rejects a sideloaded/local install or records Play install OK.
    Evidence: evidence/task-4-sideload-edge.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-4-*.txt, evidence/task-4-*.png, evidence/task-4-play-delivered-base.apk]

- [ ] 5. Hypothesis matrix and root cause decision gate

  What to do: Synthesize Tasks 1-4 into a written matrix with at least five hypotheses and distinguishing evidence: debug token missing/unregistered, Play app signing SHA mismatch, Play Integrity API/project linkage mismatch, App Check advanced settings too strict for internal-testing device, app-side bootstrap/order/SDK issue, or deployed Functions/source mismatch. Choose the minimal fix lane before code edits.
  Must NOT do: Do not proceed to source edits without a recorded root-cause decision. Do not collapse Auth and App Check into a single "session expired" explanation.

  Parallelization: Can parallel: NO | Wave 1 | Blocks: [6, 7, 8, 9] | Blocked by: [1, 2, 3, 4]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md:162-179` - previous repo note identifies Auth/App Check callable blocking and release Play Integrity setup risk.
  - Pattern:  `docs/qa/antigravity-usage-collection-analysis-2026-05-30.md:408-413` - prior emulator validation hit `FirebaseFunctionsException: Unauthenticated` because App Check rejected the test build.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/AIQuotaApplication.kt:5-9` - application startup installs Firebase App Check.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/MainActivity.kt:14-17` - MainActivity also installs Firebase App Check.
  - External: `https://firebase.google.com/docs/app-check/android/play-integrity-provider` - TTL, project linkage, SHA-256, and advanced settings guidance.
  - External: `https://support.google.com/googleplay/android-developer/answer/9842756` - Play-delivered APK uses Google-held app signing key.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -Command "if (-not (Test-Path evidence/task-5-hypothesis-matrix.md)) { exit 1 }"` proves the matrix exists.
  - [ ] `powershell -NoProfile -Command "$m=Get-Content evidence/task-5-hypothesis-matrix.md -Raw; @('debug token','Play app signing SHA-256','Play Integrity','advanced settings','bootstrap','deployed Functions') | ForEach-Object { if ($m -notmatch [regex]::Escape($_)) { throw \"missing $_\" } }"` exits 0.
  - [ ] The matrix includes one chosen fix lane and one explicit "do not disable App Check" decision.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: hypothesis matrix is complete and evidence-backed
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$m=Get-Content evidence/task-5-hypothesis-matrix.md -Raw; if (($m -split 'Hypothesis ').Count -lt 6) { exit 1 }; if ($m -notmatch 'Chosen fix lane:') { exit 1 }; if ($m -notmatch 'Do not disable App Check') { exit 1 }"
    Expected: Exit code 0; matrix contains at least five hypotheses, a chosen fix lane, and the App Check preservation decision.
    Evidence: evidence/task-5-hypothesis-matrix.md

  Scenario: no unverified release sideload claim
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$m=Get-Content evidence/task-5-hypothesis-matrix.md -Raw; if ($m -match 'sideload' -and $m -notmatch 'installerPackageName') { exit 1 }; 'PASS no unverified sideload claim' | Tee-Object evidence/task-5-sideload-claim-check.txt"
    Expected: Exit code 0; any sideload statement is tied to installer evidence.
    Evidence: evidence/task-5-sideload-claim-check.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-5-*.md, evidence/task-5-*.txt]

- [ ] 6. Redacted Android App Check diagnostics and error classification

  What to do: If Task 5 shows the app hides App Check/Auth failures or logs only the exception class, add a minimal diagnostic helper or inline mapping in Gemini and Antigravity loopback OAuth start/complete paths. The diagnostic must classify `FirebaseFunctionsException.Code.UNAUTHENTICATED`, `PERMISSION_DENIED`, and `FAILED_PRECONDITION` as Firebase Auth/App Check rejection without raw token or page data. Add failing-first tests that assert the new diagnostic strings and redaction rules before implementation.
  Must NOT do: Do not change provider success paths, do not log token values, do not alter OAuth URLs, and do not surface a false user-session-expired message.

  Parallelization: Can parallel: YES | Wave 2 | Blocks: [10] | Blocked by: [1, 5]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:108-127` - Gemini start flow currently logs only `startFailed=<ClassName>`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:108-127` - Antigravity start flow currently logs only `startFailed=<ClassName>`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:186-203` - Gemini failure snapshot/log helpers.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:186-203` - Antigravity failure snapshot/log helpers.
  - API/Type: `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt:130-159` - existing Functions exception mapping pattern for Antigravity collection.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt:55-71` - source-level Android App Check bootstrap test style.
  - Test:     `test/release-hardening.test.js:41-49` - release-hardening style for no raw WebView console logs.

  Acceptance criteria (agent-executable only):
  - [ ] A new or updated test fails before implementation with an assertion that Gemini and Antigravity classify App Check/Auth rejection using only provider, callable phase, Firebase Functions code, and redacted error kind.
  - [ ] `powershell -NoProfile -Command "& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest 2>&1 | Tee-Object evidence/task-6-android-tests.txt"` exits 0 after implementation.
  - [ ] `powershell -NoProfile -Command "npm.cmd run test:mobile 2>&1 | Tee-Object evidence/task-6-mobile-source-tests.txt"` exits 0 after implementation.
  - [ ] `powershell -NoProfile -Command "Select-String -Path android/app/src/main/java/com/aiquota/mobile/providers/*.kt,test/*.js,android/app/src/test/java/com/aiquota/mobile/providers/*.kt -Pattern 'access_token|refresh_token|client_secret|authorizationCode|cookie|DebugAppCheckProvider: Enter this debug secret' -CaseSensitive | Tee-Object evidence/task-6-secret-scan.txt; if (Get-Content evidence/task-6-secret-scan.txt | Select-String -Pattern 'Log\.|assert.*Log|message =') { exit 1 }"` exits 0 or only reports existing non-log token model field names that tests already allow.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: App Check rejection is redacted and accurately classified
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest 2>&1 | Tee-Object evidence/task-6-classification-tests.txt"
    Expected: Exit code 0; test names or assertions show App Check/Auth rejection classification for Gemini and Antigravity.
    Evidence: evidence/task-6-classification-tests.txt

  Scenario: diagnostics contain no raw secrets
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$patterns='access_token|refresh_token|client_secret|Set-Cookie|Authorization:|oauth_code|email='; Select-String -Path android/app/src/main/java/com/aiquota/mobile/providers/*.kt -Pattern $patterns | Tee-Object evidence/task-6-redaction-edge.txt; if (Get-Content evidence/task-6-redaction-edge.txt | Select-String -Pattern 'Log\.') { exit 1 }"
    Expected: Exit code 0; no diagnostic log writes raw secrets or private account data.
    Evidence: evidence/task-6-redaction-edge.txt
  ```

  Commit: YES | Message: `fix(android): classify firebase app check login failures` | Files: [android/app/src/main/java/com/aiquota/mobile/providers/*.kt, android/app/src/test/java/com/aiquota/mobile/providers/*.kt, test/*.js]

- [ ] 7. Functions App Check contract and deployment-shape tests

  What to do: Strengthen Functions tests so a future change cannot remove App Check enforcement or point Android to the wrong callable names. Add a failing-first test around the gateway builder options or release hardening if current tests do not catch every exported start callable. Keep implementation minimal, likely test-only unless Task 5 proves deployed source mismatch.
  Must NOT do: Do not add `consumeAppCheckToken` unless the client is also changed to request limited-use tokens; this task is not a replay-protection migration.

  Parallelization: Can parallel: YES | Wave 2 | Blocks: [10] | Blocked by: [1, 3, 5]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `functions/src/geminiCliGateway.js:163-171` - Gemini handler builder defaults `enforceAppCheck = true`.
  - Pattern:  `functions/src/geminiCliGateway.js:220-226` - Gemini `callOptionsFor` returns App Check enforcement and secrets.
  - Pattern:  `functions/src/antigravityGateway.js:650-658` - Antigravity handler builder defaults `enforceAppCheck = true`.
  - Pattern:  `functions/src/antigravityGateway.js:747-757` - Antigravity callable options return App Check enforcement and secrets.
  - Pattern:  `functions/src/index.js:94-102` - exports `startAntigravityOAuth` and `startGeminiCliOAuth`.
  - Test:     `functions/test/geminiCliGateway.test.js` - existing Gemini gateway test location.
  - Test:     `functions/test/antigravityGateway.test.js` - existing Antigravity gateway test location.
  - External: `https://firebase.google.com/docs/app-check/cloud-functions` - `enforceAppCheck: true` rejects missing or invalid tokens.

  Acceptance criteria (agent-executable only):
  - [ ] A red test fails if either `startGeminiCliOAuth` or `startAntigravityOAuth` lacks `enforceAppCheck: true`.
  - [ ] `powershell -NoProfile -Command "npm.cmd run test:functions 2>&1 | Tee-Object evidence/task-7-functions-tests.txt"` exits 0.
  - [ ] `powershell -NoProfile -Command "npm.cmd test 2>&1 | Tee-Object evidence/task-7-root-tests.txt"` exits 0.
  - [ ] No source contains `consumeAppCheckToken: true` unless Android callable invocations also set `limitedUseAppCheckTokens`.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: callable start functions enforce App Check in tests
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "npm.cmd run test:functions 2>&1 | Tee-Object evidence/task-7-appcheck-contract.txt"
    Expected: Exit code 0; output includes tests covering Gemini and Antigravity callable App Check enforcement.
    Evidence: evidence/task-7-appcheck-contract.txt

  Scenario: replay-protection partial migration is absent
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$server=Select-String -Path functions/src/*.js -Pattern 'consumeAppCheckToken:\s*true'; $client=Select-String -Path android/app/src/main/java/com/aiquota/mobile/providers/*.kt -Pattern 'limitedUseAppCheckTokens'; if ($server -and -not $client) { $server | Tee-Object evidence/task-7-replay-edge.txt; exit 1 }; 'PASS replay settings consistent' | Tee-Object evidence/task-7-replay-edge.txt"
    Expected: Exit code 0; evidence says replay settings are consistent.
    Evidence: evidence/task-7-replay-edge.txt
  ```

  Commit: YES | Message: `test(functions): lock app check callable enforcement` | Files: [functions/test/*.test.js, test/release-hardening.test.js, functions/src/*.js]

- [ ] 8. Debug build retest after token registration

  What to do: After Task 2, rebuild/install debug, clear logs, tap Gemini and Antigravity connect entries using UI tree-derived coordinates, and prove both flows reach Google OAuth/WebView without App Check 403. If Task 6 changed diagnostics first, include those updated logs; otherwise use the current logs. Capture UI tree, screenshots, and logcat.
  Must NOT do: Do not complete Google account sign-in unless required to reach the original failing boundary; the success criterion is reaching Google OAuth before user login.

  Parallelization: Can parallel: YES | Wave 2 | Blocks: [10] | Blocked by: [2, 5]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt:248-276` - Gemini and Antigravity connect launch loopback OAuth activities.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:37-66` - Gemini activity marks connecting and starts Firebase OAuth.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:37-66` - Antigravity activity marks connecting and starts Firebase OAuth.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:125-127` - Gemini loads returned authorization URL.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:125-127` - Antigravity loads returned authorization URL.
  - External: `https://firebase.google.com/docs/app-check/android/debug-provider` - after token registration, backend services accept debug provider tokens as valid.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -Command "& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:installDebug 2>&1 | Tee-Object evidence/task-8-install-debug.txt"` exits 0.
  - [ ] `evidence/task-8-gemini-logcat.txt` contains no App Check 403/attestation failure for `startGeminiCliOAuth`.
  - [ ] `evidence/task-8-antigravity-logcat.txt` contains no App Check 403/attestation failure for `startAntigravityOAuth`.
  - [ ] Screenshots or UI tree evidence show Google OAuth/WebView page or an authorization URL load boundary for both providers.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Gemini debug login reaches Google OAuth
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb logcat -c; adb shell am start -n com.aiquota.mobile/.MainActivity; adb exec-out uiautomator dump /dev/tty > evidence/task-8-gemini-ui-before.xml; python C:\Users\datell1357\.codex\plugins\cache\openai-curated\test-android-apps\c3319989\skills\android-emulator-qa\scripts\ui_pick.py evidence/task-8-gemini-ui-before.xml Gemini > evidence/task-8-gemini-tap.txt; $xy=(Get-Content evidence/task-8-gemini-tap.txt -Raw).Trim().Split(); adb shell input tap $xy[0] $xy[1]; Start-Sleep -Seconds 8; adb exec-out screencap -p > evidence/task-8-gemini-oauth.png; adb logcat -d -s DebugAppCheckProvider FirebaseAppCheck AIQuotaGemini AIQuotaFirebaseGateway GoogleOAuth AndroidRuntime | Tee-Object evidence/task-8-gemini-logcat.txt"
    Expected: Logcat has no App Check 403/attestation failure for `startGeminiCliOAuth`; screenshot/UI reaches Google OAuth/WebView boundary.
    Evidence: evidence/task-8-gemini-logcat.txt

  Scenario: Antigravity debug login reaches Google OAuth
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb logcat -c; adb shell am start -n com.aiquota.mobile/.MainActivity; adb exec-out uiautomator dump /dev/tty > evidence/task-8-antigravity-ui-before.xml; python C:\Users\datell1357\.codex\plugins\cache\openai-curated\test-android-apps\c3319989\skills\android-emulator-qa\scripts\ui_pick.py evidence/task-8-antigravity-ui-before.xml Antigravity > evidence/task-8-antigravity-tap.txt; $xy=(Get-Content evidence/task-8-antigravity-tap.txt -Raw).Trim().Split(); adb shell input tap $xy[0] $xy[1]; Start-Sleep -Seconds 8; adb exec-out screencap -p > evidence/task-8-antigravity-oauth.png; adb logcat -d -s DebugAppCheckProvider FirebaseAppCheck AIQuotaAntigravity AIQuotaFirebaseGateway GoogleOAuth AndroidRuntime | Tee-Object evidence/task-8-antigravity-logcat.txt"
    Expected: Logcat has no App Check 403/attestation failure for `startAntigravityOAuth`; screenshot/UI reaches Google OAuth/WebView boundary.
    Evidence: evidence/task-8-antigravity-logcat.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-8-*.txt, evidence/task-8-*.xml, evidence/task-8-*.png]

- [ ] 9. Release Play Integrity and App Check config remediation

  What to do: Correct the release/internal-testing configuration identified by Tasks 3-5. Expected fixes are external: register the Play app signing SHA-256 in Firebase App Check for `com.aiquota.mobile`, link the Play Integrity API to Firebase project `com-aiquota-mobile`, enable Play Integrity API if disabled, and set advanced App Check settings according to Google Play-only distribution (`PLAY_RECOGNIZED` required, `LICENSED` required, no explicit device integrity level). Capture before/after evidence.
  Must NOT do: Do not change Android release to use the debug provider. Do not relax App Check enforcement to make release pass.

  Parallelization: Can parallel: YES | Wave 2 | Blocks: [10] | Blocked by: [3, 4, 5]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/release/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt:3-15` - release provider is Play Integrity.
  - Pattern:  `android/app/build.gradle.kts:115-129` - release build includes Firebase Functions and Play Integrity App Check dependencies.
  - Pattern:  `README.md:84-93` - release AAB path.
  - External: `https://firebase.google.com/docs/app-check/android/play-integrity-provider` - setup requires Play Integrity API link to the same Firebase project, SHA-256 registration, default TTL, and advanced settings guidance.
  - External: `https://support.google.com/googleplay/android-developer/answer/9842756` - final APKs are signed by Google Play app signing key.
  - External: `https://developer.android.com/google/play/integrity/setup` - Play Integrity setup/linking, quota, and verdict definitions.

  Acceptance criteria (agent-executable only):
  - [ ] `evidence/task-9-play-integrity-before.json` and `evidence/task-9-play-integrity-after.json` exist with account data redacted.
  - [ ] `powershell -NoProfile -Command "gcloud.cmd services list --enabled --project com-aiquota-mobile --filter='NAME:playintegrity.googleapis.com' --format='value(NAME)' | Tee-Object evidence/task-9-playintegrity-service.txt"` prints `playintegrity.googleapis.com`.
  - [ ] Chrome/Play Console evidence shows Play Integrity API linked to the same project `com-aiquota-mobile` or its project number.
  - [ ] Firebase App Check evidence shows Play Integrity registered for package `com.aiquota.mobile` with the Play app signing SHA-256 from Task 4.
  - [ ] Advanced settings evidence matches Google Play-only distribution: `PLAY_RECOGNIZED` required, `LICENSED` required, and no explicit device integrity requirement.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: release App Check registration matches Play signing identity
    Tool:     playwright(real Chrome)
    Steps:    Open Chrome to https://console.firebase.google.com/project/com-aiquota-mobile/appcheck/apps, inspect Android app `com.aiquota.mobile`, open Play Integrity provider settings, capture the registered SHA-256 and advanced settings, then save screenshot to evidence/task-9-firebase-appcheck-after.png and a redacted text summary to evidence/task-9-firebase-appcheck-after.txt.
    Expected: Registered SHA-256 equals Task 4 delivered Play app signing SHA-256; provider is Play Integrity; advanced settings match Google Play-only guidance.
    Evidence: evidence/task-9-firebase-appcheck-after.png

  Scenario: Play Integrity project linkage is not mismatched
    Tool:     playwright(real Chrome)
    Steps:    Open Chrome to https://play.google.com/console, select package `com.aiquota.mobile`, navigate Protected with Play > App integrity > Play Integrity API, capture linked Cloud project and status to evidence/task-9-play-integrity-link.png and evidence/task-9-play-integrity-link.txt.
    Expected: Linked project is `com-aiquota-mobile` or the matching project number from `gcloud projects describe com-aiquota-mobile`; Play Integrity API is enabled/linked.
    Evidence: evidence/task-9-play-integrity-link.png
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-9-*.json, evidence/task-9-*.txt, evidence/task-9-*.png]

- [ ] 10. Build, deploy, and post-fix regression sweep

  What to do: After any source/config fixes, run the full local test suite, build debug APK and release AAB, deploy only changed Functions if Functions source changed, and capture deployment/build evidence. If Functions source was not changed, record a no-deploy decision tied to Tasks 3/7.
  Must NOT do: Do not deploy unrelated functions, do not force-deploy with uncommitted unrelated edits, and do not publish Play release artifacts in this task.

  Parallelization: Can parallel: NO | Wave 3 | Blocks: [11, 12] | Blocked by: [6, 7, 8, 9]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `package.json:7-11` - root tests and lint.
  - Pattern:  `README.md:78-88` - debug APK and release AAB build commands.
  - Pattern:  `firebase.json:15-18` - Functions deployment source/runtime.
  - Pattern:  `functions/src/index.js:94-102` - exact callable exports if deploying only changed functions.
  - Test:     `test/release-hardening.test.js:21-27` - App Check enforcement regression.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -Command "npm.cmd test 2>&1 | Tee-Object evidence/task-10-npm-test.txt"` exits 0.
  - [ ] `powershell -NoProfile -Command "& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest 2>&1 | Tee-Object evidence/task-10-gradle-tests.txt"` exits 0.
  - [ ] `powershell -NoProfile -Command "& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:assembleDebug :app:bundleRelease 2>&1 | Tee-Object evidence/task-10-builds.txt"` exits 0.
  - [ ] If `git diff --name-only` includes `functions/src` changes, `powershell -NoProfile -Command "firebase.cmd deploy --only functions:startGeminiCliOAuth,functions:startAntigravityOAuth,functions:completeGeminiCliOAuth,functions:completeAntigravityOAuth --project com-aiquota-mobile 2>&1 | Tee-Object evidence/task-10-functions-deploy.txt"` exits 0; otherwise `evidence/task-10-no-deploy.txt` explains why deployment was unnecessary.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: complete local regression sweep passes
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "npm.cmd test; & '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest; & '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:assembleDebug :app:bundleRelease" 2>&1 | powershell -NoProfile -Command "$input | Tee-Object evidence/task-10-regression-sweep.txt"
    Expected: Exit code 0; evidence contains `BUILD SUCCESSFUL` and Node test pass output.
    Evidence: evidence/task-10-regression-sweep.txt

  Scenario: App Check enforcement still present after build/deploy
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "npm.cmd run test:mobile; npm.cmd run test:functions; Select-String -Path functions/src/index.js -Pattern 'enforceAppCheck:\s*true' | Tee-Object evidence/task-10-enforcement-edge.txt; if (Select-String -Path functions/src/index.js -Pattern 'enforceAppCheck:\s*false' -Quiet) { exit 1 }"
    Expected: Exit code 0; App Check enforcement tests pass; no disablement appears.
    Evidence: evidence/task-10-enforcement-edge.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-10-*.txt]

- [ ] 11. End-to-end debug login QA for Gemini and Antigravity

  What to do: With the fixed debug build installed and registered debug token active, drive the actual provider connect flow for Gemini and Antigravity from the app UI. Stop at the Google OAuth page, capture logcat and screenshots, and prove there is no App Check 403 or false session-expired/stale-usage classification.
  Must NOT do: Do not use a backend stub, do not manually inspect without adb evidence, and do not enter personal Google credentials unless the executor has an authorized test account and that is separately documented.

  Parallelization: Can parallel: YES | Wave 3 | Blocks: [final] | Blocked by: [10]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt:238-276` - UI marks provider connecting and launches Gemini/Antigravity loopback OAuth.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:111-127` - Gemini start call and WebView load.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:111-127` - Antigravity start call and WebView load.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:196-203` - Gemini failure keeps previous snapshot.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:196-203` - Antigravity failure keeps previous snapshot.
  - External: `https://firebase.google.com/docs/app-check/android/debug-provider` - debug provider expected behavior after registration.

  Acceptance criteria (agent-executable only):
  - [ ] `evidence/task-11-gemini-debug-logcat.txt` contains no App Check 403/attestation failure and shows Gemini reached Google OAuth/WebView boundary.
  - [ ] `evidence/task-11-antigravity-debug-logcat.txt` contains no App Check 403/attestation failure and shows Antigravity reached Google OAuth/WebView boundary.
  - [ ] Screenshots exist for both flows.
  - [ ] Evidence contains no raw debug token UUID, App Check token, cookies, OAuth codes, email, or provider HTML.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Gemini debug end-to-end reaches OAuth
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb logcat -c; adb shell am force-stop com.aiquota.mobile; adb shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 2; adb exec-out uiautomator dump /dev/tty > evidence/task-11-gemini-ui.xml; python C:\Users\datell1357\.codex\plugins\cache\openai-curated\test-android-apps\c3319989\skills\android-emulator-qa\scripts\ui_pick.py evidence/task-11-gemini-ui.xml Gemini > evidence/task-11-gemini-tap.txt; $xy=(Get-Content evidence/task-11-gemini-tap.txt -Raw).Trim().Split(); adb shell input tap $xy[0] $xy[1]; Start-Sleep -Seconds 10; adb exec-out screencap -p > evidence/task-11-gemini-debug-oauth.png; adb logcat -d -s DebugAppCheckProvider FirebaseAppCheck AIQuotaGemini AIQuotaFirebaseGateway GoogleOAuth AndroidRuntime | ForEach-Object { $_ -replace '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}', '<REDACTED_UUID>' } | Tee-Object evidence/task-11-gemini-debug-logcat.txt"
    Expected: No `App attestation failed`, no callable 403, no `session expired`; screenshot shows Google OAuth/WebView boundary.
    Evidence: evidence/task-11-gemini-debug-logcat.txt

  Scenario: Antigravity debug end-to-end reaches OAuth
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb logcat -c; adb shell am force-stop com.aiquota.mobile; adb shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 2; adb exec-out uiautomator dump /dev/tty > evidence/task-11-antigravity-ui.xml; python C:\Users\datell1357\.codex\plugins\cache\openai-curated\test-android-apps\c3319989\skills\android-emulator-qa\scripts\ui_pick.py evidence/task-11-antigravity-ui.xml Antigravity > evidence/task-11-antigravity-tap.txt; $xy=(Get-Content evidence/task-11-antigravity-tap.txt -Raw).Trim().Split(); adb shell input tap $xy[0] $xy[1]; Start-Sleep -Seconds 10; adb exec-out screencap -p > evidence/task-11-antigravity-debug-oauth.png; adb logcat -d -s DebugAppCheckProvider FirebaseAppCheck AIQuotaAntigravity AIQuotaFirebaseGateway GoogleOAuth AndroidRuntime | ForEach-Object { $_ -replace '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}', '<REDACTED_UUID>' } | Tee-Object evidence/task-11-antigravity-debug-logcat.txt"
    Expected: No `App attestation failed`, no callable 403, no `session expired`; screenshot shows Google OAuth/WebView boundary.
    Evidence: evidence/task-11-antigravity-debug-logcat.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-11-*.txt, evidence/task-11-*.xml, evidence/task-11-*.png]

- [ ] 12. End-to-end Play internal-testing release QA for Gemini and Antigravity

  What to do: On a certified physical Android device with the Play Store internal-testing v15 install, drive Gemini and Antigravity connect from the app UI. Prove the release build uses Play Integrity, was installed by Play, reaches Google OAuth before user login, and no App Check 403 occurs.
  Must NOT do: Do not use emulator evidence for release Play Integrity. Do not install the local release APK/AAB over the Play install. Do not call this done without installer and signer evidence.

  Parallelization: Can parallel: YES | Wave 3 | Blocks: [final] | Blocked by: [10]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/release/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt:10-15` - release installs Play Integrity provider.
  - Pattern:  `README.md:13-19` - Play internal-testing artifact path.
  - Pattern:  `README.md:42-50` - Play package name.
  - Pattern:  `android/app/build.gradle.kts:49-54` - application ID and v15 versionCode.
  - External: `https://firebase.google.com/docs/app-check/android/play-integrity-provider` - Play Integrity App Check setup for Android release.
  - External: `https://support.google.com/googleplay/android-developer/answer/9842756` - Play signs delivered APKs with app signing key.
  - External: `https://developer.android.com/google/play/integrity/setup` - Play Integrity verdicts and linked Cloud project requirements.

  Acceptance criteria (agent-executable only):
  - [ ] `evidence/task-12-release-install.txt` proves `installerPackageName=com.android.vending` and `versionCode=15`.
  - [ ] `evidence/task-12-release-signing.txt` proves delivered SHA-256 equals Task 4/9 App Check Play signing SHA-256.
  - [ ] `evidence/task-12-gemini-release-logcat.txt` contains no App Check 403/attestation failure and shows Gemini reached Google OAuth/WebView boundary.
  - [ ] `evidence/task-12-antigravity-release-logcat.txt` contains no App Check 403/attestation failure and shows Antigravity reached Google OAuth/WebView boundary.
  - [ ] Evidence contains no raw tokens, cookies, OAuth codes, email, or provider HTML.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Gemini release/internal-testing reaches OAuth
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb shell dumpsys package com.aiquota.mobile | Tee-Object evidence/task-12-release-install.txt; if (-not (Select-String -Path evidence/task-12-release-install.txt -Pattern 'installerPackageName=com.android.vending' -Quiet)) { exit 1 }; adb logcat -c; adb shell am force-stop com.aiquota.mobile; adb shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 2; adb exec-out uiautomator dump /dev/tty > evidence/task-12-gemini-ui.xml; python C:\Users\datell1357\.codex\plugins\cache\openai-curated\test-android-apps\c3319989\skills\android-emulator-qa\scripts\ui_pick.py evidence/task-12-gemini-ui.xml Gemini > evidence/task-12-gemini-tap.txt; $xy=(Get-Content evidence/task-12-gemini-tap.txt -Raw).Trim().Split(); adb shell input tap $xy[0] $xy[1]; Start-Sleep -Seconds 12; adb exec-out screencap -p > evidence/task-12-gemini-release-oauth.png; adb logcat -d -s FirebaseAppCheck PlayIntegrity AIQuotaGemini AIQuotaFirebaseGateway GoogleOAuth AndroidRuntime | Tee-Object evidence/task-12-gemini-release-logcat.txt"
    Expected: Release install is from Play; no App Check 403/attestation failure; screenshot shows Google OAuth/WebView boundary.
    Evidence: evidence/task-12-gemini-release-logcat.txt

  Scenario: Antigravity release/internal-testing reaches OAuth
    Tool:     bash
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb shell dumpsys package com.aiquota.mobile | Tee-Object evidence/task-12-release-install-repeat.txt; if (-not (Select-String -Path evidence/task-12-release-install-repeat.txt -Pattern 'installerPackageName=com.android.vending' -Quiet)) { exit 1 }; adb logcat -c; adb shell am force-stop com.aiquota.mobile; adb shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 2; adb exec-out uiautomator dump /dev/tty > evidence/task-12-antigravity-ui.xml; python C:\Users\datell1357\.codex\plugins\cache\openai-curated\test-android-apps\c3319989\skills\android-emulator-qa\scripts\ui_pick.py evidence/task-12-antigravity-ui.xml Antigravity > evidence/task-12-antigravity-tap.txt; $xy=(Get-Content evidence/task-12-antigravity-tap.txt -Raw).Trim().Split(); adb shell input tap $xy[0] $xy[1]; Start-Sleep -Seconds 12; adb exec-out screencap -p > evidence/task-12-antigravity-release-oauth.png; adb logcat -d -s FirebaseAppCheck PlayIntegrity AIQuotaAntigravity AIQuotaFirebaseGateway GoogleOAuth AndroidRuntime | Tee-Object evidence/task-12-antigravity-release-logcat.txt"
    Expected: Release install is from Play; no App Check 403/attestation failure; screenshot shows Google OAuth/WebView boundary.
    Evidence: evidence/task-12-antigravity-release-logcat.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-12-*.txt, evidence/task-12-*.xml, evidence/task-12-*.png]

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
- Reference the plan file path in the final commit footer: `Plan: plans/appcheck-gemini-antigravity-login-v15.md`.

## Success criteria
- All Must-Have shipped; all QA scenarios pass with captured evidence; F1-F4 approved; commit history clean.
