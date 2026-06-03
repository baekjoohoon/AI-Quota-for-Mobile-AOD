# Antigravity Firebase Token Gateway Progress

Date: 2026-05-29

## 2026-05-29 16:19 KST

**작업:** Task 1 RED/GREEN 완료 - backend gateway security helpers.

**변경 파일:**
- `functions/test/core.test.js`
- `functions/src/core.js`

**명령:**
```powershell
npm run test:functions
npm.cmd run test:functions
```

**결과:** PASS

**증거:** `npm run test:functions`는 PowerShell 실행 정책 때문에 `npm.ps1` 로딩에서 실패했다. 같은 테스트를 `npm.cmd run test:functions`로 재실행해 RED를 확인했고, 첫 실패는 `core.js`가 `antigravityTokenAad` export를 제공하지 않는다는 SyntaxError였다. 구현 후 `npm.cmd run test:functions`에서 `tests 8`, `pass 8`, `fail 0`을 확인했다.

**다음 작업:** Task 2 - `functions/src/antigravityGateway.js`를 테스트 먼저 추가해 OAuth URL builder와 mockable gateway module을 만든다.

## 2026-05-29 16:23 KST

**작업:** Task 2 RED/GREEN 완료 - Antigravity OAuth URL builder와 gateway module shell.

**변경 파일:**
- `functions/test/antigravityGateway.test.js`
- `functions/src/antigravityGateway.js`
- `package.json`

**명령:**
```powershell
npm.cmd run test:functions
```

**결과:** PASS

**증거:** RED는 `ERR_MODULE_NOT_FOUND` for `functions/src/antigravityGateway.js`로 확인했다. 구현 후 `npm.cmd run test:functions`에서 `tests 9`, `pass 9`, `fail 0`을 확인했다. OAuth URL은 `access_type=offline`, `prompt=consent`, `response_type=code`, `code_challenge_method=S256`, `cloud-platform` scope를 포함한다.

**다음 작업:** Task 3 - KMS encrypt/decrypt wrapper를 테스트 먼저 추가하고 AAD mismatch 실패를 고정한다. `index.js`의 null handler export는 Firebase loader 위험을 피하기 위해 실제 callable/request handler가 생기는 Task 4까지 보류한다.

## 2026-05-29 16:27 KST

**작업:** Task 3 RED/GREEN 완료 - refresh token KMS wrapper와 AAD mismatch 검증.

**변경 파일:**
- `functions/test/antigravityGateway.test.js`
- `functions/src/antigravityGateway.js`

**명령:**
```powershell
npm.cmd run test:functions
```

**결과:** PASS

**증거:** RED는 `antigravityGateway.js`가 `decryptRefreshToken` export를 제공하지 않는다는 SyntaxError로 확인했다. 구현 후 `npm.cmd run test:functions`에서 `tests 10`, `pass 10`, `fail 0`을 확인했다. fake KMS는 encrypt AAD와 decrypt AAD가 다르면 `AAD_MISMATCH`를 던지며, wrapper는 ciphertext만 base64로 반환한다.

**다음 작업:** Task 4 - callback token record helper부터 테스트 먼저 추가하고, refresh token 누락 시 Firestore 저장 전 `REFRESH_TOKEN_MISSING`으로 중단하게 만든다.

## 2026-05-29 16:34 KST

**작업:** Task 4 진행 - encrypted token record helper, OAuth start/callback handler shell, Secret Manager/KMS 주입 연결.

**변경 파일:**
- `functions/test/antigravityGateway.test.js`
- `functions/src/antigravityGateway.js`
- `functions/src/index.js`
- `functions/package.json`

**명령:**
```powershell
npm.cmd run test:functions
node --check functions\src\index.js
node --check functions\src\antigravityGateway.js
npm.cmd --cache '.tmp\npm-cache' view @google-cloud/kms version
```

**결과:** PASS

**증거:** RED는 `buildEncryptedTokenRecord` export 부재로 확인했다. 구현 후 `npm.cmd run test:functions`에서 `tests 11`, `pass 11`, `fail 0`을 확인했다. `index.js`와 `antigravityGateway.js` syntax check도 통과했다. npm registry 조회는 workspace cache 사용 시 `@google-cloud/kms` latest `5.5.0`을 반환했고, `functions/package.json`은 `^5.5.0`으로 맞췄다.

**다음 작업:** Task 5 - `BACKEND_API_FORBIDDEN` 403 mapping 테스트를 먼저 추가하고 `collectAntigravityUsage` / `disconnectAntigravity`의 실제 backend flow를 구현한다.

## 2026-05-29 16:44 KST

**작업:** Task 5 RED/GREEN 완료 - collect 403 mapping, token missing, disconnect delete-before-local semantics.

**변경 파일:**
- `functions/test/antigravityGateway.test.js`
- `functions/src/antigravityGateway.js`

**명령:**
```powershell
npm.cmd run test:functions
node --check functions\src\antigravityGateway.js
node --check functions\src\index.js
```

**결과:** PASS

**증거:** RED는 `mapAntigravityApiFailure`, `collectAntigravityUsageForUid`, `disconnectAntigravityForUid` export 부재 또는 placeholder result mismatch로 각각 확인했다. 구현 후 `npm.cmd run test:functions`에서 `tests 15`, `pass 15`, `fail 0`을 확인했다. Fake KMS/fetch 테스트에서 private API 403은 `{ errorKind: "BACKEND_API_FORBIDDEN", requiresAuth: false, retryable: false }`로 반환되고, 반환 JSON에 refresh token/access token/client secret 문자열이 포함되지 않는 것을 확인했다. Disconnect 테스트는 revoke 500이어도 backend token doc delete가 실행되고 redacted warning만 반환됨을 확인했다.

**다음 작업:** Task 6 - Android Firebase gateway client source test를 먼저 추가해 APK/source에 OAuth client secret이 들어가지 않고 callable names만 포함되는지 고정한다.

## 2026-05-29 16:58 KST

**작업:** Task 6 RED/GREEN 완료 - Android Firebase gateway wrapper와 no-client-secret source test.

**변경 파일:**
- `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt`
- `android/app/build.gradle.kts`

**명령:**
```powershell
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest
```

**결과:** PASS

**증거:** RED는 `AntigravityFirebaseGateway.kt` missing `FileNotFoundException`으로 확인했다. Firebase Auth/Functions/App Check dependencies와 gateway wrapper 추가 후 focused test가 `BUILD SUCCESSFUL`로 통과했다. 첫 compile에서 `HttpsCallableResult.data`가 private로 보여 `getData()`로 수정했다. Gateway source/build file에는 `client_secret` 및 `ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET` 문자열이 없고 callable names `startAntigravityOAuth`, `collectAntigravityUsage`, `disconnectAntigravity`가 존재한다.

**다음 작업:** Task 7 - Antigravity definition/refresh/connect routing을 Firebase gateway connector로 전환하고 WebView login path를 제거한다.

## Purpose

This document protects implementation context for the Antigravity Firebase token gateway. Update it after every meaningful step so a later session can continue from evidence instead of repeating the same investigation.

Primary spec:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/specs/2026-05-29-antigravity-firebase-token-gateway-spec.md`

Primary plan:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/plans/2026-05-29-antigravity-firebase-token-gateway.md`

Troubleshooting log:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`

## Context Snapshot

- Antigravity has no confirmed user-visible web usage page equivalent to `https://gemini.google.com/usage`.
- WebView + JS extractor can preserve session, but has no reliable Antigravity usage payload to observe.
- `cloudcode-pa.googleapis.com` and `daily-cloudcode-pa.googleapis.com` are treated as private APIs.
- Direct Android access-token attempts have already failed; do not spend more time on mobile-only private API variations unless the user explicitly asks for a diagnostic spike.
- The selected direction is Firebase Functions gateway with server-side OAuth exchange and Cloud KMS encrypted refresh token storage.
- Firebase does not bypass private API allowlisting. If the backend OAuth client is rejected, the app must surface `BACKEND_API_FORBIDDEN`.

## Update Rules

Add a new entry when:

- a test is added or changed,
- a Firebase Function is implemented,
- a Firestore/KMS/Secret Manager rule is changed,
- Android gateway code is added,
- emulator or deployed Functions are exercised,
- Android APK is installed,
- a runtime login/collect/disconnect flow is tested,
- a blocking error is diagnosed.

Do not record raw OAuth codes, tokens, cookies, auth headers, raw private API bodies, email addresses, or account ids.

## Entry Template

````markdown
## YYYY-MM-DD HH:mm KST

**작업:** <what changed>

**변경 파일:**
- `<path>`

**명령:**
```powershell
<command>
```

**결과:** <PASS/FAIL/BLOCKED>

**증거:** <redacted test output, status code, or log summary>

**다음 작업:** <next concrete step>
````

## 2026-05-29 14:40 KST

**작업:** Antigravity Firebase token gateway 방향을 문서화함.

**변경 파일:**
- `docs/superpowers/specs/2026-05-29-antigravity-firebase-token-gateway-spec.md`
- `docs/superpowers/plans/2026-05-29-antigravity-firebase-token-gateway.md`
- `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`
- `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`

**명령:**
```powershell
Get-Content -Path 'android/app/src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt' -TotalCount 340
Get-Content -Path 'android/app/src/main/java/com/aiquota/mobile/providers/GoogleOAuthUsageBridge.kt' -TotalCount 180
```

**결과:** PASS

**증거:** Existing Android code already has Antigravity `loadCodeAssist` and `fetchAvailableModels` API shapes, but they are client-side private API calls. The new spec moves OAuth secret and refresh-token custody to Firebase Functions with KMS encryption.

**다음 작업:** Write failing tests for Functions token encryption and gateway error mapping before implementing backend code.

## 2026-05-29 15:05 KST

**작업:** 컨텍스트 보호용 진행 문서 템플릿의 깨진 라벨을 수정하고, 이어받기 규칙을 명확히 함.

**변경 파일:**
- `docs/superpowers/specs/2026-05-29-antigravity-firebase-token-gateway-spec.md`
- `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`
- `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`

**명령:**
```powershell
Get-Content -LiteralPath 'D:\Vibe Project\AI Usage for Mobile\docs\qa\antigravity-firebase-token-gateway-progress-2026-05-29.md' -Raw
Get-Content -LiteralPath 'D:\Vibe Project\AI Usage for Mobile\docs\qa\antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md' -Raw
```

**결과:** PASS

**증거:** 진행/문제해결 문서가 Spec에서 참조되고 있으며, 다음 세션은 Spec -> Plan -> Progress -> Troubleshooting 순서로 읽으면 현재 설계 방향과 실패 모드를 복구할 수 있다.

**다음 작업:** 구현 시작 전 `functions` 단위 테스트부터 추가한다.

## 2026-05-29 17:06 KST

**작업:** Task 7/8 completion pass - Antigravity routing, Firebase Auth/App Check bootstrap, Firestore/Firebase config, source tests, and emulator definition-load smoke.

**변경 파일:**
- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt`
- `android/app/src/debug/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt`
- `android/app/src/release/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt`
- `android/app/src/main/java/com/aiquota/mobile/MainActivity.kt`
- `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt`
- `firebase.json`
- `firestore.rules`
- `functions/package-lock.json`
- `test/structure.test.js`
- `test/mobile-source.test.js`
- `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`

**명령:**
```powershell
node test\structure.test.js
node test\mobile-source.test.js
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest
npm.cmd test
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:testDebugUnitTest
rg -n "ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET|client_secret|clientSecret|refreshToken|accessToken" android\app\src\main\java\com\aiquota\mobile\providers\AntigravityFirebaseGateway.kt android\app\src\main\java\com\aiquota\mobile\providers\AntigravityFirebaseConnector.kt android\app\build.gradle.kts -S
npm.cmd --prefix functions install --no-audit --no-fund --cache "$((Resolve-Path '.tmp\npm-cache').Path)"
firebase.cmd emulators:exec --only functions,firestore,auth --project demo-ai-quota-mobile "cmd /c echo emulator_smoke"
```

**결과:** PASS

**증거:** `npm.cmd test` passed Functions tests (`tests 15`, `pass 15`), structure tests (`tests 2`, `pass 2`), and mobile source tests (`tests 6`, `pass 6`). Full Android `:app:testDebugUnitTest` completed with `BUILD SUCCESSFUL`. Focused Antigravity gateway test also completed with `BUILD SUCCESSFUL`. Android gateway/build secret scan returned no matches for OAuth client secret, `client_secret`, `clientSecret`, `refreshToken`, or `accessToken`. Firebase emulator loaded function definitions for `antigravityOAuthCallback`, `collectAntigravityUsage`, `disconnectAntigravity`, and `startAntigravityOAuth`.

**다음 작업:** Real end-to-end OAuth/callback/collect validation needs production Firebase project configuration: Secret Manager values for Antigravity OAuth client id/secret/redirect URI, `ANTIGRAVITY_KMS_KEY_NAME`, KMS IAM for the Functions service account, App Check setup, and a provider account/API path that does not return `BACKEND_API_FORBIDDEN`.

## 2026-05-29 17:10 KST

**작업:** Task 8 Android install and redacted log smoke.

**변경 파일:**
- `docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`

**명령:**
```powershell
adb devices
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:installDebug
adb logcat -c
adb shell am start -n com.aiquota.mobile/.MainActivity
Start-Sleep -Seconds 5
adb shell pidof com.aiquota.mobile
adb logcat -d -s AIQuotaAntigravity AIQuotaFirebaseGateway AIQuotaBgRefreshService AIQuotaCollector AIQuotaLogin GoogleOAuth AndroidRuntime FirebaseAppCheck
```

**결과:** PASS

**증거:** `adb devices` showed `emulator-5554 device`. `:app:installDebug` installed `app-debug.apk` on `emulator-5554` and ended with `BUILD SUCCESSFUL`. App launch returned process id `11611`. Filtered logcat showed only redacted operational lines such as `cycleStart providers=codex`, `load provider=codex start=chatgpt.com/ request=1`, and `inject provider=codex url=chatgpt.com/`; no OAuth code, token, cookie value, Authorization header, or raw private API body appeared in the filtered output.

**다음 작업:** Production Firebase configuration and real Antigravity OAuth/collect validation remain external-runtime work. If backend private API returns 403 after real secrets/KMS are configured, keep the implemented `BACKEND_API_FORBIDDEN` unsupported path.

## 2026-05-29 17:44 KST

**작업:** KMS-first token custody를 Secret Manager AES-GCM validation phase로 전환.

**변경 파일:**
- `functions/src/antigravityGateway.js`
- `functions/src/index.js`
- `functions/test/antigravityGateway.test.js`
- `functions/package.json`
- `functions/package-lock.json`
- `docs/superpowers/specs/2026-05-29-antigravity-firebase-token-gateway-spec.md`
- `docs/superpowers/plans/2026-05-29-antigravity-firebase-token-gateway.md`
- `docs/qa/antigravity-firebase-token-gateway-troubleshooting-2026-05-29.md`

**명령:**
```powershell
npm.cmd run test:functions -- --test-name-pattern "refresh token decrypt requires exact AAD"
npm.cmd --prefix functions uninstall @google-cloud/kms --no-audit --no-fund --cache "$((Resolve-Path '.tmp\npm-cache').Path)"
node --check functions\src\antigravityGateway.js
node --check functions\src\index.js
npm.cmd run test:functions
npm.cmd test
& 'C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.14-all\c2qonpi39x1mddn7hk5gh9iqj\gradle-8.14\bin\gradle.bat' :app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest
firebase.cmd emulators:exec --only functions,firestore,auth --project demo-ai-quota-mobile "cmd /c echo emulator_smoke"
```

**결과:** PASS

**증거:** RED는 기존 KMS 필수 코드가 `KMS_ENCRYPT_INPUT_REQUIRED` 및 `ANTIGRAVITY_COLLECT_CONFIG_REQUIRED`를 던지는 것으로 확인했다. 구현 후 `npm.cmd test`는 Functions tests (`tests 15`, `pass 15`), structure tests (`tests 2`, `pass 2`), mobile source tests (`tests 6`, `pass 6`)로 통과했다. Focused Android `AntigravityFirebaseGatewayTest`는 `BUILD SUCCESSFUL`로 통과했다. Firebase emulator definition-load smoke는 KMS 패키지 없이 `antigravityOAuthCallback`, `collectAntigravityUsage`, `disconnectAntigravity`, `startAntigravityOAuth`를 포함한 Functions를 로드했다. KMS import와 `@google-cloud/kms` dependency를 제거했고, gateway는 `ANTIGRAVITY_TOKEN_MASTER_KEY` Secret으로 AES-256-GCM 암호화/복호화를 수행한다. AAD mismatch는 `AES_GCM_DECRYPT_FAILED`로 실패하며, 반환 결과에는 refresh token/access token/client secret이 포함되지 않는다.

**다음 작업:** Production Firebase에 `ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_ID`, `ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET`, `ANTIGRAVITY_GOOGLE_OAUTH_REDIRECT_URI`, `ANTIGRAVITY_TOKEN_MASTER_KEY` secrets를 설정한 뒤 deploy/emulator smoke와 real OAuth callback/collect를 검증한다. 서비스 가능성이 확인되면 AES Secret vault를 Cloud KMS vault로 마이그레이션한다.
