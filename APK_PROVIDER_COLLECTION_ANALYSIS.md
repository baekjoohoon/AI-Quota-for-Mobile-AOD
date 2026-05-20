# Provider Usage Collection and Normalization

Last updated: 2026-05-19

This document summarizes how AI Usage for Mobile should collect and normalize provider plan and usage data in standalone mode.
It is based on:

- APK analysis of `AI Usage_1.260505.7.apk`
- Current AI Usage for Windows provider plugins
- Local-first mobile architecture decisions

## Scope

Supported providers for this phase:

- Claude
- Codex
- Gemini
- Copilot
- Cursor

Not in scope:

- Antigravity
- Firebase sync
- Pairing code
- Server-side token relay
- Cloud Functions or Secret Manager

The mobile app should run standalone. Provider sessions, cookies, OAuth credentials, and usage snapshots must stay on the Android device.

## Common Architecture

Use the same provider pipeline for every provider:

```text
Provider login/session
-> Provider-specific collector
-> Raw provider payload
-> Normalizer
-> ProviderUsageSnapshot
-> App UI / widget / notification
```

Collection priority:

1. Official or provider-internal API response available from the authenticated session
2. WebView network response captured from `fetch` or `XMLHttpRequest`
3. Provider app state JSON embedded in the page
4. Structured script or storage JSON
5. DOM text fallback
6. Last known good snapshot

DOM text scraping is the last fallback. Do not create live usage numbers from generic labels such as `Usage`, `Total`, `Session`, `Weekly`, `Remaining`, or marketing page text.

## Normalized Snapshot Contract

Each provider should normalize to a local structure equivalent to:

```json
{
  "providerId": "gemini",
  "connectionState": "CONNECTED",
  "refreshState": "IDLE",
  "plan": "Gemini Pro",
  "account": "user@example.com",
  "fetchedAt": "2026-05-19T10:00:00Z",
  "lines": [
    {
      "key": "gemini:pro",
      "label": "Gemini Pro",
      "remainingPercent": 98,
      "usedPercent": 2,
      "resetsAt": "2026-05-19T12:00:00Z",
      "unit": "percent",
      "source": "provider-api",
      "confidence": 0.99
    }
  ]
}
```

Rules:

- Store only normalized usage data.
- Do not store provider access tokens, refresh tokens, API keys, cookies, raw logs, credential file contents, or local filesystem paths in usage snapshots.
- If a refresh fails but the previous snapshot has valid counter lines, keep the previous counter lines and mark the provider as stale or refresh failed.
- If a provider gives `remainingFraction`, compute:

```text
remainingPercent = round(remainingFraction * 100)
usedPercent = 100 - remainingPercent
```

- If a provider gives `usedPercent`, compute:

```text
remainingPercent = 100 - usedPercent
```

- If a reset timestamp exists, use it directly.
- If only reset text exists, preserve the text.
- If reset information is missing, do not invent reset time.

## APK Analysis Pattern

The analyzed APK used a provider-specific WebView service plus JavaScript extractor pattern.

Confirmed Gemini-related classes and data stores included:

- `GeminiUsageService`
- `GeminiScriptProvider`
- `GeminiUsageDataFetcher`
- `geminiScriptDataStore`
- `geminiUsageDataStore`
- `account_data_gemini`
- `usage_data_gemini`

The APK fetched an encrypted Gemini script from Firebase Remote Config under key `d260503`, then decrypted it through a native library. The exact script was not recoverable from the base APK alone because the required native split library was not included.

Important conclusion:

- Firebase was used as a dynamic script delivery mechanism, not as the required provider data model.
- For this app, bundle provider collectors as local assets, for example `assets/gemini_collector.js`.
- Keep the same WebView plus JS bridge strategy without Firebase.

The APK-normalized Gemini shape was compact:

```json
{
  "account": {
    "p": "GEMINI_PRO",
    "e": "user@example.com"
  },
  "usage": {
    "l": 1710000000000,
    "x": [
      {
        "l": "Gemini Pro",
        "u": 0.02,
        "s": null,
        "r": 1710086400000,
        "t": "Resets in 2h 42m"
      }
    ]
  }
}
```

Interpretation:

- `account.p`: plan
- `account.e`: email/account
- `usage.x[]`: usage limits
- `x[].l`: label
- `x[].u`: used fraction
- `x[].r`: reset timestamp
- `x[].t`: reset text

## Claude

### Collection

Use a Claude WebView session.

Preferred source:

```text
https://claude.ai/api/organizations/{organizationId}/usage
```

How to find `organizationId`:

- Prefer Claude session/API response.
- Use `lastActiveOrg` cookie if available.
- Verify with Claude organization API from the authenticated WebView session.

Use `credentials: include` inside the Claude WebView context.

### Normalization

Expected usage keys:

- `five_hour`
- `seven_day`
- `seven_day_omelette`

Map to:

```text
five_hour           -> Claude Session
seven_day           -> Claude Weekly
seven_day_omelette  -> Claude Design
```

Use provider response fields:

- `utilization` or equivalent used fraction/percent
- `resets_at` or equivalent reset timestamp

Plan:

- Preserve provider text, for example `Pro` or `Max`.
- Do not remap Claude plan labels unless there is a clear provider value.

### Fallback Rules

Do not save generic `Session` or `Weekly` DOM lines unless they come from the Claude usage API or a high-confidence structured payload.

If the page loads but `/usage` is not available yet, keep the previous valid Claude snapshot.

## Codex

### Collection

Use the ChatGPT/Codex OAuth or authenticated ChatGPT WebView session.

Preferred endpoint:

```text
https://chatgpt.com/backend-api/wham/usage
```

The Windows/mobile implementation should prefer structured API data over WebView text.

Plan source:

- JWT claim: `https://api.openai.com/auth.chatgpt_plan_type`
- Or structured account/subscription response if available

Plan display mapping:

```text
prolite -> Pro 5x
pro     -> Pro 20x
```

### Normalization

Map usage windows:

```text
rate_limit.primary_window      -> Codex Session
rate_limit.secondary_window    -> Codex Weekly
code_review_rate_limit.*       -> Code review
spark_rate_limit.*             -> Spark
spark_rate_limits.*            -> Spark
credits.balance                -> Credits
```

Recognize used percent fields:

- `used_percent`
- `usedPercentage`
- `used_percentage`
- `percent_used`

Recognize reset fields:

- `reset_at`
- `resets_at`
- `reset_after_seconds`

Window duration hints:

```text
18,000 seconds  -> 5-hour window
604,800 seconds -> weekly window
```

### Fallback Rules

If API collection succeeds, ignore weaker WebView fallback lines.

Do not use placeholder text such as `starts when you send a message` as a reset timestamp.

## Gemini

### Windows Collection Reference

AI Usage for Windows reads Gemini CLI credentials:

```text
~/.gemini/settings.json
~/.gemini/oauth_creds.json
```

Then it calls:

```text
POST https://cloudcode-pa.googleapis.com/v1internal:loadCodeAssist
POST https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota
```

`retrieveUserQuota` returns quota buckets with:

- `modelId`
- `remainingFraction`
- `resetTime`

### Mobile Standalone Collection

Mobile cannot assume Gemini CLI credentials exist.

Use a Gemini WebView session and local JS collector:

```text
assets/gemini_collector.js
```

Collection priority:

1. WebView network hook for Code Assist or Gemini quota responses
2. Page app state JSON
3. Structured script JSON
4. DOM fallback
5. Previous valid snapshot

If implementing native OAuth, request Google scopes compatible with Code Assist quota APIs. General Firebase Auth or ordinary Google sign-in is not enough for `retrieveUserQuota`.

### Normalization

Plan mapping:

```text
GOOGLE_AI_PRO, GEMINI_PRO, g1_pro_tier, gemini-codeassist-google-one-ai-pro -> Gemini Pro
GOOGLE_AI_PLUS, GEMINI_PLUS                                               -> Gemini Plus
GOOGLE_AI_ULTRA, GEMINI_ULTRA, g1_ultra_tier                              -> Gemini Ultra
GEMINI_ADVANCED                                                           -> Gemini Advanced
GOOGLE_ONE_AI_PREMIUM                                                     -> Google One AI Premium
GEMINI_FREE, GOOGLE_AI_FREE                                               -> Gemini Free
```

Quota mapping:

```text
modelId contains gemini + pro   -> Gemini Pro
modelId contains gemini + flash -> Gemini Flash
deep research quota             -> Gemini Deep Research
```

Use:

```text
remainingPercent = round(remainingFraction * 100)
usedPercent = 100 - remainingPercent
resetsAt = resetTime
```

For APK-style compact payload:

```text
usage.x[].l -> label
usage.x[].u -> used fraction
usage.x[].r -> reset timestamp
usage.x[].t -> reset text
```

### Fallback Rules

Allowed labels:

- `Gemini Pro`
- `Gemini Flash`
- `Gemini Deep Research`

Reject generic labels:

- `Usage`
- `Gemini Usage`
- `CheckGeminiQuota`

If a line says `100% left` but has no real quota/reset evidence, treat it as low confidence and prefer the last valid snapshot.

## Copilot

### Collection

Use a GitHub WebView session.

Start URL:

```text
https://github.com/settings/copilot
```

Preferred endpoint:

```text
https://github.com/github-copilot/chat/entitlement
```

Use same-session WebView fetch with `credentials: include`.

### Normalization

Plan source fields:

- `plan`
- `sku`
- `licenseType`
- `license_type`
- entitlement status fields

Display provider name as `Copilot`, not `GitHub Copilot`, unless the app UI explicitly needs the full product name.

Usage fields:

```text
quotas.remaining.chat
quotas.limits.chat
quotas.remaining.chatPercentage

quotas.remaining.completions
quotas.limits.completions
quotas.remaining.completionsPercentage

quotas.remaining.premiumInteractions
quotas.limits.premiumInteractions
quotas.remaining.premiumInteractionsPercentage

quotas.resetDate
```

Map to:

```text
Chat
Completions
Premium requests
```

### Fallback Rules

For Free or limited license states, if completions limit is missing but entitlement confirms a limited free plan, use the known free completions cap only as a fallback and mark confidence lower.

Do not parse marketing pages such as Copilot plan comparison pages as usage.

## Cursor

### Windows Collection Reference

AI Usage for Windows reads Cursor Desktop or Cursor CLI auth:

```text
%APPDATA%/Cursor/User/globalStorage/state.vscdb
cursorAuth/accessToken
cursorAuth/refreshToken
cursorAuth/stripeMembershipType
```

Then it calls:

```text
POST https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage
POST https://api2.cursor.sh/aiserver.v1.DashboardService/GetPlanInfo
POST https://api2.cursor.sh/aiserver.v1.DashboardService/GetCreditGrantsBalance
GET  https://cursor.com/api/auth/stripe
GET  https://cursor.com/api/usage?user={userId}
```

For Free plans, the current API can return only percent fields:

```json
{
  "planUsage": {
    "totalSpend": 8,
    "bonusSpend": 8,
    "autoPercentUsed": 8,
    "apiPercentUsed": 0,
    "totalPercentUsed": 4
  }
}
```

In that case:

```text
leftPercent = 100 - totalPercentUsed
```

Do not infer an official request count limit unless the response includes a real limit.

### Mobile Standalone Collection

Mobile cannot read Windows Cursor SQLite or keychain.

Use Cursor WebView session:

```text
https://cursor.com/dashboard
```

Collection priority:

1. Android `CookieManager` bridge reads cookie header for Cursor session.
2. If `WorkosCursorSessionToken` is available and contains an access token, call `api2.cursor.sh`.
3. WebView network hook captures dashboard API responses.
4. Structured app state JSON from dashboard.
5. `cursor.com/api/auth/stripe` and `cursor.com/api/usage` fallback.
6. Free spend heuristic only when explicitly marked estimated.

Important:

- `WorkosCursorSessionToken` may be HttpOnly and invisible to `document.cookie`.
- Use Android-side `CookieManager`, not only JS.
- Do not main-frame load `api2.cursor.sh` endpoints.
- Call APIs from the authenticated dashboard context or native client.

### Normalization

Plan source fields:

- `plan`
- `planName`
- `plan_label`
- `planType`
- `membershipType`
- `limitType`
- `subscription`
- `tier`
- `planInfo.*`
- `individualUsage.*`

Preferred usage schema:

```json
{
  "planUsage": {
    "totalSpend": 1200,
    "limit": 2400,
    "remaining": 1200,
    "totalPercentUsed": 50
  }
}
```

Map to:

```text
Total usage
Auto usage
API usage
On-demand
```

Request-based fallback schema:

```json
{
  "gpt-4": {
    "numRequests": 77,
    "maxRequestUsage": 500
  },
  "startOfMonth": "2026-05-17T06:32:31.075Z"
}
```

Map to:

```text
Requests
```

`individualUsage` fallback:

- `individualUsage.plan.limit`
- `individualUsage.plan.remaining`
- `individualUsage.plan.breakdown`
- `individualUsage.onDemand.limit`
- `individualUsage.onDemand.remaining`

### Fallback Rules

Reject:

- generic visible text fallback
- `remainingCap` alone
- marketing/sitemap/completed text
- `src="/"` with short generic labels
- `2 of 3 left` style text without provider schema evidence

If no trusted schema-based line is found, keep the last valid Cursor snapshot.

## Collector Asset Contract

Each provider JS collector should return:

```json
{
  "ok": true,
  "providerId": "cursor",
  "plan": "Free",
  "account": "user@example.com",
  "fetchedAt": 1779160000000,
  "lines": [
    {
      "key": "cursor:total",
      "label": "Total usage",
      "remainingPercent": 96,
      "usedPercent": 4,
      "resetsAt": 1781677951075,
      "unit": "percent",
      "source": "api2.cursor.sh/GetCurrentPeriodUsage",
      "confidence": 0.99
    }
  ],
  "debug": {
    "schema": "planUsage",
    "version": "2026-05-19"
  }
}
```

Debug output must not include:

- tokens
- cookies
- authorization headers
- raw credential files
- local file paths
- full raw provider responses

## Testing Checklist

For each provider:

- Login success is detected without saving tokens into snapshots.
- A real plan label is extracted or safely omitted.
- At least one trusted usage line is extracted when the provider exposes usage.
- Reset time is taken from provider data, not invented.
- Failed refresh keeps previous valid counter lines.
- Generic DOM text does not overwrite trusted API lines.
- Unit tests cover real known response shapes and fallback rejection.

Suggested unit test groups:

- `ProviderModelsTest`
- `TextUsageExtractorTest`
- `ProviderLocalUsageCollectorTest`
- `ProviderUsageCollectionServiceTest`
- `WebLoginActivityTest`

