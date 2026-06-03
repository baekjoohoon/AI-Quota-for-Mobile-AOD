# Provider Usage Freshness/Auth Continuity Handoff

Date: 2026-05-21

## ???몄뀡 紐⑹쟻

`D:/Vibe Project/AI Quota for Mobile`?먯꽌 provider usage freshness? auth continuity瑜?援ы쁽?쒕떎. ?듭떖? ?몄뀡 ?좎? ?먯껜媛 ?꾨땲?? ?ъ슜?먭? 蹂대뒗 usage ?곗씠?곌? stale/expired/current ?곹깭瑜??뺥솗??諛섏쁺?섎룄濡?留뚮뱶??寃껋씠??

## ?꾩닔 吏移?
- ?ъ슜??facing ?듬?怨?臾몄꽌???쒓뎅?대줈 ?묒꽦?쒕떎.
- ?ъ슜?섏? ?딅뒗 MCP???몄텧?섏? ?딅뒗??
- ???뚯씪 ?쎄린, 寃?? shell? 媛?ν븯硫?lean-ctx瑜??ъ슜?쒕떎.
- Store APK provider implementation-ready媛 ?꾨땶 ?곹깭?먯꽌 Store APK collector parity 援ы쁽??二쇱옣?섏? ?딅뒗??
- raw token, raw cookie, OAuth secret/callback code, auth header value, account identifier, email, username, full provider HTML??臾몄꽌???곗? ?딅뒗??
- 援ы쁽 以?吏꾪뻾/?ㅽ뙣/?몃윭釉붿뒋?낆? `docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md`???④릿??

## 湲곗? 臾몄꽌

Spec:

`D:/Vibe Project/AI Quota for Mobile/docs/superpowers/specs/2026-05-21-provider-usage-freshness-auth-continuity-spec.md`

Plan:

`D:/Vibe Project/AI Quota for Mobile/docs/superpowers/plans/2026-05-21-provider-usage-freshness-auth-continuity.md`

Progress/troubleshooting:

`D:/Vibe Project/AI Quota for Mobile/docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md`

New-session prompt:

`D:/Vibe Project/AI Quota for Mobile/docs/qa/provider-usage-freshness-auth-continuity-new-session-prompt-2026-05-21.md`

Related Store APK handoff:

`D:/Vibe Project/AI Quota for Mobile/docs/qa/store-apk-context-handoff-2026-05-20.md`

## 寃곗젙??諛⑺뼢

媛??以묒슂??紐⑺몴:

- ?댁쟾 usage瑜?理쒖떊 usage泥섎읆 蹂댁뿬二쇱? ?딅뒗??
- 怨쇨굅 媛믪쓣 ?쒖떆???섎뒗 ?덉?留?stale/last-known ?쒖떆媛 諛섎뱶???꾩슂?섎떎.
- reset??吏??volatile row???쒓굅?쒕떎.

Provider蹂?諛⑺뼢:

| Provider | 沅뚯옣 援ъ“ | ?댁쑀 |
| --- | --- | --- |
| Claude | WebView session ?좎? | Claude usage??web session/cookie collector媛 ?꾩떎?곸씠?? |
| Codex | WebView session ?좎? | ChatGPT session 湲곕컲 usage probe/collector媛 ?꾩슂?섎떎. |
| Copilot | OAuth/token 湲곕컲 native API ?곗꽑 | GitHub WebView cookie留뚯쑝濡?inline quota媛 ?덉젙?곸씠吏 ?딅떎. Chrome/Custom Tab login? app WebView cookie瑜?留뚮뱾吏 ?딅뒗?? |
| Gemini | AppAuth + Code Assist quota API | WebView scraping蹂대떎 token/API 援ъ“媛 留욌떎. |
| Cursor | token/API ?곗꽑, ???섎㈃ same WebView profile fallback | dashboard cookie/API ?묎렐??遺덉븞?뺥븯?? |

?듭떖 援ъ“:

- login ?꾨즺 議곌굔? URL ?꾩갑???꾨땲??trusted usage row ????깃났?대떎.
- connected??trusted usage payload ????꾩뿉留??쒕떎.
- refresh ??provider蹂?auth/session probe瑜?癒쇱? ?ㅽ뻾?쒕떎.
- probe ?ㅽ뙣 ??collector瑜??뚮━吏 ?딅뒗??
- WebView collector provider??login怨?collection??媛숈? app WebView profile???⑥빞 ?쒕떎.
- token/API provider??native token store? native HTTP collection???ъ슜?쒕떎.

## ?꾩옱 肄붾뱶 ?곹깭 ?붿빟

?꾩옱 relevant files:

- `android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt`
- `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/CopilotNativeUsageFetcher.kt`
- `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt`
- `android/app/src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt`
- `android/app/src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt`
- `android/app/src/main/java/com/aiquota/mobile/ui/provider/ProviderDetailScreen.kt`

Known current behavior:

- `ProviderConnectionState.STALE` exists but dashboard/detail currently maps `STALE` to `Connected`.
- `LocalUsageRepository.readSnapshots()` removes expired volatile rows only for Claude `claude:session` and Codex `codex:primary_window`.
- `ForegroundRefreshPolicy.connectedProviders()` auto-refreshes `CONNECTED`, `COLLECTING`, `STALE` snapshots when they have lines.
- `ProviderUsageCollectionService` only saves `CONNECTED` when normalizer returns a non-null snapshot with lines.
- `BackgroundProviderWebCollector` currently reports login redirect as string error.
- `ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS` is `60_000L`.

## Implementation Start Point

Start with plan Task 1:

`D:/Vibe Project/AI Quota for Mobile/docs/superpowers/plans/2026-05-21-provider-usage-freshness-auth-continuity.md`

Recommended sequence:

1. Add freshness model and tests.
2. Add volatile row expiry policy.
3. Update UI stale display.
4. Add auth-required fallback state.
5. Add provider strategy map.
6. Route refresh failure kinds.
7. Adjust refresh plan by provider strategy.
8. Run focused tests then full unit suite.
9. Record each step in progress journal.

## Store APK Boundary

Store APK analysis is still useful for architecture, but implementation readiness for full Store APK collector parity is blocked.

Known blocker:

- Source APK base file requires `base__abi` and `base__density` splits.
- Base APK has no `lib/<abi>/libs.so`.
- Native decrypt key source for `CryptoUtils.r()` is missing.
- Runtime collector source and live provider payloads are not captured.

This work must not invent Store APK collector behavior.

## Done Criteria

- Stale usage is clearly displayed as stale or needs refresh.
- Expired volatile rows are removed.
- No failed refresh makes old data look current.
- Connected means trusted usage row saved.
- Provider strategy tests match the approved provider table.
- `.\gradlew.bat :app:testDebugUnitTest` passes.
- Progress journal includes implementation and troubleshooting history.
