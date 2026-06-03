# Gemini/Antigravity Store APK Comparison

Date: 2026-05-28

Target APK: `C:/Users/datell1357/Downloads/AI Usage_1.260505.7.apk`

Target APK SHA-256: `98DF39A34B233D2F3CC6FEFB9200150AD87F3F51147F81D8C850BDF5F205EBA0`

Scope: Gemini and Antigravity login, session retention, usage collection, state/storage model, and how to apply the store APK pattern to the current AI Quota app.

## Executive Summary

The store APK and the current app are not equivalent for Gemini and Antigravity.

The store APK treats both providers as provider-owned usage monitors:

- provider-specific `UsageService`
- provider-specific `UsageDataFetcher`
- provider-specific `ScriptProvider`
- provider-specific `account_data_*`, `usage_data_*`, and `script_data_*` DataStores
- collector script assets `c` and `d`
- WebView JavaScript execution as the collection surface

The current app treats Gemini and Antigravity as mixed Google OAuth/native API providers:

- `GeminiCliOAuthRepository`
- `AntigravityOAuthRepository`
- `GoogleIdentityUsageAuthorizer`
- `GoogleOAuthUsageBridge`
- `GoogleWebSessionCodeAssistFetcher`
- inline WebView JS collector paths
- direct `cloudcode-pa.googleapis.com/v1internal:*` calls
- global snapshot cache as the primary UI state

This explains the current blocker. The current app can finish Google login, but then immediately tries to turn the OAuth token or WebView cookie into a trusted usage payload by calling private Cloud Code/Code Assist endpoints. If `cloudcode-pa.googleapis.com` rejects the AI Quota OAuth project or the WebView-origin/auth combination, login falls back to `STALE` / "자동 새로고침 대기" instead of becoming connected with usage.

To match the store APK, Gemini and Antigravity should be moved away from "native API first" and into "provider WebView plus script collector first". OAuth success should only store account/token/session material. `CONNECTED` should require a trusted collector payload.

## Store APK Evidence

### Provider Login Definitions

Store APK provider definitions are in `.tmp/store-apk-2605057/jadx/sources/defpackage/z3.java`.

Antigravity starts at a Google OAuth URL with:

- client id: `1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com`
- redirect: `http://127.0.0.1:8080/callback`
- scopes: `cloud-platform`, `userinfo.email`, `userinfo.profile`
- `access_type=offline`
- `prompt=consent`

Gemini CLI starts at a Google OAuth URL with:

- client id: `681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com`
- redirect: `http://127.0.0.1:46417/oauth2callback`
- scopes: `cloud-platform`, `userinfo.email`, `userinfo.profile`
- `access_type=offline`
- `prompt=consent`
- callback matcher: `^http://127\.0\.0\.1:46417/oauth2callback.*`

The store APK source search did not find provider-level hardcoded `cloudcode-pa`, `loadCodeAssist`, `retrieveUserQuota`, or `fetchAvailableModels` strings in JADX output. The only `client_secret` hits were generic Google library classes, not Gemini/Antigravity provider code. Assets `c` and `d` are packaged as opaque asset files, not plain Kotlin/Java strings.

### Provider Service Separation

Gemini:

- `u.sage.GeminiUsageService`
- foreground channel `gemini_usage_channel`
- repository `mg1`
- usage store `kg1`
- account store `ff(..., 4)`
- provider enum `t84.t`
- service start page `about:blank`

Antigravity:

- `u.sage.AntigravityUsageService`
- foreground channel `antigravity_usage_channel`
- repository `mf`
- usage store `kf`
- account store `ff(..., 0)`
- provider enum `t84.f356u`
- service start page `about:blank`
- WebView user agent override: `antigravity`

### Provider Storage Separation

The store APK uses separate DataStores:

- `account_data_antigravity`
- `account_data_gemini`
- `usage_data_antigravity`
- `usage_data_gemini`
- `script_data_antigravity`
- `script_data_gemini`

This is materially different from the current app's single local snapshot cache plus provider-specific secure token stores.

### Script Provider Structure

Gemini:

- `jg1 extends wr`
- provider name: `GeminiScriptProvider`
- version key: `d260503`
- local asset: `assets/d`

Antigravity:

- `hf extends wr`
- provider name: `AntigravityScriptProvider`
- version key: `c260503`
- local asset: `assets/c`

The base script provider `wr` loads the local asset and base64 encodes it before use. The WebView fetcher path uses `evaluateJavascript` to inject and run script content in the WebView.

### Store APK Collection Shape

The common fetcher class `jf` constructs:

- `GeminiUsageDataFetcher` with `GeminiScriptProvider`, `account_data_gemini`, `usage_data_gemini`, and provider id `gemini-cli`
- `AntigravityUsageDataFetcher` with `AntigravityScriptProvider`, `account_data_antigravity`, `usage_data_antigravity`, and provider id `antigravity`

The repository layer then runs the fetcher and persists a normalized usage object into the provider usage DataStore. The important architectural point is that collection is mediated by the script provider and a provider-specific service/data-store stack, not by a shared native direct API path.

## Current App Evidence

### Provider Definitions

Current app provider definitions are in `android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt`.

Gemini:

- login start URL: `https://gemini.google.com/app`
- preferred usage endpoint: `https://cloudcode-pa.googleapis.com/`
- collection kind: `NATIVE_API_WITH_WEBVIEW_FALLBACK`
- session probe URL: `https://cloudcode-pa.googleapis.com/`

Antigravity:

- login start URL: `https://antigravity.google/docs/plans`
- preferred usage endpoint: `https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels`
- collection kind: `NATIVE_API_WITH_WEBVIEW_FALLBACK`
- session probe URL: `https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels`

This already diverges from the store APK: the current app classifies both Google providers as native API-first collectors.

### OAuth Repositories

Current Gemini OAuth is in `GeminiCliOAuthRepository.kt`.

It does:

- create PKCE verifier/state
- build Google OAuth URL
- exchange authorization code at `https://oauth2.googleapis.com/token`
- persist `id_token`, `access_token`, `refresh_token`, expiry, and email
- call `https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist`
- optionally call `onboardUser`
- call `https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota`
- normalize buckets into app usage lines

Current Antigravity OAuth is in `AntigravityOAuthRepository.kt`.

It does:

- create PKCE verifier/state
- build Google OAuth URL
- exchange authorization code at `https://oauth2.googleapis.com/token`
- persist `id_token`, `access_token`, `refresh_token`, expiry, and email
- call `https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist`
- call `https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels`
- normalize model quota data

Both repositories include an optional `client_secret` from `BuildConfig`. That secret is not hardcoded directly in Kotlin source, but if a non-empty value is built into a release APK it becomes packaged app material. That does not match the observed store APK pattern.

### WebLoginActivity Login Completion

Current `WebLoginActivity.completeGoogleProviderAuthorization` does this:

1. Flush cookies.
2. Complete OAuth token exchange.
3. Immediately call `repository.fetchUsagePayload()`.
4. If payload exists, finish as successful.
5. If payload is missing, mark Google usage pending or navigate to recovery URL.

This is the most important current mismatch. The app tries to collect trusted usage during OAuth completion via native direct API. In the current failure mode, token exchange can succeed but the private Code Assist API rejects usage collection, so the login appears to stall or returns to `STALE`.

### Google Identity Path

The current app also has a separate `GoogleAuthorizationActivity` and `GoogleIdentityUsageAuthorizer` path using Google Play Services Identity authorization.

That path also feeds the resulting access token into `GeminiCliOAuthRepository.fetchUsagePayloadWithAccessToken` or `AntigravityOAuthRepository.fetchUsagePayloadWithAccessToken`, so it still lands on the same private Code Assist endpoints.

### WebView Bridge Path

The current app has two WebView-related Code Assist fallback paths:

1. In-page JS fetch inside `ProviderWebCollectorScripts.kt`
   - Gemini calls `fetch("https://cloudcode-pa.googleapis.com/v1internal:" + method, credentials: "include")`.
   - Antigravity calls the same origin for `loadCodeAssist` and `fetchAvailableModels`.

2. Native WebView session bridge in `GoogleWebSessionCodeAssistFetcher.kt`
   - Pulls cookies from `CookieManager`.
   - Derives SAPISID/SAPISIDHASH style auth headers.
   - Sends native `HttpURLConnection` requests to `cloudcode-pa.googleapis.com`.
   - Tries several origin/referer/auth-origin profiles.

These are still direct private API probes. They are more sophisticated than the OAuth repository, but they are not the same as the store APK's provider-specific ScriptProvider service model.

### Background Refresh

Current refresh settings:

- auto refresh interval: 90 seconds
- general provider timeout: 10 seconds
- Codex timeout: 30 seconds
- Gemini/Antigravity timeout: 75 seconds

However, `ProviderRefreshPlan.refreshModeFor` returns `NATIVE_API` for Gemini and Antigravity because they are in `GOOGLE_OAUTH_PROVIDERS`. So automatic refresh keeps entering native API collection instead of hidden WebView collector mode.

Current stale handling is already close to the desired behavior:

- automatic refresh includes `STALE` providers through foreground refresh policy
- when a provider starts from `STALE`, repeated automatic refresh failures are counted
- after 3 stale automatic failures, `ProviderSessionResetter.disconnect(provider)` is called and the snapshot becomes disconnected

## Key Differences

| Area | Store APK | Current App | Impact |
| --- | --- | --- | --- |
| Provider ownership | Dedicated `GeminiUsageService` and `AntigravityUsageService` | Shared `WebLoginActivity`, shared background service, native repositories | Current state and collection concerns are coupled |
| Login URL | Google OAuth loopback URL per provider | Provider page start URL plus generated OAuth path | Current login can reach provider page without completing usage |
| OAuth use | Appears to bootstrap account/session for provider fetcher | Token exchange is followed by immediate usage API calls | OAuth success is treated as a near-collection path |
| Collection primary path | ScriptProvider asset in WebView/service | Native direct `cloudcode-pa` first, WebView as fallback | Current app is blocked by private API/project acceptance |
| Script storage | `script_data_gemini`, `script_data_antigravity` | inline Kotlin JS plus `gemini_collector.js` asset | Harder to update independently and not store-like |
| Usage storage | `usage_data_gemini`, `usage_data_antigravity` | global `ai_quota_local_usage/provider_snapshots` | Login state and usage state can blur |
| Account storage | `account_data_gemini`, `account_data_antigravity` | token stores plus global snapshot | OAuth and WebView account/session are not cleanly separated |
| User agent | Antigravity sets WebView UA to `antigravity` | login WebView uses generic login UA; native repo uses `antigravity/1.104.0 android/arm64` | Store-like WebView identity is missing for Antigravity service collection |
| Direct API strings | No provider-level plain `cloudcode-pa` strings found | many direct `cloudcode-pa` calls | Current app depends on a private API that rejects the current project |
| Connected state | Implied by successful provider fetch and usage save | currently can become `STALE` after token succeeds but usage fails | User sees "자동 새로고침 대기" after real login |

## Why The Current Gemini Failure Happens

The current observed Gemini flow is:

1. User completes Google OAuth in WebView.
2. Callback is captured.
3. Token exchange succeeds.
4. `WebLoginActivity` immediately calls `GeminiCliOAuthRepository.fetchUsagePayload()`.
5. That calls `cloudcode-pa.googleapis.com/v1internal:loadCodeAssist`.
6. The API rejects the AI Quota OAuth project or private API access.
7. No trusted payload is produced.
8. The app marks Google usage pending (`STALE`) and starts refresh.
9. Refresh repeats the same native/private API path.

So the blocker is not just callback handling, PKCE, or waiting time. The blocking dependency is the direct private Code Assist usage API path.

## Application Plan

### Phase 1: Stop Native Direct API From Being The Login Gate

Change `WebLoginActivity.completeGoogleProviderAuthorization` so OAuth completion only:

1. verifies callback code/state,
2. exchanges and stores token material if still needed,
3. flushes cookies,
4. loads the provider collector page in the same WebView,
5. waits for trusted collector payload before finishing connected.

Do not call `GeminiCliOAuthRepository.fetchUsagePayload()` or `AntigravityOAuthRepository.fetchUsagePayload()` as the immediate success gate after OAuth token exchange.

Expected effect: login no longer returns to `STALE` solely because `cloudcode-pa` rejects the native request.

### Phase 2: Reclassify Gemini And Antigravity As WebView Collector Providers

Update the provider model so Gemini and Antigravity behave like store APK collector providers:

- Gemini: `WEBVIEW_COLLECTOR`
- Antigravity: `WEBVIEW_COLLECTOR`
- automatic refresh mode: `HIDDEN_WEB_COLLECTOR`
- Gemini hidden collector URL: `https://gemini.google.com/app`
- Antigravity hidden collector URL: `https://antigravity.google/docs/plans` or the authenticated app URL if confirmed better

Native Code Assist fetchers can remain as diagnostic/fallback code, but they should not be the first path and should not decide login success.

### Phase 3: Introduce Store-Like ScriptProvider Boundaries

Create explicit script provider boundaries:

- `GeminiScriptProvider`
- `AntigravityScriptProvider`
- local asset fallback
- optional remote script source
- script metadata/version store

Move the inline Gemini/Antigravity collector code out of `ProviderWebCollectorScripts.kt` into provider assets, mirroring store APK's `assets/d` and `assets/c` model. The current `gemini_collector.js` already moves part of Gemini logic into an asset, but Antigravity is still inline and both providers still have inline `cloudcode-pa` direct fetch logic.

### Phase 4: Make The Collector WebView-First

The collector should first observe real provider page behavior:

1. install fetch/XHR hooks,
2. capture provider page state such as `__NEXT_DATA__`, app bootstrap state, or relevant globals,
3. capture provider network responses made by the page itself,
4. parse usage/quota/plan rows,
5. normalize only if trusted usage lines exist.

Direct synthetic calls to `cloudcode-pa.googleapis.com` from native or injected JS should move behind the WebView-observed data path. If retained, they should be fallback only and should not change auth state by themselves.

### Phase 5: Split Provider Account, Usage, And Script State

For store-like behavior, add provider-specific stores or store adapters:

- `account_data_gemini`
- `usage_data_gemini`
- `script_data_gemini`
- `account_data_antigravity`
- `usage_data_antigravity`
- `script_data_antigravity`

The current `LocalUsageRepository` can still feed the dashboard and widgets, but it should become the display cache, not the only source of truth for account/session/script/usage state.

### Phase 6: Preserve Trust-First State Rules

State rules should be:

- OAuth callback captured: not connected
- token exchange stored: not connected
- WebView provider page reached: not connected
- trusted usage payload produced: `CONNECTED`
- usage unavailable but credential/session exists: `STALE`
- 3 automatic stale refresh failures: disconnect and reset provider session
- explicit token/auth failure or login redirect: interactive auth required

Current repeated stale failure handling already mostly exists and should be kept.

### Phase 7: Test Targets

Add source tests for these regressions:

1. Gemini/Antigravity OAuth callback does not immediately call native `fetchUsagePayload`.
2. Gemini/Antigravity automatic refresh resolves to hidden WebView collector mode.
3. `CONNECTED` is written only after normalized trusted payload.
4. `STALE` is allowed to refresh.
5. 3 stale refresh failures disconnect and clear provider session.
6. Release build does not package Google OAuth client secrets.
7. Antigravity collector WebView uses the store-like `antigravity` UA where applicable.

Then run emulator login tests:

1. Gemini login reaches callback.
2. Callback stores credentials and keeps/reuses WebView profile.
3. App waits for trusted usage payload instead of time-based return.
4. If usage is unavailable, state becomes `STALE` with no fake usage.
5. Three automatic stale failures disconnect.
6. Successful collector payload saves `CONNECTED`.

## Implementation Priority

1. Remove native Code Assist fetch as OAuth callback success gate.
2. Force Gemini/Antigravity automatic refresh into hidden WebView collector mode.
3. Move Antigravity collector script into an asset-backed ScriptProvider.
4. Move Gemini's remaining inline `cloudcode-pa` collection logic behind asset/provider boundaries.
5. Add provider-specific stores or adapters.
6. Make native `cloudcode-pa` fetch diagnostic/fallback only.
7. Verify on emulator with real Google login and real usage payload.

## Expected Outcome

This should solve the current class of blockers where login succeeds but the app falls back to `자동 새로고침 대기` because `cloudcode-pa.googleapis.com` rejects direct native usage collection.

It will not magically produce usage if Gemini/Antigravity pages do not expose any trusted usage data to WebView JavaScript. The store APK hides that collector logic in assets `c` and `d`, so the implementation still needs emulator-driven iteration to discover the real provider page/network payloads. The important change is that failures will be failures of the WebView collector, not failures caused by the app's own native/private API gate.

## Current Recommendation

Do not spend more time trying to fix Gemini/Antigravity by adding DPoP, enabling `cloudaicompanion.googleapis.com`, or making more native `cloudcode-pa` request profile variants.

The next engineering move should be structural:

- Gemini and Antigravity become WebView collector providers.
- OAuth is a bootstrap step only.
- ScriptProvider assets become the collection boundary.
- Trusted usage payload is the only connected signal.
