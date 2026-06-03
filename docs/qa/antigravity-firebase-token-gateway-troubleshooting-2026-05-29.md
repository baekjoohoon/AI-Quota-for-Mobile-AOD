# Antigravity Firebase Token Gateway Troubleshooting

Date: 2026-05-29

## Purpose

This document captures known failure modes for the Antigravity Firebase token gateway. Update it whenever a failure is reproduced, diagnosed, or fixed.

Primary spec:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/specs/2026-05-29-antigravity-firebase-token-gateway-spec.md`

Progress log:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/antigravity-firebase-token-gateway-progress-2026-05-29.md`

## Redaction Rules

Never paste:

- OAuth authorization code,
- access token,
- refresh token,
- cookie value,
- full `Authorization` header,
- raw provider HTML,
- raw private API body,
- email address,
- account id.

Allowed evidence:

- provider id,
- endpoint host and method name,
- HTTP status,
- redacted Google API error code,
- typed gateway error kind,
- timeout duration,
- test name and pass/fail result.

## Issue 7: PowerShell Blocks npm.ps1 Test Command

**증상:** `npm run test:functions` does not reach the test runner in this Windows PowerShell session.

**재현 조건:** Run `npm run test:functions` from `D:/Vibe Project/AI Usage for Mobile`.

**증거:**
```text
PSSecurityException: npm.ps1 cannot be loaded because running scripts is disabled on this system.
```

**원인:** PowerShell execution policy blocks the `npm.ps1` shim before Node starts.

**조치:** Use `npm.cmd` for test commands in this session.

**검증:** `npm.cmd run test:functions` reached the Node test runner, first failed on the expected missing Task 1 helper export, then passed after implementation with `tests 8`, `pass 8`, `fail 0`.

**추가 증거:** `npm.cmd view @google-cloud/kms version` also failed when npm tried to write under the default AppData cache, and `npm.cmd --cache C:\tmp\npm-cache ...` failed because `C:\tmp` denied creating `npm-cache`. The same command succeeded with a workspace-local cache path `.tmp\npm-cache`.

**남은 위험:** Future PowerShell commands using bare `npm` or a non-writable npm cache can fail for the same reason; prefer `npm.cmd` with a workspace-local cache for registry checks in this session.

## Issue 8: Kotlin Daemon Temp File Access Denied

**증상:** Android Gradle test output includes Kotlin daemon connection failures and `AccessDeniedException` under `C:\Users\datell1357\AppData\Local\kotlin\daemon`.

**재현 조건:** Run `:app:testDebugUnitTest` in this sandboxed Windows session.

**증거:**
```text
AccessDeniedException: C:\Users\datell1357\AppData\Local\kotlin\daemon\kotlin-daemon-client-tsmarker...
Using fallback strategy: Compile without Kotlin daemon
```

**원인:** The Kotlin daemon tries to write a temp marker under AppData, which is outside the writable sandbox roots for this run.

**조치:** Treat the daemon message as environmental if Gradle falls back and the build/test reaches the actual compile/test result. Fix real compile errors separately.

**검증:** After changing `HttpsCallableResult.data` to `getData()`, `:app:testDebugUnitTest --tests com.aiquota.mobile.providers.AntigravityFirebaseGatewayTest` completed with `BUILD SUCCESSFUL` despite the daemon fallback warnings.

**남은 위험:** Full Android test runs will continue to print this fallback warning unless Kotlin daemon temp/cache paths are moved into a writable location or sandbox permissions change.

## Issue Template

````markdown
## Issue N: <short title>

**증상:** <what user/test sees>

**재현 조건:** <state and steps>

**증거:**
```text
<redacted evidence>
```

**원인:** <confirmed or current hypothesis>

**조치:** <code/config/docs change>

**검증:** <command/result>

**남은 위험:** <remaining risk>
````

## Issue 1: Backend Private API Still Returns 403

**증상:** OAuth succeeds and refresh token decrypts, but `collectAntigravityUsage` returns no usage.

**재현 조건:** Functions uses a valid Google access token and calls Antigravity private API adapter.

**증거:**
```text
provider=antigravity method=fetchAvailableModels status=403 errorKind=BACKEND_API_FORBIDDEN
```

**원인:** Firebase Functions does not grant private API allowlist access. The backend OAuth client or Google Cloud project may still be rejected by `cloudcode-pa` / Antigravity private API.

**조치:** Map the response to `BACKEND_API_FORBIDDEN`, do not ask the user to login again, and show Antigravity as unavailable/unsupported for this build.

**검증:** Unit test `collectAntigravityUsageForUid maps private api forbidden without leaking tokens` asserts `requiresAuth=false`, `retryable=false`, and no refresh/access/client secret string appears in the returned result.

**남은 위험:** If this persists in production, Antigravity cannot be supported without an accepted provider API path.

## Issue 2: Refresh Token Missing After OAuth Callback

**증상:** Callback succeeds but token storage is not created.

**재현 조건:** Google token endpoint returns access token but no refresh token.

**증거:**
```text
provider=antigravity callback tokenExchange=true refreshToken=false
```

**원인:** Google may omit refresh token when prior consent exists or OAuth parameters are wrong.

**조치:** Ensure `access_type=offline` and `prompt=consent` are present in `startAntigravityOAuth`. If still missing, return `REFRESH_TOKEN_MISSING` and require the user to revoke/re-consent.

**검증:** Unit test for generated OAuth URL includes both parameters. Callback test rejects missing refresh token without writing Firestore token doc.

**남은 위험:** Users with stale consent may need a manual revoke/reconnect flow.

## Issue 3: AES-GCM Token Decrypt Fails

**증상:** Stored token doc exists, but collection returns `TOKEN_DECRYPT_FAILED`.

**재현 조건:** Functions decrypts `users/{uid}/providerSecrets/antigravity.encryptedRefreshToken` with the `ANTIGRAVITY_TOKEN_MASTER_KEY` Secret.

**증거:**
```text
provider=antigravity aesDecrypt=false errorKind=TOKEN_DECRYPT_FAILED
```

**원인:** AAD mismatch, wrong `ANTIGRAVITY_TOKEN_MASTER_KEY`, malformed ciphertext, or unsupported token ciphertext version.

**조치:** Verify AAD is exactly `uid:{uid}:provider:antigravity:oauthClient:{oauthClientId}:aad:v1`. Verify `ANTIGRAVITY_TOKEN_MASTER_KEY` is the same 32-byte AES key used during callback storage. Do not log the key or token.

**검증:** Unit test encrypts with one AAD and proves decrypt with another AAD fails with `AES_GCM_DECRYPT_FAILED`.

**남은 위험:** This phase has weaker key isolation than Cloud KMS because Functions can read the AES master key. Key rotation requires explicit versioning and re-encryption work.

## Issue 4: App Check Or Firebase Auth Blocks Callable

**증상:** Android cannot start OAuth or collect usage.

**재현 조건:** Callable Functions are invoked from debug APK or emulator.

**증거:**
```text
function=startAntigravityOAuth status=401_or_403 errorKind=AUTH_OR_APP_CHECK_REQUIRED
```

**원인:** Firebase Auth user is missing, App Check token is missing, or debug App Check provider is not configured.

**조치:** Ensure Android signs in to Firebase before calling the gateway. Configure App Check debug provider for debug builds and Play Integrity provider for release.

**검증:** Android unit/source tests assert Firebase auth bootstrap happens before gateway calls. Firebase emulator definition-load smoke confirms `startAntigravityOAuth`, `collectAntigravityUsage`, and `disconnectAntigravity` initialize locally; real callable invocation still needs configured OAuth secrets, `ANTIGRAVITY_TOKEN_MASTER_KEY`, and App Check/Auth tokens.

**남은 위험:** Release builds need Play Integrity/App Check setup before Play distribution.

## Issue 5: Antigravity Schema Changes Or No Trusted Model Quotas

**증상:** Backend API returns 2xx JSON but Android shows no usage.

**재현 조건:** `fetchAvailableModels` response has no recognized model quota fields.

**증거:**
```text
provider=antigravity method=fetchAvailableModels status=200 trustedModelQuotaCount=0
```

**원인:** Provider schema changed, quota field names changed, or the account has no exposed quota bucket.

**조치:** Keep previous snapshot and return `NO_TRUSTED_PAYLOAD`. Add redacted schema-key diagnostics only, not raw body.

**검증:** Normalizer tests cover the new schema using synthetic redacted JSON before production collection accepts it.

**남은 위험:** Provider schema changes can temporarily make Antigravity stale until the adapter is updated.

## Issue 6: Disconnect Deletes Local State But Backend Token Remains

**증상:** User taps disconnect, app removes card, but reconnect silently reuses old backend token.

**재현 조건:** Android disconnect path fails before `disconnectAntigravity` completes.

**증거:**
```text
provider=antigravity disconnect local=true backendDelete=false
```

**원인:** Network/Function failure during disconnect.

**조치:** Android must call backend disconnect first, then clear local snapshot/session after success. If backend disconnect fails, surface retryable disconnect failure and keep local state visible.

**검증:** Android tests assert connector disconnect calls backend before `removeProviderSnapshot`.

**남은 위험:** If revoke fails but backend doc is deleted, Google token may remain valid until revoked by Google/account controls.

## Issue 9: Firebase Emulator Needs Local Config, Cache, Java 21, And Functions Dependencies

**증상:** Firebase CLI or emulator startup fails before runtime verification.

**재현 조건:** Run Firebase CLI from the sandboxed Windows session with default user config/cache paths, default `java`, or without `functions/node_modules`.

**증거:**
```text
EPERM: operation not permitted, open 'C:\Users\datell1357\.config\configstore\firebase-tools.json'
firebase-tools no longer supports Java version before 21
EPERM: operation not permitted, mkdir 'C:\Users\datell1357\.cache\firebase\emulators'
Couldn't find firebase-functions package in your source code. Have you run 'npm install'?
```

**원인:** Firebase CLI defaults to user config/cache locations outside writable sandbox roots. The default `java` on PATH is 1.8, while firebase-tools 15.15.0 requires Java 21. The Functions emulator also needs local dependencies installed under `functions/node_modules`.

**조치:** Use workspace-local Firebase config/cache, Android Studio JBR Java 21, and install Functions dependencies with an absolute workspace npm cache.

**검증:**
```powershell
$env:XDG_CONFIG_HOME=(Resolve-Path '.tmp\firebase-config').Path
$env:FIREBASE_EMULATORS_PATH=(Resolve-Path '.tmp\firebase-emulators').Path
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
npm.cmd --prefix functions install --no-audit --no-fund --cache "$((Resolve-Path '.tmp\npm-cache').Path)"
firebase.cmd emulators:exec --only functions,firestore,auth --project demo-ai-quota-mobile "cmd /c echo emulator_smoke"
```

Result: PASS. Emulator loaded function definitions for `antigravityOAuthCallback`, `collectAntigravityUsage`, `disconnectAntigravity`, and `startAntigravityOAuth`.

**남은 위험:** The smoke test only verifies local function loading. End-to-end OAuth/callback/collect still requires real Secret Manager values, `ANTIGRAVITY_TOKEN_MASTER_KEY`, and provider API access. Cloud KMS migration will later add KMS API/IAM requirements again.

## Issue 10: AES Secret Vault Is Weaker Than Cloud KMS

**증상:** KMS setup is intentionally deferred to avoid Cloud KMS API/key setup during the first Antigravity viability test.

**재현 조건:** Functions stores refresh-token ciphertext using AES-256-GCM and a Secret Manager master key.

**증거:**
```text
secret=ANTIGRAVITY_TOKEN_MASTER_KEY provider=antigravity encryption=aes-gcm-secret
```

**원인:** The AES master key is readable by the deployed Functions that need token storage/decryption. Cloud KMS would keep key material outside the function process and provide KMS-level audit/control for decrypt operations.

**조치:** Keep the AES key only in Secret Manager, attach it only to callback/collect/disconnect functions, never to Android, logs, docs, or Firestore. Use AES-GCM with exact AAD binding. Migrate to Cloud KMS after real OAuth/collect viability is confirmed.

**검증:** Functions tests prove ciphertext does not contain the refresh token, AAD mismatch fails decrypt, and returned gateway errors do not leak refresh/access/client secret material.

**남은 위험:** If the function runtime or Secret Manager access is compromised, stored Antigravity refresh-token ciphertext can be decrypted offline. KMS migration remains required before treating this as production-hardening complete.
