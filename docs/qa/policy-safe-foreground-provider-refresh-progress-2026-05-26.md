# Policy Safe Foreground Provider Refresh Progress

Date: 2026-05-26

## Scope

Implement policy-safe foreground provider refresh for AI Quota Android:

- Passive widget updates redraw cached data only.
- Manual widget refresh remains a user-action immediate refresh path.
- Live monitoring requires user approval and notification permission.
- The long-running refresh loop uses a `dataSync` foreground service.
- Provider diagnostics and heartbeat state remain redacted.
- Trusted provider state is preserved: `CONNECTED` is written only after trusted usage save success.

## Progress Log

### Baseline And Required Docs

Created the required QA/store documentation before production code changes:

- `docs/qa/policy-safe-foreground-provider-refresh-progress-2026-05-26.md`
- `docs/qa/policy-safe-foreground-provider-refresh-troubleshooting-2026-05-26.md`
- `docs/qa/policy-safe-foreground-provider-refresh-limitations-2026-05-26.md`
- `docs/store/foreground-service-declaration.md`

Verified the referenced spec and plan exist:

```powershell
Test-Path 'docs\superpowers\specs\2026-05-26-policy-safe-foreground-provider-refresh-spec.md'
Test-Path 'docs\superpowers\plans\2026-05-26-policy-safe-foreground-provider-refresh.md'
```

Result: PASS.

### RED Tests

Added focused tests for:

- Passive widget update does not start foreground service.
- Manual widget refresh can start foreground service as a user action.
- Manual/delayed widget refresh clears spinner when service start fails.
- Live monitoring requires user approval plus notification permission.
- `ProviderBackgroundRefreshService` uses `dataSync` and typed `startForeground`.
- Service heartbeat state is written without sensitive values.
- Foreground notification includes a user stop action.
- English and Korean copy mention 90-second live monitoring.
- Gemini/Antigravity OAuth code does not embed `client_secret`.
- Timeout/no payload/selector/API shape failures do not automatically become `INTERACTIVE_AUTH_REQUIRED`.

Initial focused RED command:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest" --tests "com.aiquota.mobile.widget.WidgetManualRefreshActionTest" --tests "com.aiquota.mobile.sync.ForegroundRefreshControllerTest" --tests "com.aiquota.mobile.sync.ForegroundRefreshPolicyTest" --tests "com.aiquota.mobile.notification.NotificationPermissionLaunchTest" --tests "com.aiquota.mobile.providers.GeminiCliOAuthRepositoryTest" --tests "com.aiquota.mobile.providers.AntigravityOAuthRepositoryTest" --tests "com.aiquota.mobile.providers.ProviderCollectorErrorPolicyTest"
```

Result: FAIL as intended after assertion import fixes. 34 tests ran, 8 failed against missing policy features.

### Implementation

Implemented the minimal production changes needed to pass the policy tests:

- Added persisted live-monitoring approval to `ForegroundRefreshController`.
- Changed foreground loop policy to require live-monitoring approval, notification permission, and at least one refreshable trusted provider snapshot.
- Added `ProviderBackgroundRefreshStateRepository` for redacted heartbeat/running/failure state.
- Switched service foreground promotion to typed `dataSync` `startForeground` on supported Android versions.
- Added notification stop action and wired it to disable live monitoring and stop the loop.
- Added 90-second English and Korean live-monitoring copy.
- Guarded manual widget refresh foreground-service starts with failure cleanup.
- Restarted the loop on app foreground entry only when policy allows it and heartbeat is stale.

### Focused GREEN

Focused policy test command:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests "com.aiquota.mobile.providers.ProviderBackgroundRefreshServicePolicyTest" --tests "com.aiquota.mobile.widget.WidgetManualRefreshActionTest" --tests "com.aiquota.mobile.sync.ForegroundRefreshControllerTest" --tests "com.aiquota.mobile.sync.ForegroundRefreshPolicyTest" --tests "com.aiquota.mobile.notification.NotificationPermissionLaunchTest" --tests "com.aiquota.mobile.providers.GeminiCliOAuthRepositoryTest" --tests "com.aiquota.mobile.providers.AntigravityOAuthRepositoryTest" --tests "com.aiquota.mobile.providers.ProviderCollectorErrorPolicyTest"
```

Result: PASS. `BUILD SUCCESSFUL in 14s`.

### Full Verification

Unit tests:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest
```

Result: PASS. `BUILD SUCCESSFUL in 4s`.

Debug APK build:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug
```

Result: PASS. `BUILD SUCCESSFUL in 5s`.

Whitespace check:

```powershell
git diff --check
```

Result: PASS. Exit code 0. Output contained only existing LF-to-CRLF warnings, not whitespace errors.

### Emulator Verification

Installed and launched debug APK on `emulator-5554`:

```powershell
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell monkey -p com.aiquota.mobile 1
```

Result: PASS.

Passive widget update direct shell trigger:

```powershell
adb -s emulator-5554 shell cmd appwidget update --user 0 com.aiquota.mobile/.widget.AIQuotaCircularWidgetProvider
adb -s emulator-5554 shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n com.aiquota.mobile/.widget.AIQuotaCircularWidgetProvider --eia android.appwidget.extra.APPWIDGET_IDS 1
```

Result: BLOCKED by emulator/shell platform restrictions. See troubleshooting Issue 4. The passive no-FGS behavior is covered by unit/source tests.

Foreground-service background-start exception check:

```powershell
adb -s emulator-5554 logcat -d | Select-String -Pattern "ForegroundServiceStartNotAllowedException"
```

Result: PASS. No matching log lines were present in the captured emulator log.

Live monitoring loop:

- Seeded a trusted Gemini provider snapshot.
- Enabled live monitoring and notification permission.
- Launched app, sent Home, and captured filtered logcat.

Evidence:

```text
05-26 13:33:35.569 D AIQuotaBgRefreshService: cycleStart providers=gemini
05-26 13:35:05.625 D AIQuotaBgRefreshService: cycleStart providers=gemini
```

Result: PASS. The loop produced roughly 90-second cycle starts while the notification/service was active.

Notification stop action:

- Expanded notification shade.
- Verified notification action text: `라이브 갱신 중지`.
- Tapped the action.
- Read service state prefs.

Evidence:

```xml
<boolean name="running" value="false" />
<boolean name="live_monitoring_enabled" value="false" />
```

Result: PASS. The stop action stopped the loop and disabled live monitoring.

Manual widget refresh:

Direct shell verification is blocked because the widget refresh receiver and service are intentionally non-exported. The behavior is covered by tests that assert manual refresh is the only widget path that starts `ProviderBackgroundRefreshService` and clears widget refresh feedback on start failure.

## 2026-05-27 Google Provider Retry Guard Update

User retest reported Gemini/Antigravity still entering `오류` after Google login. Emulator evidence at this point only showed the Gemini OAuth sign-in page before callback; local prefs remained `CONNECTING` with only encrypted OAuth `state`, so the post-login failure could not be reproduced from emulator logs.

Code inspection found remaining recoverable Google provider paths that could still persist `ERROR`: `WebLoginActivity.failKeepingPrevious()` on main-frame WebView/HTTP failures, `LocalUsageRepository.failKeepingPrevious()` on generic recoverable usage failures, and previously persisted Google `ERROR`/`UNAVAILABLE` snapshots from older builds.

Added RED tests:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest --tests com.aiquota.mobile.local.LocalUsageRepositoryTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
```

Initial result: FAIL. New tests proved the guard did not exist.

Implemented:

- Google recoverable login failures stay `STALE` and start collection retry instead of becoming `ERROR`.
- Google recoverable generic usage failures route through `markGoogleUsagePending()`.
- Previously saved Gemini/Antigravity `ERROR` or `UNAVAILABLE` snapshots with recoverable messages are read back as retry-pending `STALE`.
- Explicit OAuth failure remains explicit and is not silently treated as success.

Focused verification result: PASS. `BUILD SUCCESSFUL in 40s`.

Full verification after the guard:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
git diff --check
rg -n "client_secret|clientSecret|notasecret" android/app/src/main android/app/build.gradle.kts
rg -a -n "client_secret|clientSecret|notasecret" android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Result:

- Unit tests: PASS. `BUILD SUCCESSFUL in 12s`.
- Debug APK build: PASS. `BUILD SUCCESSFUL in 14s`.
- `git diff --check`: PASS. Only LF-to-CRLF warnings.
- Source and APK secret scan: PASS. No matching secret strings found.
- Emulator reinstall: PASS. `Success`.

Post-install emulator state still showed the prior in-progress Gemini login snapshot (`CONNECTING`, `Opening provider login`) because the emulator had not completed the OAuth callback. Runtime collection success still needs a completed Google login flow.

## 2026-05-27 Google Identity Authorization Update

User retest still showed Gemini/Antigravity as `오류` after Google login. The earlier WebView OAuth repair was not enough because WebView login can complete visually without leaving a usable provider access token or Code Assist web session for collection.

Added RED coverage for a client-secret-free native Google authorization path:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
```

Initial result: FAIL. `GoogleAuthorizationActivity.kt` and `GoogleIdentityUsageAuthorizer.kt` did not exist, and `GoogleOAuthUsageBridge` did not report identity diagnostics.

Implemented:

- Added Google Play Services authorization dependency without adding any OAuth client secret.
- Gemini/Antigravity connect now launches `GoogleAuthorizationActivity` instead of the WebView OAuth start URL.
- `GoogleIdentityUsageAuthorizer` requests `cloud-platform`, `userinfo.email`, and `userinfo.profile` scopes through `AuthorizationRequest`.
- Usage collection now tries Google Identity access token first, then stored OAuth tokens, then the existing WebView cookie bridge.
- Gemini and Antigravity repositories can collect with a provided short-lived access token without persisting it as a refresh credential.
- Bridge diagnostics now separate `identityDiagnostic`, `oauthDiagnostic`, and WebView session diagnostics.

Focused verification result: PASS. `BUILD SUCCESSFUL in 40s`.

Full unit test first attempt:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
```

Result: FAIL. `GoogleProviderLoginRoutingTest.geminiAndAntigravityLoginUseWebViewOAuthLoopbackInsteadOfNativeGoogleAccount` still asserted the old WebView OAuth routing. Updated that regression test to assert the new client-secret-free native Google authorization route while preserving WebView login for non-Google providers.

Full verification and emulator install are still pending for this update.

## 2026-05-27 Google WebView Loopback Restoration

Emulator verification of the native Google Identity path showed it was not a viable replacement for the existing APK-style flow. Gemini opened the Play Services account chooser, but Google Play Services failed at `EXTERNAL_REAUTH_ACCOUNT`, returned a cancelled authorization result, and `GoogleAuthorizationActivity` produced an ANR while unwinding the failed flow.

Runtime evidence:

```text
Auth.Api.Credentials Flow step started: EXTERNAL_REAUTH_ACCOUNT
Auth.Api.Credentials Flow step completed: EXTERNAL_REAUTH_ACCOUNT; successful=false
AIQuotaGoogleIdentity provider=gemini googleIdentityPending diagnostic=google_identity_authorization_cancelled
ANR in com.aiquota.mobile/.providers.GoogleAuthorizationActivity
```

Action taken:

- Removed the default native Google Identity route for Gemini/Antigravity.
- Removed the Play Services auth dependency and native authorization Activity/helper.
- Restored Gemini/Antigravity connect routing to provider-specific WebView loopback OAuth URLs.
- Gemini now starts `GeminiCliOAuthRepository.beginAuthorizationUrl()` with `http://127.0.0.1:46417/oauth2callback`.
- Antigravity now starts `AntigravityOAuthRepository.beginAuthorizationUrl()` with `http://127.0.0.1:8080/callback`.
- Token exchange still uses public installed-app `client_id` only. No `client_secret` is embedded.
- `GoogleOAuthUsageBridge` now tries stored provider OAuth tokens and then the existing WebView session bridge; it no longer blocks first on the native Identity path.

Focused verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleProviderLoginRoutingTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
```

Result: PASS. `BUILD SUCCESSFUL in 45s`.

Full/static verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
git diff --check
rg -n "client_secret|clientSecret|notasecret|GOCSPX|GoogleAuthorizationActivity|GoogleIdentityUsageAuthorizer|play-services-auth" android/app/src/main android/app/build.gradle.kts
rg -a -n "client_secret|clientSecret|notasecret|GOCSPX|GoogleAuthorizationActivity|GoogleIdentityUsageAuthorizer" android/app/build/outputs/apk/debug/app-debug.apk
```

Result:

- Debug APK build: PASS. `BUILD SUCCESSFUL in 16s`.
- Unit tests: PASS. `BUILD SUCCESSFUL in 15s`.
- `git diff --check`: PASS. Only LF-to-CRLF warnings.
- Source and APK secret/native-path scans: PASS. No matching strings found.

Emulator result:

- Reinstalled the debug APK and cleared app data.
- Gemini connect now opens `WebLoginActivity`, not the native Google Identity account chooser.
- Logcat shows the expected client-secret-free WebView OAuth URL:

```text
provider=gemini resource=https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com&redirect_uri=http%3A%2F%2F127.0.0.1%3A46417%2Foauth2callback...
```

- The WebView login reached the Google password screen for the user account. Runtime collection success is blocked until the user enters the password/2FA in the emulator and completes consent.

## 2026-05-27 Google Web Session Bridge Header Follow-Up

Runtime evidence after completed Gemini login showed the loopback callback was reached, but the direct authorization-code exchange failed:

```text
provider=gemini googleTokenExchange success=false failure=Token exchange failed status=400 error=invalid_request description=client_secret is missing.
```

Action taken:

- Stopped treating the Google loopback callback as a token-exchange path in `WebLoginActivity`.
- Kept the callback as a WebView session completion signal and recovery trigger.
- Added browser-shaped Code Assist web-session bridge profiles where `Origin` stays on the provider page and `X-Origin`/SAPISID auth target `https://cloudcode-pa.googleapis.com`.
- Added focused regression checks for the new bridge profile and for avoiding callback-time token exchange.

Runtime verification of trusted usage collection is still pending after reinstall.

## 2026-05-27 Google Callback Token Exchange Removal

Follow-up runtime evidence showed the completed Gemini WebView callback still failed because the app attempted direct authorization-code exchange:

```text
provider=gemini googleTokenExchange success=false failure=Token exchange failed status=400 error=invalid_request description=client_secret is missing.
```

Action taken:

- Removed the unused callback-time Google token exchange helper from `WebLoginActivity`.
- Kept Google loopback callbacks as WebView session recovery only.
- Updated regression tests so `WebLoginActivity` must not contain `persistGoogleProviderTokensAndMaybeCollect`, `googleTokenExchange`, or `completeAuthorization(callbackUrl)`.
- Added another Code Assist web-session profile: API `Origin`/`X-Origin` with provider-origin SAPISID auth.

Focused verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleWebSessionCodeAssistFetcherTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
```

Result: PASS. Kotlin daemon emitted an `AccessDeniedException` warning and Gradle fell back to non-daemon Kotlin compilation; build still completed successfully.

Debug build/install:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Result: PASS. APK installed on `emulator-5554`.

Emulator note: Launching with the previous emulator data produced an ANR. Clearing app data made the app launch normally and show the Google notification permission prompt, so that ANR appears tied to prior stored local state/cache and is tracked separately from the Google login failure. Runtime post-login collection still requires the user to complete Google password/2FA/consent in the emulator.

## 2026-05-27 Google Identity Access Token Collection Reintroduced

Latest user retest still reported Gemini/Antigravity reaching an error state after Google login. The WebView-only recovery path removes `client_secret` exposure, but it still depends on Google WebView cookies/SAPISID request shape and has not produced trusted usage payload in runtime evidence.

Action taken:

- Reintroduced Google Play Services Identity authorization for Gemini and Antigravity as the default connect path.
- The new path requests short-lived access tokens through `Identity.getAuthorizationClient(...)`.
- It does not request offline access and does not embed `client_secret`, `clientSecret`, `notasecret`, or `GOCSPX`.
- Gemini/Antigravity usage collection now tries, in order: Google Identity access token, stored legacy OAuth token if present, then WebView session bridge.
- Google Identity account name/email are stored only through the existing Android Keystore-backed `SecureStringStore`.
- Disconnect now clears the Google Identity account hint before clearing WebView session state.
- If Google authorization succeeds but quota payload is unavailable, Gemini/Antigravity are kept in retry-pending stale state instead of direct `ERROR`.

Focused verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest --tests com.aiquota.mobile.providers.GoogleProviderLoginRoutingTest --tests com.aiquota.mobile.providers.GoogleIdentityUsageAuthorizerTest --tests com.aiquota.mobile.providers.ProviderSessionResetterTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
```

Result: PASS. Kotlin daemon emitted an `AccessDeniedException` warning and Gradle used fallback non-daemon compilation.

Full verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
rg -n "client_secret|clientSecret|notasecret|GOCSPX" android/app/src/main android/app/build.gradle.kts
rg -a -n "client_secret|clientSecret|notasecret|GOCSPX" android/app/build/outputs/apk/debug/app-debug.apk
```

Result:

- Full unit test: PASS. `BUILD SUCCESSFUL in 41s`.
- Debug APK build: PASS. `BUILD SUCCESSFUL in 17s`.
- Source/APK secret scan: PASS. No matches.

Emulator result:

- Reinstalled the debug APK.
- Cleared app data to remove the stale pre-install `CONNECTING` snapshot.
- Gemini connect now starts `GoogleAuthorizationActivity`.
- Google Play Services shows the account chooser for AI Quota and requests access to a Google account.
- Post-consent quota collection evidence is still pending because the user must select the account and consent in the emulator.

## 2026-05-27 Google Identity OAuth Console Registration Fix

Runtime evidence after the user retried Gemini login showed Google Play Services failing after account selection with:

```text
status=UNREGISTERED_ON_API_CONSOLE
```

Cause:

- The installed debug APK was signed by the sandbox debug keystore.
- Google Cloud had the user's local debug keystore SHA-1 registered, not the sandbox debug keystore SHA-1.
- Google Play Services therefore rejected the package/signature pair before an access token could be returned.

Action taken:

- Added optional local debug signing configuration driven by ignored `android/local.properties` keys.
- Set the local debug build to use `C:\Users\datell1357\.android\debug.keystore` in this workspace.
- Rebuilt the debug APK and verified the signing certificate:

```text
Signer #1 certificate SHA-1 digest: 0f756852329eea93c3f24f30b0de3817c77deb9a
```

- Reinstalled the APK after uninstalling the previously signed package.

Follow-up action:

- Added `GoogleIdentityCredentialStore.hasStoredCredential(...)`.
- Updated `GoogleOAuthUsageBridge.hasStoredCredential(...)` so a Google Identity account/email hint counts as stored Google authorization state.
- This prevents Gemini/Antigravity fallback collector failures after Identity authorization from dropping back to generic error handling just because no legacy OAuth token was stored.

Verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleIdentityUsageAuthorizerTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
apksigner verify --print-certs android\app\build\outputs\apk\debug\app-debug.apk
rg -n "client_secret|clientSecret|notasecret|GOCSPX" android/app/src/main android/app/build.gradle.kts
rg -a -n "client_secret|clientSecret|notasecret|GOCSPX" android/app/build/outputs/apk/debug/app-debug.apk
git diff --check
```

Result:

- Focused tests: PASS.
- Debug APK build: PASS.
- APK SHA-1 matches the Google Cloud-registered local debug SHA-1.
- Source/APK secret scan: PASS. No matches.
- `git diff --check`: PASS with line-ending warnings only.

Current emulator state:

- Gemini connect opens Google Play Services account selection.
- Logcat shows `AUTHORIZATION_VERIFY_CALLER`, `RESOLVE_ACCOUNT`, and `EXTERNAL_ACCOUNT_CHOOSER` completed/started without `UNREGISTERED_ON_API_CONSOLE`.
- Provider snapshot remains `CONNECTING`/`REFRESHING` while the account chooser is open, which is expected.
- Post-consent quota payload collection is still pending user account selection/consent.

## 2026-05-27 Google Login ANR And WebView Session Re-route

Runtime evidence after account selection showed the native Google Identity path was not viable for Gemini/Antigravity:

- `GoogleAuthorizationActivity` received an authorization result, then stayed on top while quota collection called Code Assist.
- `cloudcode-pa.googleapis.com` returned `403` for `loadCodeAssist` with the Android Identity access token.
- Android then reported activity top-resumed/pause timeout symptoms around the authorization Activity.

Action taken:

- `GoogleAuthorizationActivity` now finishes immediately after recording retry-pending Google authorization state; network collection runs on a background thread if that Activity is ever used again.
- Fixed a stale-state launch ANR where `STALE` Google snapshots without usage lines were decoded to `UNAVAILABLE`, then recovered back to `STALE`, causing repeated SharedPreferences save/read loops. `ProviderSnapshotCodec` now only demotes empty trusted rows for `CONNECTED`.
- Default Gemini/Antigravity connect routing was moved back to the APK-style WebView OAuth/session path:
  - Gemini uses `GeminiCliOAuthRepository.beginAuthorizationUrl()`.
  - Antigravity uses `AntigravityOAuthRepository.beginAuthorizationUrl()`.
  - Both launch `WebLoginActivity`, not `GoogleAuthorizationActivity`.
- `GoogleOAuthUsageBridge` now tries WebView Google session cookies first, then stored OAuth tokens, then Google Identity as a last fallback.
- `GoogleWebSessionCodeAssistFetcher.hasSessionCookie(...)` was added so WebView Google cookies count as stored Google authorization state.

Verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleProviderLoginRoutingTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest --tests com.aiquota.mobile.providers.ProviderDefinitionRegistryTest --tests com.aiquota.mobile.providers.GoogleIdentityUsageAuthorizerTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
```

Result:

- Focused tests: PASS.
- Debug APK build: PASS.
- Emulator install: PASS.
- Clean emulator launch after `pm clear`: PASS.
- Gemini connect now opens `WebLoginActivity` and loads `accounts.google.com/o/oauth2/v2/auth` with the loopback callback URL.
- No `GoogleAuthorizationActivity`, `Input dispatching timed out`, or `ForegroundServiceStartNotAllowedException` appeared during this route check.
- Trusted post-login quota collection still requires the user to complete Google login/consent in the emulator.

Follow-up hardening after another account-selection freeze report:

- Added a no-op guard to `LocalUsageRepository.saveSnapshots(...)`.
- Snapshot cleanup can still persist recovered state, but identical encoded provider JSON is no longer written back to SharedPreferences.
- This prevents SharedPreferences listeners from re-entering `readSnapshots()`/`saveSnapshots()` when there is no actual state change.

Verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
git diff --check
rg -n "client_secret|clientSecret|notasecret|GOCSPX" android/app/src/main android/app/build.gradle.kts
rg -a -n "client_secret|clientSecret|notasecret|GOCSPX" android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Result:

- Unit tests: PASS.
- Debug APK build: PASS.
- Source/APK secret scan: PASS. No matches.
- `git diff --check`: PASS with line-ending warnings only.
- Latest APK reinstall and launch: PASS. No new ANR or crash appeared in the 5-second post-launch log window.

## 2026-05-27 Antigravity Plans Return-To-App Fix

Runtime evidence after a successful Antigravity Google login showed the WebView reached `https://antigravity.google/docs/plans/`, then posted `antigravity_no_trusted_payload`. The app kept the WebView open because Google collector errors after OAuth callback were deferred to the 75-second fallback.

Action taken:

- Removed the `googleCollectorErrorAwaitingFallback` branch from `WebLoginActivity`.
- Google provider collector errors now use the same retry-pending path when a stored WebView/session credential exists.
- This makes Antigravity return to the app immediately after the Plans page proves no trusted payload is available.

Verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Result:

- Focused callback tests: PASS.
- Unit tests: PASS.
- Debug APK build: PASS.
- Latest APK install: PASS.
- Emulator re-run currently stops at Google account selection, which requires user action. The previous Plans-page wait branch is removed in code and covered by the callback test.

## 2026-05-27 Google OAuth Callback Token Exchange Fix

Runtime evidence after the Plans return-to-app fix showed Antigravity was retry-pending because WebView cookies existed but the stored Google OAuth token was unavailable.

Action taken:

- Added provider-specific Google callback token exchange in `WebLoginActivity`.
- Gemini callback now calls `GeminiCliOAuthRepository.completeAuthorization(callbackUrl)`.
- Antigravity callback now calls `AntigravityOAuthRepository.completeAuthorization(callbackUrl)`.
- The token exchange uses public-client request fields only and keeps `client_secret` out of source/APK.
- After a successful token exchange, login immediately tries `GoogleOAuthUsageBridge` collection before loading the recovery page.

Verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
git diff --check
rg -n "client_secret|clientSecret|notasecret|GOCSPX" android/app/src/main android/app/build.gradle.kts
rg -a -n "client_secret|clientSecret|notasecret|GOCSPX" android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Result:

- Focused callback tests: PASS.
- Unit tests: PASS.
- Debug APK build: PASS.
- `git diff --check`: PASS with line-ending warnings only.
- Source/APK secret scan: PASS. No matches.
- Latest APK install: PASS.
- Emulator launch: PASS.
- Runtime trusted usage verification requires the user to run Antigravity connect again so a new OAuth callback can persist tokens.

## 2026-05-27 Google Provider Hidden Collector Return-To-App Hardening

Runtime evidence after the latest user retest showed Gemini and Antigravity could still stay on the provider page or hidden collector path until the outer provider timeout, then return as retry-pending/auto-refresh-waiting.

Action taken:

- Gemini WebView collector now emits `gemini_no_trusted_payload` after bounded attempts instead of retrying until the 75-second outer timeout.
- Background hidden collectors now allow Gemini and Antigravity same-page reinjection after an early first injection. This handles Google pages that finish/stabilize after the first script evaluation.
- Google Web session Code Assist requests now try all redacted SAPISID-family credential candidates (`__Secure-3PAPISID`, `__Secure-1PAPISID`, `SAPISID`, `APISID`) across the existing request-origin profiles.
- Logs include only credential labels, status codes, profile labels, body length, and redacted error summaries. No token/cookie/account values are logged.

Verification:

```powershell
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleWebSessionCodeAssistFetcherTest --tests com.aiquota.mobile.providers.ProviderWebCollectorScriptsTest --tests com.aiquota.mobile.providers.GoogleWebSessionBridgeSourceTest "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
.\.tmp\tools\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug "-Dkotlin.compiler.execution.strategy=in-process" --no-daemon
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Result:

- Focused tests: PASS.
- Debug APK build: PASS.
- Latest APK install: PASS.
- Gemini manual refresh: PASS for bounded return-to-app behavior. Logs showed `inject`, `reinject`, SAPISID candidate attempts, Google Identity fallback `403`, then `error provider=gemini kind=gemini_no_trusted_payload`.
- Antigravity manual refresh: PASS for bounded return-to-app behavior. Logs showed SAPISID candidate attempts, token/identity fallback failures, then `error provider=antigravity kind=antigravity_no_trusted_payload`.
- Trusted usage collection is still BLOCKED. Code Assist rejected WebView-cookie calls with `400 INVALID_ARGUMENT`/`401 UNAUTHENTICATED`; Google Identity token fallback returned `403`; legacy OAuth token exchange still cannot be used without embedding `client_secret`, which remains forbidden.
