# Copilot WebView Inline Quota Investigation - 2026-05-21

## Context Refresh Checklist

- 이 문서는 컨텍스트 압축 후 먼저 확인한다.
- Store APK 방식 분석은 별도 문서에서 계속 진행 중이다.
- 이 문서의 범위는 현재 앱의 WebView 기반 Copilot 수집 경로에서 `Inline suggestions` 실제 quota를 얻을 수 있는지 검증하는 것이다.
- 성공 기준은 연결 상태가 아니라 refresh마다 실제 provider 응답에서 `limited_user_quotas.completions`와 `monthly_quotas.completions` 또는 동등한 가변 quota가 수집되는 것이다.
- 고정값, preview sample, 마케팅 페이지 숫자, fallback 0%는 성공으로 보지 않는다.

## Source Docs Checked

- `PROVIDER_USAGE_COLLECTION.md`
  - Copilot 우선 수집 경로: `https://api.github.com/copilot_internal/user`
  - WebView 보조 경로: `https://github.com/github-copilot/chat/entitlement`
  - 인라인 제안: `limited_user_quotas.completions` + `monthly_quotas.completions`
- `APK_PROVIDER_COLLECTION_ANALYSIS.md`
  - Copilot 시작 URL: `https://github.com/settings/copilot`
  - 같은 WebView 세션에서 `credentials: include`로 entitlement를 확인한다.
- `MOBILE_PROVIDER_LOGIN_WIREFRAME_HANDOFF.md`
  - 로그인 완료 조건은 GitHub URL 도착이 아니라 Copilot entitlement 200 + payload 확인이다.
- `docs/qa/store-apk-analysis-journal-2026-05-20.md`
  - Store APK Copilot fallback asset은 `assets/b`, remote key는 `b260503`.
  - Store APK의 로그인 전 sample 값은 실제 수집값으로 사용하지 않는다.

## Current Code Path

- `ProviderRefreshPlan`은 Copilot refresh를 `HIDDEN_WEB_COLLECTOR`로 실행한다.
- hidden collector URL은 `https://github.com/settings/copilot`이다.
- `ProviderWebCollectorScripts.copilot()`은 다음 후보를 병렬 호출한다.
  - `https://github.com/github-copilot/chat/entitlement`
  - `https://github.com/github-copilot/chat/token`
  - `https://github.com/settings/copilot`
  - `https://github.com/settings/billing/premium_requests_usage`
- `CopilotNativeUsageFetcher`는 native bridge에서 allowlist된 Copilot/GitHub endpoint만 호출한다.
- `ProviderUsageNormalizer`는 `limited_user_quotas.completions`와 `monthly_quotas.completions`가 들어오면 `Inline suggestions`를 정상 생성한다.

## Evidence So Far

- Logcat에서 Copilot background refresh는 다음까지 성공했다.
  - `/github-copilot/chat/entitlement`: 200
  - `/settings/copilot`: 200
  - `/settings/billing/premium_requests_usage`: 200
  - payload 저장됨
- 1차 변경 후 logcat에서 다음이 확인됐다.
  - `/github-copilot/chat/token`: 200
  - token endpoint 응답으로 API header 후보 생성 가능: `apiAuth=true`
  - `https://api.github.com/copilot_internal/user`: 401
  - internal user 응답에는 `limited_user_quotas`, `monthly_quotas`, `completions`, `quota_snapshots`가 모두 없음
- 따라서 `/github-copilot/chat/token`의 token을 `Authorization: token <value>`로 바꿔 쓰는 경로는 현재 계정/세션에서 실패한다.
- 현재 누락 지점은 normalizer가 아니라 collector가 `completions` 원본을 payload에 넣지 못하는 단계다.

## Change Under Test

- `CopilotNativeUsageFetcher.copilotApiAuthorizationHeader()` 추가.
- `/github-copilot/chat/token` 응답의 token 계열 값을 GitHub API용 `Authorization: token <value>` 후보로도 시험한다.
- WebView collector도 같은 후보 헤더를 만들어 `https://api.github.com/copilot_internal/user`를 재시도한다.
- 토큰 값은 로그에 남기지 않는다.
- 추가 로그는 상태와 구조만 남긴다.
  - token endpoint status/ok
  - internal user endpoint status/ok
  - settings page raw marker 위치
  - `limited_user_quotas`, `monthly_quotas`, `completions`, `quota_snapshots` 존재 여부
- `shouldInterceptRequest`에서 GitHub 페이지가 직접 `https://api.github.com/copilot_internal/user`를 호출하는지 감시한다.
  - request에 GitHub API용 `Authorization: Bearer ...` 또는 `Authorization: token ...`이 있으면 native에서 같은 요청을 복제한다.
  - 응답이 200이고 completions quota가 있으면 즉시 normalized payload로 저장한다.
  - Authorization header 값 자체는 로그에 남기지 않는다.
- internal user 응답만 들어온 경우도 신뢰 payload로 인정하도록 `limited_user_quotas/monthly_quotas` 존재 조건을 추가했다.
  - 기존에는 entitlement의 `remaining`이나 `quota_snapshots`가 없으면 internal-only completions payload가 null 처리될 수 있었다.

## Open Questions

- `/github-copilot/chat/token`의 token이 `api.github.com/copilot_internal/user`에서 실제 GitHub API token으로 인정되는가?
  - 현재 검증 결과: 401. 이 경로 단독으로는 실패.
- 인정되지 않는다면 현재 WebView 방식만으로 GitHub OAuth token을 얻을 수 있는 다른 page state, embeddedData, JS chunk, response hook이 있는가?
- `/settings/copilot` HTML 또는 embedded app state에 `limited_user_quotas.completions`가 들어오지만 현재 parser가 놓치는가?
- Store APK `b260503` collector는 위 세 후보 중 어느 원본에서 inline suggestions를 얻는가?
- GitHub 페이지 자체가 `/copilot_internal/user`를 호출한다면 WebView request header에 API Authorization이 실리는가?

## Next Verification

1. 단위 테스트 실행.
2. debug APK 빌드/설치.
3. Copilot refresh 실행.
4. logcat에서 `AIUsageCopilot token status=`, `AIUsageCopilot internal status=` 확인.
5. logcat에서 `resource=/copilot_internal/user hasAuth=` 확인.
6. `internal ok=true`와 `completions=true` 또는 `resourceInternal payload=true`가 나오면 dashboard에 `Inline suggestions`가 떠야 한다.
7. `internal 401/403`이고 resource hook도 `hasAuth=false` 또는 미발생이면 WebView 세션만으로는 GitHub API token이 없으므로 Store APK `b260503` runtime capture 또는 GitHub page state token 추적이 필요하다.

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
- 사용자가 GitHub 로그인 완료 후 앱으로 돌아가지 않고 GitHub `Dashboard/Home` 화면에 머무는 상태를 확인했다.

Root Cause:
- Copilot collector는 `/settings/copilot`, `/settings/billing/*`, `/github-copilot/*`에서만 실행된다.
- GitHub가 로그인 후 원래 요청 URL인 `/settings/copilot` 대신 대시보드로 보낼 경우 collector가 실행되지 않아 앱 복귀가 발생하지 않는다.

Change:
- `ProviderLoginStrategy.shouldRedirectCopilotToSettings()` 추가.
- Copilot 로그인 WebView가 `github.com/` 또는 `github.com/dashboard` 또는 signed-in dashboard text를 감지하면 `/settings/copilot`로 1회만 재진입한다.
- `/login`, `/sessions`, two-factor, `/settings/copilot`, `/github-copilot` 경로에서는 재진입하지 않는다.

Expected:
- GitHub 인증 완료 후 대시보드에 멈추면 앱이 자동으로 `https://github.com/settings/copilot`을 다시 로드한다.
- 그 뒤 Copilot collector가 실행되고 usage payload 수집/앱 복귀를 시도한다.

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
  - `AIUsageCopilot internal_session status=... ok=... usage=limited=... monthly=... completions=...`

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
- After reinstall, Claude UI shows `메시지를 보내면 시작` under `Claude Session`.
- Hidden refresh currently redirects to `claude.ai/login`; the emulator WebView session is not authenticated, so fresh Claude usage cannot be collected until Claude is reconnected.
