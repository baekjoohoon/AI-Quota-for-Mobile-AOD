# Policy Safe Foreground Provider Refresh Troubleshooting

Date: 2026-05-26

## Issue 1: Focused RED Test Compile Failure

Symptom: The first focused RED run stopped at Kotlin test compilation instead of reaching the intended policy failures.

Evidence:

```text
Unresolved reference 'assertTrue'
Unresolved reference 'assertFalse'
```

Cause: `ForegroundRefreshControllerTest.kt` and `ForegroundRefreshPolicyTest.kt` were missing JUnit assertion imports.

Action: Added `org.junit.Assert.assertTrue` and `org.junit.Assert.assertFalse`.

Result: The next focused run reached the intended RED failures.

## Issue 2: Missing Policy Features

Symptom: Focused RED tests failed after compilation was fixed.

Evidence:

```text
NotificationPermissionLaunchTest > liveMonitoringCopyMentionsNinetySecondsAndPinnedNotification FAILED
ProviderBackgroundRefreshServicePolicyTest > pinnedNotificationIncludesUserStopAction FAILED
ProviderBackgroundRefreshServicePolicyTest > providerRefreshServiceUsesDataSyncForegroundServiceOnly FAILED
ProviderBackgroundRefreshServicePolicyTest > providerRefreshServiceKeepsNinetySecondCadenceAndHeartbeat FAILED
ForegroundRefreshControllerTest > controllerPersistsLiveMonitoringApprovalSeparatelyFromRuntimeStartState FAILED
ForegroundRefreshPolicyTest > foregroundLoopRequiresNotificationOptInAndPermission FAILED
ForegroundRefreshPolicyTest > foregroundLoopRequiresLiveMonitoringApproval FAILED
WidgetManualRefreshActionTest > delayedProviderWidgetConfigureRefreshClearsSpinnerWhenForegroundStartFails FAILED
```

Cause: Existing code did not yet persist a separate live-monitoring approval flag, did not write heartbeat state, did not include a notification stop action, did not use typed `dataSync` foreground start, and did not clean up delayed widget refresh failure state.

Action: Implemented the minimal policy changes in controller, policy, service, notification, widget refresh, and strings.

Result: Focused policy tests passed.

## Issue 3: Claude Seed Stopped The Runtime Loop

Symptom: The first emulator live-monitoring attempt started the foreground service once, then stopped instead of showing a second 90-second cycle.

Evidence:

```text
05-26 13:24:10.248 D AIQuotaBgRefreshService: cycleStart providers=claude
05-26 13:24:10.638 D AIQuotaBgRefreshService: load provider=claude start=claude.ai/ request=1
```

State after the cycle:

```xml
<boolean name="running" value="false" />
<string name="last_failure">INTERACTIVE_AUTH_REQUIRED</string>
```

Local provider state:

```text
connectionState=INTERACTIVE_AUTH_REQUIRED
message=Background refresh returned HTTP 403.
```

Cause: The emulator did not have a real Claude login session. The hidden collector reached a main-frame HTTP 403, which is correctly classified as `INTERACTIVE_AUTH_REQUIRED`. Once the only provider became auth-required, there were no refreshable providers left, so the loop stopped.

Action: Switched runtime loop verification to a seeded Gemini snapshot with no OAuth token, which fails as `NO_TRUSTED_PAYLOAD` and keeps previous trusted usage as `STALE` instead of auth-required.

Result: Gemini seed produced two cycle starts roughly 90 seconds apart.

## Issue 4: Passive Widget Update Shell Trigger Blocked

Symptom: Emulator shell could not directly trigger passive widget update.

Evidence:

```text
cmd appwidget update: No shell command implementation.
am broadcast android.appwidget.action.APPWIDGET_UPDATE: SecurityException: not allowed to send broadcast android.appwidget.action.APPWIDGET_UPDATE from unknown caller.
```

Cause: The emulator image does not implement the `cmd appwidget update` helper, and Android protects `APPWIDGET_UPDATE` from direct shell broadcast.

Action: Kept passive widget runtime verification documented as blocked and relied on unit/source tests for this invariant.

Result: `WidgetManualRefreshActionTest.systemWidgetUpdatesRefreshCachedSurfacesWithoutStartingForegroundService` verifies the passive widget path does not start `ProviderBackgroundRefreshService`.

## Issue 5: Manual Widget Refresh Shell Trigger Blocked

Symptom: Emulator shell cannot directly invoke the manual widget refresh receiver or foreground service action.

Evidence:

```text
Error: Requires permission not exported from uid 10229
```

Cause: `WidgetManualRefreshReceiver`, `ProviderRefreshReceiver`, and `ProviderBackgroundRefreshService` are intentionally non-exported. A real widget click uses the app-created `PendingIntent`; shell cannot impersonate that path.

Action: Verified notification action through real UI tap, and kept manual widget refresh covered by unit tests.

Result: Manual widget refresh is covered by tests that assert user-action service start and spinner cleanup on start failure.

## Issue 6: Foreground Service Type Logger Warning

Symptom: During emulator live-monitoring startup, system log contained:

```text
ForegroundServiceTypeLoggerModule: Foreground service start for UID: 10229 does not have any types
```

Cause: Android logs this at the service-start boundary before or around foreground promotion. The app manifest declares `android:foregroundServiceType="dataSync"`, and production code calls typed `startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` on supported versions.

Action: Kept source/unit tests for typed foreground promotion and manifest `dataSync`; no `specialUse` type was added.

Result: Service ran and produced 90-second cycle logs. No `ForegroundServiceStartNotAllowedException` was observed during the verified live-monitoring path.

## Issue 7: Gemini/Antigravity Still Shows Error After Google Login

Symptom: User retest reported Gemini and Antigravity still reaching `오류` after Google login.

Evidence from emulator at investigation time:

```xml
connectionState=CONNECTING
refreshState=REFRESHING
message=Opening provider login
```

The Gemini OAuth secure prefs contained only encrypted `state`; no access token or refresh token was present. Logcat showed the Google sign-in page and no `oauthCallback`, `googleTokenExchange`, `googleCallbackFallback`, or `webSessionCodeAssist` lines. That means the emulator had not yet reproduced the reported post-login failure.

Cause found in code review: Recoverable Google provider failures still had generic `ERROR` routes. Main-frame WebView load/HTTP failures in `WebLoginActivity.failKeepingPrevious()`, generic repository failures in `LocalUsageRepository.failKeepingPrevious()`, and older persisted `ERROR`/`UNAVAILABLE` snapshots could keep Gemini/Antigravity visible as `오류` even when the next action should be retrying usage collection.

Action:

- Added a Google retry-pending guard in `WebLoginActivity.failKeepingPrevious()` before it writes `ERROR`.
- Added a Google retry-pending guard in `LocalUsageRepository.failKeepingPrevious()`.
- Added read-time recovery for Gemini/Antigravity recoverable `ERROR` and `UNAVAILABLE` snapshots.
- Kept explicit OAuth failure (`google_oauth_failed`) outside the recoverable path.

Result: Focused tests passed. Runtime collection still needs post-login emulator evidence from a completed Google login flow to prove trusted usage is collected.

## Issue 8: WebView Google Login Completes But Usage Collection Still Fails

Symptom: User retest still reported Gemini/Antigravity as `오류` after another login attempt.

Cause: A Google WebView login can prove the account page accepted the user, but it does not guarantee the app has a usable Code Assist access token or SAPISID-backed web session for provider API collection. The previous implementation still depended on WebView OAuth/cookie recovery first.

Action:

- Added a native Google Identity authorization path for Gemini/Antigravity.
- The app now uses Google Play Services `AuthorizationRequest` and `Identity.getAuthorizationClient(...)` to get a short-lived access token after user consent.
- No `client_secret`, `clientSecret`, `notasecret`, or offline `requestOfflineAccess` path was added.
- Usage collection falls back in order: Google Identity token, stored OAuth token, WebView cookie bridge.
- Added focused tests to lock the client-secret-free native path and separate identity/OAuth/web-session diagnostics.

Result: Focused tests passed. Full unit/build/secret scan and emulator runtime verification remain pending.

Follow-up: Full unit tests initially failed because `GoogleProviderLoginRoutingTest` still encoded the old WebView OAuth loopback requirement for Gemini/Antigravity. The test was updated to assert the new native Google authorization path and to keep the no-client-secret invariant.

Follow-up 2: Emulator runtime verification showed the native Google Identity replacement was not viable. Play Services reached account selection but failed `EXTERNAL_REAUTH_ACCOUNT`, returned `google_identity_authorization_cancelled`, and the native authorization Activity produced an ANR while closing.

Action: Removed the native Google Identity path from default provider connection. Restored the existing APK-style WebView loopback OAuth path for Gemini and Antigravity. This keeps `client_secret` out of the APK because token exchange uses only the public installed-app `client_id`.

Result: Gemini connect now opens `WebLoginActivity` with the expected `accounts.google.com/o/oauth2/v2/auth` URL and reaches the password screen. Runtime trusted usage collection remains blocked until the user completes password/2FA and consent in the emulator.

Follow-up 3: After password/consent completion, Gemini reached the loopback callback but the app attempted to exchange the authorization code and Google returned `invalid_request` with `client_secret is missing`. This confirms the observed Google OAuth client must not be used from the APK as a direct token-exchange client unless a secret is embedded, which is not acceptable.

Action: Removed the callback-time token exchange call. The WebView callback now only recovers to the provider page and lets the collector use the WebView session. Added two Code Assist bridge profiles that more closely match browser requests: provider-page `Origin` with API `X-Origin`, using both API-origin and provider-origin SAPISID hashes.

Result: Focused code changes are in place. Build/install/emulator retest is still pending.

Follow-up 4: The direct token-exchange helper was still present in source even after the callback stopped invoking it, which made regressions easy to reintroduce.

Action: Deleted the helper and added focused assertions that the login Activity contains no callback-time Google token exchange strings. Added the missing Code Assist profile where `Origin` and `X-Origin` both use the API origin but the SAPISID hash is computed with the provider origin.

Result: Focused tests pass and the latest debug APK is installed on the emulator. Post-login runtime evidence still needs a completed Google login because credentials/2FA cannot be entered by the agent.

## Issue 9: Emulator ANR With Previous Stored State

Symptom: After installing the updated APK over the previous emulator state, launching AI Quota produced an Android "app is not responding" dialog.

Evidence:

```text
ANR in com.aiquota.mobile (com.aiquota.mobile/.MainActivity)
Reason: Input dispatching timed out
```

Disabling live-monitoring prefs did not remove the ANR. Running `pm clear com.aiquota.mobile` made the app launch normally and show the notification permission prompt.

Current assessment: The ANR is tied to prior local app state/cache on the emulator, not to a clean install startup path. It is separate from the Gemini/Antigravity post-login collection bug but should be investigated before release if it can be reproduced from saved `ai_quota_local_usage` or widget cache data.

## Issue 10: WebView-Only Google Recovery Still Does Not Produce Trusted Usage

Symptom: User retest still reports Gemini/Antigravity as error after Google login.

Evidence currently available:

- Previous post-login WebView callback evidence reached `127.0.0.1` but direct code exchange failed with `client_secret is missing`.
- After removing code exchange, the remaining WebView session bridge still has no runtime proof of trusted Gemini/Antigravity payload.
- The latest emulator state before this change was invalid for post-login analysis because the APK was reinstalled while `WebLoginActivity` was open, leaving only a stale `CONNECTING` snapshot.

Cause: WebView loopback login alone is not enough unless the WebView cookie/session can call Code Assist successfully. The safe fix is to avoid APK-embedded secrets while using an Android-supported access-token path.

Action:

- Added Google Play Services Identity authorization for Gemini/Antigravity.
- Default connect now opens `GoogleAuthorizationActivity` instead of Google OAuth WebView for those two providers.
- The app requests only access tokens for `cloud-platform`, `userinfo.email`, and `userinfo.profile`; no offline access/refresh token/client secret is requested.
- `GoogleOAuthUsageBridge` now records identity/OAuth/web-session diagnostics separately.
- Usage payload failures after successful Google authorization become retry-pending stale state, not direct error.

Runtime result:

- Clean emulator launch works after `pm clear`.
- Gemini connect opens the Google Play Services account chooser for AI Quota.
- Account selection/consent is pending user action; the agent will inspect `AIQuotaGoogleIdentity`, `AIQuotaGemini`, and `AIQuotaCollector` logs after consent.

## Issue 11: Google Identity Fails With UNREGISTERED_ON_API_CONSOLE

Symptom: Gemini login reached Google Play Services account selection, then returned to the app without authorizing usage collection.

Evidence:

```text
[Authorization_flowRunner] Flow step completed: EXTERNAL_ACCOUNT_CHOOSER; successful=true.
[Authorization_flowRunner] Flow step started: EXTERNAL_REAUTH_ACCOUNT.
Unknown error [status=UNREGISTERED_ON_API_CONSOLE].
```

Cause: The debug APK installed on the emulator was signed with a different debug keystore from the SHA-1 registered in Google Cloud. Google Identity validates the Android OAuth client against the tuple:

```text
package name + signing certificate SHA-1
```

The package name was `com.aiquota.mobile`, but the signing certificate was the sandbox debug keystore, not the user's registered local debug keystore.

Action:

- Added optional local debug signing through ignored `android/local.properties`.
- Configured the workspace debug build to use `C:\Users\datell1357\.android\debug.keystore`.
- Rebuilt and verified the APK SHA-1:

```text
0F:75:68:52:32:9E:EA:93:C3:F2:4F:30:B0:DE:38:17:C7:7D:EB:9A
```

- Uninstalled the previous emulator app and installed the newly signed APK.

Result:

- Gemini now reaches Google Play Services account selection without the `UNREGISTERED_ON_API_CONSOLE` failure.
- The app waits in `CONNECTING`/`REFRESHING` while the Google account chooser is open.
- Post-consent collection remains pending because account selection/consent must be performed by the user.

Follow-up: Identity authorization stores an account/email hint, not a refresh token. `GoogleOAuthUsageBridge.hasStoredCredential(...)` now treats that Identity hint as stored Google authorization state so retry-pending handling is preserved after successful consent even without a legacy OAuth refresh token.

## Issue 12: Google Identity Account Selection ANR

Symptom: After selecting a Google account, the app became unresponsive.

Evidence:

- Google Play Services authorization completed far enough to return an authorization result.
- The app then called Gemini/Antigravity usage collection while `GoogleAuthorizationActivity` was still visible.
- Code Assist returned `403` for `loadCodeAssist`.
- Android reported activity resumed/pause timeout symptoms around the authorization Activity.

Cause: The authorization Activity was doing network quota collection before finishing. A slow or rejected Code Assist call could therefore keep the auth Activity in an unsafe lifecycle transition.

Action:

- Changed `GoogleAuthorizationActivity` so successful authorization immediately records retry-pending state, starts background collection, and calls `finish()`.
- Removed this Activity from the default Gemini/Antigravity connection route after runtime evidence showed Android Identity access tokens are rejected by Code Assist.

Result:

- The account-selection ANR path is removed from the default provider flow.
- The Activity also no longer blocks on network collection if reached by a future fallback/manual path.

## Issue 13: Stale Google Snapshot Launch ANR

Symptom: Installing over an emulator state with a retry-pending Gemini snapshot caused MainActivity ANR on launch.

Evidence:

```text
ProviderSnapshotCodec.encode(...)
LocalUsageRepository.saveSnapshots(...)
LocalUsageRepository.readSnapshots(...)
AIQuotaAppShell.refreshSnapshots(...)
```

Cause: `ProviderSnapshotCodec` demoted `STALE` snapshots without usage rows to `UNAVAILABLE`; `LocalUsageRepository` then recovered Google retry-pending `UNAVAILABLE` back to `STALE` and saved again, causing a repeated read/save loop.

Action:

- `ProviderSnapshotCodec.requireUsageForConnectedState()` now demotes only `CONNECTED` snapshots without trusted usage rows.
- `STALE` without rows remains valid retry-pending state.

Result:

- Launch over the previous retry-pending Google state no longer ANRs.
- Trust-first behavior is preserved because `CONNECTED` still requires trusted usage rows.

Follow-up hardening:

- `LocalUsageRepository.saveSnapshots(...)` now compares the newly encoded snapshot JSON with the current stored value.
- If the value is identical, it returns without writing SharedPreferences.
- This reduces the blast radius of any future cleanup rule that would otherwise wake the snapshot listener without changing user-visible state.

## Issue 14: Google Identity Token Code Assist 403

Symptom: Gemini/Antigravity login completed far enough to get a native Google Identity token, but usage remained unavailable.

Evidence:

```text
collection geminiCliOAuthUsage loadCodeAssist status=403 projectPresent=false
provider=gemini identityUsagePayload=false diagnostic=Gemini Code Assist setup was unavailable.
```

Cause: The Android Google Identity access token is accepted by Google Identity but rejected by the Code Assist internal quota endpoint. This is different from a user session expiry.

Action:

- Default Gemini/Antigravity login no longer uses Google Identity.
- The route now uses WebView OAuth/session login and WebView session cookies first, matching the older APK architecture more closely without embedding `client_secret`.

Current blocker:

- The agent cannot complete Google password/2FA/consent in the emulator.
- Runtime proof of trusted Gemini/Antigravity quota collection still requires the user to finish that login flow while logcat is monitored.

## Issue 15: Antigravity Plans Page Did Not Return To App

Symptom: Google login succeeded and the WebView reached `https://antigravity.google/docs/plans/`, but `WebLoginActivity` stayed open instead of returning to AI Quota.

Evidence:

```text
provider=antigravity resource=https://antigravity.google/docs/plans/
provider=antigravity googleUsageBridge ok=false diagnostic=antigravity_fetch_models_failed
provider=antigravity collectorMode=webview-js errorKind=antigravity_no_trusted_payload
provider=antigravity googleCollectorErrorAwaitingFallback=true
```

Cause: After the OAuth callback, Google provider collector errors were always deferred to the 75-second fallback. For Antigravity, the Plans page collector had already proven that no trusted payload was available, so waiting kept the user trapped in the login WebView.

Action:

- Removed the Google collector-error deferral branch from `WebLoginActivity`.
- If a Google provider has a stored WebView Google session or token state and the collector reports no trusted payload, the activity now marks the provider retry-pending and finishes immediately.
- Background/manual/foreground refresh can then retry without leaving the user in the Plans page.

Result:

- Antigravity Plans page no-trusted-payload is no longer treated as an in-WebView wait state.
- The provider returns to the app as retry-pending instead of staying on the documentation page.

## Issue 16: Antigravity Returned As Auto Refresh Waiting

Symptom: After the Plans-page return-to-app fix, Antigravity returned to AI Quota but showed retry-pending/auto-refresh-waiting instead of trusted usage rows.

Evidence:

```text
provider=antigravity webSessionCodeAssist method=POST status=400 profile=browser_api_x_origin error=INVALID_ARGUMENT|Bad request: Origin doesn't match Host for XD3.
provider=antigravity webSessionCodeAssist method=POST status=401 profile=api_origin error=UNAUTHENTICATED|Request is missing required authentication credential.
provider=antigravity googleUsageBridge ok=false diagnostic=antigravity_fetch_models_failed oauthDiagnostic=Antigravity Google account token was unavailable.
```

Cause: `WebLoginActivity` detected the Google loopback OAuth callback and navigated to the recovery page, but it did not exchange the authorization code through `AntigravityOAuthRepository.completeAuthorization(callbackUrl)` or `GeminiCliOAuthRepository.completeAuthorization(callbackUrl)`. WebView cookies existed, but no persisted OAuth access/refresh token existed for the Code Assist API fallback.

Action:

- `WebLoginActivity` now performs provider-specific public-client token exchange after a Google OAuth callback.
- The exchange uses the existing Gemini/Antigravity repositories and does not add `client_secret`.
- After a successful exchange, the activity attempts an immediate Google bridge collection before loading the recovery page.
- If no trusted payload is available, it still falls back to the recovery page and scheduled retry-pending path.

Result:

- Future Gemini/Antigravity OAuth callbacks can persist access/refresh tokens.
- Existing retry-pending emulator state still needs one more reconnect because the previous callback already completed without saving tokens.

Follow-up: Later runtime evidence showed the public-client exchange path still returns Google `invalid_request` with `client_secret is missing`, so this path cannot be relied on for store-safe builds. Do not reintroduce a client secret into the APK.

## Issue 17: Gemini/Antigravity Hidden Collector Waited Until Outer Timeout

Symptom: After provider pages loaded, Gemini/Antigravity could remain in the provider page or hidden collector flow and only later return as retry-pending/auto-refresh-waiting.

Evidence:

```text
provider=gemini start=gemini.google.com/app
provider=gemini inject provider=gemini url=gemini.google.com/app
provider=gemini pageFinished ... url=gemini.google.com/app
```

Initially no `postCollectorError` arrived before the outer timeout because the first hidden collector injection could happen before the Google page was stable, and the background collector allowed only one script evaluation per request/page.

Action:

- Allowed Gemini/Antigravity background collectors to reinject on the same request/page.
- Shortened Gemini collector duplicate-start guard to 3 seconds.
- Added bounded Gemini collector failure with `gemini_no_trusted_payload`.

Result:

- Gemini and Antigravity now return to retry-pending without waiting for the full outer timeout when no trusted payload is found.
- No `ForegroundServiceStartNotAllowedException`, ANR, raw token, or raw cookie appeared in the verification logs.

Remaining blocker:

```text
provider=gemini webSessionCodeAssist ... status=400 ... error=INVALID_ARGUMENT|Bad request: Origin doesn't match Host for XD3.
provider=gemini webSessionCodeAssist ... status=401 ... error=UNAUTHENTICATED|Request is missing required authentication credential.
collection geminiCliOAuthUsage loadCodeAssist status=403 projectPresent=false
provider=antigravity webSessionCodeAssist ... status=400/401 ...
collection antigravityIdentityUsage fetchAvailableModels status=403 projectPresent=false modelQuotaCount=0
```

Cause: Returning to the app is now bounded, but trusted quota collection is still blocked because Code Assist rejects all tested WebView-cookie request profiles and Android Google Identity access tokens. This is a provider collection blocker, not proof of expired user session.
