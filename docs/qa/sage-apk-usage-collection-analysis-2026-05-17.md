# Sage APK usage collection analysis

Date: 2026-05-17

## Source APK

- Package: `u.sage`
- Version: `1.260505.7` (`26050507`)
- Unpacked working copies used during analysis:
  - `.tmp/sage-apk.zip`
  - `.tmp/sage-apk-unpack`
  - `.tmp/u-sage-jadx/sources`

## Confirmed collection architecture

The APK does not call provider public APIs with user-supplied provider API keys.
It keeps provider login state inside an Android WebView and injects provider-specific JavaScript into that authenticated local session.
The injected script reads same-origin page state, storage, and network responses, then returns a structured usage payload through Android JavaScript bridges.

High-level flow:

1. Provider login opens in a WebView.
2. A provider script is loaded from Firebase Remote Config, with an encrypted asset fallback bundled in `assets/a` through `assets/e`.
3. The script runs inside the authenticated provider origin.
4. The script scans local browser session data and same-origin provider responses for plan, usage, remaining quota, reset time, and related metadata.
5. The script sends a compact JSON response through bridges such as `SAGE1`, `SAGE10`, `SAGE11`, and `SAGE12`.
6. Kotlin parses that JSON into a usage model and stores it locally.

## Remote Config mapping

The Remote Config keys are versioned provider script keys, not names such as `claude_usage`.

| Key | Provider found in APK | Asset fallback |
| --- | --- | --- |
| `a260503` | Claude | `assets/a` |
| `b260503` | Copilot | `assets/b` |
| `c260503` | Antigravity | `assets/c` |
| `d260503` | Gemini | `assets/d` |
| `e260503` | Codex | `assets/e` |

The APK also contains provider usage identifiers such as `claude_usage` and `codex_usage`, but those are not the Remote Config script parameter names.

## Script storage and encryption

Relevant APK classes observed in JADX output:

- `wr`: base remote/default script provider.
- `o50`: Claude script provider, key `a260503`.
- `yh0`: Copilot script provider, key `b260503`.
- `jg1`: Gemini script provider, key `d260503`.
- `h70`: Codex script provider, key `e260503`.
- `ke3`: DataStore holders, including `script_data`, `script_data_copilot`, `script_data_gemini`, and `script_data_codex`.
- `u.sage.CryptoUtils`: decrypts script bytes.
- `u.sage.a`: base WebView service that registers Android JavaScript bridges and injects the decrypted script.

The encrypted payload format is:

1. Base64 string from Remote Config or fallback asset.
2. Base64-decoded byte array.
3. First 16 bytes are AES IV.
4. Remaining bytes are AES/CBC/PKCS5Padding ciphertext.
5. AES key is returned by native method `CryptoUtils.r()`.

The provided APK did not include a visible `lib/s.so`, so the native AES key was not recoverable from the static APK alone. The collection architecture and payload schema were still recoverable from Kotlin/Java bytecode and Remote Config behavior.

## Usage payload schema

The APK model uses a compact JSON shape:

```json
{
  "s": "s",
  "d": {
    "x": [
      {
        "l": "Weekly messages",
        "u": 65,
        "s": 1777593600000,
        "r": 1780272000000,
        "t": "Resets Jun 1"
      }
    ],
    "l": 1780000000000
  },
  "m": null
}
```

Observed field meaning:

- `s`: status.
- `d`: usage data.
- `x`: usage limit rows.
- `l` inside a row: label/title.
- `u` inside a row: used percentage/utilization.
- `s` inside a row: window start epoch milliseconds.
- `r` inside a row: reset epoch milliseconds.
- `t`: human-readable reset text.
- `m`: error message.

## Changes applied to this app

- Kept the same local-first WebView session model rather than requiring provider API keys.
- Made endpoint-prefixed response rows parseable as JSON, so strings like `/backend-api/accounts/... { ... }` are scanned structurally.
- Made generic browser storage keys parseable when the stored value contains usage/limit data. This covers SPA cache keys such as persisted Apollo/Redux/app-state entries where the key itself does not contain `usage` or `limit`.
- Added balanced JSON extraction for rows that contain prefix/suffix text around a JSON object or array.
- Added nested JSON-string scanning so cache values that store JSON inside JSON strings can still produce usage rows.
- Added bounded IndexedDB scanning inside the provider origin. The collector enumerates databases when supported by WebView, scans a limited number of object-store records, and emits another bridge payload when usage/limit signals are found.
- Increased the in-page network event buffer from 12 to 32 rows, so multiple provider endpoint probes survive until extraction.
- Added support for APK-style compact limit fields:
  - JavaScript scanner now treats `l` as a title and `u` as utilization.
  - Kotlin extractor now maps row `s` and `r` epoch milliseconds to `startsAt` and `resetsAt`.
- Removed generic `nav` and generic `textarea` shell markers from authenticated-login detection. Login completion now depends on stronger provider app/account/settings markers or actual usage data.
- Increased login and background collection evaluation attempts to reduce races after OAuth/WebView redirects.
- Expanded safe same-origin probes for each provider:
  - Claude now checks bootstrap, organization, profile, settings, usage, usage limit, subscription, and billing surfaces.
  - Codex now starts from `chatgpt.com`, never `platform.openai.com`, and checks ChatGPT `backend-api` account, subscription, billing, usage, limit, and message-limit surfaces.
  - Copilot now checks GitHub Copilot settings, billing, usage, usage metrics, and plan surfaces.
  - Cursor now checks dashboard pages plus same-origin account, usage, subscription, billing, settings, session, and limit endpoints.
- Broadened structured key support for remaining quota and subscription limits, including `usage_limit`, `max_messages`, `remaining_credits`, `available_credits`, `hard_limit`, `soft_limit`, and related count/credit variants.
- Cleaned login-completion heuristics so ChatGPT public prompt text alone does not count as login completion; completion now requires authenticated navigation/app markers or actual usage/plan data.

## Current limitation

Static analysis confirms the mechanism and the implemented local collector now matches the recoverable parts of that mechanism. Real provider usage availability still depends on each provider exposing quota data in the authenticated WebView session. Runtime validation is now available for the connected emulator, but live counters remain provider-dependent when an authenticated page exposes only app-shell or subscription markers.

## Runtime validation update

Date: 2026-05-17

- Connected ADB targets: `emulator-5556`.
- Locale validation: app locale `[ko-KR]`, system locale `ko-KR`.
- Build validation: `:app:testDebugUnitTest :app:assembleDebug` passed.
- Installed artifact: `android/app/build/outputs/apk/debug/app-debug.apk`.
- Claude validation: the logged-in Korean Claude app shell returned to this app and saved a connected `Pro` snapshot. Runtime responses exposed subscription plan data, but not live remaining counters in the captured session.
- Codex validation: connection opens the official Codex OAuth-style `auth.openai.com/oauth/authorize` flow rather than `platform.openai.com`. The emulator was not logged into the account during this validation, so callback token exchange and live `wham` usage fetch were validated by unit tests and callback interception code, not by real credentials.
- Copilot validation: connection opens `github.com/settings/copilot` and then GitHub sign-in when no GitHub session exists. The login page is correctly treated as incomplete, so the app does not mark Copilot connected before authentication.
- Gemini validation: the logged-in Korean Gemini app shell is detected as authenticated and returns to this app. The WebView session did not expose subscription-limit rows, so the app saves a connected fallback snapshot and shows that no local subscription limit was exposed.
- Cursor validation: connection opens `cursor.com/dashboard` and then `authenticator.cursor.sh` when no Cursor session exists. The login/authenticator page is treated as incomplete and provider endpoint probes return 404 on that auth host, so the app does not mark Cursor connected before authentication.

## Cursor return fix update

Date: 2026-05-17

- Root cause: with an authenticated Cursor session, `cursor.com/dashboard` loaded inside `WebLoginActivity` and same-origin probes returned `200` for `/api/usage` plus `/api/auth/me`, but `document.body.innerText` was empty in WebView and the Cursor usage JSON exposed `gpt-4.maxRequestUsage`-style keys that the JavaScript collector did not yet map to amount limits. The payload therefore stayed `status=e`, `authenticatedApp=false`, and the login activity did not finish.
- Fix: the local JavaScript collector now understands Cursor request usage fields such as `maxRequestUsage`, `numRequests`, `requestUsage`, and `remainingRequestUsage`.
- Fix: Cursor can now treat successful same-origin `/api/usage` or `/api/auth/me` responses as an authenticated endpoint marker when DOM text is hidden.
- Fix: structured login completion no longer rejects provider payloads solely because `textLength` is below the visible-text threshold when usage data or an authenticated marker is present.
- Validation: provider unit tests covering `ProviderLocalUsageCollector`, `ProviderLoginCompletionDetector`, and `TextUsageExtractor` pass, and full `:app:testDebugUnitTest :app:assembleDebug` passes.
- Runtime note: the latest APK was installed on `emulator-5556` with app locale `[ko-KR]` and system locale `ko-KR`. The previous logged-in WebView session could not be preserved because the emulator had an older package signature, so the final Cursor post-login return path needs to be re-run after logging in again on the freshly installed APK.
