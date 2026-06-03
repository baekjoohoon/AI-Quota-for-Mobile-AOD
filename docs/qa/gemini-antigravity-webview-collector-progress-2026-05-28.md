# Gemini/Antigravity WebView Collector Progress

Read this file first after any context compaction or session handoff.

Date started: 2026-05-28

Primary plan: `docs/qa/gemini-antigravity-store-apk-comparison-2026-05-28.md`

## Current Objective

Make Gemini and Antigravity follow the store-APK-like WebView + ScriptProvider collector direction instead of using native direct `cloudcode-pa.googleapis.com` collection as the login/refresh success gate.

## Ground Rules

- Keep `CONNECTED` trust-first: only a trusted normalized usage payload can mark the provider connected.
- OAuth callback/token exchange can store credentials, but must not be treated as usage success.
- Gemini/Antigravity usage collection should prefer WebView collector execution.
- Native `cloudcode-pa` calls can remain as diagnostics/fallback, but must not decide login success or automatic refresh mode.
- Do not introduce fake usage values.
- Do not package Google OAuth client secrets into release APKs.

## Work Log

### 2026-05-28

- Created comparison report at `docs/qa/gemini-antigravity-store-apk-comparison-2026-05-28.md`.
- Confirmed store APK uses provider-specific Gemini/Antigravity services, script providers, and `account_data_*` / `usage_data_*` / `script_data_*` stores.
- Confirmed current app routes Gemini/Antigravity login completion through native OAuth repositories and direct `cloudcode-pa.googleapis.com/v1internal:*` calls.
- Started implementation from Phase 1 and Phase 2:
  - remove native Code Assist fetch as OAuth callback success gate,
  - force Gemini/Antigravity automatic refresh into hidden WebView collector mode,
  - keep trusted payload as the only connected signal.
- Added source and unit regression tests before implementation:
  - OAuth callback must recover into the same WebView collector instead of calling native `fetchUsagePayload()`.
  - Gemini/Antigravity refresh jobs must use hidden WebView collector mode.
  - Antigravity must have an asset-backed ScriptProvider boundary.
  - Google WebView collector path must inspect observed WebView data before synthetic `cloudcode-pa` fallback.
- Changed Gemini/Antigravity provider definitions to `WEBVIEW_COLLECTOR` and hidden collector URLs:
  - Gemini: `https://gemini.google.com/app`
  - Antigravity: `https://antigravity.google/docs/plans`
- Changed `WebLoginActivity.completeGoogleProviderAuthorization`:
  - token exchange can store credentials,
  - it no longer calls repository-native usage fetch as the login success gate,
  - it recovers to the provider WebView collector after callback handling.
- Added `android/app/src/main/assets/antigravity_collector.js` and wired `ProviderCollectorAssets`.
  - This is a boundary/fallback step only; existing inline Antigravity collector still remains until full extraction.
- Changed `GoogleOAuthUsageBridge.bridgeUsagePayload` so WebView session cookies are attempted before stored OAuth/Identity credentials.
- Changed Gemini/Antigravity injected collectors so observed page/network data is checked before synthetic `cloudcode-pa` web fetch or native bridge fallback.
- Confirmed release variant generated `BuildConfig` keeps both Google OAuth client secret fields empty.
- Added `ProviderScopedStateRepository` and connected it from `LocalUsageRepository`.
  - The global snapshot cache still feeds UI/widgets.
  - Provider-specific mirrors are now written to `account_data_*`, `usage_data_*`, and `script_data_*` SharedPreferences.
- Runtime/log review after user retest showed the remaining auto-refresh-waiting state was caused by wrong collector entry URLs:
  - Gemini was using `https://gemini.google.com/app` as the hidden collector start URL.
  - Antigravity was using `https://antigravity.google/docs/plans`, which produced `antigravity_no_trusted_payload`.
  - Logcat also showed `cloudcode-pa` fallback CORS/400/403 failures, so those calls still cannot be trusted as collection success.
- Updated Google provider recovery/refresh to store-APK-style `about:blank` collector starts:
  - Gemini callback recovery: `about:blank`
  - Antigravity callback recovery: `about:blank`
  - Gemini hidden refresh: `about:blank`
  - Antigravity hidden refresh: `about:blank`
  - Removed `antigravity.google/docs/plans` from main runtime source.

## Checklist

- [x] Add failing source tests for callback behavior and refresh mode.
- [x] Change `WebLoginActivity.completeGoogleProviderAuthorization` so token exchange stores credentials, then recovers to WebView collector instead of calling native usage fetch.
- [x] Change `ProviderRefreshPlan` so Gemini/Antigravity automatic refresh jobs use `HIDDEN_WEB_COLLECTOR`.
- [x] Verify Google providers still become `STALE`/usage pending when collector cannot produce trusted usage.
- [x] Run focused source tests.
- [x] Record verification output here.
- [ ] Extract remaining Gemini/Antigravity inline collector logic into provider assets or script-provider-backed modules.
- [x] Add provider-specific account/usage/script store adapters.
- [ ] Run emulator login/collection evidence for Gemini and Antigravity after `about:blank` collector-entry patch.

## Current Findings

- Current blocker is not primarily PKCE/callback. Callback can be captured and token exchange can succeed.
- Current blocker is native/private Code Assist collection after token exchange.
- Existing stale failure handling already disconnects after 3 automatic stale refresh failures.
- After the current patch, native/private Code Assist is no longer the callback gate or automatic refresh mode for Gemini/Antigravity.
- `cloudcode-pa` calls still exist as fallback/diagnostic paths inside the collector and repositories. They are not fully removed yet.
- Provider-specific account/usage/script stores now exist as mirrors. They are not yet the only source of truth; the display cache remains `ai_quota_local_usage/provider_snapshots`.
- `antigravity.google/docs/plans` was confirmed to be a bad collector/recovery URL. It is now removed from main runtime source.
- `about:blank` now triggers and accepts Gemini/Antigravity collector payloads, matching the store APK service-start shape more closely.

## Open Questions

- Gemini page may not expose quota data unless its own network requests reveal usable payloads.
- Antigravity authenticated collector URL may need adjustment after emulator evidence.

## Verification Log

- RED: focused Gradle tests failed before implementation as expected:
  - `GoogleProviderOAuthCallbackHandlingTest.googleProviderBridgeReportsOauthAndWebSessionDiagnosticsSeparately`
  - `ProviderWebCollectorScriptsTest.googleCollectorsInspectObservedWebViewDataBeforeSyntheticCodeAssistFetch`
- GREEN: focused Gradle tests passed:
  - `:app:testDebugUnitTest --tests com.aiquota.mobile.providers.GoogleProviderOAuthCallbackHandlingTest.googleProviderBridgeReportsOauthAndWebSessionDiagnosticsSeparately --tests com.aiquota.mobile.providers.ProviderWebCollectorScriptsTest.googleCollectorsInspectObservedWebViewDataBeforeSyntheticCodeAssistFetch`
- GREEN: provider-focused Gradle suite passed:
  - `ProviderRefreshPlanTest`
  - `GoogleProviderOAuthCallbackHandlingTest`
  - `ProviderScriptProviderTest`
  - `ProviderRefreshQueuePolicyTest`
  - `ProviderWebCollectorScriptsTest`
- GREEN: focused Node source tests passed:
  - `Google provider OAuth callback returns to WebView collector instead of native usage gate`
  - `Gemini and Antigravity automatic refresh use hidden WebView collector jobs`
  - `Antigravity collector has an asset-backed ScriptProvider boundary`
- GREEN: release secret regression test passed:
  - `GoogleProviderOAuthCallbackHandlingTest.releaseBuildDoesNotPackageGoogleOAuthClientSecrets`
- GREEN: provider-scoped store adapter regression test passed:
  - `ProviderScriptProviderTest.providerScopedStateRepositoryMirrorsStoreApkStyleStores`
- GREEN: debug APK build passed:
  - `:app:assembleDebug`
- GREEN: emulator install/launch smoke passed:
  - `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`
  - `adb shell monkey -p com.aiquota.mobile 1`
  - resumed activity: `com.aiquota.mobile/.MainActivity`
- GREEN: `about:blank` collector-entry patch verification passed:
  - focused Gradle RED tests failed first, then passed after implementation.
  - `ProviderRefreshPlanTest`
  - `ProviderLoginStrategyTest`
  - `ProviderWebCollectorScriptsTest`
  - `ProviderDefinitionRegistryTest`
  - `GoogleProviderOAuthCallbackHandlingTest`
  - focused Node source tests for Google callback/refresh/script boundary passed.
  - `:app:assembleDebug` passed.
  - debug APK reinstalled successfully with `adb install -r`.
- PARTIAL: `:app:assembleRelease` reached release BuildConfig generation and confirmed:
  - `ANTIGRAVITY_OAUTH_SECRET = ""`
  - `GEMINI_OAUTH_SECRET = ""`
  - full assemble stopped at `:app:validateSigningRelease` because `android/keystores/ai-usage-upload.jks` is missing.
- KNOWN FAIL: full `node --test test/mobile-source.test.js` still fails on unrelated existing source-suite issues:
  - missing `android/app/src/main/java/com/aiquota/mobile/sync/Models.kt`
  - `attachBaseContext(newBase: ctx)` regex mismatch
  - iOS `Continue with Google` regex mismatch
  - missing `android/app/src/main/java/com/aiquota/mobile/ads/AdConsentManager.kt`
- CHECK: `git diff --check` reports only existing line-ending warnings for `01_PRD.md`, `02_DATA_MODEL.md`, and `03_PHASES.md`.

## 2026-05-28 Follow-up: Store OAuth Client Alignment

- User retest confirmed the separate Google/Gemini page no longer stays open after login, but usage collection still produced no trusted payload.
- Runtime evidence after the previous collector patches:
  - WebView cookies existed, but `cloudcode-pa.googleapis.com` web-session bridge still returned `400 INVALID_ARGUMENT` / `Origin doesn't match Host for XD3` or `401 UNAUTHENTICATED`.
  - Native OAuth fallback with the AI Quota custom Desktop client reached `cloudcode-pa` but returned `403 PERMISSION_DENIED` with `SERVICE_DISABLED` for project `248131081430`.
  - This means the remaining failure was not login callback timing. The custom OAuth client project was not accepted for the private Cloud Code API used by the Gemini/Antigravity quota endpoints.
- Changed Gemini/Antigravity OAuth client IDs back to the store-APK-discovered public installed clients:
  - Gemini: `681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com`
  - Antigravity: `1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com`
- Added client-scoped credential invalidation:
  - stored OAuth tokens now save `oauth_client_id`;
  - existing custom-client tokens without the matching client id are cleared automatically;
  - this prevents old AI Quota custom-client tokens from continuing to hit `SERVICE_DISABLED`.
- Added collector handling so a bridge diagnostic that says the OAuth login must be refreshed is surfaced as `auth_required`, instead of looping as no-trusted-payload.
- Installed the new debug APK on the emulator.
- Emulator verification after install:
  - logs showed `clearing Gemini OAuth tokens from previous client`;
  - logs showed `clearing Antigravity OAuth tokens from previous client`;
  - provider snapshots moved Gemini/Antigravity out of `COLLECTING`/`REFRESHING` into `DISCONNECTED`/`IDLE` after timeout recovery.
- Next required manual step:
  - Gemini and Antigravity must be logged in again, because the OAuth client IDs changed and the old custom-client tokens were intentionally invalidated.
  - After that new login, verify whether `loadCodeAssist` / `retrieveUserQuota` and `fetchAvailableModels` return trusted payloads with the store public clients.

## 2026-05-28 Follow-up Verification

- RED: focused OAuth repository tests failed after changing expected test IDs and adding client-scoped credential expectations:
  - `GeminiCliOAuthRepositoryTest.authorizationUrlUsesGeminiCliPublicOAuthClient`
  - `GeminiCliOAuthRepositoryTest.tokenRequestsUseGeminiCliPublicClientWithoutClientSecretByDefault`
  - `GeminiCliOAuthRepositoryTest.storedCredentialsAreScopedToCurrentGeminiCliClient`
  - `AntigravityOAuthRepositoryTest.authorizationUrlUsesAntigravityOAuthClientAndLocalCallback`
  - `AntigravityOAuthRepositoryTest.tokenRequestsUseAntigravityPublicClientWithoutClientSecretByDefault`
  - `AntigravityOAuthRepositoryTest.storedCredentialsAreScopedToCurrentAntigravityClient`
- GREEN: focused OAuth repository tests passed after implementation:
  - `:app:testDebugUnitTest --tests com.aiquota.mobile.providers.GeminiCliOAuthRepositoryTest --tests com.aiquota.mobile.providers.AntigravityOAuthRepositoryTest`
- RED then GREEN: collector script test for OAuth-client-refresh-to-auth-required behavior:
  - `ProviderWebCollectorScriptsTest.googleCollectorsSurfaceOauthClientRefreshAsAuthRequired`
- GREEN: provider-focused Gradle suite passed:
  - `ProviderRefreshPlanTest`
  - `ProviderLoginStrategyTest`
  - `ProviderWebCollectorScriptsTest`
  - `GoogleProviderOAuthCallbackHandlingTest`
  - `GoogleWebSessionBridgeSourceTest`
  - `GoogleApiErrorDiagnosticsTest`
  - `GeminiCliOAuthRepositoryTest`
  - `AntigravityOAuthRepositoryTest`
- GREEN: debug APK build passed:
  - `:app:assembleDebug`
- GREEN: debug APK reinstall passed:
  - `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`
