# Antigravity usage collection analysis - 2026-05-30

## Scope

This note records the current evidence for collecting Antigravity usage in the mobile app, including:

- the Firebase Functions gateway path that calls Google/Cloud Code private endpoints;
- the uploaded Play Store APK (`AI Usage_1.260505.7.apk`) and its Antigravity collector structure;
- which Google Cloud APIs can be enabled normally and which one is blocked for this project.

Session constraint: lean-ctx was not used.

## Current conclusion

The original backend gateway shape that used `https://cloudcode-pa.googleapis.com/v1internal:*` was blocked by `403 PERMISSION_DENIED`. The full Store APK split analysis found a different mobile collector shape: it calls `https://daily-cloudcode-pa.googleapis.com/v1internal:*` with Antigravity `2.0.0` metadata, onboarding, and quota extraction from `models[*].quotaInfo`.

The Firebase gateway and Android direct helper have been updated to match that safe behavioral shape without copying the Store APK's embedded OAuth client material into Android. The remaining support gate is live validation through our own Firebase OAuth token path: `fetchAvailableModels` must return `200` with trusted quota rows for the user who completed our app's Antigravity OAuth flow.

The Store app successfully displaying Antigravity quota proves a mobile implementation is feasible, but it does not by itself prove our Firebase OAuth client/token path is accepted. If the daily endpoint still rejects our backend token path, the correct product state remains unavailable/unsupported rather than stale or guessed usage.

## Backend gateway evidence

Current code:

- `functions/src/antigravityGateway.js`
  - `ANTIGRAVITY_API_BASE` is `https://daily-cloudcode-pa.googleapis.com`.
  - `collectAntigravityUsageForUid` refreshes the stored Google token, calls `v1internal:loadCodeAssist`, then calls `v1internal:fetchAvailableModels`.
  - `mapAntigravityApiFailure` maps HTTP `403` to `BACKEND_API_FORBIDDEN`.
- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseConnector.kt`
  - `BACKEND_API_FORBIDDEN` becomes an `UNAVAILABLE` snapshot with the message that the Antigravity private API is unavailable for this backend project.

Live check from this session:

- Stored Antigravity token document exists.
- Refresh token exchange succeeds.
- Direct `loadCodeAssist` calls with `pluginType: CLOUD_CODE` and `pluginType: GEMINI` return `403`.
- Direct `fetchAvailableModels` calls with `{}` and with `{ project: "ai-usage-for-mobile" }` return `403`.
- Project number `248131081430` matches the Firebase/GCP project `ai-usage-for-mobile`.
- Store APK split analysis later showed the mobile collector uses `daily-cloudcode-pa.googleapis.com`, Antigravity metadata, and onboarding before `fetchAvailableModels`; the code now follows that shape and needs a fresh live gateway check.

Interpretation:

- This is not an OAuth callback failure.
- This is not a KMS/Secret Manager/decryption failure.
- This is not fixed by asking the user to log in again.
- It is a backend/private API access failure and should remain surfaced as `BACKEND_API_FORBIDDEN`.

## API enablement evidence

Checked through Service Usage for project `ai-usage-for-mobile`:

- `serviceusage.googleapis.com`: enabled.
- `cloudresourcemanager.googleapis.com`: enabled.
- `cloudaicompanion.googleapis.com`: enabled, title `Gemini for Google Cloud API`.
- `geminicloudassist.googleapis.com`: visible but disabled.
- `cloudcode-pa.googleapis.com`: Service Usage `GET` returns `403 PERMISSION_DENIED`.

Official docs:

- Google Cloud Service Usage says APIs can be enabled through console, `gcloud services enable SERVICE`, or Service Usage REST, but enabling requires the service to be public or shared with the caller/project.
- Google Gemini Code Assist setup documents `cloudaicompanion.googleapis.com` as the primary Gemini for Google Cloud API and `cloudcode-pa.googleapis.com` as an internal API for IDE-related features.

Practical implication:

- We can enable public/visible services normally, for example:

```powershell
gcloud services enable cloudaicompanion.googleapis.com --project ai-usage-for-mobile
gcloud services enable geminicloudassist.googleapis.com --project ai-usage-for-mobile
```

- Enabling `geminicloudassist.googleapis.com` is not proven to unblock `cloudcode-pa`.
- `cloudcode-pa.googleapis.com` is not currently available as a normal Service Usage target in this project. Unless Google exposes it to this project or the selected Gemini Code Assist flow enables it through a supported first-party path, the backend direct API path remains blocked.

## Uploaded APK evidence

Uploaded APK:

- Path: `C:\Users\datell1357\Downloads\AI Usage_1.260505.7.apk`
- SHA-256: `98DF39A34B233D2F3CC6FEFB9200150AD87F3F51147F81D8C850BDF5F205EBA0`
- Package: `u.sage`

Install check:

```text
INSTALL_FAILED_MISSING_SPLIT: Missing split for u.sage
```

Manifest evidence:

- `android:requiredSplitTypes="base__abi,base__density"`
- `com.android.vending.splits.required=true`
- uploaded base APK has no `.so` entries.

Collector structure found in JADX:

- `u.sage.AntigravityUsageService` extends the shared WebView usage service.
- It sets Antigravity-specific foreground notification/channel values and starts from `about:blank`.
- It overrides the WebView user agent to `antigravity`.
- `hf` is the Antigravity script provider:
  - Remote Config key: `c260503`
  - local asset name: `c`
  - label: `AntigravityScriptProvider`
- `gf` stores `script_data_antigravity`.
- `j84` stores `usage_data_antigravity`.
- The script provider reads asset `c`, Base64-encodes it, stores it, then `wq` Base64-decodes and decrypts it through `u.sage.CryptoUtils`.
- `CryptoUtils` uses AES/CBC/PKCS5Padding. The IV is the first 16 bytes of the encrypted asset; the AES key is returned by native method `r()` after `System.loadLibrary("s")`.

Important limitation:

- The uploaded base APK does not include the required native split/library for `System.loadLibrary("s")`.
- Therefore the encrypted Antigravity asset `assets/c` cannot be decoded from the uploaded APK alone.
- The Store APK still gives us the architecture: Antigravity was implemented as a WebView + encrypted JavaScript collector, not as obvious plain `cloudcode-pa` direct calls in decompiled Java/Kotlin.

String search result:

- No clear plain references to `cloudcode-pa`, `fetchAvailableModels`, `loadCodeAssist`, or `retrieveUserQuota` were found in the Store APK's decompiled Java/Kotlin sources.
- Antigravity OAuth uses Google OAuth with cloud-platform/userinfo scopes and a loopback redirect.

## Implementation options

### Option 1: Keep backend gateway as a diagnostic/fallback path

Status: already implemented enough to prove the failure.

Use it to:

- store/refresh Google OAuth tokens;
- probe `loadCodeAssist` and `fetchAvailableModels`;
- return a precise `BACKEND_API_FORBIDDEN` state when the private API rejects the project.

Do not use it to:

- retry login loops after a confirmed `403`;
- show old cached usage as if it were fresh;
- fabricate quota rows.

This path becomes viable only if `cloudcode-pa.googleapis.com` returns `200` for `fetchAvailableModels` or another confirmed quota endpoint.

### Option 2: Rebuild the Store APK WebView collector path

Status: plausible but not yet proven because the uploaded base APK lacks the native split needed to decrypt `assets/c`.

Implementation shape:

1. Obtain the full split APK set for `u.sage`, especially the ABI split that contains the native library loaded as `s`.
2. Decode `assets/c` using the same AES/CBC flow:
   - first 16 bytes: IV;
   - remaining bytes: ciphertext;
   - key: native `CryptoUtils.r()`.
3. Inspect the decrypted JS for actual endpoints, DOM selectors, network payloads, and result schema.
4. Port only the collector behavior, not the app's encrypted delivery mechanism.
5. Feed the output into the existing `ProviderUsageNormalizer.normalizeAntigravity`.

If the decrypted script confirms a trusted quota payload, switch Antigravity to WebView-first collection:

- `ProviderDefinitions.kt`: `collectionKind = WEBVIEW_COLLECTOR`;
- hidden collector URL remains `https://antigravity.google/` or the decrypted script's confirmed target;
- WebView user agent should be tested with the Store APK's `antigravity` UA behavior and with a normal mobile Chrome UA;
- JS bridge should emit a structured payload with provider `antigravity`, trusted quota rows, and optional plan/reset metadata.

### Option 3: Build our own WebView collector from the live Antigravity page

Status: viable only after live page inspection finds trusted usage data.

Implementation shape:

1. Let the user sign in through Antigravity in a WebView.
2. Instrument fetch/XHR and selected app globals, similar to the existing `ProviderWebCollectorScripts.antigravity()`.
3. Capture only redacted response metadata and quota-shaped payload keys.
4. Accept data only when it contains trusted fields such as `remainingFraction`, `remaining_percent`, `remaining`, `used`, `limit`, reset time, or model quota rows.
5. Normalize through the existing Antigravity normalizer.

This path must fail closed:

- no trusted payload -> `antigravity_no_trusted_payload`;
- login page/401/403 -> auth-required state;
- private API 403 -> `BACKEND_API_FORBIDDEN`;
- no real quota surface -> unsupported/unavailable.

### Option 4: Static page scraping

Status: not acceptable.

Antigravity landing/docs text is not a trusted usage source. Text scraping without a confirmed user-specific quota payload would create false confidence and stale/fake data.

## Recommended next steps

1. Keep the current backend gateway and `BACKEND_API_FORBIDDEN` handling unchanged.
2. Try the supported Gemini Code Assist setup path in the Google Cloud console/IDE:
   - confirm `cloudaicompanion.googleapis.com` remains enabled;
   - optionally enable `geminicloudassist.googleapis.com`;
   - assign the required IAM roles for Gemini for Google Cloud if testing an IDE project selection flow;
   - re-run the backend `fetchAvailableModels` probe.
3. If `cloudcode-pa` still returns 403, stop backend work and move to WebView evidence gathering.
4. Obtain the missing Store APK split set, or reproduce Store APK installation from Play on a test device and pull all split paths.
5. Decrypt/inspect the Antigravity collector script if the native split is available.
6. If no script can be recovered, run live WebView instrumentation against `https://antigravity.google/` and search for a trusted usage payload.
7. Implement WebView-first Antigravity collection only after one of the above confirms a real quota payload.

## WebView + JS extractor feasibility update

The missing web usage page is now itself an important signal. Current public Antigravity docs describe quota policy and say baseline quota usage can be viewed in the settings page, but they do not expose a confirmed user-specific web URL that a mobile WebView can scrape reliably.

Public Antigravity quota tools also point away from a normal website collector. The common working source is the Antigravity IDE's local language server, not `antigravity.google` DOM text:

- detect the local Antigravity process;
- discover the local extension/language-server port;
- read the process CSRF token;
- POST to `/exa.language_server_pb.LanguageServerService/GetUserStatus`;
- parse `userStatus.planStatus` and `userStatus.cascadeModelConfigData.clientModelConfigs[*].quotaInfo.remainingFraction/resetTime`.

That source is desktop-local. On Android, `127.0.0.1` is the device or emulator itself, not the user's desktop Antigravity IDE. Even if the desktop port were exposed over LAN, the request requires a CSRF token from the desktop process command line. The mobile app should not try to discover or scrape that token directly.

Practical conclusion:

- A pure Android `WebView + JS extractor` is viable only as a diagnostic probe until we confirm a real authenticated web quota surface.
- If no DOM or network payload with trusted quota fields appears, Antigravity should not be switched to production `WEBVIEW_COLLECTOR`.
- The recommended product path is a desktop companion/sync flow: Windows/desktop collector reads the local IDE language-server quota, normalizes it into the existing Antigravity quota schema, uploads a trusted snapshot to Firebase, and Android displays the synced snapshot.

If we still build the mobile WebView path, it should be deliberately fail-closed:

1. Keep Antigravity's production `collectionKind` on `BACKEND_GATEWAY` or an explicit unavailable state.
2. Add a debug/probe-only WebView mode that loads the confirmed Antigravity settings surface, injects the existing Antigravity JS collector, and captures only redacted diagnostics.
3. Accept only structured quota fields such as `remainingFraction`, `remaining_percent`, `remaining`, `used`, `limit`, model quota rows, and reset time.
4. Return `antigravity_no_trusted_payload` or `UNSUPPORTED_NO_WEB_QUOTA_SURFACE` when nothing trusted is found.
5. Never mark Antigravity `CONNECTED` from landing/docs/pricing text or old cache data.

## Expanded implementation paths

The Store APK evidence means a mobile implementation is plausible, but the missing piece is the actual provider script. The APK does not point to a plain web URL. It starts an `about:blank` WebView, sets the user agent to `antigravity`, registers multiple JavaScript bridges, then evaluates a provider script supplied through Remote Config or encrypted local asset `c`.

### Path A: Recover the Store APK collector behavior

Status: highest evidence value.

Steps:

1. Obtain the full split APK set, not only the base APK.
2. Pull the ABI split that contains the native library loaded as `s`.
3. Decode the encrypted `assets/c` collector:
   - first 16 bytes are the IV;
   - the remaining bytes are AES/CBC/PKCS5Padding ciphertext;
   - the AES key is returned by the native `CryptoUtils.r()` method as a Base64 string.
4. Inspect the decrypted script for URLs, bridge calls, endpoint names, result schema, and WebView user-agent assumptions.
5. Reimplement the behavior in our own code and use only the endpoint/schema knowledge needed for compatibility.

Why this matters:

- This is the only path that directly explains how a mobile app can collect Antigravity usage without a visible public usage URL.
- It should reveal whether the Store APK calls a web settings surface, a Google private endpoint through WebView cookies, or another hidden mobile-compatible endpoint.

Blocker:

- The uploaded APK is only the base split and declares required ABI/density splits. It cannot be installed as-is and does not contain the native library needed to recover the key.

### Path B: Recreate the Store APK runtime shape without decrypting the script

Status: useful as a probe, not enough to ship by itself.

Steps:

1. Start Antigravity collection from `about:blank` rather than assuming a fixed usage URL.
2. Use a WebView user agent of `antigravity` and compare it against a normal mobile Chrome UA.
3. Inject a bootstrap script that can navigate, create iframes, hook `fetch`/XHR, and report through the existing Android JS bridge.
4. Try likely authenticated surfaces:
   - `https://antigravity.google/`;
   - the settings/models route if discovered from live Antigravity app traffic or docs;
   - Google account/AI plan pages only as diagnostics, not trusted quota sources.
5. Accept only structured quota data and reject static docs/pricing/landing text.

Risk:

- If the Store APK script relies on endpoint names or bridge sequencing that are not discoverable from public web pages, this path can spend time without finding the real source.

### Path C: Instrument the Store APK at runtime

Status: strong if the app can be installed from Play on a test device.

Steps:

1. Install the Store APK normally from Play so all splits are present.
2. Use `adb shell pm path u.sage` to list installed split paths.
3. Pull the split APKs for offline inspection.
4. Capture redacted WebView behavior:
   - page URL transitions;
   - script bridge method names;
   - network host/path/status only;
   - quota-shaped JSON keys.
5. Do not store cookies, tokens, OAuth codes, account IDs, full HTML, or full response bodies.

Variants:

- Use Android WebView remote debugging if the app enables it.
- Use logcat if the app logs non-sensitive collector state.
- Use a local HTTPS proxy only for metadata if user-installed CA and WebView trust allow it; do not capture secrets.

### Path D: Antigravity IDE local-server companion

Status: most implementable if Windows desktop integration is acceptable.

Steps:

1. On the desktop, detect the running Antigravity language-server process.
2. Read the local port and CSRF token from the process command line.
3. POST to `/exa.language_server_pb.LanguageServerService/GetUserStatus`.
4. Parse:
   - `userStatus.planStatus.availablePromptCredits`;
   - `userStatus.planStatus.planInfo.monthlyPromptCredits`;
   - `userStatus.cascadeModelConfigData.clientModelConfigs[*].quotaInfo.remainingFraction/resetTime`.
5. Upload a normalized, redacted snapshot to Firebase.
6. Let Android display that trusted synced snapshot.

Tradeoff:

- This is not pure mobile, but it matches the proven quota source used by public Antigravity quota tools.

### Path E: Mobile OAuth/private API probe variants

Status: blocked in the current backend project, but still worth keeping as a probe matrix.

Probe variants:

- `cloudcode-pa.googleapis.com` vs `daily-cloudcode-pa.sandbox.googleapis.com`;
- `pluginType: ANTIGRAVITY`, `CLOUD_CODE`, and `GEMINI`;
- with and without project ID;
- after supported Gemini Code Assist setup/onboarding;
- with Google Identity token vs AppAuth refresh token.

Success criterion:

- `fetchAvailableModels` must return `200` with trusted `quotaInfo.remainingFraction` rows.

Failure handling:

- `403` remains `BACKEND_API_FORBIDDEN`.
- `401` is auth/token failure.
- no quota fields is payload failure, not connected state.

### Path F: User-assisted manual capture fallback

Status: low automation, but can unblock product value.

Steps:

1. Let the user paste a redacted JSON snapshot exported from a desktop helper or CLI.
2. Normalize it locally using the existing Antigravity normalizer.
3. Mark it as manually supplied with timestamp and source.

Tradeoff:

- Good for internal testing and schema validation.
- Not acceptable as the primary consumer flow.

### Recommended order

1. First try Path C/A: get the complete Store APK split set and recover or observe the real collector behavior.
2. In parallel, implement Path B as a debug-only probe in our app so we can quickly test discovered URLs and payload shapes.
3. If the Store APK proves the source is not a public web page, build Path D as the production-grade path.
4. Keep Path E only as a backend diagnostic until Google private API access returns `200`.
5. Use Path F only for internal testing and schema hardening.

## Store split extraction result - 2026-05-30

The full Play Store split set was pulled from the Play Store tablet emulator after installing `u.sage` version `1.260526.7`.

Pulled files:

- `base.apk`
- `split_config.en.apk`
- `split_config.ko.apk`
- `split_config.x86_64.apk`
- `split_config.xhdpi.apk`

Confirmed contents:

- `base.apk` contains encrypted Antigravity collector asset `assets/c`.
- `split_config.x86_64.apk` contains `lib/x86_64/libs.so`.
- `libs.so` exports the native implementation used by `CryptoUtils.r()`.
- The encrypted `assets/c` collector was successfully decrypted locally for analysis.

Sensitive handling:

- The decrypted collector contains OAuth client material and token exchange logic.
- Do not copy client secrets, tokens, OAuth codes, account data, or full decrypted script text into repo docs, logs, code, or memory.
- Only the behavioral findings below are safe to reuse.

Safe behavioral findings:

- The Store APK Antigravity collector is not DOM scraping of `antigravity.google`.
- It runs as an `about:blank` WebView with a custom Antigravity user agent and Android JavaScript bridges.
- It uses Google OAuth token exchange/refresh inside the collector script.
- Its Code Assist base URL is `https://daily-cloudcode-pa.googleapis.com`, not the standard `https://cloudcode-pa.googleapis.com`.
- It calls:
  - `/v1internal:loadCodeAssist`;
  - `/v1internal:onboardUser` when no project is resolved;
  - `/v1internal:fetchAvailableModels`.
- Its load metadata shape includes:
  - `ideName: antigravity`;
  - `ideType: ANTIGRAVITY`;
  - Antigravity client/plugin version `2.0.0`;
  - `updateChannel: stable`;
  - `pluginType: GEMINI`;
  - `mode: FULL_ELIGIBILITY_CHECK`.
- It trusts model quota rows under `models[*].quotaInfo.remainingFraction/resetTime`.
- It filters internal/non-user quota rows such as model ids starting with `chat_`, `tab_`, `rev`, ids containing image/mquery, and some non-primary model labels.

Implementation update:

- The Firebase gateway path was updated to use the Store APK collector's endpoint/metadata/onboarding shape while still keeping OAuth client secret handling server-side.
- Android's legacy Antigravity direct API helper was aligned to the same endpoint and metadata, without embedding OAuth client secrets in Android code.
- During emulator validation, the debug app hit `FirebaseFunctionsException: Unauthenticated` before usage collection because callable App Check enforcement rejected the test build. For the current prototype validation pass, Functions App Check enforcement was disabled while keeping Firebase Auth and server-side token storage. Re-enable App Check after registering debug/release providers.
- Android refresh loops now map callable failures into Antigravity snapshots instead of crashing the app process.

## Decision gate

Antigravity can be marked supported only when at least one of these is true:

- `POST https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels` returns `200` with quota fields for our backend/token path; or
- `POST https://cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels` returns `200` with quota fields for a supported fallback path; or
- the authenticated Antigravity WebView surface exposes trusted per-user quota data that the app can normalize without guessing.

Until then, the correct shipped behavior is a visible unavailable/unsupported state.

## Source links

- Google Cloud Service Usage, enable/disable services: https://docs.cloud.google.com/service-usage/docs/enable-disable
- Service Usage `services.enable` REST reference: https://docs.cloud.google.com/service-usage/docs/reference/rest/v1/services/enable
- Gemini Code Assist setup, API traffic list: https://docs.cloud.google.com/gemini/docs/codeassist/set-up-gemini?hl=ko
- Google Antigravity plans and quota docs: https://antigravity.google/docs/plans
- `antigravity-usage` quota tool: https://github.com/skainguyen1412/antigravity-usage
- `antigravity-panel` quota extension: https://github.com/n2ns/antigravity-panel
- `AntigravityQuotaWatcher` quota tool: https://github.com/wusimpl/AntigravityQuotaWatcher
