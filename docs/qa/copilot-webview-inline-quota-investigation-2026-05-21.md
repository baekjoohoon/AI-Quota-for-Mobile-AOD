# Copilot WebView Inline Quota Investigation - 2026-05-21

## Context Refresh Checklist

- ??臾몄꽌??而⑦뀓?ㅽ듃 ?뺤텞 ??癒쇱? ?뺤씤?쒕떎.
- Store APK 諛⑹떇 遺꾩꽍? 蹂꾨룄 臾몄꽌?먯꽌 怨꾩냽 吏꾪뻾 以묒씠??
- ??臾몄꽌??踰붿쐞???꾩옱 ?깆쓽 WebView 湲곕컲 Copilot ?섏쭛 寃쎈줈?먯꽌 `Inline suggestions` ?ㅼ젣 quota瑜??살쓣 ???덈뒗吏 寃利앺븯??寃껋씠??
- ?깃났 湲곗?? ?곌껐 ?곹깭媛 ?꾨땲??refresh留덈떎 ?ㅼ젣 provider ?묐떟?먯꽌 `limited_user_quotas.completions`? `monthly_quotas.completions` ?먮뒗 ?숇벑??媛蹂 quota媛 ?섏쭛?섎뒗 寃껋씠??
- 怨좎젙媛? preview sample, 留덉????섏씠吏 ?レ옄, fallback 0%???깃났?쇰줈 蹂댁? ?딅뒗??

## Source Docs Checked

- `PROVIDER_USAGE_COLLECTION.md`
  - Copilot ?곗꽑 ?섏쭛 寃쎈줈: `https://api.github.com/copilot_internal/user`
  - WebView 蹂댁“ 寃쎈줈: `https://github.com/github-copilot/chat/entitlement`
  - ?몃씪???쒖븞: `limited_user_quotas.completions` + `monthly_quotas.completions`
- `APK_PROVIDER_COLLECTION_ANALYSIS.md`
  - Copilot ?쒖옉 URL: `https://github.com/settings/copilot`
  - 媛숈? WebView ?몄뀡?먯꽌 `credentials: include`濡?entitlement瑜??뺤씤?쒕떎.
- `MOBILE_PROVIDER_LOGIN_WIREFRAME_HANDOFF.md`
  - 濡쒓렇???꾨즺 議곌굔? GitHub URL ?꾩갑???꾨땲??Copilot entitlement 200 + payload ?뺤씤?대떎.
- `docs/qa/store-apk-analysis-journal-2026-05-20.md`
  - Store APK Copilot fallback asset? `assets/b`, remote key??`b260503`.
  - Store APK??濡쒓렇????sample 媛믪? ?ㅼ젣 ?섏쭛媛믪쑝濡??ъ슜?섏? ?딅뒗??

## Current Code Path

- `ProviderRefreshPlan`? Copilot refresh瑜?`HIDDEN_WEB_COLLECTOR`濡??ㅽ뻾?쒕떎.
- hidden collector URL? `https://github.com/settings/copilot`?대떎.
- `ProviderWebCollectorScripts.copilot()`? ?ㅼ쓬 ?꾨낫瑜?蹂묐젹 ?몄텧?쒕떎.
  - `https://github.com/github-copilot/chat/entitlement`
  - `https://github.com/github-copilot/chat/token`
  - `https://github.com/settings/copilot`
  - `https://github.com/settings/billing/premium_requests_usage`
- `CopilotNativeUsageFetcher`??native bridge?먯꽌 allowlist??Copilot/GitHub endpoint留??몄텧?쒕떎.
- `ProviderUsageNormalizer`??`limited_user_quotas.completions`? `monthly_quotas.completions`媛 ?ㅼ뼱?ㅻ㈃ `Inline suggestions`瑜??뺤긽 ?앹꽦?쒕떎.

## Evidence So Far

- Logcat?먯꽌 Copilot background refresh???ㅼ쓬源뚯? ?깃났?덈떎.
  - `/github-copilot/chat/entitlement`: 200
  - `/settings/copilot`: 200
  - `/settings/billing/premium_requests_usage`: 200
  - payload ??λ맖
- 1李?蹂寃???logcat?먯꽌 ?ㅼ쓬???뺤씤?먮떎.
  - `/github-copilot/chat/token`: 200
  - token endpoint ?묐떟?쇰줈 API header ?꾨낫 ?앹꽦 媛?? `apiAuth=true`
  - `https://api.github.com/copilot_internal/user`: 401
  - internal user ?묐떟?먮뒗 `limited_user_quotas`, `monthly_quotas`, `completions`, `quota_snapshots`媛 紐⑤몢 ?놁쓬
- ?곕씪??`/github-copilot/chat/token`??token??`Authorization: token <value>`濡?諛붽퓭 ?곕뒗 寃쎈줈???꾩옱 怨꾩젙/?몄뀡?먯꽌 ?ㅽ뙣?쒕떎.
- ?꾩옱 ?꾨씫 吏?먯? normalizer媛 ?꾨땲??collector媛 `completions` ?먮낯??payload???ｌ? 紐삵븯???④퀎??

## Change Under Test

- `CopilotNativeUsageFetcher.copilotApiAuthorizationHeader()` 異붽?.
- `/github-copilot/chat/token` ?묐떟??token 怨꾩뿴 媛믪쓣 GitHub API??`Authorization: token <value>` ?꾨낫濡쒕룄 ?쒗뿕?쒕떎.
- WebView collector??媛숈? ?꾨낫 ?ㅻ뜑瑜?留뚮뱾??`https://api.github.com/copilot_internal/user`瑜??ъ떆?꾪븳??
- ?좏겙 媛믪? 濡쒓렇???④린吏 ?딅뒗??
- 異붽? 濡쒓렇???곹깭? 援ъ“留??④릿??
  - token endpoint status/ok
  - internal user endpoint status/ok
  - settings page raw marker ?꾩튂
  - `limited_user_quotas`, `monthly_quotas`, `completions`, `quota_snapshots` 議댁옱 ?щ?
- `shouldInterceptRequest`?먯꽌 GitHub ?섏씠吏媛 吏곸젒 `https://api.github.com/copilot_internal/user`瑜??몄텧?섎뒗吏 媛먯떆?쒕떎.
  - request??GitHub API??`Authorization: Bearer ...` ?먮뒗 `Authorization: token ...`???덉쑝硫?native?먯꽌 媛숈? ?붿껌??蹂듭젣?쒕떎.
  - ?묐떟??200?닿퀬 completions quota媛 ?덉쑝硫?利됱떆 normalized payload濡???ν븳??
  - Authorization header 媛??먯껜??濡쒓렇???④린吏 ?딅뒗??
- internal user ?묐떟留??ㅼ뼱??寃쎌슦???좊ː payload濡??몄젙?섎룄濡?`limited_user_quotas/monthly_quotas` 議댁옱 議곌굔??異붽??덈떎.
  - 湲곗〈?먮뒗 entitlement??`remaining`?대굹 `quota_snapshots`媛 ?놁쑝硫?internal-only completions payload媛 null 泥섎━?????덉뿀??

## Open Questions

- `/github-copilot/chat/token`??token??`api.github.com/copilot_internal/user`?먯꽌 ?ㅼ젣 GitHub API token?쇰줈 ?몄젙?섎뒗媛?
  - ?꾩옱 寃利?寃곌낵: 401. ??寃쎈줈 ?⑤룆?쇰줈???ㅽ뙣.
- ?몄젙?섏? ?딅뒗?ㅻ㈃ ?꾩옱 WebView 諛⑹떇留뚯쑝濡?GitHub OAuth token???살쓣 ???덈뒗 ?ㅻⅨ page state, embeddedData, JS chunk, response hook???덈뒗媛?
- `/settings/copilot` HTML ?먮뒗 embedded app state??`limited_user_quotas.completions`媛 ?ㅼ뼱?ㅼ?留??꾩옱 parser媛 ?볦튂?붽??
- Store APK `b260503` collector???????꾨낫 以??대뒓 ?먮낯?먯꽌 inline suggestions瑜??삳뒗媛?
- GitHub ?섏씠吏 ?먯껜媛 `/copilot_internal/user`瑜??몄텧?쒕떎硫?WebView request header??API Authorization???ㅻ━?붽??

## Next Verification

1. ?⑥쐞 ?뚯뒪???ㅽ뻾.
2. debug APK 鍮뚮뱶/?ㅼ튂.
3. Copilot refresh ?ㅽ뻾.
4. logcat?먯꽌 `AIQuotaCopilot token status=`, `AIQuotaCopilot internal status=` ?뺤씤.
5. logcat?먯꽌 `resource=/copilot_internal/user hasAuth=` ?뺤씤.
6. `internal ok=true`? `completions=true` ?먮뒗 `resourceInternal payload=true`媛 ?섏삤硫?dashboard??`Inline suggestions`媛 ?좎빞 ?쒕떎.
7. `internal 401/403`?닿퀬 resource hook??`hasAuth=false` ?먮뒗 誘몃컻?앹씠硫?WebView ?몄뀡留뚯쑝濡쒕뒗 GitHub API token???놁쑝誘濡?Store APK `b260503` runtime capture ?먮뒗 GitHub page state token 異붿쟻???꾩슂?섎떎.

## Verification Log

- `:app:testDebugUnitTest --tests CopilotNativeUsageFetcherTest --tests ProviderWebCollectorScriptsTest`: PASS
- `:app:testDebugUnitTest --tests ProviderLoginStrategyTest --tests CopilotNativeUsageFetcherTest --tests ProviderWebCollectorScriptsTest`: PASS
- `:app:assembleDebug`: PASS
- `app-debug.apk` installed to `emulator-5554`: PASS
- Current emulator state after reinstall: hidden Copilot refresh reaches `github.com/login`, so account-session runtime verification is pending until GitHub login is completed again.
- 2026-05-21 06:47 KST targeted tests after session-internal endpoint change: PASS
  - `ProviderRefreshPlanTest`
  - `ProviderWebCollectorScriptsTest`
  - `CopilotNativeUsageFetcherTest`
  - `UsageResetTextTest`
- 2026-05-21 06:47 KST `:app:assembleDebug`: PASS
- 2026-05-21 06:48 KST `app-debug.apk` installed to `emulator-5554`: PASS
- 2026-05-21 06:49 KST Copilot refresh after reinstall:
  - hidden collector landed on `github.com/login`.
  - Current emulator no longer has a valid GitHub WebView session, so inline endpoint runtime verification cannot be completed from this state.
  - No success claim is made for `Inline suggestions`.

## 2026-05-21 - GitHub Dashboard After Login

Issue:
- ?ъ슜?먭? GitHub 濡쒓렇???꾨즺 ???깆쑝濡??뚯븘媛吏 ?딄퀬 GitHub `Dashboard/Home` ?붾㈃??癒몃Т???곹깭瑜??뺤씤?덈떎.

Root Cause:
- Copilot collector??`/settings/copilot`, `/settings/billing/*`, `/github-copilot/*`?먯꽌留??ㅽ뻾?쒕떎.
- GitHub媛 濡쒓렇?????먮옒 ?붿껌 URL??`/settings/copilot` ?????쒕낫?쒕줈 蹂대궪 寃쎌슦 collector媛 ?ㅽ뻾?섏? ?딆븘 ??蹂듦?媛 諛쒖깮?섏? ?딅뒗??

Change:
- `ProviderLoginStrategy.shouldRedirectCopilotToSettings()` 異붽?.
- Copilot 濡쒓렇??WebView媛 `github.com/` ?먮뒗 `github.com/dashboard` ?먮뒗 signed-in dashboard text瑜?媛먯??섎㈃ `/settings/copilot`濡?1?뚮쭔 ?ъ쭊?낇븳??
- `/login`, `/sessions`, two-factor, `/settings/copilot`, `/github-copilot` 寃쎈줈?먯꽌???ъ쭊?낇븯吏 ?딅뒗??

Expected:
- GitHub ?몄쬆 ?꾨즺 ????쒕낫?쒖뿉 硫덉텛硫??깆씠 ?먮룞?쇰줈 `https://github.com/settings/copilot`???ㅼ떆 濡쒕뱶?쒕떎.
- 洹???Copilot collector媛 ?ㅽ뻾?섍퀬 usage payload ?섏쭛/??蹂듦?瑜??쒕룄?쒕떎.

## 2026-05-21 - Web Session Internal Endpoint Attempt

Root Cause Status:
- Current WebView refresh still does not have a confirmed `Inline suggestions` source.
- Confirmed prior evidence:
  - `/github-copilot/chat/entitlement`: 200
  - `/github-copilot/chat/token`: 200
  - `/settings/copilot`: 200
  - `/settings/billing/premium_requests_usage`: 200
  - `https://api.github.com/copilot_internal/user` with the Copilot chat token as API token: 401
  - `/settings/copilot` HTML marker had `completions`, but not `limited_user_quotas` or `monthly_quotas`.

Change:
- Added a second real endpoint attempt:
  - `https://github.com/copilot_internal/user`
  - Same GitHub WebView session cookies, no fake payload.
- Added safe console marker:
  - `AIQuotaCopilot internal_session status=... ok=... usage=limited=... monthly=... completions=...`

Verification Gate:
- Success requires `internal_session ok=true` with `completions=true`, or a hooked `/copilot_internal/user` resource with payload=true.
- If it still lands on `github.com/login`, the session is gone and the app must reconnect before endpoint verification.
- If it returns non-200 after reconnect, current WebView session alone is not enough and the next implementation path is real GitHub OAuth token acquisition or Store APK `b260503` decrypted/runtime collector evidence.

## 2026-05-21 - Claude Session Reset Display

Root Cause:
- Claude 5-hour session row had an expired `resetsAt`.
- `UsageResetText.effectiveResetText()` converted expired reset to `Starts when a message is sent`, then suppressed that text when remaining percent was below 99.5%.
- Result: the session row kept stale usage percent but displayed no reset text.

Change:
- Removed that suppression.
- Expired reset now displays the provider-compatible message instead of blank.
- This is display semantics only; it does not fabricate usage values.

Runtime Evidence:
- After reinstall, Claude UI shows `硫붿떆吏瑜?蹂대궡硫??쒖옉` under `Claude Session`.
- Hidden refresh currently redirects to `claude.ai/login`; the emulator WebView session is not authenticated, so fresh Claude usage cannot be collected until Claude is reconnected.
