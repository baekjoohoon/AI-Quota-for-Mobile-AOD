# Mobile Provider Login Wireframe Handoff

Last updated: 2026-05-19

이 문서는 AI Usage for Mobile 디버깅/구현 세션에 넘기기 위한 provider별 로그인 및 사용량 수집 와이어프레임이다.

목표:

- Firebase, pairing code, Windows 연동 없이 Android 단독으로 동작한다.
- 각 provider는 앱 내 로그인 화면으로 이동한다.
- 로그인 성공 후 앱으로 돌아와 provider별 collector를 실행한다.
- 앱은 token/cookie 원문이 아니라 정규화된 usage snapshot만 UI, widget, notification에 노출한다.

지원 provider:

- Claude
- Codex
- Gemini
- Copilot
- Cursor

제외:

- Antigravity
- Firebase Sync
- 서버 relay
- raw token/cookie/log 저장

## 공통 UX 흐름

```text
Dashboard
  -> Provider card tap
  -> Provider login sheet / full-screen WebView
  -> Provider login
  -> Login success signal detected
  -> App closes WebView
  -> Provider collector runs immediately
  -> Normalized ProviderUsageSnapshot saved locally
  -> Dashboard/widget/notification refresh
```

## 공통 화면 와이어프레임

### 1. Provider 카드

```text
┌────────────────────────────────────┐
│ Claude                         Pro │
│                                    │
│ Not connected                      │
│ [ Sign in ]                        │
└────────────────────────────────────┘
```

상태:

- `NOT_CONNECTED`: 로그인 필요
- `CONNECTING`: 로그인 WebView 열림
- `CONNECTED`: 세션 확인 완료
- `COLLECTING`: 로그인 직후/수동/주기 refresh 중
- `STALE`: 마지막 성공 snapshot 유지 중
- `ERROR`: 로그인 또는 수집 실패

### 2. 로그인 WebView

```text
┌────────────────────────────────────┐
│ Sign in to Claude              X   │
├────────────────────────────────────┤
│                                    │
│        Provider login page         │
│                                    │
└────────────────────────────────────┘
```

공통 요구:

- 로그인 WebView는 provider별 isolated session 정책을 따라야 한다.
- OAuth 중간 페이지의 transient navigation error를 즉시 실패로 저장하지 않는다.
- 로그인 성공 signal을 provider별로 확인한 뒤 WebView를 닫는다.
- 로그인 성공 직후 collector를 1회 자동 실행한다.

### 3. 로그인 후 수집 중

```text
┌────────────────────────────────────┐
│ Gemini                         Pro │
│                                    │
│ Collecting usage...                │
│ Last successful sync: 10:23        │
└────────────────────────────────────┘
```

### 4. 수집 성공

```text
┌────────────────────────────────────┐
│ Gemini                         Pro │
│ Pro                                │
│ ████████████████████░ 98% left     │
│ Resets in 2h 42m                   │
│ Flash                              │
│ ███████████████████░░ 99% left     │
│ Resets in 2h 36m                   │
└────────────────────────────────────┘
```

### 5. 수집 실패 + 이전 값 유지

```text
┌────────────────────────────────────┐
│ Cursor                        Free │
│ Total usage                        │
│ ███████████████████░░ 96% left     │
│ Resets in 29d 3h                   │
│                                    │
│ Last refresh failed. Showing last  │
│ successful data.                   │
└────────────────────────────────────┘
```

## 공통 LoginResult 계약

로그인 WebView는 provider마다 성공 기준이 다르지만, 앱으로 돌아올 때 최소한 아래 형태의 결과를 넘긴다.

```json
{
  "providerId": "gemini",
  "status": "success",
  "accountHint": "user@example.com",
  "sessionKind": "webview-cookie",
  "collectorMode": "webview-js",
  "canCollectNow": true,
  "completedAt": 1779160000000
}
```

금지:

- access token 원문을 일반 로그에 출력하지 않는다.
- refresh token 원문을 snapshot에 넣지 않는다.
- cookie 원문을 snapshot에 넣지 않는다.
- provider raw response 전체를 debug snapshot에 넣지 않는다.

Secure storage에는 provider별 세션 유지에 필요한 최소 credential만 저장한다.

## 공통 CollectorResult 계약

collector는 provider별 raw 응답을 아래 형태로 정규화해서 앱으로 넘긴다.

```json
{
  "providerId": "cursor",
  "status": "success",
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
      "resetText": "Resets in 29d 3h",
      "unit": "percent",
      "source": "api2.cursor.sh/GetCurrentPeriodUsage",
      "confidence": 0.99
    }
  ]
}
```

수집 실패 시:

```json
{
  "providerId": "cursor",
  "status": "failed",
  "errorKind": "quota_not_found",
  "shouldKeepPreviousSnapshot": true
}
```

## Provider별 흐름

## Claude

### 로그인 와이어프레임

```text
Dashboard
  -> Claude card [Sign in]
  -> WebView: https://claude.ai/
  -> Claude login / Google login / account selection
  -> Claude app shell or /new entered
  -> Verify session
  -> Close WebView
  -> Fetch usage
```

### 로그인 완료 판정

성공 signal 우선순위:

1. `lastActiveOrg` cookie 존재
2. `https://claude.ai/api/organizations/me` 또는 organization API가 200 응답
3. Claude app shell 진입 후 organization id 확인
4. `/new` 진입 + organization cookie 확인

주의:

- `/login` URL에 머문다고 무조건 실패로 보면 안 된다.
- Google OAuth 중간 페이지 오류를 즉시 실패로 저장하지 않는다.
- Cloudflare 관련 cookie는 무리하게 삭제하지 않는다.

### 앱으로 돌아올 때 전달할 정보

```json
{
  "providerId": "claude",
  "status": "success",
  "sessionKind": "webview-cookie",
  "organizationId": "org_xxx",
  "accountHint": "optional",
  "collectorMode": "same-session-fetch",
  "canCollectNow": true
}
```

### 로그인 직후 수집

우선 endpoint:

```text
GET https://claude.ai/api/organizations/{organizationId}/usage
```

수집 방식:

- WebView same-session fetch
- `credentials: include`
- response JSON을 Android bridge로 전달

### 정규화

```text
five_hour          -> Claude Session
seven_day          -> Claude Weekly
seven_day_omelette -> Claude Design
```

필드:

- `utilization` 또는 equivalent used fraction
- `resets_at` 또는 equivalent reset timestamp
- plan은 provider 원문 값 `Pro`, `Max` 등을 보존

### 실패 처리

```text
organization_missing -> 로그인은 됐지만 organization id 없음
usage_api_401        -> 세션 만료, 재로그인 필요
usage_api_empty      -> 이전 snapshot 유지
dom_only             -> 저장 금지, API 재시도
```

## Codex

### 로그인 와이어프레임

```text
Dashboard
  -> Codex card [Sign in]
  -> WebView: OpenAI OAuth authorize URL
  -> Login at auth.openai.com
  -> Redirect to loopback/custom callback
  -> App intercepts authorization code
  -> Exchange code locally
  -> Close WebView
  -> Fetch WHAM usage
```

### 로그인 시작 URL

Codex CLI 계열 OAuth와 같은 originator/scope를 사용한다.

```text
https://auth.openai.com/oauth/authorize
```

필수 개념:

- redirect URI는 Android가 intercept할 수 있어야 한다.
- loopback URL을 WebView가 로드 실패해도 실패로 저장하면 안 된다.
- callback URL에서 `code`만 추출하면 WebView는 닫아도 된다.

### 로그인 완료 판정

성공 signal:

1. callback URL에 `code` 존재
2. token exchange 성공
3. access token 또는 id token payload decode 가능
4. WHAM usage endpoint가 200 응답

### 앱으로 돌아올 때 전달할 정보

```json
{
  "providerId": "codex",
  "status": "success",
  "sessionKind": "oauth-token",
  "accountHint": "user@example.com",
  "planClaim": "prolite",
  "collectorMode": "native-api",
  "canCollectNow": true
}
```

Secure storage:

- access token
- refresh token
- expiry
- account id if needed

Snapshot에는 저장 금지.

### 로그인 직후 수집

우선 endpoint:

```text
GET https://chatgpt.com/backend-api/wham/usage
```

필요 header:

```text
Authorization: Bearer <access_token>
ChatGPT-Account-Id: <account_id> if available
```

### 정규화

Plan:

```text
prolite -> Pro 5x
pro     -> Pro 20x
```

Usage:

```text
rate_limit.primary_window   -> Codex Session
rate_limit.secondary_window -> Codex Weekly
code_review_rate_limit.*    -> Code review
spark_rate_limit.*          -> Spark
credits.balance             -> Credits
```

Reset:

- `reset_at`
- `resets_at`
- `reset_after_seconds`

### 실패 처리

```text
oauth_callback_ignored -> WebView callback intercept 누락
token_exchange_failed  -> OAuth client/redirect/scope 확인
wham_401               -> token refresh 또는 재로그인
wham_empty             -> 이전 snapshot 유지
placeholder_reset      -> 저장 금지
```

## Gemini

### 로그인 와이어프레임

```text
Dashboard
  -> Gemini card [Sign in]
  -> WebView: Google AccountChooser -> gemini.google.com/app
  -> Google login/account selection
  -> Gemini app shell entered
  -> Inject gemini_collector.js
  -> Detect account/plan/quota source
  -> Close or keep hidden WebView
  -> Save normalized snapshot
```

### 로그인 시작 URL

```text
https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fgemini.google.com%2Fapp&hl=ko
```

### 로그인 완료 판정

성공 signal:

1. WebView current host가 `gemini.google.com`
2. Gemini app shell 진입
3. account email 또는 profile signal 확인
4. collector가 account 또는 usage payload를 1회 이상 반환

주의:

- Firebase Auth 로그인은 이 구조에 사용하지 않는다.
- 일반 Google 로그인 token만으로 `retrieveUserQuota`가 되는 것은 아니다.
- Gemini는 DOM text보다 network/app-state/collector 결과를 우선한다.

### 앱으로 돌아올 때 전달할 정보

```json
{
  "providerId": "gemini",
  "status": "success",
  "sessionKind": "webview-cookie",
  "accountHint": "user@example.com",
  "collectorMode": "webview-js",
  "collectorAsset": "gemini_collector.js",
  "canCollectNow": true
}
```

### 로그인 직후 수집

수집 우선순위:

1. WebView network hook에서 Gemini/Code Assist quota 응답 감지
2. Gemini page app state JSON 탐색
3. APK 분석 기반 compact payload 구조 탐색
4. DOM fallback
5. 이전 snapshot 유지

APK 분석 기반 collector payload:

```json
{
  "account": {
    "p": "GEMINI_PRO",
    "e": "user@example.com"
  },
  "usage": {
    "x": [
      {
        "l": "Gemini Pro",
        "u": 0.02,
        "r": 1779160000000,
        "t": "Resets in 2h 42m"
      },
      {
        "l": "Gemini Flash",
        "u": 0.01,
        "r": 1779159640000,
        "t": "Resets in 2h 36m"
      }
    ]
  }
}
```

### 정규화

Plan:

```text
GEMINI_PRO, GOOGLE_AI_PRO       -> Gemini Pro
GEMINI_PLUS, GOOGLE_AI_PLUS     -> Gemini Plus
GEMINI_ULTRA, GOOGLE_AI_ULTRA   -> Gemini Ultra
GEMINI_FREE, GOOGLE_AI_FREE     -> Gemini Free
```

Usage:

```text
Gemini Pro            -> key gemini:pro
Gemini Flash          -> key gemini:flash
Gemini Deep Research  -> key gemini:deep-research
```

계산:

```text
if used fraction u exists:
  usedPercent = round(u * 100)
  remainingPercent = 100 - usedPercent

if remainingFraction exists:
  remainingPercent = round(remainingFraction * 100)
  usedPercent = 100 - remainingPercent
```

### 실패 처리

```text
google_login_incomplete -> AccountChooser 또는 consent 진행 중
gemini_shell_not_ready  -> app shell 미진입, collector 대기
quota_not_found         -> 이전 snapshot 유지
generic_dom_only        -> 저장 금지
fake_100_percent        -> reset/plan/account 근거 없으면 저장 금지
```

## Copilot

### 로그인 와이어프레임

```text
Dashboard
  -> Copilot card [Sign in]
  -> WebView: https://github.com/settings/copilot
  -> GitHub login / 2FA / device verification
  -> Copilot settings page entered
  -> Fetch entitlement from same session
  -> Close WebView
  -> Save normalized snapshot
```

### 로그인 시작 URL

```text
https://github.com/settings/copilot
```

### 로그인 완료 판정

성공 signal:

1. host가 `github.com`
2. path가 `/settings/copilot` 또는 Copilot entitlement 호출 가능 상태
3. `github-copilot/chat/entitlement` endpoint가 200 응답
4. entitlement payload에 license/plan/status 정보 존재

### 앱으로 돌아올 때 전달할 정보

```json
{
  "providerId": "copilot",
  "status": "success",
  "sessionKind": "webview-cookie",
  "accountHint": "github-username",
  "collectorMode": "same-session-fetch",
  "canCollectNow": true
}
```

### 로그인 직후 수집

우선 endpoint:

```text
GET https://github.com/github-copilot/chat/entitlement
```

수집 방식:

- GitHub WebView same-session fetch
- `credentials: include`
- Android bridge로 JSON 전달

### 정규화

Plan source:

```text
plan
sku
licenseType
license_type
entitlement status
```

Usage lines:

```text
quotas.remaining.chat / quotas.limits.chat
  -> Chat

quotas.remaining.completions / quotas.limits.completions
  -> Completions

quotas.remaining.premiumInteractions / quotas.limits.premiumInteractions
  -> Premium requests
```

Reset:

```text
quotas.resetDate
```

### 실패 처리

```text
github_login_required -> 재로그인 필요
two_factor_pending    -> WebView 계속 유지
entitlement_404       -> endpoint/session/headers 확인
marketing_page        -> 저장 금지
quota_missing         -> plan만 저장하거나 이전 usage 유지
```

## Cursor

### 로그인 와이어프레임

```text
Dashboard
  -> Cursor card [Sign in]
  -> WebView: https://cursor.com/dashboard
  -> WorkOS / Google / GitHub login
  -> Cursor dashboard entered
  -> Android CookieManager reads cookies
  -> Try API2 usage call or dashboard network capture
  -> Close WebView
  -> Save normalized snapshot
```

### 로그인 시작 URL

```text
https://cursor.com/dashboard
```

### 로그인 완료 판정

성공 signal:

1. host가 `cursor.com`
2. path가 `/dashboard`, `/dashboard/usage`, `/settings` 계열
3. `cursor.com/api/auth/stripe`가 200 응답
4. 가능하면 `WorkosCursorSessionToken` cookie 확인

주의:

- `WorkosCursorSessionToken`은 HttpOnly일 수 있으므로 `document.cookie`만 보면 안 된다.
- Android `CookieManager.getCookie("https://cursor.com")`를 사용한다.
- `api2.cursor.sh`를 main-frame으로 직접 로드하지 않는다.
- WorkOS 로그인 직후 transient error를 즉시 실패로 저장하지 않는다.

### 앱으로 돌아올 때 전달할 정보

```json
{
  "providerId": "cursor",
  "status": "success",
  "sessionKind": "webview-cookie",
  "accountHint": "optional",
  "collectorMode": "cookie-bridge-or-network-hook",
  "hasWorkosSessionCookie": true,
  "canCollectNow": true
}
```

### 로그인 직후 수집

우선순위:

1. `WorkosCursorSessionToken`에서 access token을 얻고 `api2.cursor.sh` 호출
2. WebView network hook에서 dashboard usage payload 캡처
3. `cursor.com/api/auth/stripe`로 plan 확인
4. `cursor.com/api/usage?user={userId}`로 request-based usage 확인
5. Free spend heuristic은 estimated로만 표시

API2 endpoint:

```text
POST https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage
POST https://api2.cursor.sh/aiserver.v1.DashboardService/GetPlanInfo
```

### 정규화

Plan:

```text
planInfo.planName
membershipType
individualMembershipType
plan
planName
tier
```

Usage:

```text
planUsage.totalPercentUsed
  -> Total usage usedPercent

planUsage.limit + totalSpend/remaining
  -> Total usage money/percent depending account type

planUsage.autoPercentUsed
  -> Auto usage

planUsage.apiPercentUsed
  -> API usage

spendLimitUsage.individualLimit / individualRemaining
  -> On-demand

gpt-4.numRequests / gpt-4.maxRequestUsage
  -> Requests
```

Free plan 예시:

```json
{
  "planUsage": {
    "totalSpend": 8,
    "bonusSpend": 8,
    "totalPercentUsed": 4
  }
}
```

표시:

```text
usedPercent = 4
remainingPercent = 96
```

### 실패 처리

```text
dashboard_loaded_but_no_api -> network hook 확인, 이전 snapshot 유지
cookie_missing              -> CookieManager bridge 확인
http_only_cookie_hidden     -> JS가 아니라 Android CookieManager 사용
stripe_only                 -> plan만 업데이트, usage는 이전 snapshot 유지
spend_only_free             -> estimated fallback만 허용
generic_dom_only            -> 저장 금지
```

## 디버깅 APK 우선 수정 체크리스트

현재 디버깅 APK에서 로그인과 수집이 엉망이라면 아래 순서로 고친다.

### 1. 로그인 완료 판정을 URL 하나로 처리하지 말 것

각 provider별 성공 signal을 사용한다.

```text
Claude  -> lastActiveOrg 또는 organization API
Codex   -> OAuth callback code + token exchange
Gemini  -> Gemini app shell + collector result
Copilot -> entitlement endpoint 200
Cursor  -> dashboard + stripe/session endpoint 200
```

### 2. WebView transient error를 즉시 실패 snapshot으로 저장하지 말 것

OAuth/SSO 중간 단계에서는 main-frame error가 발생할 수 있다.

예:

- Google OAuth redirect
- OpenAI loopback callback
- WorkOS authenticator redirect
- GitHub device/2FA intermediate page

이런 오류는 provider별 recovery host/path 안에서는 대기하거나 성공 signal을 재검사한다.

### 3. 로그인 성공 직후 collector를 반드시 1회 실행할 것

로그인만 성공하고 collector가 돌지 않으면 UI는 계속 `Not connected` 또는 stale 상태로 보인다.

필수 흐름:

```text
LoginResult.success
  -> save connection state
  -> runProviderCollection(providerId, reason = "after_login")
  -> save snapshot
  -> refresh dashboard/widget/notification
```

### 4. provider별 collector source를 로그에 남길 것

민감정보 없이 아래만 남긴다.

```text
providerId
collectorMode
source endpoint or source type
line count
plan present true/false
account present true/false
errorKind
keptPreviousSnapshot true/false
```

예:

```text
Gemini collector success: mode=webview-js source=app-state lines=2 plan=true account=true
Cursor collector failed: mode=cookie-bridge source=stripe-only lines=0 keptPreviousSnapshot=true
```

### 5. generic DOM line은 live counter로 저장하지 말 것

금지 예:

```text
Usage
Total
Session
Weekly
Remaining
2 of 3 left
completed
sitemap
pricing
```

provider API, network response, app-state JSON, APK-style structured payload 근거가 없으면 live usage로 저장하지 않는다.

## 모바일 세션에 넘길 구현 순서

1. `ProviderLoginResult`와 `ProviderCollectorResult` 모델을 고정한다.
2. WebView 로그인 완료 판정을 provider별 strategy로 분리한다.
3. 로그인 성공 직후 collector 자동 실행을 붙인다.
4. Claude와 Copilot처럼 endpoint가 명확한 provider부터 고친다.
5. Codex는 OAuth callback intercept와 token exchange를 먼저 고친다.
6. Cursor는 Android CookieManager bridge와 network hook을 먼저 고친다.
7. Gemini는 `gemini_collector.js` 결과 schema를 APK-style payload로 맞춘다.
8. 모든 provider에서 실패 refresh가 이전 정상 snapshot을 덮어쓰지 않게 한다.

## 완료 기준

- 각 provider에서 로그인 WebView가 성공 signal을 잡고 앱으로 돌아온다.
- 로그인 직후 1회 수집이 자동 실행된다.
- 수집 실패 시 기존 정상 usage가 사라지지 않는다.
- provider별 plan이 실제 근거가 있을 때만 표시된다.
- provider별 usage line이 generic DOM fallback으로 만들어지지 않는다.
- dashboard, widget, notification이 같은 `ProviderUsageSnapshot`을 읽는다.

