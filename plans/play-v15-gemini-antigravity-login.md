# Fix Play v15 Gemini And Antigravity Login

## TL;DR
> Summary:      Diagnose the Play Store internal-testing `versionCode=15` login-start failure on Pixel_6a/emulator, then apply the smallest valid fix while keeping Firebase App Check enforcement enabled. Current evidence points away from signing mismatch and toward Firebase API key/App Check token acquisition, because the Play-installed app logs `API key not valid` before `gemini_cli_oauth_start_failed`.
> Deliverables:
> - Refreshed Play-installed v15 package, signer, installer, resource, and UI/logcat evidence.
> - Firebase App Check, Play Integrity, API key restriction, and Cloud Functions log root-cause report.
> - Exactly one remediation path: cloud config fix, API key restriction fix, app config/build fix, source fix, or device-state blocker.
> - RED->GREEN tests for any source/config file changes.
> - Gemini and Antigravity adb/computer-use QA proving both reach Google OAuth/WebView or callable `app=VALID auth=VALID`.
> Effort:       Medium
> Risk:         Medium - release behavior depends on external Firebase/Google Play/API key state and the Play-installed artifact cannot be changed in-place if its bundled Firebase config is wrong.

## Scope
### Must have
- Reproduce or clear the failure on the actual Play-installed `com.aiquota.mobile` `versionCode=15` package; existing package evidence shows `installerPackageName=com.android.vending` and `versionCode=15` in `.omo/ulw-loop/evidence/play-v15-package-dump.txt:12-24`.
- Treat `.omo/ulw-loop/evidence/play-v15-login-repro.txt:26-30` as the current leading signal: App Check token fetch reports `API key not valid`, then Gemini reports `gemini_cli_oauth_start_failed`.
- Preserve App Check enforcement in `functions/src/index.js:26-31` and `functions/src/index.js:67-72`.
- Preserve Android App Check bootstrapping: `AIQuotaApplication` calls `FirebaseGatewayBootstrap.install()` in `android/app/src/main/java/com/aiquota/mobile/AIQuotaApplication.kt:5-9`, debug uses `DebugAppCheckProviderFactory` in `android/app/src/debug/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt:10-15`, and release uses `PlayIntegrityAppCheckProviderFactory` in `android/app/src/release/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt:10-15`.
- Preserve the shared callable retry contract in `android/app/src/main/java/com/aiquota/mobile/providers/FirebaseAppCheckCallable.kt:9-21`.
- Verify both providers: Gemini start path in `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt:18-31` and Antigravity start path in `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt:19-32`.
- If the bundled Firebase API key is deleted, from the wrong project, or cannot be made valid for the Play-delivered app, explicitly state that existing v15 cannot be repaired in-place except by restoring/fixing that cloud key; otherwise build and validate a new upload artifact.

### Must NOT have (guardrails, anti-slop, scope boundaries)
- Do not disable App Check, set `enforceAppCheck: false`, weaken `test/release-hardening.test.js:21-27`, or switch release builds to the debug provider.
- Do not store raw Firebase API keys, App Check tokens, debug UUIDs, OAuth codes, cookies, auth headers, emails, usernames, account chooser screenshots, or provider HTML in repo evidence.
- Do not classify this as an Auth/session issue unless Cloud Functions logs show `auth=MISSING` or the app has an explicit Firebase Auth failure. `auth=VALID app=MISSING` and API-key failures are separate from provider account login.
- Do not treat stale usage or `codex_usage_unavailable` as a forced re-login signal; keep the repo guardrail that provider payload/collector failures are not auth failures.
- Do not commit pulled APKs, screenshots with account data, raw token temp files, or console dumps with secrets.
- Do not add broad provider, dashboard, widget, or collector refactors.
- Do not call the fix complete from source tests alone; Android runtime evidence is mandatory.

## Verification strategy
> Zero human intervention - all verification is agent-executed.
- Test decision: TDD + Node `node --test`, root `npm.cmd test`, focused Android JVM tests, and adb/computer-use runtime QA.
- QA policy: every task has agent-executed scenarios
- Evidence: `evidence/task-<N>-play-v15-gemini-antigravity-login.<ext>`

## Execution strategy
### Parallel execution waves
> Target 5-8 tasks per wave. <3 per wave (except final) = under-splitting.
> Extract shared dependencies as Wave-1 tasks to maximize parallelism.

Wave 1 (no dependencies):
- Task 1: Evidence hygiene, auth/tool/device preflight, and baseline tests
- Task 2: Play-installed v15 Gemini/Antigravity reproduction
- Task 3: Delivered APK package, signer, and Firebase resource extraction
- Task 4: Firebase App Check, Play Integrity, and Functions inventory
- Task 5: Firebase API key validity and restriction audit

Wave 2 (after Wave 1):
- Task 6: depends [1, 2, 3, 4, 5]
- Task 7: depends [6]
- Task 8: depends [6]

Wave 3 (after Wave 2):
- Task 9: depends [7, 8]
- Task 10: depends [9]

Critical path: Task 1 -> Task 2 -> Task 6 -> Task 7 -> Task 9 -> Task 10

### Dependency matrix
| Task | Depends on | Blocks | Can parallelize with |
|------|------------|--------|----------------------|
| 1    | none       | 6      | 2, 3, 4, 5           |
| 2    | none       | 6      | 1, 3, 4, 5           |
| 3    | none       | 6      | 1, 2, 4, 5           |
| 4    | none       | 6      | 1, 2, 3, 5           |
| 5    | none       | 6      | 1, 2, 3, 4           |
| 6    | 1,2,3,4,5  | 7, 8   | none                 |
| 7    | 6          | 9      | 8                    |
| 8    | 6          | 9      | 7                    |
| 9    | 7,8        | 10     | none                 |
| 10   | 9          | final  | none                 |

## Todos
> Implementation + Test = ONE task. Never separate.
> Every task MUST have: References + Acceptance Criteria + QA Scenarios + Commit.

- [ ] 1. Evidence hygiene, auth/tool/device preflight, and baseline tests

  What to do: Create `evidence/`, snapshot `git status`, confirm tools and account access, confirm `emulator-5554` is present, and run baseline tests that prove App Check is currently enforced and the source matches v15. Record any missing external auth as a blocker artifact instead of guessing.
  Must NOT do: Do not edit source, change Firebase/Play config, upload builds, or store secrets in evidence.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [6] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `package.json:7-11` - root test scripts omit `test/release-hardening.test.js`, so run that file explicitly.
  - Pattern:  `README.md:68-88` - documented npm, Gradle unit test, debug build, and release bundle commands.
  - Test:     `test/release-hardening.test.js:21-27` - App Check enforcement guard.
  - Test:     `test/mobile-source.test.js:137-207` - Android Firebase Auth/Functions/App Check wiring and no client secret guard.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt:56-82` - Android App Check bootstrap and retry guard.
  - External: `https://firebase.google.com/docs/app-check/cloud-functions` - callable `enforceAppCheck: true` rejects missing/invalid App Check tokens.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force evidence | Out-Null; git status --short | Tee-Object evidence/task-1-play-v15-git-status.txt"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "adb devices | Tee-Object evidence/task-1-play-v15-adb-devices.txt; if (-not (Select-String -Path evidence/task-1-play-v15-adb-devices.txt -Pattern 'emulator-5554\\s+device')) { exit 1 }"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "gcloud.cmd auth list 2>&1 | Tee-Object evidence/task-1-play-v15-gcloud-auth.txt; firebase.cmd projects:list 2>&1 | Tee-Object evidence/task-1-play-v15-firebase-auth.txt"` exits 0 or writes explicit auth failure evidence.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "npm.cmd test 2>&1 | Tee-Object evidence/task-1-play-v15-npm-test.txt"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "node --test test/release-hardening.test.js 2>&1 | Tee-Object evidence/task-1-play-v15-release-hardening.txt"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "& '.\\.tmp\\tools\\gradle-8.10.2\\bin\\gradle.bat' -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest 2>&1 | Tee-Object evidence/task-1-play-v15-android-gateway-tests.txt"` exits 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: baseline contracts are green
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "npm.cmd test; node --test test/release-hardening.test.js; & '.\\.tmp\\tools\\gradle-8.10.2\\bin\\gradle.bat' -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest"
    Expected: Exit code 0; output includes Node pass output and Gradle `BUILD SUCCESSFUL`.
    Evidence: evidence/task-1-play-v15-baseline-tests.txt

  Scenario: missing external auth is classified before mutation
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "gcloud.cmd auth print-access-token 2>&1 | Tee-Object evidence/task-1-play-v15-auth-token-check.txt; if ($LASTEXITCODE -ne 0) { 'BLOCKED_EXTERNAL_AUTH' | Tee-Object -Append evidence/task-1-play-v15-auth-token-check.txt; exit 0 }"
    Expected: Exit code 0; evidence either contains an auth token command success without token value, or `BLOCKED_EXTERNAL_AUTH`.
    Evidence: evidence/task-1-play-v15-auth-token-check.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-1-play-v15-*]

- [ ] 2. Play-installed v15 Gemini/Antigravity reproduction

  What to do: Use the Play-installed app on `emulator-5554`, derive provider row tap coordinates from the UI tree, tap Gemini and Antigravity connect actions, and capture redacted logcat/UI evidence. The target is to reproduce or clear `API key not valid`, `App attestation failed`, `*_oauth_start_failed`, and Cloud Functions `app/auth` status for both providers.
  Must NOT do: Do not enter Google credentials, do not screenshot account chooser emails, and do not overwrite the Play-installed app with a local APK in this task.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [6] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Evidence: `.omo/ulw-loop/evidence/play-v15-package-dump.txt:12-24` - prior Play-installed package identity.
  - Evidence: `.omo/ulw-loop/evidence/play-v15-login-repro.txt:26-30` - current Gemini failure signature.
  - Pattern:  `android/app/src/main/AndroidManifest.xml:18-44` - Gemini and Antigravity OAuth activities.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:108-127` - Gemini start failure and WebView load boundary.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:108-127` - Antigravity start failure and WebView load boundary.
  - External: `https://firebase.google.com/docs/app-check/cloud-functions` - callable client SDKs attach App Check tokens when available.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 shell am force-stop com.aiquota.mobile; adb -s emulator-5554 logcat -c; adb -s emulator-5554 shell am start -n com.aiquota.mobile/.MainActivity | Tee-Object evidence/task-2-play-v15-launch.txt"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 exec-out uiautomator dump /dev/tty | Tee-Object evidence/task-2-play-v15-ui-before.xml"` exits 0 and contains `Gemini` and `Antigravity`.
  - [ ] Gemini tap evidence saved to `evidence/task-2-play-v15-gemini-logcat-redacted.txt` with any API keys, UUIDs, emails, OAuth codes, and bearer tokens redacted.
  - [ ] Antigravity tap evidence saved to `evidence/task-2-play-v15-antigravity-logcat-redacted.txt` with the same redaction policy.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$bad = Select-String -Path evidence/task-2-play-v15-*-logcat-redacted.txt -Pattern 'AIza[0-9A-Za-z_-]+|Bearer\\s+|oauth.*code=|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+' -Quiet; if ($bad) { exit 1 }"` exits 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Gemini login start is reproduced or reaches OAuth
    Tool:     computer-use + powershell(adb)
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 logcat -c; adb -s emulator-5554 shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 2; adb -s emulator-5554 shell input tap 878 1978; Start-Sleep -Seconds 8; adb -s emulator-5554 logcat -d | Select-String -Pattern 'AIQuotaGemini|FirebaseAppCheck|LocalRequestInterceptor|App attestation failed|API key not valid|startGeminiCliOAuth|app=|auth=' | ForEach-Object { $_.Line -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' -replace '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+','<REDACTED_EMAIL>' -replace 'code=[^&\\s]+','code=<REDACTED_OAUTH_CODE>' } | Tee-Object evidence/task-2-play-v15-gemini-logcat-redacted.txt"
    Expected: Evidence contains either WebView/OAuth transition or a concrete failure signature among `API key not valid`, `App attestation failed`, `gemini_cli_oauth_start_failed`, with no raw secret patterns.
    Evidence: evidence/task-2-play-v15-gemini-logcat-redacted.txt

  Scenario: Antigravity login start is reproduced or reaches OAuth
    Tool:     computer-use + powershell(adb)
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 shell am force-stop com.aiquota.mobile; adb -s emulator-5554 logcat -c; adb -s emulator-5554 shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 2; adb -s emulator-5554 exec-out uiautomator dump /dev/tty | Set-Content evidence/task-2-play-v15-ui-antigravity.xml; adb -s emulator-5554 shell input tap 878 2120; Start-Sleep -Seconds 8; adb -s emulator-5554 logcat -d | Select-String -Pattern 'AIQuotaAntigravity|FirebaseAppCheck|LocalRequestInterceptor|App attestation failed|API key not valid|startAntigravityOAuth|app=|auth=' | ForEach-Object { $_.Line -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' -replace '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+','<REDACTED_EMAIL>' -replace 'code=[^&\\s]+','code=<REDACTED_OAUTH_CODE>' } | Tee-Object evidence/task-2-play-v15-antigravity-logcat-redacted.txt"
    Expected: Evidence contains either WebView/OAuth transition or a concrete failure signature among `API key not valid`, `App attestation failed`, `antigravity_oauth_start_failed`, with no raw secret patterns.
    Evidence: evidence/task-2-play-v15-antigravity-logcat-redacted.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-2-play-v15-*]

- [ ] 3. Delivered APK package, signer, and Firebase resource extraction

  What to do: Pull only the Play-delivered base APK to a temporary location, verify installer/version/signer, extract Firebase resource values in redacted/hash form, and compare them with `android/app/google-services.json`. Keep only signer output, hashes, and redacted resource names in evidence.
  Must NOT do: Do not commit the APK. Do not write the raw Firebase API key to evidence. Delete pulled APK/temp files after signer/resource evidence is captured.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [6] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/build.gradle.kts:49-54` - `applicationId=com.aiquota.mobile`, `versionCode=15`.
  - Pattern:  `android/app/google-services.json:2-20` - Firebase project/app config and API key field.
  - Evidence: `.omo/ulw-loop/evidence/play-v15-root-cause.txt:1-16` - prior signer SHA-256/SHA-1 and resource names.
  - External: `https://support.google.com/googleplay/android-developer/answer/9842756` - Play-delivered APKs use Play app signing certificate, not just the upload key.
  - External: `https://cloud.google.com/docs/authentication/api-keys` - Android API key restrictions use package name and SHA-1 certificate fingerprint.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 shell dumpsys package com.aiquota.mobile | Tee-Object evidence/task-3-play-v15-dumpsys.txt"` includes `versionCode=15` and `installerPackageName=com.android.vending`.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$remote=(adb -s emulator-5554 shell pm path com.aiquota.mobile | Select-Object -First 1).Replace('package:','').Trim(); $apk=Join-Path $env:TEMP 'aiquota-play-v15-base.apk'; adb -s emulator-5554 pull $remote $apk | Tee-Object evidence/task-3-play-v15-pull.txt; $apksigner=(Get-ChildItem $env:LOCALAPPDATA\\Android\\Sdk\\build-tools -Recurse -Filter apksigner.bat | Sort-Object FullName -Descending | Select-Object -First 1).FullName; & $apksigner verify --print-certs $apk | Tee-Object evidence/task-3-play-v15-apksigner.txt; Remove-Item $apk -Force"` exits 0.
  - [ ] `evidence/task-3-play-v15-apksigner.txt` contains signer SHA-1 and SHA-256; SHA-256 must match Firebase App Check SHA-256 evidence from Task 4 or be classified in Task 6.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$cfg=Get-Content android/app/google-services.json -Raw | ConvertFrom-Json; $key=$cfg.client[0].api_key[0].current_key; $hash=[BitConverter]::ToString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($key))).Replace('-','').ToLowerInvariant(); [pscustomobject]@{project_id=$cfg.project_info.project_id; project_number=$cfg.project_info.project_number; mobilesdk_app_id=$cfg.client[0].client_info.mobilesdk_app_id; package=$cfg.client[0].client_info.android_client_info.package_name; api_key_sha256=$hash} | ConvertTo-Json | Tee-Object evidence/task-3-local-firebase-config-hash.json"` exits 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Play app signer and package are proven
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 shell dumpsys package com.aiquota.mobile | Tee-Object evidence/task-3-play-v15-package.txt; Select-String -Path evidence/task-3-play-v15-package.txt -Pattern 'versionCode=15','installerPackageName=com.android.vending' | Tee-Object evidence/task-3-play-v15-package-check.txt"
    Expected: Evidence includes `versionCode=15` and `installerPackageName=com.android.vending`.
    Evidence: evidence/task-3-play-v15-package-check.txt

  Scenario: raw Firebase API key is not written to evidence
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$bad = Select-String -Path evidence/task-3-* -Pattern 'AIza[0-9A-Za-z_-]+' -Quiet; if ($bad) { exit 1 } else { 'PASS no raw Firebase API key in task 3 evidence' | Tee-Object evidence/task-3-play-v15-redaction-check.txt }"
    Expected: Exit code 0 and evidence says no raw Firebase API key is present.
    Evidence: evidence/task-3-play-v15-redaction-check.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-3-play-v15-*]

- [ ] 4. Firebase App Check, Play Integrity, and Functions inventory

  What to do: Query Firebase project `com-aiquota-mobile`, Android app `com.aiquota.mobile`, App Check Play Integrity config, registered SHA-256 fingerprints, enabled services, and Cloud Functions logs around the reproduction timestamps. Capture only redacted JSON/logs.
  Must NOT do: Do not mutate Firebase/Play config in this task. Do not paste access tokens or account emails into evidence.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [6] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `.firebaserc:1-5` - default Firebase project is `com-aiquota-mobile`.
  - Pattern:  `functions/src/index.js:94-102` - exported Gemini/Antigravity callable names.
  - Evidence: `.omo/ulw-loop/evidence/C002-release-play-integrity-config-2026-06-09.txt:3-30` - prior project/app/signing/Play Integrity alignment.
  - External: `https://firebase.google.com/docs/app-check/android/play-integrity-provider` - App Check registration requires Play Integrity provider and SHA-256 signing certificate.
  - External: `https://developer.android.com/google/play/integrity/verdicts` - `PLAY_RECOGNIZED` means app/certificate match Google Play; `UNRECOGNIZED_VERSION` means package/certificate mismatch.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "firebase.cmd apps:list --project com-aiquota-mobile --json | Tee-Object evidence/task-4-firebase-apps.json"` exits 0 and includes Android package `com.aiquota.mobile`.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "gcloud.cmd services list --enabled --project com-aiquota-mobile --format=json | Tee-Object evidence/task-4-enabled-services.json"` exits 0 and includes `firebaseappcheck.googleapis.com`, `playintegrity.googleapis.com`, and `cloudfunctions.googleapis.com`.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "firebase.cmd functions:log --project com-aiquota-mobile --only startGeminiCliOAuth,startAntigravityOAuth --limit 80 2>&1 | ForEach-Object { $_ -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' -replace '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+','<REDACTED_EMAIL>' -replace 'Bearer\\s+\\S+','Bearer <REDACTED>' } | Tee-Object evidence/task-4-functions-log-redacted.txt"` exits 0 or records permission failure.
  - [ ] REST or console evidence for App Check Play Integrity config is saved to `evidence/task-4-appcheck-playintegrity-redacted.json` or `evidence/task-4-appcheck-playintegrity-console.png`; evidence must show package/app id, provider status, token TTL, advanced settings, and registered SHA-256 without account data.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Firebase project and services are visible
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$apps = firebase.cmd apps:list --project com-aiquota-mobile --json | Tee-Object evidence/task-4-firebase-apps.json | ConvertFrom-Json; $android = $apps.result | Where-Object { $_.platform -eq 'ANDROID' -and $_.packageName -eq 'com.aiquota.mobile' }; if (-not $android) { exit 1 }; gcloud.cmd services list --enabled --project com-aiquota-mobile --format=json | Tee-Object evidence/task-4-services.json"
    Expected: Exit code 0; Android Firebase app exists; App Check, Play Integrity, and Cloud Functions services are enabled.
    Evidence: evidence/task-4-services.json

  Scenario: Functions logs separate Auth from App Check
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "firebase.cmd functions:log --project com-aiquota-mobile --only startGeminiCliOAuth,startAntigravityOAuth --limit 80 2>&1 | Select-String -Pattern 'app=|auth=|401|403|API key not valid|App attestation failed|status=200' | ForEach-Object { $_.Line -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' } | Tee-Object evidence/task-4-functions-app-auth-status.txt"
    Expected: Evidence contains enough log rows to classify `app` and `auth` separately, or an explicit `NO_LOG_ROWS`/permission blocker.
    Evidence: evidence/task-4-functions-app-auth-status.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-4-*]

- [ ] 5. Firebase API key validity and restriction audit

  What to do: Audit the API key referenced by `android/app/google-services.json` and by the Play-delivered resources. Verify the key exists in project `com-aiquota-mobile`, is not deleted, is associated with the Firebase Android app, has Android application restrictions that include `com.aiquota.mobile` plus the Play app signing SHA-1 from Task 3, and has required Firebase API allowlist entries for App Check/Auth.
  Must NOT do: Do not write the raw API key to evidence. Do not update key restrictions in this task; this is inventory only.

  Parallelization: Can parallel: YES | Wave 1 | Blocks: [6] | Blocked by: []

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/google-services.json:16-19` - Firebase Android config contains `current_key`; hash it, do not print it.
  - Evidence: `.omo/ulw-loop/evidence/play-v15-login-repro.txt:26` - current `API key not valid` App Check token-fetch failure.
  - Evidence: `.omo/ulw-loop/evidence/play-v15-root-cause.txt:1-5` - prior Play signer SHA-1/SHA-256.
  - External: `https://firebase.google.com/docs/projects/api-keys` - invalid API keys can be caused by restrictions, deletion, or a key from the wrong project; Firebase config for Android uses `current_key`.
  - External: `https://cloud.google.com/docs/authentication/api-keys` - Android application restrictions use package name and SHA-1 certificate fingerprint.
  - External: `https://firebase.google.com/docs/projects/api-keys` - required Firebase API allowlist includes `firebaseappcheck.googleapis.com`, `identitytoolkit.googleapis.com`, and `securetoken.googleapis.com`.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$cfg=Get-Content android/app/google-services.json -Raw | ConvertFrom-Json; $key=$cfg.client[0].api_key[0].current_key; $meta=gcloud.cmd services api-keys lookup --key-string=$key --format=json | ConvertFrom-Json; $meta | ConvertTo-Json -Depth 10 | ForEach-Object { $_ -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' } | Tee-Object evidence/task-5-api-key-lookup-redacted.json"` exits 0 and does not print the raw key.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$cfg=Get-Content android/app/google-services.json -Raw | ConvertFrom-Json; $key=$cfg.client[0].api_key[0].current_key; $meta=gcloud.cmd services api-keys lookup --key-string=$key --format=json | ConvertFrom-Json; $auth=gcloud.cmd auth print-access-token; Invoke-RestMethod -Headers @{Authorization='Bearer ' + $auth} -Uri ('https://apikeys.googleapis.com/v2/' + $meta.name) | ConvertTo-Json -Depth 20 | ForEach-Object { $_ -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' } | Tee-Object evidence/task-5-api-key-describe-redacted.json"` exits 0.
  - [ ] `evidence/task-5-api-key-describe-redacted.json` includes `androidKeyRestrictions.allowedApplications` for `packageName=com.aiquota.mobile` and the Task 3 Play app signing SHA-1, or records a concrete missing restriction to fix.
  - [ ] `evidence/task-5-api-key-describe-redacted.json` includes API targets for `firebaseappcheck.googleapis.com`, `identitytoolkit.googleapis.com`, and `securetoken.googleapis.com`, or records a concrete missing API target to fix.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: API key lookup proves the key belongs to the expected project
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$cfg=Get-Content android/app/google-services.json -Raw | ConvertFrom-Json; $key=$cfg.client[0].api_key[0].current_key; $meta=gcloud.cmd services api-keys lookup --key-string=$key --format=json | ConvertFrom-Json; if ($meta.name -notmatch 'projects/550123003638/') { $meta | ConvertTo-Json | Tee-Object evidence/task-5-wrong-project-error.json; exit 1 }; $meta | ConvertTo-Json | ForEach-Object { $_ -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' } | Tee-Object evidence/task-5-api-key-project-check.json"
    Expected: Exit code 0; resource name is under project number `550123003638`.
    Evidence: evidence/task-5-api-key-project-check.json

  Scenario: deleted or unusable key is caught without exposing key string
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$cfg=Get-Content android/app/google-services.json -Raw | ConvertFrom-Json; $key=$cfg.client[0].api_key[0].current_key; gcloud.cmd services api-keys lookup --key-string=$key --format=json 2>&1 | ForEach-Object { $_ -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' } | Tee-Object evidence/task-5-api-key-lookup-error.txt"
    Expected: Evidence shows successful lookup or a concrete invalid/deleted key error, with no raw API key.
    Evidence: evidence/task-5-api-key-lookup-error.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-5-*]

- [ ] 6. Root-cause matrix and remediation gate

  What to do: Produce a single root-cause report from Tasks 1-5. Use fixed classification keys: `API_KEY_APPLICATION_RESTRICTION`, `API_KEY_API_ALLOWLIST`, `API_KEY_DELETED_OR_WRONG_PROJECT`, `APP_CHECK_PLAY_INTEGRITY_CONFIG`, `APP_CHECK_CLIENT_BOOTSTRAP`, `SHIPPED_CONFIG_STALE`, `EMULATOR_PLAY_SERVICES_STATE`, `OAUTH_PROVIDER_AFTER_START`, or `NO_REPRO`. Select exactly one primary remediation route and mark all non-selected routes skipped with evidence.
  Must NOT do: Do not mutate source or cloud config. Do not choose multiple primary root causes unless one is blocker and one is consequence.

  Parallelization: Can parallel: NO | Wave 2 | Blocks: [7, 8] | Blocked by: [1, 2, 3, 4, 5]

  References (executor has NO interview context - be exhaustive):
  - Evidence: `.omo/ulw-loop/evidence/play-v15-login-repro.txt:26-30` - current failure signature.
  - Evidence: `.omo/ulw-loop/evidence/C002-release-play-integrity-config-2026-06-09.txt:18-30` - prior release App Check/Play Integrity alignment.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/FirebaseAppCheckCallable.kt:14-20` - client App Check token preflight and retry behavior.
  - Pattern:  `functions/src/index.js:26-31` - Antigravity App Check enforcement.
  - Pattern:  `functions/src/index.js:67-72` - Gemini App Check enforcement.
  - External: `https://firebase.google.com/docs/projects/api-keys` - invalid key causes include restrictions, deletion, or wrong project.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$files='task-2-play-v15-gemini-logcat-redacted.txt','task-2-play-v15-antigravity-logcat-redacted.txt','task-3-play-v15-apksigner.txt','task-4-functions-app-auth-status.txt','task-5-api-key-describe-redacted.json'; foreach ($f in $files) { if (-not (Test-Path (Join-Path evidence $f))) { Write-Host missing:$f; exit 1 } }; 'inputs complete' | Tee-Object evidence/task-6-input-check.txt"` exits 0.
  - [ ] `evidence/task-6-root-cause-matrix.md` contains one `PRIMARY_ROOT_CAUSE:` line and one `REMEDIATION_ROUTE:` line.
  - [ ] `evidence/task-6-root-cause-matrix.md` contains a table row for every fixed classification key listed in this task.
  - [ ] If `API key not valid` is present in Task 2 evidence, Task 6 must classify API key restriction/deletion/wrong-project before App Check/Auth/session hypotheses.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: root cause report is structurally complete
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$p='evidence/task-6-root-cause-matrix.md'; Select-String -Path $p -Pattern '^PRIMARY_ROOT_CAUSE:','^REMEDIATION_ROUTE:','API_KEY_APPLICATION_RESTRICTION','API_KEY_API_ALLOWLIST','API_KEY_DELETED_OR_WRONG_PROJECT','APP_CHECK_PLAY_INTEGRITY_CONFIG','APP_CHECK_CLIENT_BOOTSTRAP','SHIPPED_CONFIG_STALE','EMULATOR_PLAY_SERVICES_STATE','OAUTH_PROVIDER_AFTER_START','NO_REPRO' | Tee-Object evidence/task-6-structure-check.txt; if ((Select-String -Path $p -Pattern '^PRIMARY_ROOT_CAUSE:' | Measure-Object).Count -ne 1) { exit 1 }"
    Expected: Exit code 0; structure check lists all classification keys and exactly one primary root cause.
    Evidence: evidence/task-6-structure-check.txt

  Scenario: session/auth misclassification is blocked
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$p='evidence/task-6-root-cause-matrix.md'; if ((Select-String -Path evidence/task-2-play-v15-*-logcat-redacted.txt -Pattern 'API key not valid' -Quiet) -and (Select-String -Path $p -Pattern 'PRIMARY_ROOT_CAUSE:.*AUTH_SESSION' -Quiet)) { exit 1 }; 'PASS no auth-session misclassification' | Tee-Object evidence/task-6-auth-classification-check.txt"
    Expected: Exit code 0; API-key failures are not mislabeled as auth session expiry.
    Evidence: evidence/task-6-auth-classification-check.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-6-*]

- [ ] 7. External Firebase/API key/App Check remediation

  What to do: If Task 6 selects `API_KEY_APPLICATION_RESTRICTION`, `API_KEY_API_ALLOWLIST`, `API_KEY_DELETED_OR_WRONG_PROJECT`, or `APP_CHECK_PLAY_INTEGRITY_CONFIG`, apply the smallest cloud/config fix and capture before/after snapshots. For API key restrictions, include `com.aiquota.mobile` with the Play app signing SHA-1 from Task 3. For API allowlist, include required Firebase APIs. For App Check config, preserve Play Integrity and registered Play app signing SHA-256. If Task 6 selects a non-external route, write a skip artifact.
  Must NOT do: Do not change Android source in this task. Do not create a broad unrestricted API key unless Task 6 proves the old key cannot be restored and Task 8/9 will ship updated app config. Do not weaken Play Integrity/App Check enforcement.

  Parallelization: Can parallel: YES | Wave 2 | Blocks: [9] | Blocked by: [6]

  References (executor has NO interview context - be exhaustive):
  - External: `https://cloud.google.com/docs/authentication/api-keys` - Android API key restrictions require package name and SHA-1 fingerprint.
  - External: `https://firebase.google.com/docs/projects/api-keys` - required API targets include Firebase App Check, Identity Toolkit, and Secure Token APIs.
  - External: `https://firebase.google.com/docs/app-check/android/play-integrity-provider` - Play Integrity App Check requires app signing SHA-256 and suitable advanced settings.
  - Evidence: `.omo/ulw-loop/evidence/C002-release-play-integrity-config-2026-06-09.txt:9-21` - prior Firebase registered SHA-256 fingerprints include Play app signing and upload keys.
  - Test:     `test/release-hardening.test.js:21-27` - run after remediation to prove source still enforces App Check.

  Acceptance criteria (agent-executable only):
  - [ ] If Task 6 remediation is not external config, `powershell -NoProfile -ExecutionPolicy Bypass -Command "'SKIP external config route: ' + (Select-String -Path evidence/task-6-root-cause-matrix.md -Pattern '^REMEDIATION_ROUTE:' | Select-Object -First 1).Line | Tee-Object evidence/task-7-skip.txt"` exits 0.
  - [ ] If external config is selected, `evidence/task-7-before-redacted.json` and `evidence/task-7-after-redacted.json` exist and contain no raw API keys or tokens.
  - [ ] If API key restrictions are updated, after evidence includes `packageName=com.aiquota.mobile`, Task 3 Play SHA-1, and required API targets.
  - [ ] If App Check Play Integrity config is updated, after evidence still includes Play Integrity provider and Play app signing SHA-256.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "node --test test/release-hardening.test.js 2>&1 | Tee-Object evidence/task-7-release-hardening-after-config.txt"` exits 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: cloud config fix is minimal and redacted
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path evidence/task-7-skip.txt) { Get-Content evidence/task-7-skip.txt; exit 0 }; $bad = Select-String -Path evidence/task-7-before-redacted.json,evidence/task-7-after-redacted.json -Pattern 'AIza[0-9A-Za-z_-]+|Bearer\\s+|refreshToken|debugToken' -Quiet; if ($bad) { exit 1 }; Select-String -Path evidence/task-7-after-redacted.json -Pattern 'com.aiquota.mobile|firebaseappcheck.googleapis.com|identitytoolkit.googleapis.com|securetoken.googleapis.com' | Tee-Object evidence/task-7-config-check.txt"
    Expected: Exit code 0; selected config is present after remediation and no raw secret patterns exist.
    Evidence: evidence/task-7-config-check.txt

  Scenario: wrong package/SHA restriction remains impossible
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path evidence/task-7-skip.txt) { 'SKIPPED' | Tee-Object evidence/task-7-negative-check.txt; exit 0 }; if (Select-String -Path evidence/task-7-after-redacted.json -Pattern 'packageName\":\\s*\"com.aiquota.mobile\"' -Quiet) { 'PASS package restriction present' | Tee-Object evidence/task-7-negative-check.txt } else { exit 1 }"
    Expected: Exit code 0; after evidence cannot omit `com.aiquota.mobile` for selected API key restriction fix.
    Evidence: evidence/task-7-negative-check.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-7-*]

- [ ] 8. Conditional app-side TDD/source/config fix

  What to do: If Task 6 selects `APP_CHECK_CLIENT_BOOTSTRAP`, `SHIPPED_CONFIG_STALE`, or another source/app-config route, make the smallest source/config change with RED->GREEN evidence. Likely files are `android/app/google-services.json`, `android/app/src/main/java/com/aiquota/mobile/providers/FirebaseAppCheckCallable.kt`, Gemini/Antigravity gateway/activity files, and source tests. If Task 6 selected an external-only or no-repro route, write a skip artifact.
  Must NOT do: Do not change source to bypass App Check. Do not add raw API keys to test output. Do not update unrelated UI/collector/provider code.

  Parallelization: Can parallel: YES | Wave 2 | Blocks: [9] | Blocked by: [6]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/google-services.json:2-20` - Firebase project/app/API key config if app-bundled config is stale.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/FirebaseAppCheckCallable.kt:9-21` - App Check preflight and retry helper.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt:18-31` - Gemini callable start/complete path.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt:19-32` - Antigravity callable start/complete path.
  - Test:     `test/mobile-source.test.js:137-207` - source contract for Google native OAuth and App Check.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/GeminiCliFirebaseGatewayTest.kt:27-63` - Gemini/Antigravity source assertions.
  - Test:     `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt:56-82` - App Check bootstrap assertions.

  Acceptance criteria (agent-executable only):
  - [ ] If Task 6 remediation is not source/app config, `powershell -NoProfile -ExecutionPolicy Bypass -Command "'SKIP source route: ' + (Select-String -Path evidence/task-6-root-cause-matrix.md -Pattern '^REMEDIATION_ROUTE:' | Select-Object -First 1).Line | Tee-Object evidence/task-8-skip.txt"` exits 0.
  - [ ] If source/app config is selected, a RED test artifact exists at `evidence/task-8-red-before.txt` and fails for the identified missing contract before implementation.
  - [ ] If source/app config is selected, implementation touches only files listed in this task's references or a new focused test file.
  - [ ] If source/app config is selected, `powershell -NoProfile -ExecutionPolicy Bypass -Command "npm.cmd test 2>&1 | Tee-Object evidence/task-8-npm-green.txt; node --test test/release-hardening.test.js 2>&1 | Tee-Object evidence/task-8-release-hardening-green.txt"` exits 0.
  - [ ] If source/app config is selected, `powershell -NoProfile -ExecutionPolicy Bypass -Command "& '.\\.tmp\\tools\\gradle-8.10.2\\bin\\gradle.bat' -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest 2>&1 | Tee-Object evidence/task-8-android-gateway-green.txt"` exits 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: selected app-side fix goes RED then GREEN
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path evidence/task-8-skip.txt) { Get-Content evidence/task-8-skip.txt; exit 0 }; if (-not (Test-Path evidence/task-8-red-before.txt)) { exit 1 }; npm.cmd test; node --test test/release-hardening.test.js; & '.\\.tmp\\tools\\gradle-8.10.2\\bin\\gradle.bat' -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest --tests com.aiquota.mobile.providers.GeminiCliFirebaseGatewayTest"
    Expected: Exit code 0 after implementation; RED artifact proves the test failed before the fix.
    Evidence: evidence/task-8-green-summary.txt

  Scenario: no App Check bypass or raw key leak was introduced
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "$files = @('functions/src/index.js') + (Get-ChildItem functions/src -Filter *.js).FullName + (Get-ChildItem android/app/src/main/java/com/aiquota/mobile -Recurse -Filter *.kt).FullName; Select-String -Path $files -Pattern 'enforceAppCheck:\\s*false|DebugAppCheckProviderFactory' | Tee-Object evidence/task-8-bypass-scan.txt; if (Select-String -Path functions/src/index.js,functions/src/*.js -Pattern 'enforceAppCheck:\\s*false' -Quiet) { exit 1 }; if (Select-String -Path android/app/src/release/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt -Pattern 'DebugAppCheckProviderFactory' -Quiet) { exit 1 }"
    Expected: Exit code 0; no App Check disablement and no debug provider in release.
    Evidence: evidence/task-8-bypass-scan.txt
  ```

  Commit: YES | Message: `fix(android): restore Play v15 Firebase login start` | Files: [android/app/google-services.json, android/app/src/main/java/com/aiquota/mobile/providers/FirebaseAppCheckCallable.kt, android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt, android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt, android/app/src/test/java/com/aiquota/mobile/providers/GeminiCliFirebaseGatewayTest.kt, android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt, test/mobile-source.test.js, test/release-hardening.test.js]

- [ ] 9. Build, package, and install the selected remediation

  What to do: Based on Task 6/7/8, either keep the Play-installed v15 and retest after cloud-only remediation, or build/install the minimally corrected artifact. If app-bundled config/source changed, build a release bundle for upload and a debug/release test install as appropriate. If existing v15 cannot be fixed in-place because its bundled key cannot be restored, record that explicitly and prepare the next version artifact instead.
  Must NOT do: Do not overwrite Play-installed v15 before Task 10 unless Task 6 selected app-side remediation requiring a local test install. Do not claim v15 is fixed if a new bundle is required.

  Parallelization: Can parallel: NO | Wave 3 | Blocks: [10] | Blocked by: [7, 8]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `README.md:78-88` - debug APK and Google Play AAB build commands.
  - Pattern:  `android/app/build.gradle.kts:49-59` - application id, version, and client IDs.
  - Pattern:  `android/app/build.gradle.kts:81-97` - debug/release build types.
  - Evidence: `.omo/ulw-loop/evidence/C003-security-regression-cli-2026-06-09.txt:21-24` - prior installDebug/bundleRelease and upload signing evidence.
  - External: `https://firebase.google.com/docs/app-check/android/play-integrity-provider` - release App Check path remains Play Integrity.

  Acceptance criteria (agent-executable only):
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "npm.cmd test 2>&1 | Tee-Object evidence/task-9-npm-test.txt; node --test test/release-hardening.test.js 2>&1 | Tee-Object evidence/task-9-release-hardening.txt"` exits 0.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "& '.\\.tmp\\tools\\gradle-8.10.2\\bin\\gradle.bat' -p android :app:bundleRelease 2>&1 | Tee-Object evidence/task-9-bundle-release.txt"` exits 0 if source/app config changed, or `evidence/task-9-bundle-skip.txt` records cloud-only remediation.
  - [ ] If a local debug install is needed, `powershell -NoProfile -ExecutionPolicy Bypass -Command "& '.\\.tmp\\tools\\gradle-8.10.2\\bin\\gradle.bat' -p android :app:installDebug 2>&1 | Tee-Object evidence/task-9-install-debug.txt"` exits 0 and a package dump records the installed package.
  - [ ] If v15 cannot be repaired in-place, `evidence/task-9-v15-in-place-impossible.txt` states the exact blocker and the next artifact path.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: selected route has a build or skip artifact
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path evidence/task-9-bundle-skip.txt) { Get-Content evidence/task-9-bundle-skip.txt; exit 0 }; if (-not (Test-Path 'android/app/build/outputs/bundle/release/app-release.aab')) { exit 1 }; Get-Item android/app/build/outputs/bundle/release/app-release.aab | Format-List FullName,Length,LastWriteTime | Tee-Object evidence/task-9-aab-info.txt"
    Expected: Exit code 0; either cloud-only skip is explicit or release AAB exists.
    Evidence: evidence/task-9-aab-info.txt

  Scenario: package install state is not confused
    Tool:     powershell
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 shell dumpsys package com.aiquota.mobile | Select-String -Pattern 'versionCode=|installerPackageName=|Package \\[com.aiquota.mobile\\]' | Tee-Object evidence/task-9-install-state.txt"
    Expected: Evidence clearly shows whether the emulator still has Play-installed v15 or a local test install.
    Evidence: evidence/task-9-install-state.txt
  ```

  Commit: YES | Message: `chore(android): package Play login remediation` | Files: [android/app/build.gradle.kts, android/app/google-services.json, docs/qa/play-v15-gemini-antigravity-login.md]

- [ ] 10. Final Gemini and Antigravity runtime QA

  What to do: Run final adb/computer-use QA for both Gemini and Antigravity after the selected remediation. Success is either both providers reach Google OAuth/WebView on Play-installed v15 after cloud-only remediation, or both reach Google OAuth/WebView on the corrected local/release candidate with a clear statement that Play v15 needs a new upload. Confirm Cloud Functions/App Check logs show `app=VALID auth=VALID` or status 200 for both start callables.
  Must NOT do: Do not enter Google credentials. Do not keep raw account screenshots. Do not ignore one provider because the other passed.

  Parallelization: Can parallel: NO | Wave 3 | Blocks: [final] | Blocked by: [9]

  References (executor has NO interview context - be exhaustive):
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:108-127` - Gemini OAuth start success boundary is `webView.loadUrl(authorizationUrl)`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:108-127` - Antigravity OAuth start success boundary is `webView.loadUrl(authorizationUrl)`.
  - Pattern:  `android/app/src/main/java/com/aiquota/mobile/providers/FirebaseAppCheckCallable.kt:14-20` - final logs should not show the token preflight ending with placeholder token.
  - Evidence: `.omo/ulw-loop/evidence/C001-debug-appcheck-token-postfix-2026-06-09.txt:17-25` - prior expected Cloud Logging shape: status 200 and `app=VALID auth=VALID`.
  - External: `https://firebase.google.com/docs/app-check/cloud-functions` - client SDK attaches App Check token and callable requires valid token when enforcement is enabled.

  Acceptance criteria (agent-executable only):
  - [ ] `evidence/task-10-gemini-final-logcat-redacted.txt` contains no `API key not valid`, no `App attestation failed`, and no `gemini_cli_oauth_start_failed`; it contains WebView/OAuth evidence or corresponding Cloud Functions status 200.
  - [ ] `evidence/task-10-antigravity-final-logcat-redacted.txt` contains no `API key not valid`, no `App attestation failed`, and no `antigravity_oauth_start_failed`; it contains WebView/OAuth evidence or corresponding Cloud Functions status 200.
  - [ ] `evidence/task-10-functions-final-redacted.txt` includes both `startGeminiCliOAuth` and `startAntigravityOAuth` rows with either `status=200` or `app=VALID auth=VALID`.
  - [ ] `powershell -NoProfile -ExecutionPolicy Bypass -Command "$bad = Select-String -Path evidence/task-10-* -Pattern 'AIza[0-9A-Za-z_-]+|Bearer\\s+|oauth.*code=|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+' -Quiet; if ($bad) { exit 1 } else { 'PASS final evidence redacted' | Tee-Object evidence/task-10-redaction-check.txt }"` exits 0.

  QA scenarios (MANDATORY - task incomplete without these):
  ```
  Scenario: Gemini final login start reaches OAuth/App Check valid state
    Tool:     computer-use + powershell(adb)
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 shell am force-stop com.aiquota.mobile; adb -s emulator-5554 logcat -c; adb -s emulator-5554 shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 2; adb -s emulator-5554 shell input tap 878 1978; Start-Sleep -Seconds 10; adb -s emulator-5554 logcat -d | Select-String -Pattern 'AIQuotaGemini|FirebaseAppCheck|LocalRequestInterceptor|startGeminiCliOAuth|App attestation failed|API key not valid|oauth_start_failed|WebView|accounts.google.com|app=|auth=' | ForEach-Object { $_.Line -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' -replace '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+','<REDACTED_EMAIL>' -replace 'code=[^&\\s]+','code=<REDACTED_OAUTH_CODE>' } | Tee-Object evidence/task-10-gemini-final-logcat-redacted.txt"
    Expected: Evidence has WebView/OAuth or valid callable state and lacks `API key not valid`, `App attestation failed`, and `gemini_cli_oauth_start_failed`.
    Evidence: evidence/task-10-gemini-final-logcat-redacted.txt

  Scenario: Antigravity final login start reaches OAuth/App Check valid state
    Tool:     computer-use + powershell(adb)
    Steps:    powershell -NoProfile -ExecutionPolicy Bypass -Command "adb -s emulator-5554 shell am force-stop com.aiquota.mobile; adb -s emulator-5554 logcat -c; adb -s emulator-5554 shell am start -n com.aiquota.mobile/.MainActivity; Start-Sleep -Seconds 2; adb -s emulator-5554 shell input tap 878 2120; Start-Sleep -Seconds 10; adb -s emulator-5554 logcat -d | Select-String -Pattern 'AIQuotaAntigravity|FirebaseAppCheck|LocalRequestInterceptor|startAntigravityOAuth|App attestation failed|API key not valid|oauth_start_failed|WebView|accounts.google.com|app=|auth=' | ForEach-Object { $_.Line -replace 'AIza[0-9A-Za-z_-]+','<REDACTED_FIREBASE_API_KEY>' -replace '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+','<REDACTED_EMAIL>' -replace 'code=[^&\\s]+','code=<REDACTED_OAUTH_CODE>' } | Tee-Object evidence/task-10-antigravity-final-logcat-redacted.txt"
    Expected: Evidence has WebView/OAuth or valid callable state and lacks `API key not valid`, `App attestation failed`, and `antigravity_oauth_start_failed`.
    Evidence: evidence/task-10-antigravity-final-logcat-redacted.txt
  ```

  Commit: NO | Message: `n/a` | Files: [evidence/task-10-*]

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
- Evidence-only and cloud-config-only tasks do not require git commits; commit source/config changes only when Task 8 or Task 9 actually changes tracked files.
- Reference the plan file path in the final commit footer: `Plan: plans/play-v15-gemini-antigravity-login.md`.

## Success criteria
- All Must-Have shipped; all QA scenarios pass with captured evidence; F1-F4 approved; commit history clean.
- The final root-cause report explains why the Play-installed v15 failed, using package/signing/API key/App Check/Functions evidence rather than session-expiry assumptions.
- Both Gemini and Antigravity login starts reach Google OAuth/WebView or callable status 200 with App Check still enforced.
