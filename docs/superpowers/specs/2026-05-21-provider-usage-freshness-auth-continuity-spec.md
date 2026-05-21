# Provider Usage Freshness and Auth Continuity Spec

Date: 2026-05-21

## Objective

AI Usage Android app은 provider별로 한 번 로그인한 뒤, 가능한 한 보이지 않는 인증 세션 또는 native token/API를 사용해 1분 단위로 usage를 수집해야 한다. 더 중요한 목표는 수집 데이터 신뢰성이다. 최신 수집이 실패했는데 과거 usage를 최신값처럼 계속 보여주면 안 된다.

Primary implementation plan:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/plans/2026-05-21-provider-usage-freshness-auth-continuity.md`

Progress and troubleshooting journal:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md`

New-session handoff:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/provider-usage-freshness-auth-continuity-handoff-2026-05-21.md`

New-session prompt:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/provider-usage-freshness-auth-continuity-new-session-prompt-2026-05-21.md`

Related Store APK analysis:

- `D:/Vibe Project/AI Usage for Mobile/docs/superpowers/specs/2026-05-20-store-apk-provider-reverse-engineering-spec.md`
- `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-context-handoff-2026-05-20.md`
- `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-provider-evidence-2026-05-20.md`
- `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-parity-gap-tracker-2026-05-20.md`

## Problem Statement

현재 앱은 hidden refresh가 실패해도 이전 usage lines가 있으면 snapshot을 `STALE`로 보존한다. 하지만 UI는 `STALE`을 `Connected`처럼 표시한다. 사용자는 현재 usage로 오해할 수 있다.

또한 refresh가 `/login` 또는 auth page로 리다이렉트될 때 즉시 실패 처리하면 1분마다 같은 실패가 반복될 수 있다. 반대로 과거 값을 계속 보여주면 제품 가치는 떨어진다.

따라서 우선순위는 다음 순서다.

1. usage freshness를 정확히 표시한다.
2. reset이 지난 volatile row를 제거한다.
3. login/auth/session probe와 collector success를 분리한다.
4. provider별로 맞는 인증 저장소를 사용한다.
5. 세션 유지는 위 조건을 만족시키기 위한 수단으로 구현한다.

## Definitions

Trusted usage payload:

- provider별 normalizer가 성공적으로 parse했다.
- 최소 1개 이상의 usage row가 생성됐다.
- source가 DOM text fallback만인 payload가 아니다.
- hardcoded preview/sample/fake row가 아니다.

Fresh usage:

- 최근 trusted usage payload에서 저장됐다.
- provider-specific volatile row의 reset time이 지나지 않았다.
- latest refresh 실패 상태가 아니다.

Stale usage:

- 과거에는 trusted usage payload였지만 최근 refresh가 실패했거나 freshness TTL을 넘었다.
- 화면에 표시할 수 있으나 `Connected` 또는 최신값처럼 표시하면 안 된다.
- 반드시 last successful update 또는 stale status를 함께 보여준다.

Expired volatile row:

- provider별 reset window가 이미 지난 row다.
- Claude session, Codex primary window, Copilot monthly/inline quota, Gemini quota window, Cursor dashboard quota처럼 reset time이 의미 있는 row가 대상이다.
- expired volatile row는 현재 사용량으로 표시하지 않는다.

Connected provider:

- login page 도착, OAuth redirect 성공, session cookie 감지, account page 감지는 connected가 아니다.
- connected는 trusted usage payload 저장 성공 이후에만 허용한다.

Auth store:

- WebView profile: 앱 내부 WebView의 cookie/DOM storage/local storage.
- Native token store: AppAuth 또는 provider OAuth/token 저장소.
- Hybrid fallback: token/API 우선, 불가하면 같은 앱 WebView profile을 사용하는 collector로 fallback.

## Requirements

### R1. Stale Display

이전 값을 계속 표시할 수 있다. 단, 사용자가 최신 usage라고 오해하지 않게 표시해야 한다.

Required UI behavior:

- `STALE` snapshot은 dashboard/detail에서 `Connected`가 아니라 `Needs refresh` 또는 `Stale` 계열 상태로 표시한다.
- detail 화면에는 last successful update를 표시한다.
- stale 상태의 row는 그대로 current gauge처럼 보일 수 있으므로 status text가 가까운 위치에 있어야 한다.
- all rows가 expired로 제거되면 이전 값 gauge를 보여주지 않는다.

### R2. Expired Row Removal

reset time이 지난 volatile row는 저장소 read 단계에서 제거한다.

Initial policy:

| Provider | Volatile rows |
| --- | --- |
| Claude | `claude:session` |
| Codex | `codex:primary_window` |
| Copilot | `copilot:chat`, `copilot:completions`, `copilot:premium_requests` |
| Gemini | `gemini:*` rows with `resetsAt` |
| Cursor | rows with `resetsAt` |

If all rows are removed, provider remains stale/unavailable and does not show old gauges.

### R3. Connected Gate

Provider must become `CONNECTED` only after trusted usage payload is saved.

Invalid connected signals:

- login URL reached,
- OAuth redirect reached,
- provider home/dashboard page loaded,
- session cookie exists,
- account probe succeeds without usage row.

Valid connected signal:

- session/auth probe succeeds,
- usage API/collector succeeds,
- normalizer returns at least one usage row,
- repository saves the snapshot.

### R4. Provider Strategy

| Provider | Required structure | Reason |
| --- | --- | --- |
| Claude | WebView session-based login and collector | Claude usage is realistically tied to web session/cookie collector. |
| Codex | WebView session-based login and collector | ChatGPT/Codex usage probe such as `/backend-api/wham/usage` depends on ChatGPT session. |
| Copilot | OAuth/token-backed native collection first | GitHub WebView cookie scraping does not reliably prove inline quota. Chrome/Custom Tab auth does not populate app WebView cookies. |
| Gemini | AppAuth + Code Assist quota API | Token/API structure fits better than WebView scraping. |
| Cursor | Token/API first; same WebView profile fallback if token path is unavailable | Dashboard cookie/API access is unstable if auth store is not unified. |

### R5. Unified Auth Store

Login and collection must use the same auth store.

Rules:

- WebView provider login must happen inside app WebView if collection uses WebView.
- Chrome Custom Tab login must not feed a WebView collector unless token/API collection is used.
- Token/API provider must store token state in the native token store and collect through native HTTP.

### R6. Probe Before Collection

Every refresh job must run a provider-appropriate probe before expensive collection.

Examples:

- Claude: authenticated Claude web session probe.
- Codex: ChatGPT/Codex account or usage endpoint probe.
- Copilot: GitHub auth/token probe.
- Gemini: Code Assist token/quota probe.
- Cursor: token/API auth probe, then dashboard/API fallback probe.

Probe failure behavior:

- Do not run collector when auth probe fails.
- Mark `INTERACTIVE_AUTH_REQUIRED` only when silent/session/token recovery has failed.
- Mark transient errors separately from auth-required failures.

### R7. One-Minute Collection

When user enables precise refresh, the app should collect roughly every 60 seconds using a foreground execution path.

Rules:

- Do not claim exact 1-minute collection without foreground execution guarantee.
- If Android only allows background work, UI must describe that standard refresh is best-effort.
- Widgets must use the same trusted snapshot/freshness policy as the app UI.

### R8. No Fake Data

Never add or preserve hardcoded live usage values.

Forbidden:

- hardcoded quota rows,
- Store APK preview/sample rows as live rows,
- DOM-only low-confidence text as connected proof,
- stale rows shown as current usage.

### R9. Documentation During Work

The implementation session must update:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md`

Update after:

- every meaningful code change,
- every focused test run,
- every failed test that changes the plan,
- every auth/provider blocker,
- every runtime verification pass/failure,
- every accepted tradeoff.

Record:

- action,
- result,
- files changed,
- commands run,
- observed failure,
- troubleshooting decision,
- next action.

Redaction:

- Do not write raw tokens, cookies, OAuth codes, auth headers, account identifiers, emails, usernames, or full provider HTML.
- Use `<email>` and `<username>` where needed.
- Keep endpoint paths, status codes, JSON key names, quota numeric shapes, reset field names, and row labels.

## Acceptance Criteria

Data correctness:

- A provider with stale data is visibly marked stale or needs refresh.
- Expired volatile rows are not displayed as current usage.
- `CONNECTED` is reachable only after at least one trusted usage row is saved.
- Refresh failure does not silently make old rows look fresh.

Auth continuity:

- Claude and Codex use in-app WebView auth for WebView collection.
- Copilot implementation direction is token/API first.
- Gemini uses token/API flow.
- Cursor uses token/API first when available, same WebView profile fallback otherwise.

Collection:

- Automatic jobs continue every 60 seconds for providers that are connected or recoverable.
- Providers requiring user interaction are not retried silently forever.
- Successful refresh updates `updatedAt`, lines, plan/account when available, and clears stale status.

Tests:

- Unit tests cover stale display state, expired row removal, connected gate, refresh policy, and provider strategy mapping.
- Existing normalizer tests continue to pass.
- Full `:app:testDebugUnitTest` passes before implementation is considered complete.

## Non-Goals

- Do not complete Store APK collector parity from static-only evidence.
- Do not implement Antigravity until product scope and evidence are approved.
- Do not promote Cursor as Store APK-supported.
- Do not bypass provider security or interactive auth requirements.
- Do not write secrets or raw provider HTML into docs.

## Open Engineering Notes

- Store APK runtime collector evidence is still blocked by missing split/native library. This spec is for current app reliability and UX, not proof of Store APK collector parity.
- Copilot token/API collection may still require provider-approved scopes and API behavior validation.
- If a provider refuses silent recovery, the correct UX is a clear but quiet reconnect cue, not fake continuity.
