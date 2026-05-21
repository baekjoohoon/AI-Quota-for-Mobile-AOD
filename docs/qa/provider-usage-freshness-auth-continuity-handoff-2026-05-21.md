# Provider Usage Freshness/Auth Continuity Handoff

Date: 2026-05-21

## 새 세션 목적

`D:/Vibe Project/AI Usage for Mobile`에서 provider usage freshness와 auth continuity를 구현한다. 핵심은 세션 유지 자체가 아니라, 사용자가 보는 usage 데이터가 stale/expired/current 상태를 정확히 반영하도록 만드는 것이다.

## 필수 지침

- 사용자-facing 답변과 문서는 한국어로 작성한다.
- 사용하지 않는 MCP는 호출하지 않는다.
- 큰 파일 읽기, 검색, shell은 가능하면 lean-ctx를 사용한다.
- Store APK provider implementation-ready가 아닌 상태에서 Store APK collector parity 구현을 주장하지 않는다.
- raw token, raw cookie, OAuth secret/callback code, auth header value, account identifier, email, username, full provider HTML을 문서에 쓰지 않는다.
- 구현 중 진행/실패/트러블슈팅은 `docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md`에 남긴다.

## 기준 문서

Spec:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/specs/2026-05-21-provider-usage-freshness-auth-continuity-spec.md`

Plan:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/plans/2026-05-21-provider-usage-freshness-auth-continuity.md`

Progress/troubleshooting:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md`

New-session prompt:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/provider-usage-freshness-auth-continuity-new-session-prompt-2026-05-21.md`

Related Store APK handoff:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-context-handoff-2026-05-20.md`

## 결정된 방향

가장 중요한 목표:

- 이전 usage를 최신 usage처럼 보여주지 않는다.
- 과거 값을 표시할 수는 있지만 stale/last-known 표시가 반드시 필요하다.
- reset이 지난 volatile row는 제거한다.

Provider별 방향:

| Provider | 권장 구조 | 이유 |
| --- | --- | --- |
| Claude | WebView session 유지 | Claude usage는 web session/cookie collector가 현실적이다. |
| Codex | WebView session 유지 | ChatGPT session 기반 usage probe/collector가 필요하다. |
| Copilot | OAuth/token 기반 native API 우선 | GitHub WebView cookie만으로 inline quota가 안정적이지 않다. Chrome/Custom Tab login은 app WebView cookie를 만들지 않는다. |
| Gemini | AppAuth + Code Assist quota API | WebView scraping보다 token/API 구조가 맞다. |
| Cursor | token/API 우선, 안 되면 same WebView profile fallback | dashboard cookie/API 접근이 불안정하다. |

핵심 구조:

- login 완료 조건은 URL 도착이 아니라 trusted usage row 저장 성공이다.
- connected는 trusted usage payload 저장 후에만 된다.
- refresh 전 provider별 auth/session probe를 먼저 실행한다.
- probe 실패 시 collector를 돌리지 않는다.
- WebView collector provider는 login과 collection이 같은 app WebView profile을 써야 한다.
- token/API provider는 native token store와 native HTTP collection을 사용한다.

## 현재 코드 상태 요약

현재 relevant files:

- `android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`
- `android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`
- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderDefinitions.kt`
- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshPlan.kt`
- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderUsageCollectionService.kt`
- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderUsageNormalizer.kt`
- `android/app/src/main/java/com/aiusage/mobile/providers/CopilotNativeUsageFetcher.kt`
- `android/app/src/main/java/com/aiusage/mobile/ui/BackgroundProviderWebCollector.kt`
- `android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`
- `android/app/src/main/java/com/aiusage/mobile/ui/dashboard/UnifiedDashboardScreen.kt`
- `android/app/src/main/java/com/aiusage/mobile/ui/provider/ProviderDetailScreen.kt`

Known current behavior:

- `ProviderConnectionState.STALE` exists but dashboard/detail currently maps `STALE` to `Connected`.
- `LocalUsageRepository.readSnapshots()` removes expired volatile rows only for Claude `claude:session` and Codex `codex:primary_window`.
- `ForegroundRefreshPolicy.connectedProviders()` auto-refreshes `CONNECTED`, `COLLECTING`, `STALE` snapshots when they have lines.
- `ProviderUsageCollectionService` only saves `CONNECTED` when normalizer returns a non-null snapshot with lines.
- `BackgroundProviderWebCollector` currently reports login redirect as string error.
- `ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS` is `60_000L`.

## Implementation Start Point

Start with plan Task 1:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/plans/2026-05-21-provider-usage-freshness-auth-continuity.md`

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
