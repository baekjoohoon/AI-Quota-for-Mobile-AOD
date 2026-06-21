# Provider refresh/session baseline - 2026-06-21

이 문서는 provider refresh/session 코드가 다시 꼬였을 때 비교할 기준선이다.
현재 기준은 "세션을 유지한 상태에서 실제 usage payload를 수집하고, 실패를 stale time 갱신으로 숨기지 않는다"이다.

## 현재 정상 동작 기준

- Foreground refresh는 연결된 provider를 대시보드 snapshot 순서대로 순회한다.
- refresh 대상 provider는 `Claude`, `Codex`, `OpenCode`, `Gemini`, `Copilot`, `Antigravity`, `Cursor`이다.
- `GLM`은 현재 에뮬레이터에 snapshot 파일이 없어서 런타임 검증 대상이 아니다. 코드상으로는 API key 모드와 Web OAuth 모드가 모두 존재한다.
- hidden WebView 기반 provider는 WebView/cookie/session을 가능한 한 유지한다.
- refresh 중 usage payload가 실제로 들어왔을 때만 `updatedAt`이 새 snapshot 시각으로 전진한다.
- 수집 실패, auth required, stale clearing은 `statusUpdatedAt`과 `message`를 바꿀 수 있지만 기존 usage line이 있으면 `updatedAt`을 임의 갱신하지 않는다.
- 실패를 숨기기 위한 hardcoded fallback, fake usage, 임의 `updatedAt` 변경은 금지한다.

## 2026-06-21 런타임 검증 스냅샷

에뮬레이터에서 logcat을 지운 뒤 1분 단위로 15분 연속 관찰했다.

대상:

- `claude`
- `codex`
- `opencode`
- `gemini`
- `copilot`
- `antigravity`
- `cursor`

최종 sample 15:

| provider | state | refresh | updatedAt | lines |
| --- | --- | --- | --- | --- |
| claude | CONNECTED | IDLE | 2026-06-21T13:38:06.444649Z | 3 |
| codex | CONNECTED | IDLE | 2026-06-21T13:38:06.852257Z | 2 |
| opencode | CONNECTED | IDLE | 2026-06-21T13:38:08.032755Z | 3 |
| gemini | CONNECTED | IDLE | 2026-06-21T13:38:11.795309Z | 2 |
| copilot | CONNECTED | IDLE | 2026-06-21T13:38:16.226208Z | 2 |
| antigravity | CONNECTED | IDLE | 2026-06-21T13:38:19.225656Z | 14 |
| cursor | CONNECTED | IDLE | 2026-06-21T13:38:23.032789Z | 3 |

관찰 로그:

- `cycleStart providers=claude,codex,opencode,gemini,copilot,antigravity,cursor`가 13:24부터 13:39까지 매분 반복됐다.
- `Gemini`는 각 cycle에서 `outcome provider=gemini type=Payload`가 반복 확인됐다.
- 필터링한 logcat에서 `httpError`, `Provider session requires sign-in`, `Background refresh timed out`, `Previous collection did not finish`, `ANR in com.aiquota.mobile`, `No response to onStartJob`가 새로 나오지 않았다.
- `/data/anr`에는 11:20, 11:26의 과거 ANR만 있었고 관찰 구간 신규 ANR은 없었다.

## 전체 refresh 실행 흐름

### 1. Live monitoring 시작

파일:

- `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshController.kt`
- `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshPolicy.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`

흐름:

1. `ForegroundRefreshController.setLiveMonitoringEnabled(true)`가 호출되면 `startPreciseRefresh()`가 실행된다.
2. `ForegroundRefreshHealthScheduler.schedule()`가 health check WorkManager 작업을 예약한다.
3. `ContextCompat.startForegroundService()`로 `ProviderBackgroundRefreshService.ACTION_START`를 시작한다.
4. foreground service는 notification을 띄운 뒤 1분 loop를 돈다.
5. `ProviderRefreshPlan.automaticJobsFor(repository.readSnapshots())`가 현재 snapshot 기반 job 목록을 만든다.
6. `refreshProvider()`가 provider별 수집 방식을 보고 native/API 또는 hidden WebView collector로 분기한다.

주의:

- FGS 없이 같은 수준의 지속 refresh는 현재 구조상 기대하지 않는다.
- 알림은 Android FGS 요구사항이라 유지해야 한다.
- health scheduler는 FGS가 죽었을 때 복구 보조 역할이다.

### 2. refresh 대상 선정

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt`

핵심 상수:

- `AUTO_REFRESH_INTERVAL_MILLIS = 60_000`
- `PROVIDER_REFRESH_TIMEOUT_MILLIS = 10_000`
- `CODEX_REFRESH_TIMEOUT_MILLIS = 60_000`
- `OPENCODE_REFRESH_TIMEOUT_MILLIS = 20_000`
- `GEMINI_WEB_REFRESH_TIMEOUT_MILLIS = 45_000`
- `GOOGLE_REFRESH_TIMEOUT_MILLIS = 75_000`

대상 선정:

- `ForegroundRefreshPolicy.connectedProviders()`가 `CONNECTED`, `STALE`, `COLLECTING`, `UNAVAILABLE`, `ERROR` snapshot을 refresh 후보로 본다.
- 이미 `REFRESHING`인 provider는 제외한다.
- reset refresh job이 있으면 reset job을 먼저 넣고 중복 normal job은 제외한다.
- `COLLECTING` 상태의 snapshot은 automatic normal job에서 제외한다.
- `GLM`이 "Plan 없음" snapshot이면 automatic refresh에서 제외한다.
- `Gemini`와 `Antigravity`는 Google usage pending 상태일 때 retry policy가 허용하면 다시 refresh 후보가 된다.

중요 guardrail:

- 최근 실패에 대한 15분 자동 backoff는 현재 기준선에 없다.
- 실패 직후에도 session이 살아 있으면 다음 cycle에서 다시 실제 payload 수집을 시도해야 한다.

### 3. provider별 수집 방식

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderAuthStrategy.kt`

| provider | login start | collectionKind | authStoreKind | refresh start |
| --- | --- | --- | --- | --- |
| Claude | `https://claude.ai/login` | WEBVIEW_COLLECTOR | WEBVIEW_PROFILE | `https://claude.ai/` |
| Codex | `https://chatgpt.com/auth/login` | WEBVIEW_COLLECTOR | WEBVIEW_PROFILE | `ProviderLoginStrategy.CODEX_CALLBACK_RECOVERY_URL` |
| GLM | `aiquota://provider/glm-api-key` | NATIVE_API | NATIVE_TOKEN | API key mode: native API, Web OAuth mode: `https://z.ai/manage-apikey/coding-plan/personal/usage` |
| OpenCode | `https://opencode.ai/auth` | WEBVIEW_COLLECTOR | WEBVIEW_PROFILE | last saved workspace Go URL or `https://opencode.ai/auth` |
| Gemini | `https://gemini.google.com/usage` | WEBVIEW_COLLECTOR | WEBVIEW_PROFILE | `https://gemini.google.com/usage` |
| Copilot | `https://github.com/settings/copilot/features` | WEBVIEW_COLLECTOR | WEBVIEW_PROFILE | `https://github.com/settings/copilot/features` |
| Antigravity | `https://antigravity.google/` | NATIVE_API | NATIVE_TOKEN | native Google token/API path |
| Cursor | `https://cursor.com/dashboard` | NATIVE_API_WITH_WEBVIEW_FALLBACK | TOKEN_OR_WEBVIEW_FALLBACK | `https://cursor.com/dashboard` |

주의:

- `ProviderRefreshPlan.refreshModeFor()`는 `NATIVE_API_WITH_WEBVIEW_FALLBACK`도 hidden WebView collector로 실행한다.
- `Antigravity`는 hidden WebView retention 대상이 아니다.
- `Gemini`는 현재 DOM/WebView usage 수집 기준선이며 desktop Chrome UA를 사용한다.

## hidden WebView session 유지 구조

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderHiddenWebViewRetentionPolicy.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionClearPolicy.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebViewUserAgent.kt`

retention:

- hidden WebView 유지 대상: `Claude`, `Codex`, `Copilot`, `GLM`, `OpenCode`, `Gemini`, `Cursor`
- hidden WebView 미유지 대상: `Antigravity`
- `INTERACTIVE_AUTH_REQUIRED` 실패 뒤 WebView recreate 대상은 failure kind가 `INTERACTIVE_AUTH_REQUIRED`인 경우다.

cookie/session clear:

- 로그인 전에 세션을 지우는 provider는 현재 `Cursor`만이다.
- disconnect 시에는 provider별 cookie/storage origin을 지운다.
- Google 계열 cookie domain 삭제 시 `google.com` parent cookie를 건드리지 않도록 보호한다.
- 이 보호가 깨지면 `Gemini`, `Claude`, `OpenCode`, `Cursor` 등 Google SSO를 타는 provider들이 서로 세션을 오염시킬 수 있다.

user agent:

- `Gemini`: desktop Chrome UA
- `Antigravity`: `"antigravity"`
- 그 외: Android WebView default UA에서 `; wv`, `Version/4.0`만 제거

주의:

- Codex에 desktop UA를 강제로 적용하지 않는다.
- shared SSO cookie를 전역 삭제하지 않는다.
- provider별 disconnect가 아닌 refresh 실패만으로 cookie를 삭제하지 않는다.

## hidden WebView collector 실행 구조

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`
- `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`

공통 흐름:

1. `collectWebProviderUsage()`가 provider timeout 안에서 WebView job을 시작한다.
2. `startWebCollection()`이 provider 전용/retained WebView를 준비한다.
3. page load 후 `PAGE_CAPTURE_SCRIPT`로 DOM text를 얻는다.
4. `ProviderWebCollectorScripts.shouldRunCollector()`가 현재 URL, cookie, pageText 기준으로 JS 주입 여부를 판단한다.
5. injection key는 `requestId:providerId:host:path?query#fragment` 형식이다.
6. 같은 문서에 중복 주입하지 않되, provider별 `shouldAllowCollectorReinjection()`이 허용하면 재주입한다.
7. JS가 `postUsagePayload(rawPayload)`를 호출하면 `shouldAcceptCollectorPayload()`로 bridge page 신뢰도를 확인한다.
8. 정상 payload는 `ProviderUsageNormalizer.normalize()`를 거쳐 snapshot으로 저장된다.
9. JS가 `postCollectorError(rawError)`를 호출하면 `shouldAcceptCollectorError()`와 `ProviderCollectorErrorPolicy`를 거쳐 failure로 분류된다.

중요 guardrail:

- injection key에 fragment가 빠지면 `chatgpt.com/codex/cloud/settings/analytics#usage` 같은 SPA route를 같은 페이지로 오판할 수 있다.
- bridge payload/error는 provider 허용 URL에서 온 것만 받아야 한다.
- Codex root URL(`/`)에서 error를 받는 경우는 `codex_auth_required`처럼 신뢰 가능한 auth error만 허용한다.

## Codex 기준선

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshHttpErrorPolicy.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt`

사용량 URL:

- primary: `https://chatgpt.com/codex/cloud/settings/analytics#usage`
- fallback: `https://chatgpt.com/codex/settings/usage`

수집 흐름:

1. refresh start URL은 `ProviderLoginStrategy.CODEX_CALLBACK_RECOVERY_URL`이다.
2. Codex collector는 `chatgpt.com/api/auth/session`, `backend-api/me`, `backend-api/accounts/check/v4-2023-04-27`로 session/auth hint를 본다.
3. local/session token hint 또는 session auth hint가 있을 때만 usage URL로 이동한다.
4. usage payload는 network hook(`fetch`, XHR)으로 잡은 `window.__AIQuotaCodexNetworkRows`를 먼저 스캔한다.
5. network row에서 못 찾으면 visible DOM usage extractor가 fallback으로 작동한다.
6. network row extraction에는 `c.rows()` DOM text를 섞지 않는다. DOM text는 visible DOM extractor가 담당한다.
7. subscription/account id는 `/backend-api/subscriptions?account_id=...`에서 관찰될 수 있다.
8. `codex_usage_unavailable`은 즉시 auth failure가 아니다. auth failure는 login page, main-frame 401/403, `codex_auth_required` 등 명확한 증거가 있어야 한다.

Codex 리셋 표기:

- `2026. 6. 11. 오전 09:59 초기화` 같은 날짜 포함 reset을 우선 파싱한다.
- 5시간 세션은 time-only reset이 미래일 때만 허용한다.
- 주간/spark weekly는 날짜 reset이 없고 `오후 2:52 초기화` 같은 time-only reset만 있을 경우 다음 발생 시각으로 해석해 `Resets in Xh Ym` 또는 `Resets in Xd Yh`로 변환한다.
- 이미 지난 time-only reset은 5시간 세션에서는 버리고, weekly fallback에서는 다음 날로 넘겨 상대시간을 만든다.

HTTP error 기준:

- Codex main-frame `401` 또는 `403`이 `chatgpt.com`, `*.chatgpt.com`, `auth.openai.com`에서 발생하면 `INTERACTIVE_AUTH_REQUIRED`.
- 그 외 main-frame HTTP error는 `TRANSIENT_HTTP`.
- Cursor auth exchange host의 403은 기존처럼 무시한다.

주의:

- Codex refresh stuck을 피하기 위해 usage 없는 상태를 그냥 pass 처리하지 않는다.
- session이 살아 있는데 usage payload가 안 잡히면 retry/diagnostic 대상이다.
- `updatedAt`만 전진하고 usage line이 그대로인 상태를 성공으로 보지 않는다.

## Gemini 기준선

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/GeminiUsagePageRoutes.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt`

현재 기준:

- Gemini는 `https://gemini.google.com/usage` DOM/WebView usage 수집을 기준으로 한다.
- 기존 `cloudcode-pa.googleapis.com` CLI quota 수집은 primary 기준이 아니다.
- `ProviderWebViewUserAgent`에서 hidden collector는 desktop Chrome UA를 쓴다.

로그인 흐름:

1. 로그인 시작/refresh start는 `https://gemini.google.com/usage`.
2. `gemini.google.com`, `myaccount.google.com`, `accounts.google.*` 흐름을 허용한다.
3. 로그인 후 `myaccount.google.com` 또는 `gemini.google.com` landing/app/u route가 보이면 `GeminiUsagePageRoutes.USAGE_URL`로 이동한다.
4. usage URL에서 collector가 DOM/network rows를 스캔한다.
5. usage payload가 없고 login page라면 sign-in 버튼을 최대 2회 클릭한다.
6. 그래도 payload가 없으면 terminal check가 `NO_TRUSTED_PAYLOAD` 또는 auth required로 닫는다.

정규화:

- Gemini payload는 compact array `usage.x` 또는 Code Assist style `limits/quotaBuckets`를 지원한다.
- label은 `5-hour limit`, `weekly limit` 등으로 정규화된다.
- reset은 `resetAt/resetsAt/resetText`를 받아 `UsageResetText`가 상대시간으로 표시한다.

주의:

- Gemini가 `gemini.google.com` landing에 머물면 usage payload를 못 잡는다. 반드시 `/usage`로 이동해야 한다.
- Gemini login이 다시 열릴 때 Google SSO cookie를 지우면 Codex/Claude/OpenCode 등 다른 provider에도 영향을 줄 수 있다.

## Claude 기준선

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt`

수집:

- start URL은 `https://claude.ai/`.
- network hook이 `five_hour`, `seven_day`, `extra_usage`, model/usage limit shape을 포함한 response를 잡는다.
- DOM text fallback은 `Claude: Session Reset`, `Claude: Weekly Reset`, `Sonnet`, `Opus`, `Cowork`, `Design` 계열 label을 파싱한다.

정규화:

- 기본 line: `claude:session`, `claude:weekly`, `claude:opus`, `claude:sonnet`, `claude:cowork`, `claude:design`
- 추가 line은 raw usage object 중 display 가능한 key만 추가한다.
- `Extra Usage`는 raw data에 `extra_usage`가 실제로 있을 때만 표시한다.
- dashboard preview에서 Claude는 3개 line까지 허용할 수 있지만 fake row를 만들지 않는다.

## OpenCode 기준선

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/OpenCodeUsagePageRoutes.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt`

흐름:

1. login start는 `https://opencode.ai/auth`.
2. 로그인 뒤 `https://opencode.ai/workspace/{workspaceId}` 형태가 보이면 `https://opencode.ai/workspace/{workspaceId}/go`로 이동한다.
3. 이동한 Go usage URL은 `ProviderScopedStateRepository.saveOpenCodeUsageUrl()`에 저장한다.
4. refresh 때 저장된 Go usage URL이 있으면 `resolveRuntimeRefreshJob()`이 startUrl로 재사용한다.
5. collector는 visible DOM과 captured rows에서 Go 5-hour/weekly/monthly limit 및 Zen credits 후보를 찾는다.

표시명:

- raw label은 정규화 단계에서 `5시간 한도`, `주간 한도`, `월간 한도`로 UI 표시될 수 있게 mapping된다.

주의:

- OpenCode docs/brand URL은 collector navigation에서 제외한다.
- `/workspace/{id}/go` route를 저장하지 않으면 다음 refresh가 auth page에 머물 수 있다.

## GLM 기준선

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/GlmUsageRepository.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmUsagePageRoutes.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt`

모드:

- API key mode: `https://api.z.ai/api/monitor/usage/quota/limit`
- Web OAuth mode: `https://z.ai/manage-apikey/coding-plan/personal/usage`

API key mode:

- 먼저 `Authorization: Bearer <apiKey>`로 요청한다.
- auth/400/404 또는 MCP-only payload면 raw API key authorization으로 한 번 더 시도한다.
- `TOKENS_LIMIT`가 있으면 5-hour/weekly token limit으로 정규화한다.
- `TIME_LIMIT`만 있으면 MCP Monthly Quota만 표시될 수 있다.

Web OAuth mode:

- `my-plan` page에서 `usage` page로 redirect한다.
- visible DOM에서 `TOKENS_LIMIT`, `TIME_LIMIT`, plan/account 후보를 수집한다.
- `You don't have any subscription`이 감지되면 `CONNECTED`, `Plan 없음`, line 0개 상태로 저장한다.
- 이 "Plan 없음" snapshot은 automatic refresh에서 제외한다.

주의:

- 현재 에뮬레이터에는 `usage_data_glm.xml`이 없어 15분 runtime 검증 대상이 아니었다.
- GLM API가 MCP quota만 반환하는 계정에서는 coding token quota를 표시할 수 없다.

## Copilot 기준선

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/CopilotNativeUsageFetcher.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt`

흐름:

- start URL은 `https://github.com/settings/copilot/features`.
- login 뒤 settings/copilot/features로 redirect한다.
- collector는 feature DOM, GitHub billing/settings/internal endpoint, native bridge fetch를 조합한다.
- `fetchCopilotJson()`과 `fetchCopilotJsonWithAuthorization()` bridge는 trusted page에서만 허용된다.

정규화:

- line: `Chat`, `Inline suggestions`, `Premium requests`
- `remaining`, `limits`, quota reset date, premium billing payload를 합쳐 가장 신뢰 가능한 line을 만든다.

## Antigravity 기준선

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/GoogleWebSessionCodeAssistFetcher.kt`

흐름:

- primary는 native Google token/API path다.
- `fetchAvailableModels` 계열 response에서 model별 quota를 수집한다.
- native token payload가 없고 session cookie가 있으면 Google Web Session Code Assist fallback을 시도한다.
- hidden WebView는 retention하지 않는다.

정규화:

- compact line, explicit session/weekly/credits, model lines를 합친다.
- `remainingPercent`가 있는 line만 표시한다.
- 현재 검증 snapshot 기준 14개 line이 수집됐다.

## Cursor 기준선

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/CursorNativeUsageFetcher.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt`

흐름:

- start URL은 `https://cursor.com/dashboard`.
- auth exchange host의 403은 무시한다.
- WebView page에서 native bridge `fetchCursorJson()`를 통해 Cursor API payload를 가져올 수 있다.
- bridge fetch는 trusted page에서만 허용된다.

정규화:

- total usage, auto usage, API usage, request usage, individual/on-demand usage를 합친다.
- percent 기반 값이 있으면 그것을 우선하고, 없으면 amount/limit에서 remaining fraction을 계산한다.

## usage 정규화 공통 기준

파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt`
- `android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt`
- `android/app/src/main/java/com/aiquota/mobile/local/UsageResetText.kt`

payload source 신뢰도:

- `PROVIDER_API`: 0.99
- `NETWORK_RESPONSE`: 0.96
- `VISIBLE_DOM`: 0.94
- `APP_STATE`: 0.92
- `STRUCTURED_SCRIPT`: 0.90
- `DOM_TEXT`: normalize 대상 아님

percent 기준:

- 일반 `toLine()`은 `used_*` 값을 먼저 보고, 없으면 `remaining_*` 값에서 used percent를 역산한다.
- `preferRemainingPercent = true`인 line은 `remaining_*` 값을 먼저 신뢰한다.
- Claude, Codex, OpenCode 등 remaining display가 핵심인 provider는 `preferRemainingPercent = true`를 써야 한다.
- 이 기준이 깨지면 `used=0.1` 같은 fractional value가 1% used로 잘못 해석되고, 실제 `remaining`보다 used가 우선되는 버그가 재발할 수 있다.

reset 표시:

- `resetText`가 `Resets in ...`이면 canonical relative text로 정규화한다.
- `resetText`가 ISO instant/epoch이면 `resetTextForInstant()`로 상대시간 표시한다.
- 한국어 `오전/오후 H:mm 초기화`, `M월 D일 오후 H:mm 초기화`, `YYYY. M. D. 오전 H:mm 초기화`를 파싱한다.
- 1일 이상 남으면 `Resets in Xd Yh`, 1일 미만이면 `Resets in Xh Ym`, 1시간 미만이면 `Resets in Xm`.

updatedAt 기준:

- `ProviderUsageSnapshot.collecting()`은 기존 line이 있으면 `updatedAt`을 유지하고 `statusUpdatedAt`만 바꾼다.
- `failedKeepingPrevious()`, `connectedWithoutUsage()`, `interactiveAuthRequiredKeepingPrevious()`도 기존 line이 있으면 `updatedAt`을 유지한다.
- `saveSnapshot()`으로 정상 payload snapshot이 저장될 때만 실제 usage 기준 `updatedAt`이 전진한다.

## 상태 전이 기준

파일:

- `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt`
- `android/app/src/main/java/com/aiquota/mobile/local/ProviderModels.kt`

주요 상태:

- `CONNECTED`: usage line이 있고 정상 연결.
- `COLLECTING`: 현재 수집 중. 기존 line이 있으면 `updatedAt` 유지.
- `UNAVAILABLE`: 연결은 되었지만 trusted usage payload가 아직 없음. Google provider는 pending retry 가능.
- `INTERACTIVE_AUTH_REQUIRED`: 명확한 login/auth required 증거가 있음.
- `ERROR`: 이전 usage가 없고 실패함.
- `STALE`: background refresh 중단 등으로 오래된 상태.

stale clearing:

- refresh가 오래 `REFRESHING`으로 남으면 `clearStaleRefreshing()`이 `IDLE`로 돌린다.
- provider별 timeout:
  - Google provider: 긴 timeout
  - OpenCode: OpenCode 전용 timeout
  - Codex: Codex 전용 timeout
  - connecting: connecting timeout
  - 그 외: 일반 timeout

주의:

- 이전 usage line이 있는 provider의 실패는 `CONNECTED` + message/status update로 남길 수 있다.
- 이것은 UI에서 사용자가 기존 usage를 계속 보게 하기 위한 것이며, 실제 fresh success와 구분하려면 `updatedAt`을 봐야 한다.

## app update/health scheduling 기준

현재 dirty code에는 provider refresh 외에도 시작 직후 background 작업 지연이 포함된다.

파일:

- `android/app/src/main/java/com/aiquota/mobile/MainActivity.kt`
- `android/app/src/main/java/com/aiquota/mobile/update/AppUpdateCheckScheduler.kt`
- `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshHealthScheduler.kt`

기준:

- 앱 시작 시 update check는 `APP_UPDATE_CHECK_STARTUP_DELAY_MS = 5_000` 뒤 실행한다.
- update WorkManager one-time check는 1분 initial delay를 둔다.
- foreground refresh health one-time check도 1분 initial delay를 둔다.

이유:

- 앱 시작과 WebView/session restore 직후 update/health one-time work가 동시에 몰리는 것을 줄인다.

## 재발 방지 체크리스트

provider refresh가 다시 꼬이면 아래 순서로 본다.

1. `adb logcat -c` 후 2-15분 관찰한다.
2. `cycleStart providers=...`에 대시보드 대상 provider가 모두 들어가는지 확인한다.
3. 각 provider의 `usage_data_{provider}.xml` snapshot에서 `updatedAt`, `statusUpdatedAt`, `connectionState`, `refreshState`, `lines`를 비교한다.
4. `updatedAt`이 안 움직이고 `statusUpdatedAt`만 움직이면 실제 usage 수집 성공이 아니다.
5. `httpError provider=...`, `Provider session requires sign-in`, `Background refresh timed out`, `Previous collection did not finish`를 찾는다.
6. Codex는 반드시 `chatgpt.com/codex/cloud/settings/analytics#usage` route와 trusted usage payload까지 갔는지 본다.
7. Gemini는 반드시 `https://gemini.google.com/usage`까지 이동했는지 본다.
8. OpenCode는 saved workspace Go URL이 있는지 본다.
9. GLM은 no subscription snapshot이면 refresh 대상에서 빠지는 것이 정상이다.
10. Google SSO cookie parent domain 삭제가 들어갔는지 확인한다. 들어갔다면 다른 provider 세션 오염 가능성이 높다.

## 절대 금지

- usage payload 없이 `updatedAt`만 갱신하기.
- provider 실패를 generic pass로 숨기기.
- Codex `codex_usage_unavailable`을 auth failure로 단정하기.
- login redirect 증거 없이 cookie/session을 지우기.
- Google parent cookie(`google.com`)를 provider별 cleanup에서 지우기.
- route key에서 query/fragment를 빼기.
- Gemini를 `/usage`로 이동시키지 않고 landing page에서 collector 완료 처리하기.
- OpenCode workspace Go URL 저장 없이 auth page에서 refresh 완료 처리하기.
- GLM no subscription을 error loop로 계속 refresh하기.

## 기준선 검증 명령

이번 문서 작성 시점에 사용한 핵심 관찰 명령:

```powershell
adb logcat -c
# 1분마다 usage_data_{provider}.xml snapshot의 state/refresh/updatedAt/line count 확인
adb logcat -d -v time | Select-String -Pattern 'cycleStart|outcome provider=|httpError provider=|Provider session requires sign-in|Background refresh timed out|Previous collection did not finish|ANR in com.aiquota.mobile|No response to onStartJob'
adb shell ls -lt /data/anr
```

커밋 전 Gradle 검증 결과는 이 문서 하단의 "커밋 전 검증" 섹션에 갱신한다.

## 커밋 전 검증

- 2026-06-21 13:24-13:38 device log 기준 15분 연속 refresh 관찰 성공.
- `git diff --check` 통과.
- `:app:testDebugUnitTest` 대상 테스트 통과:
  - `ProviderWebCollectorScriptsTest`
  - `ProviderRefreshHttpErrorPolicyTest`
  - `ProviderWebViewUserAgentTest`
  - `ProviderBackgroundRefreshServicePolicyTest`
  - `ProviderRefreshPlanTest`
  - `ProviderWebSessionClearPolicyTest`
  - `GoogleWebSessionBridgeSourceTest`
  - `AppUpdateCheckTest`
- `:app:assembleDebug` 통과.
