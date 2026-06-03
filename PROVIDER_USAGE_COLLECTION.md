# Provider Plan and Usage Collection Reference

??臾몄꽌??AI Quota for Mobile??provider蹂?濡쒓렇?? plan, ?ъ슜???쒕룄 ?섏쭛 諛⑹떇??湲곗? 臾몄꽌??
plan ?먮뒗 ?ъ슜???섏쭛??瑗ъ????뚮뒗 癒쇱? ??臾몄꽌瑜?蹂닿퀬 ?꾩옱 援ы쁽???꾨옒 怨꾩빟??源⑥? ?딆븯?붿? ?뺤씤?쒕떎.

理쒖쥌 ?뺣━ 湲곗?: 2026-05-19

## 紐⑺몴

- Firebase, ?몃? ?쒕쾭, Windows ???곕룞 ?놁씠 Android 濡쒖뺄 ?깅쭔?쇰줈 provider 援щ룆 ?쒕룄? ?⑥? ?ъ슜?됱쓣 異붿쟻?쒕떎.
- provider 濡쒓렇?몄? Android ???대? WebView ?먮뒗 provider蹂?濡쒖뺄 OAuth ?먮쫫?먯꽌 泥섎━?쒕떎.
- ?좏겙, 荑좏궎, ?몄뀡, ?ъ슜???ㅻ깄?룹? Android 湲곌린 ?대??먮쭔 ??ν븳??
- Dashboard, provider ?? ?꾩젽, ?뚮┝? 濡쒖뺄???뺢퇋?붾맂 `ProviderUsageSnapshot`留??쎈뒗??
- ?붾㈃ DOM ?띿뒪??scraping? 理쒗썑 fallback?대떎. ?곗꽑?쒖쐞??濡쒖뺄 OAuth/API ?묐떟, WebView network/JSON ?묐떟, provider ???곹깭 JSON, ?쒗븳???띿뒪??fallback ?쒖꽌??

## ?꾩껜 ?섏쭛 ?뚯씠?꾨씪??
```text
Provider Login
  -> Secure Local Session Storage
  -> Provider Usage Fetcher
  -> Normalized Local Usage Snapshot
  -> App UI / Widget / Notification
```

## ?듭떖 肄붾뱶

| ??븷 | ?뚯씪 |
| --- | --- |
| provider 紐⑤뜽 諛?plan ?쒖떆 ?뺢퇋??| `android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt` |
| 濡쒖뺄 ?ㅻ깄?????蹂댁〈 | `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt` |
| provider ?섏쭛 ?ㅼ??ㅽ듃?덉씠??| `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt` |
| 濡쒓렇??WebView | `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt` |
| 濡쒓렇??transient error 蹂듦뎄 ?뺤콉 | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderLoginRecoveryPolicy.kt` |
| 濡쒓렇??URL rewrite | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderLoginUrlRewriter.kt` |
| 濡쒓렇?????몄뀡 ?뺣━ | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderLoginSessionPreparer.kt` |
| WebView JS collector | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderLocalUsageCollector.kt` |
| provider蹂?collector asset | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderCollectorAssets.kt` |
| Gemini WebView fallback asset | `android/app/src/main/assets/gemini_collector.js` |
| payload ?뺢퇋??| `android/app/src/main/java/com/aiquota/mobile/providers/TextUsageExtractor.kt` |
| Codex OAuth/API ?섏쭛 | `android/app/src/main/java/com/aiquota/mobile/providers/CodexOAuthRepository.kt` |
| Gemini OAuth/API ?섏쭛 | `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt` |
| 濡쒓렇???쒖옉 URL | `android/app/src/main/java/com/aiquota/mobile/providers/ProviderConnectorRegistry.kt` |
| 1遺??먮룞 refresh | `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshPolicy.kt`, `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshService.kt` |

## ?뺢퇋???곗씠??怨꾩빟

```kotlin
ProviderUsageSnapshot(
    providerId = ProviderId,
    connectionState = CONNECTED | DISCONNECTED | CONNECTING | UNAVAILABLE | ERROR,
    refreshState = IDLE | REFRESHING,
    planLabel = String?,
    updatedAt = String,
    lines = List<ProviderUsageLine>,
    message = String?
)

ProviderUsageLine(
    label = String,
    remainingPercent = Float?,
    remainingText = String,
    resetText = String?,
    usedAmount = Double?,
    limitAmount = Double?,
    remainingAmount = Double?,
    unit = String?,
    category = String?,
    windowText = String?,
    startsAt = String?,
    resetsAt = String?,
    sourceLabel = String?,
    confidence = Float?
)
```

?쒖떆????긽 "?⑥? ?? 以묒떖?대떎.
provider ?묐떟??used percent留?二쇰㈃ ?깆? `remainingPercent = 1 - usedPercent`濡?蹂?섑븳??

## 怨듯넻 ???洹쒖튃

- `ProviderUsageCollectionService.saveUsageSnapshot()`? ??????좊ː 媛?ν븳 counter line留??④릿??
- ??snapshot???좊ː 媛?ν븳 ?ъ슜??line???녾퀬 ?댁쟾 snapshot???뺤긽 counter line???덉쑝硫??댁쟾 line???좎??쒕떎.
- ?댁쟾 ?뺤긽 snapshot蹂대떎 ?좊ː?꾧? ??? fallback???ㅼ뼱?ㅻ㈃ ?뺤긽媛믪쓣 ??뼱?곗? ?딅뒗??
- provider媛 ?ㅼ젣 ?ъ슜?됱쓣 ?몄텧?섏? ?딅뒗 ?곹깭瑜??꾩쓽 ?レ옄濡?梨꾩슦硫????쒕떎.
- generic label??`Usage`, `?ъ슜??, `Session`, `Weekly`留뚯쑝濡쒕뒗 live counter濡???ν븯硫????쒕떎.
- provider蹂?API ?묐떟 援ъ“瑜??덈줈 ?뺤씤?섎㈃ ??臾몄꽌瑜?癒쇱? 媛깆떊?섍퀬 愿??unit test瑜?媛숈씠 媛깆떊?쒕떎.

## 怨듯넻 reset ?쒖떆 洹쒖튃

- ?ъ슜??line??`resetsAt` ?먮뒗 ?좊ː 媛?ν븳 `resetText`媛 ?덉쑝硫?UI???대떦 媛믪쓣 洹몃?濡??쒖떆?댁빞 ?쒕떎.
- ?ㅼ젣 reset 媛믪씠 ?덈뒗??`由ъ뀑 ??대㉧ ?湲?以??쇰줈 ?泥댄븯硫????쒕떎.
- `由ъ뀑 ??대㉧ ?湲?以?? provider媛 ?꾩쭅 reset window瑜??쒖옉?섏? ?딆븯嫄곕굹 reset 洹쇨굅媛 ?녿뒗 line?먮쭔 ?쒖떆?쒕떎.
- Provider ??쓽 ?ъ슜??遺꾩꽍 ?곸뿭? ?ㅼ젣 reset 媛믪씠 ?덉쓣 ?뚮쭔 `由ъ뀑 湲곗?`???쒖떆?쒕떎.
- Dashboard? Provider ??? 媛숈? `ProviderUsageLine.effectiveResetText()` 怨꾩뿴 媛믪쓣 湲곗??쇰줈 ?쒖떆?댁빞 ?쒕떎.

## 怨듯넻 濡쒓렇??transient error 蹂듦뎄 洹쒖튃

- Google/WorkOS/GitHub/OpenAI OAuth 以묎컙 ?섏씠吏?먯꽌 main-frame error媛 諛쒖깮?대룄 利됱떆 `ERROR` snapshot?쇰줈 ??ν븯吏 ?딅뒗??
- Codex/Gemini??loopback OAuth callback(`localhost`, `127.0.0.1`)? WebView媛 ?곌껐 ?ㅽ뙣瑜??꾩슱 ???덉쑝誘濡?token exchange 泥섎━媛 ?앸굹?꾨줉 error瑜?臾댁떆?쒕떎.
- 蹂듦뎄 ???host??`ProviderLoginRecoveryPolicy.shouldRecoverLoginNavigationError()`??紐낆떆???몄쬆 host濡??쒗븳?쒕떎.
- 蹂듦뎄??provider蹂???URL(`claude.ai`, `chatgpt.com`, `gemini.google.com/app`, `github.com/settings/copilot`, `cursor.com/dashboard`)濡?理쒕? 2?뚮쭔 ?ъ떆?꾪븳??
- 蹂듦뎄 ??곸씠 ?꾨땶 host?먯꽌 ?ㅽ뙣?섎㈃ 湲곗〈泥섎읆 濡쒓렇???ㅽ뙣濡?泥섎━?쒕떎.

## ?먮룞 媛깆떊

- 濡쒓렇?몃맂 provider留??먮룞 媛깆떊 ??곸씠??
- `ForegroundRefreshPolicy.REFRESH_INTERVAL_MS = 60_000L` 湲곗? 1遺꾨쭏??refresh?쒕떎.
- refresh 以??섏쭛 ?ㅽ뙣媛 諛쒖깮?대룄 ?댁쟾 ?뺤긽 ?ъ슜??line? 蹂댁〈?쒕떎.
- ?섎룞 refresh 踰꾪듉? ?대떦 provider ?섏쭛??利됱떆 ?ㅽ뻾?섍퀬 ?꾨즺 ???좊땲硫붿씠?섏쓣 硫덉텣??

## Provider蹂??붿빟

| Provider | 濡쒓렇???몄뀡 | plan ?곗꽑?쒖쐞 | ?ъ슜???곗꽑?쒖쐞 |
| --- | --- | --- | --- |
| Claude | Claude WebView + `lastActiveOrg` cookie | provider ?먮Ц 媛?| `/api/organizations/{id}/usage` |
| Codex | ChatGPT/Codex OAuth | JWT ChatGPT plan claim | `chatgpt.com/backend-api/wham/usage` |
| Gemini | Google account chooser + Gemini CLI OAuth/API | Code Assist tier | `retrieveUserQuota` |
| Copilot | GitHub WebView session | Copilot entitlement | `github-copilot/chat/entitlement` |
| Cursor | Cursor WebView session | dashboard/account JSON | `planUsage`, request-based usage, `individualUsage` |

## Claude

### 濡쒓렇??
- ?쒖옉 URL: `https://claude.ai/`
- WebView ?몄뀡 荑좏궎瑜??ъ슜?쒕떎.
- Claude 濡쒓렇???쒖옉 ??`ProviderLoginSessionPreparer`媛 Claude ?몄뀡 荑좏궎? WebStorage留??뺣━?쒕떎.
- Google WebView ?몄뀡? 怨꾩젙 ?좏깮 ?꾨낫濡??좎??쒕떎.
- Cloudflare 寃利?荑좏궎??`cf_clearance`, `__cf_bm`? 蹂댁〈?쒕떎.
- Claude??Google 濡쒓렇?몄? 湲곗〈 Google WebView 荑좏궎媛 ?덉뼱??怨꾩젙 ?좏깮 ?붾㈃???좎빞 ?쒕떎.
- `ProviderLoginUrlRewriter`??Claude?먯꽌 `accounts.google.com` OAuth ?쒖옉 URL??`prompt=select_account`瑜?吏곸젒 異붽??쒕떎.
- `AccountChooser`濡??ㅼ떆 媛먯떥硫?怨꾩젙 ?좏깮 ?붾㈃???좉퉸 蹂댁씤 ??湲곗〈 ?몄뀡?쇰줈 ?먮룞 吏꾪뻾?????덉쑝誘濡?Claude OAuth?먯꽌???ъ슜?섏? ?딅뒗??

### 濡쒓렇???꾨즺 ?먮떒

- 濡쒓렇???꾨즺 ??`lastActiveOrg` 荑좏궎?먯꽌 organization id瑜?李얜뒗??
- Claude SPA??濡쒓렇???꾩뿉??`/login`??癒몃Ъ ???덉쑝誘濡?`/login`? 湲곕낯?곸쑝濡??꾨즺 泥섎━?섏? ?딅뒗??
- `/login`? JS payload??`authenticatedApp=true`, `appShellConfirmed=true`, ?먮뒗 ?ㅼ젣 limit data媛 ?덉쓣 ?뚮쭔 ?꾨즺 泥섎━?쒕떎.
- `/logout`? ??긽 ?꾨즺 泥섎━?섏? ?딅뒗??
- `/new` 媛숈? Claude ??寃쎈줈濡??대룞?덇퀬 `lastActiveOrg` 荑좏궎媛 ?덉쑝硫???shell 吏꾩엯?쇰줈 蹂닿퀬 WebView瑜??ル뒗??
- main frame error媛 諛쒖깮?덈뜑?쇰룄 `lastActiveOrg` 荑좏궎媛 ?덉쑝硫?濡쒓렇???깃났 ??transient WebView error濡?蹂닿퀬 ?곌껐 ?꾨즺 泥섎━?쒕떎.
- Claude host ?섏씠吏?먯꽌??WebView ?대??먯꽌 `/api/organizations`, `/api/organizations/me`瑜?`credentials: include`濡??몄텧???몄뀡??寃利앺븳??
- API 寃利?payload?먯꽌 organization id瑜?李얠쑝硫?`lastActiveOrg` 荑좏궎瑜?蹂닿컯 ??ν븯怨??깆쑝濡?蹂듦??쒕떎.
- 濡쒓렇???꾨즺 ?좏샇媛 ?뺤씤?섎㈃ WebView瑜?利됱떆 ?リ퀬 ?깆쑝濡?蹂듦??쒕떎.
- ?ъ슜???섏쭛? ??λ맂 濡쒖뺄 ?몄뀡?쇰줈 諛깃렇?쇱슫???섏쭛?먯꽌 ?댁뼱媛꾨떎.

### plan ?섏쭛

- Claude plan? provider媛 ?몄텧??媛믪쓣 洹몃?濡??쒖떆?쒕떎.
- `ProviderId.CLAUDE.normalizedPlanLabelForDisplay()`???꾩쓽 留ㅽ븨?섏? ?딅뒗??
- `Pro`, `Max` ??Claude媛 ?쒓났???먮Ц 媛믪쓣 ?좎??댁빞 ?쒕떎.

### ?ъ슜???섏쭛

- ?곗꽑 寃쎈줈: `https://claude.ai/api/organizations/{organizationId}/usage`
- `ProviderUsageCollectionService.startClaudeDirectUsageFetch()`媛 organization id瑜??뺣낫?섎㈃ 吏곸젒 API URL??WebView濡?濡쒕뱶?쒕떎.
- direct usage API媛 pending?대㈃ 湲곗〈 snapshot fallback ?먮뒗 plan-only fallback?쇰줈 議곌린 醫낅즺?섎㈃ ???쒕떎.
- direct usage API timeout??吏?섎㈃ ?ㅼ쓬 probe URL濡??섏뼱媛????덈떎.
- direct usage API ?먯젙? ?뺥솗??`/api/organizations/{id}/usage`留???곸쑝濡??쒕떎. `/usage_limits`??direct usage API濡?痍④툒?섏? ?딅뒗??
- `TextUsageExtractor.extractClaudeUsageApiResponse()`???ㅼ쓬 ?ㅻ? ?댁꽍?쒕떎.
  - `five_hour` -> `Claude 5?쒓컙 ?쒕룄`, window `5 hours`
  - `seven_day` -> `Claude 二쇨컙 ?쒕룄`, window `7 days`
  - `seven_day_omelette` -> `Claude Design`, window `7 days`
- 媛???ぉ? `utilization`??used percent濡?蹂닿퀬 `resets_at`??reset 湲곗??쇰줈 ?ъ슜?쒕떎.
- Claude ?ъ슜??line? ?????canonical key濡?dedupe?쒕떎.
  - `Five_hour`, `five_hour`, `Claude 5?쒓컙 ?쒕룄`, `5 hours` -> `claude:five_hour`
  - `Seven_day`, `seven_day`, `Claude 二쇨컙 ?쒕룄`, `7 days` -> `claude:seven_day`
  - `Seven_day_omelette`, `Claude Design` -> `claude:seven_day_omelette`
- 媛숈? canonical key媛 ?щ윭 媛쒖씠硫?`/api/organizations/{id}/usage` source, `resetsAt`, `remainingPercent`, confidence媛 ??媛뺥븳 line???곗꽑?쒕떎.

### 湲덉? fallback

- `/new`, `/`, 鍮?source?먯꽌 ?섏삩 `Session`, `Weekly` plan-only/placeholder line? ??ν븯硫????쒕떎.
- plan???녿뒗 ?곹깭?먯꽌 usage留??≫엳硫?`shouldWaitForPlanLabel()` 湲곗??쇰줈 異붽? probe瑜?湲곕떎由곕떎.
- ?뺤긽 `/usage` line???덈뒗???ㅼ씠? 鍮?fallback?쇰줈 ??뼱?곕㈃ ???쒕떎.
- 媛숈? Claude quota媛 raw label?대굹 source ?뺤떇留??ㅻⅤ?ㅻ뒗 ?댁쑀濡?以묐났 ?쒖떆?섎㈃ ???쒕떎.

## Codex

### 濡쒓렇??
- ?쒖옉 URL: `https://chatgpt.com/`
- ?ㅼ젣 OAuth??`CodexOAuthRepository`媛 ChatGPT/Codex CLI 怨꾩뿴 OAuth瑜??ъ슜?쒕떎.
- authorize URL: `https://auth.openai.com/oauth/authorize`
- redirect URI: `http://localhost:1455/auth/callback`
- originator: `codex_cli_rs`
- scope: `openid profile email offline_access api.connectors.read api.connectors.invoke`

### plan ?섏쭛

- JWT claim `https://api.openai.com/auth.chatgpt_plan_type`?먯꽌 plan type????ν븳??
- `ProviderId.CODEX.normalizedPlanLabelForDisplay()` 留ㅽ븨? ?ㅼ쓬怨?媛숇떎.
  - `prolite` -> `Pro 5x`
  - `pro` -> `Pro 20x`
  - 洹???媛믪? 泥?湲??titlecase留??곸슜?섍퀬 ???섎?瑜??좎??쒕떎.

### ?ъ슜???섏쭛

- ?곗꽑 寃쎈줈: `https://chatgpt.com/backend-api/wham/usage`
- `CodexOAuthRepository.fetchUsageSnapshot()`媛 Bearer token怨?`ChatGPT-Account-Id` ?ㅻ뜑瑜??ъ슜?쒕떎.
- `structuredPayloadFromUsageBodies()`媛 ?ㅼ쓬 ?묐떟 ?ㅻ? ?뺢퇋?뷀븳??
  - `rate_limit.primary_window` -> `Codex 5?쒓컙 ?쒕룄`
  - `rate_limit.secondary_window` -> `Codex 二쇨컙 ?쒕룄`
  - `code_review_rate_limit.*` -> `Code review ...`
  - `spark_rate_limit.*`, `spark_rate_limits.*` -> `Spark`, `Spark weekly`
  - `credits.balance` -> `Credits`
- `limit_window_seconds = 18_000`?대㈃ 5?쒓컙 ?쒕룄??
- `limit_window_seconds = 604_800`?대㈃ 二쇨컙 ?쒕룄??
- `used_percent`, `usedPercentage`, `used_percentage`, `percent_used`瑜?used percent濡??ъ슜?쒕떎.
- `reset_at`, `resets_at`, `reset_after_seconds`瑜?reset 湲곗??쇰줈 ?ъ슜?쒕떎.

### 湲덉? fallback

- Codex OAuth/API ?섏쭛???깃났?섎㈃ WebView fallback蹂대떎 ?곗꽑?쒕떎.
- 5?쒓컙 ?쒕룄媛 ?대? ?ъ슜 以묒씤??reset??`硫붿떆吏瑜?蹂대궡硫??쒖옉`?쇰줈 諛붾뚮뒗 媛믪? ?좊ː?섏? ?딅뒗??
- ?ㅼ젣 WHAM usage window??reset 媛믪쓣 ?곗꽑?쒕떎.

## Gemini

### 濡쒓렇??
- ?쒖옉 URL? Google 怨꾩젙 ?좏깮 URL?대떎.
  - `https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fgemini.google.com%2Fapp&hl=ko`
- Gemini??Gemini WebView 濡쒓렇???몄뿉 Gemini CLI/Code Assist OAuth ?섏쭛 寃쎈줈瑜?媛吏꾨떎.
- OAuth redirect URI: `http://127.0.0.1:46417/oauth2callback`
- scope: `cloud-platform`, `userinfo.email`, `userinfo.profile`

### plan ?섏쭛

- ?곗꽑 寃쎈줈??`GeminiCliOAuthRepository.loadCodeAssist()`??`loadCodeAssist` ?묐떟?대떎.
- `paidTier.name`, `paidTier.id`, `currentTier.name`, `currentTier.id` ?쒖꽌濡?plan ?꾨낫瑜??쎈뒗??
- `ProviderId.GEMINI.normalizedPlanLabelForDisplay()` 留ㅽ븨? ?ㅼ쓬怨?媛숇떎.
  - `GOOGLE_AI_PRO`, `GEMINI_PRO`, `g1_pro_tier`, `gemini-codeassist-google-one-ai-pro` 怨꾩뿴 -> `Gemini Pro`
  - `GOOGLE_AI_PLUS`, `GEMINI_PLUS` 怨꾩뿴 -> `Gemini Plus`
  - `GOOGLE_AI_ULTRA`, `GEMINI_ULTRA`, `g1_ultra_tier` 怨꾩뿴 -> `Gemini Ultra`
  - `GEMINI_ADVANCED` 怨꾩뿴 -> `Gemini Advanced`
  - `GOOGLE_ONE_AI_PREMIUM` 怨꾩뿴 -> `Google One AI Premium`
  - `GEMINI_FREE`, `GOOGLE_AI_FREE` 怨꾩뿴 -> `Gemini Free`
  - `GEMINI_UNKNOWN`? ???plan?쇰줈 ?곗? ?딅뒗??

### ?ъ슜???섏쭛

- ?곗꽑 寃쎈줈: Google Code Assist API
  - `loadCodeAssist`
  - ?꾩슂 ??`onboardUser`
  - `retrieveUserQuota`
- `GeminiCliOAuthRepository.structuredPayloadFromCodeAssist()`媛 `retrieveUserQuota.buckets`瑜??쎈뒗??
- `bucket.modelId`??`pro`媛 ?덉쑝硫?`Gemini Pro`濡??쒖떆?쒕떎.
- `bucket.modelId`??`flash`媛 ?덉쑝硫?`Gemini Flash`濡??쒖떆?쒕떎.
- Deep Research quota媛 ?묐떟???덉쑝硫?`Gemini Deep Research`濡??쒖떆?쒕떎.
- `bucket.remainingFraction`???⑥? 鍮꾩쑉濡??ъ슜?쒕떎.
- used percent媛 ?꾩슂?섎㈃ `1 - remainingFraction`?쇰줈 怨꾩궛?쒕떎.
- `bucket.resetTime`??reset 湲곗??쇰줈 ?ъ슜?쒕떎.
- ?뺤긽 source??`Gemini Code Assist retrieveUserQuota`, confidence??`0.99`??

### WebView/asset fallback

- `gemini_collector.js`??APK 遺꾩꽍?먯꽌 ?뺤씤??援ъ“? 媛숈? `account.p`, `usage.x[]` ?뺥깭瑜?濡쒖뺄 asset?쇰줈 援ы쁽??fallback?대떎.
- 吏??label? 諛섎뱶???ㅼ쓬 3媛쒕쭔 ?덉슜?쒕떎.
  - `Gemini Pro`
  - `Gemini Flash`
  - `Gemini Deep Research`
- 以묐났 ?쇱씤? label蹂?score媛 ?믪? line???좏깮?쒕떎.
- ?ㅼ젣 痢≪젙媛믪씠 ?덈뒗 line, reset ?뺣낫媛 ?덈뒗 line, confidence媛 ?믪? line???곗꽑?쒕떎.

### 湲덉? fallback

- `Usage`, `?ъ슜??, `Gemini Usage` 媛숈? generic label? ??ν븯硫????쒕떎.
- `CheckGeminiQuota` 怨꾩뿴?먯꽌 confidence媛 ??퀬 100% ?⑥쓬??line? ?ㅼ젣 ?ъ슜?됱쑝濡??뺤젙?섎㈃ ???쒕떎.
- reset??`硫붿떆吏瑜?蹂대궡硫??쒖옉`?닿퀬 100% ?⑥쓬??line? ?ㅼ젣 ?ъ슜?됰낫????? ?곗꽑?쒖쐞濡??붾떎.
- 湲곗〈 ???plan??`Free`??寃쎌슦 ??refresh?먯꽌 plan 洹쇨굅媛 ?놁쑝硫?carry?섏? ?딅뒗??

### ?뺤씤???뺤긽 ?덉떆

- ?쒕툝由우뿉???뺤씤???뺤긽 snapshot:
  - plan: `Gemini Pro`
  - `Gemini Pro`: ??`98% left`
  - `Gemini Flash`: ??`99% left`
  - source: `Gemini Code Assist retrieveUserQuota`

## Copilot

### 濡쒓렇??
- ?쒖옉 URL: `https://github.com/settings/copilot`
- GitHub WebView ?몄뀡???ъ슜?쒕떎.

### plan ?섏쭛

- ?곗꽑 `github-copilot/chat/entitlement` ?묐떟??`plan`, `sku`, `licenseType`, `license_type` 怨꾩뿴 媛믪쓣 ?ъ슜?쒕떎.
- provider ?쒖떆紐낆? `GitHub Copilot`???꾨땲??`Copilot`?쇰줈 ?듭씪?쒕떎.
- plan label? provider ?먮Ц 媛믪쓣 titlecase ?섏??쇰줈留??쒖떆?쒕떎.

### ?ъ슜???섏쭛

- ?곗꽑 寃쎈줈: GitHub Copilot OAuth token 湲곕컲 `https://api.github.com/copilot_internal/user`
- WebView ?몄뀡 寃쎈줈 `https://github.com/github-copilot/chat/entitlement`??蹂댁“ 寃쎈줈濡쒕쭔 ?ъ슜?쒕떎.
- ?꾩옱 ?꾨━誘몄뾼 ?ъ슜?됱? `quota_snapshots.premium_interactions`媛 ?좊ː 寃쎈줈?닿퀬, 援ы삎 entitlement??`premiumInteractionsPercentage`留뚯쑝濡??곌껐 ?꾨즺 ?먮떒???섎㈃ ???쒕떎.
- `TextUsageExtractor.extractCopilotEntitlementResponse()`媛 `quotas.remaining`, `quotas.limits`, `quotas.resetDate`瑜??댁꽍?쒕떎.
- 二쇱슂 line? ?ㅼ쓬怨?媛숇떎.
  - `Chat`: `remaining.chat`, `limits.chat/messages`, `remaining.chatPercentage`
  - `Completions`: `remaining.completions`, `limits.completions/...`, `remaining.completionsPercentage`
  - `Premium requests`: `quota_snapshots.premium_interactions.percent_remaining`, ?먮뒗 援ы삎 `remaining.premiumInteractions`, `limits.premiumInteractions`, `remaining.premiumInteractionsPercentage`
- `resetDate`???붽컙 reset 湲곗??쇰줈 ?ъ슜?쒕떎.
- Free ?먮뒗 `licensed_limited`?먯꽌 completions limit???묐떟???놁쑝硫?`4000` completions瑜?湲곕낯 cap?쇰줈 蹂댁젙?쒕떎.

### 湲덉? fallback

- `/features/copilot/plans` 媛숈? 留덉????뚮옖 ?섏씠吏 ?レ옄???ъ슜?됱쑝濡???ν븯硫????쒕떎.
- URL, sitemap, completed 媛숈? 鍮꾩궗?⑸웾 臾몄옄?댁? line label濡???ν븯硫????쒕떎.

## Cursor

### 濡쒓렇??
- ?쒖옉 URL: `https://cursor.com/dashboard`
- Cursor WebView ?몄뀡怨?dashboard JSON/network/?곹깭 ?묐떟???ъ슜?쒕떎.
- main-frame probe??`dashboard`, `dashboard/usage`, `settings` 媛숈? ??shell ?붾㈃留?濡쒕뱶?쒕떎.
- `/api/*`, `api2.cursor.sh/auth/*`??main-frame?쇰줈 吏곸젒 濡쒕뱶?섏? ?딅뒗?? dashboard ??shell???대┛ ??媛숈? WebView ?몄뀡?먯꽌 `credentials: include` fetch? network hook?쇰줈 ?섏쭛?쒕떎.
- Google/WorkOS 濡쒓렇??吏곹썑 `authenticator.cursor.sh` main-frame transient error媛 諛쒖깮?????덈떎. ??寃쎌슦 利됱떆 `ERROR`濡???ν븯吏 ?딄퀬 吏㏐쾶 ?湲고븳 ??`https://cursor.com/dashboard`濡?蹂듦뎄 吏꾩엯?쒕떎.

### plan ?섏쭛

- `rememberCursorPlan()`???ㅼ쓬 ?ㅼ뿉??plan ?꾨낫瑜?李얜뒗??
  - `plan`, `planName`, `plan_label`, `planType`, `membershipType`, `limitType`, `subscription`, `tier`
  - `planInfo.*`, `plan.*`, `individualUsage.*`
- ?쒖떆 plan? provider ?먮Ц 媛믪쓣 titlecase ?섏??쇰줈 ?좎??쒕떎.

### ?ъ슜???섏쭛

Cursor????援ъ“瑜?紐⑤몢 吏?먰빐???쒕떎.

濡쒓렇?????ㅼ젣 ?대? ?ъ슜??API瑜??닿린 ?꾪빐 JS extractor??Android bridge瑜??듯빐 `CookieManager`??Cursor cookie header瑜??쎈뒗?? `WorkosCursorSessionToken`? HttpOnly??`document.cookie`?먯꽌??蹂댁씠吏 ?딆쓣 ???덉쑝誘濡? ??bridge ?놁씠??`api2.cursor.sh` ?ъ슜??API瑜??몄텧?섏? 紐삵븳??

#### 1. planUsage 諛⑹떇

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

- `scanCursorPlanUsageObject()`媛 `planUsage`, `usage.planUsage`, ?먮뒗 媛앹껜 ?먯껜媛 planUsage??寃쎌슦瑜??쎈뒗??
- `totalSpend`, `totalUsage`, `spend`, `used`, `usage`??used ?꾨낫濡?蹂몃떎.
- `limit`, `monthlyLimit`, `totalLimit`??cap ?꾨낫濡?蹂몃떎.
- `remaining`, `totalRemaining`, `balance`??remaining ?꾨낫濡?蹂몃떎.
- 湲덉븸 ?⑥쐞媛 cent泥섎읆 ???뺤닔濡??대젮?ㅻ㈃ `cursorMoneyAmount()`?먯꽌 USD濡?蹂댁젙?쒕떎.
- 湲곕낯 line? `Total usage`??
- 媛?ν븳 breakdown? `Auto usage`, `API usage`, `On-demand`??
- `api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage`??`planUsage`???곗꽑 ?섏쭛 ??곸씠??

#### 2. request-based 諛⑹떇

```json
{
  "gpt-4": {
    "numRequests": 77,
    "maxRequestUsage": 500
  },
  "startOfMonth": "..."
}
```

- `scanCursorRequestUsage()`媛 `numRequests`, `requestUsage`, `currentRequestUsage`, `requests`, `used`, `usage`瑜?used ?꾨낫濡??쎈뒗??
- `maxRequestUsage`, `maxRequests`, `requestLimit`, `limit`, `limitAmount`瑜?cap ?꾨낫濡??쎈뒗??
- ?щ윭 model bucket???덉쑝硫?total used/cap?쇰줈 ?⑹궛?섍퀬 `Total usage` line?쇰줈 ??ν븳??
- ?⑥쐞??`requests`??

#### 3. individualUsage summary 諛⑹떇

- `scanCursorUsageSummary()`??`individualUsage.plan`怨?`individualUsage.onDemand`瑜??쎈뒗??
- `plan.limit`, `plan.remaining`, `plan.breakdown`??`Total usage`濡??뺢퇋?뷀븳??
- `onDemand.limit`, `onDemand.remaining`??`On-demand usage`濡??뺢퇋?뷀븳??

### 湲덉? fallback

- `remainingCap`留??덈뒗 ??? confidence line? ???좊ː???믪? `Total usage`媛 ?덉쑝硫??곗꽑?쒖쐞?먯꽌 諛?ㅼ빞 ?쒕떎.
- `remainingCap`, `capWindowLabel`, `10 USD left`??free plan???ㅼ젣 珥??ъ슜?됱씠 ?꾨땲?? ??媛믩쭔?쇰줈 `Total usage`瑜?留뚮뱾硫????쒕떎.
- `/api/usage-summary`媛 `individualUsage.plan.used=0`, limit/remaining=0 ?먮뒗 percent-only 100% ?뺥깭留?以??뚮뒗 ?ㅼ젣 珥??ъ슜?됱쑝濡???ν븯硫????쒕떎.
- `On-demand` 媛숈? breakdown??DOM?먯꽌 percent-only濡??⑤룆 ?섏쭛?섎㈃ ????ъ슜?됱쑝濡???ν븯硫????쒕떎. 援ъ“?붾맂 `Total usage` ?먮뒗 planUsage 湲곕컲 breakdown???덉쓣 ?뚮쭔 ?몃? ??ぉ?쇰줈 ?좎??쒕떎.
- `completed`, `sitemap`, 留덉????띿뒪?몃뒗 ??ν븯硫????쒕떎.
- Cursor??`isTrustedCursorUsageLine()` 湲곗????듦낵?댁빞 live counter濡?痍④툒?쒕떎.
- `source="/"`, label `Md`, unit `md`, generic `2 of 3 left` 媛숈? DOM text fallback? ??ν븯硫????쒕떎.
- Cursor?먯꽌 generic visible text fallback? ?ъ슜?섏? ?딅뒗?? ?ㅼ젣 ?먯쿇? dashboard network response, app-state JSON, storage/script JSON ?덉쓽 `planUsage`, request usage, `individualUsage` schema?ъ빞 ?쒕떎.
- ??refresh?먯꽌 schema 湲곕컲 line??紐?李얠쑝硫??レ옄瑜?諛쒕챸?섏? ?딄퀬 留덉?留??뺤긽 Cursor snapshot留??좎??쒕떎.

## 臾몄젣 諛쒖깮 ???뺤씤 ?덉감

1. ?쒕툝由?湲곗??쇰줈 ?꾩옱 ???snapshot??癒쇱? ?뺤씤?쒕떎.

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$xml = & $adb -s emulator-5556 exec-out run-as com.aiquota.mobile cat shared_prefs/ai_quota_local_usage.xml
$raw = [regex]::Match($xml, '<string name="provider_snapshots">(.*?)</string>', 'Singleline').Groups[1].Value
$decoded = [System.Net.WebUtility]::HtmlDecode($raw)
$decoded | ConvertFrom-Json | ConvertTo-Json -Depth 20
```

2. ?섏쭛 濡쒓렇瑜?provider蹂꾨줈 ?뺤씤?쒕떎.

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5556 logcat -d -s AIQuotaProvider
```

3. 濡쒓렇?먯꽌 ?ㅼ쓬 marker瑜??곗꽑 ?뺤씤?쒕떎.

| Provider | ?뺤긽 marker |
| --- | --- |
| Claude | `collection claudeDirectUsageWebView`, `/api/organizations/.../usage` |
| Codex | `collection codexOAuthUsage`, `backend-api/wham/usage` |
| Gemini | `collection geminiCliOAuthUsage`, `retrieveUserQuota` |
| Copilot | `/github-copilot/chat/entitlement:200` |
| Cursor | `planUsage`, `requestUsage`, `usage-summary` |

4. ?ъ슜?됱씠 generic line?쇰줈 諛붾뚮㈃ `TextUsageExtractor`? `ProviderUsageCollectionService.isTrustedCounterLine()` ?꾪꽣媛 ?쏀빐議뚮뒗吏 ?뺤씤?쒕떎.
5. plan留??덇퀬 usage媛 ?놁쑝硫?API/network ?묐떟??鍮꾩뼱 ?덈뒗吏, 濡쒓렇???몄뀡???딄꼈?붿?, 湲곗〈 ?좊ː 媛?ν븳 snapshot 蹂댁〈 濡쒖쭅???묐룞?덈뒗吏 ?뺤씤?쒕떎.
6. reset??`由ъ뀑 ??대㉧ ?湲?以??쇰줈 諛붾뚮㈃ ?ㅼ젣 `resetsAt`?????snapshot?먯꽌 ?щ씪議뚮뒗吏 癒쇱? ?뺤씤?쒕떎.
7. Gemini媛 `Free` ?먮뒗 `?ъ슜??0%`泥섎읆 蹂댁씠硫?Gemini 湲덉? fallback怨?Code Assist `retrieveUserQuota` ?묐떟??癒쇱? ?뺤씤?쒕떎.

## ?뚯뒪??湲곗?

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.local.ProviderModelsTest
.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.TextUsageExtractorTest
.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderLocalUsageCollectorTest
.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.ProviderUsageCollectionServiceTest
.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest --tests com.aiquota.mobile.providers.WebLoginActivityTest
.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug
```

## ?좎?蹂댁닔 泥댄겕由ъ뒪??
- provider蹂??ㅼ젣 API/network ?묐떟 洹쇨굅 ?놁씠 ?レ옄瑜?留뚮뱾吏 ?딆븯??
- plan ?쒖떆紐낆? provider蹂?洹쒖튃??留욊쾶 ?뺢퇋?뷀뻽??
- reset 媛믪씠 ?덈뒗 line??pending 臾멸뎄濡???? ?딆븯??
- ??? ?좊ː??fallback???댁쟾 ?뺤긽 snapshot????? 紐삵븯寃??덈떎.
- Dashboard, provider ?? ?꾩젽??媛숈? snapshot 怨꾩빟???쎈뒗??
- ???섏쭛 議곌굔?먮뒗 unit test瑜?異붽??덈떎.
- Android ?먮??덉씠?곗뿉???쒕툝由?湲곗? snapshot怨?UI ?쒖떆瑜??④퍡 ?뺤씤?덈떎.
