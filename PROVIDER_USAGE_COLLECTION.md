# Provider Plan and Usage Collection Reference

이 문서는 provider별 plan 및 사용량 수집 로직의 기준 문서다.
앞으로 코드 변경 후 plan 또는 사용량 수집이 비정상으로 보이면 이 문서를 먼저 확인하고, 현재 구현이 아래 계약을 깨지 않았는지 비교한다.

## 공통 원칙

- Firebase, 외부 서버, Windows 앱 연동 없이 Android 로컬 세션만 사용한다.
- provider 로그인은 앱 내부 WebView 또는 provider별 로컬 OAuth 흐름에서 처리한다.
- 토큰, 쿠키, 세션, 수집 스냅샷은 Android 기기 내부에만 저장한다.
- UI, 위젯, 알림은 `LocalUsageRepository`와 `WidgetSnapshotCache`에 저장된 정규화 스냅샷만 읽는다.
- DOM 텍스트만 긁는 방식은 최후 fallback이다. 우선순위는 로컬 OAuth/API 응답, WebView network/JSON 응답, provider 페이지 상태 JSON, 제한적인 텍스트 fallback 순서다.
- 사용량 라인은 `ProviderUsageLine`으로 정규화한다.
- 저장 전에는 `ProviderUsageCollectionService.saveUsageSnapshot()`에서 신뢰 가능한 counter line만 남겨야 한다.
- 자동 갱신은 로그인된 provider만 대상으로 `ForegroundRefreshPolicy.REFRESH_INTERVAL_MS = 60_000L` 기준 1분 주기로 실행한다.

## 핵심 파일

- 모델 및 plan 표시 정규화: `android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`
- 로컬 스냅샷 저장/정규화: `android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`
- provider 수집 오케스트레이션: `android/app/src/main/java/com/aiusage/mobile/providers/ProviderUsageCollectionService.kt`
- WebView JS extractor: `android/app/src/main/java/com/aiusage/mobile/providers/ProviderLocalUsageCollector.kt`
- provider별 extractor asset: `android/app/src/main/java/com/aiusage/mobile/providers/ProviderCollectorAssets.kt`
- Gemini asset extractor: `android/app/src/main/assets/gemini_collector.js`
- payload 정규화: `android/app/src/main/java/com/aiusage/mobile/providers/TextUsageExtractor.kt`
- Codex OAuth/API 수집: `android/app/src/main/java/com/aiusage/mobile/providers/CodexOAuthRepository.kt`
- Gemini OAuth/API 수집: `android/app/src/main/java/com/aiusage/mobile/providers/GeminiCliOAuthRepository.kt`
- 로그인 시작 URL: `android/app/src/main/java/com/aiusage/mobile/providers/ProviderConnectorRegistry.kt`
- 1분 foreground refresh: `android/app/src/main/java/com/aiusage/mobile/sync/ForegroundRefreshPolicy.kt`, `android/app/src/main/java/com/aiusage/mobile/sync/ForegroundRefreshService.kt`

## 정규화 데이터 계약

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

표시는 "남은 양" 중심이다. provider 응답이 used percent를 주면 앱은 `remainingPercent = 1 - usedPercent`로 변환한다.

## Claude

### 로그인

- 시작 URL: `https://claude.ai/`
- WebView 세션 쿠키를 사용한다.
- 로그인 완료 판단 후 `lastActiveOrg` 쿠키에서 organization id를 찾는다.
- Claude의 "Google 로그인하기"는 기존 Google WebView 쿠키가 있어도 계정 선택 화면이 떠야 한다.
- `ProviderLoginUrlRewriter`가 Claude에서 `accounts.google.com` OAuth 시작 URL에 `prompt=select_account`를 직접 추가한다.
- `AccountChooser`로 다시 감싸면 계정 선택 화면이 잠깐 보인 뒤 기존 세션으로 자동 진행될 수 있으므로 Claude OAuth에서는 사용하지 않는다.

### plan 수집

- Claude plan은 provider가 노출한 값을 그대로 표시한다.
- `ProviderId.CLAUDE.normalizedPlanLabelForDisplay()`는 임의 매핑하지 않는다.
- `Pro`, `Max` 등 Claude가 제공한 원문 값을 유지해야 한다.

### 사용량 수집

- 우선 경로: `https://claude.ai/api/organizations/{organizationId}/usage`
- `ProviderUsageCollectionService.startClaudeDirectUsageFetch()`가 organization id를 확보하면 직접 API URL을 WebView로 로드한다.
- `TextUsageExtractor.extractClaudeUsageApiResponse()`가 다음 키를 해석한다.
  - `five_hour` -> `Five_hour`, window `5 hours`
  - `seven_day` -> `Seven_day`, window `7 days`
  - `seven_day_omelette` -> `Seven_day_omelette`, window `7 days`
- 각 항목은 `utilization`을 used percent로 보고, `resets_at`을 reset 기준으로 사용한다.

### 금지 fallback

- `/new`, `/`, 빈 source에서 나온 `Session`, `Weekly` plan-only/placeholder line은 저장하면 안 된다.
- plan이 없는 상태에서 usage만 잡히면 `shouldWaitForPlanLabel()` 기준으로 추가 probe를 기다린다.

## Codex

### 로그인

- 시작 URL: `https://chatgpt.com/`
- 실제 OAuth는 `CodexOAuthRepository`가 ChatGPT/Codex CLI 계열 OAuth를 사용한다.
- authorize URL: `https://auth.openai.com/oauth/authorize`
- redirect URI: `http://localhost:1455/auth/callback`
- originator: `codex_cli_rs`
- scope: `openid profile email offline_access api.connectors.read api.connectors.invoke`

### plan 수집

- JWT claim `https://api.openai.com/auth.chatgpt_plan_type`에서 plan type을 저장한다.
- `ProviderId.CODEX.normalizedPlanLabelForDisplay()` 매핑:
  - `prolite` -> `Pro 5x`
  - `pro` -> `Pro 20x`
  - 그 외 값은 첫 글자 titlecase만 적용하고 원 의미를 유지한다.

### 사용량 수집

- 우선 경로: `https://chatgpt.com/backend-api/wham/usage`
- `CodexOAuthRepository.fetchUsageSnapshot()`가 Bearer token과 `ChatGPT-Account-Id` 헤더를 사용한다.
- `structuredPayloadFromUsageBodies()`가 다음 응답 키를 정규화한다.
  - `rate_limit.primary_window` -> `Codex 5-hour limit`
  - `rate_limit.secondary_window` -> `Codex weekly limit`
  - `code_review_rate_limit.*` -> `Code review ...`
  - `spark_rate_limit.*`, `spark_rate_limits.*` -> `Spark`, `Spark weekly`
  - `credits.balance` -> `Credits`
- `limit_window_seconds = 18_000`이면 5시간 한도, `604_800`이면 주간 한도다.
- `used_percent`, `usedPercentage`, `used_percentage`, `percent_used`를 used percent로 사용한다.
- `reset_at` 또는 `reset_after_seconds`를 reset 기준으로 사용한다.

### 금지 fallback

- Codex는 OAuth 수집이 성공하면 WebView fallback보다 우선한다.
- 5시간 한도가 이미 사용 중인데 reset이 "메시지를 보내면 시작"으로 바뀌는 값은 신뢰하지 않는다. 실제 WHAM usage window의 reset 값을 우선한다.

## Gemini

### 로그인

- 시작 URL: Google 계정 선택 URL
  - `https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fgemini.google.com%2Fapp&hl=ko`
- Gemini는 일반 Gemini WebView 로그인 외에 Gemini CLI/Code Assist OAuth 수집 경로를 가진다.
- OAuth redirect URI: `http://127.0.0.1:46417/oauth2callback`
- scope: `cloud-platform`, `userinfo.email`, `userinfo.profile`

### plan 수집

- 우선 경로는 `GeminiCliOAuthRepository.loadCodeAssist()`의 `loadCodeAssist` 응답이다.
- `paidTier.name`, `paidTier.id`, `currentTier.name`, `currentTier.id` 순서로 plan 후보를 읽는다.
- `ProviderId.GEMINI.normalizedPlanLabelForDisplay()` 매핑:
  - `GOOGLE_AI_PRO`, `GEMINI_PRO`, `g1_pro_tier`, `gemini-codeassist-google-one-ai-pro` 계열 -> `Gemini Pro`
  - `GOOGLE_AI_PLUS`, `GEMINI_PLUS` 계열 -> `Gemini Plus`
  - `GOOGLE_AI_ULTRA`, `GEMINI_ULTRA`, `g1_ultra_tier` 계열 -> `Gemini Ultra`
  - `GEMINI_ADVANCED` 계열 -> `Gemini Advanced`
  - `GOOGLE_ONE_AI_PREMIUM` 계열 -> `Google One AI Premium`
  - `GEMINI_FREE`, `GOOGLE_AI_FREE` 계열 -> `Gemini Free`
  - `GEMINI_UNKNOWN`은 저장 plan으로 쓰지 않는다.

### 사용량 수집

- 우선 경로: Google Code Assist API
  - `loadCodeAssist`
  - 필요 시 `onboardUser`
  - `retrieveUserQuota`
- `GeminiCliOAuthRepository.structuredPayloadFromCodeAssist()`가 `retrieveUserQuota.buckets`를 읽는다.
- `bucket.modelId`에 `pro`가 있으면 `Gemini Pro`, `flash`가 있으면 `Gemini Flash`로 표시한다.
- `bucket.remainingFraction`을 남은 비율로 사용하고, used percent는 `1 - remainingFraction`으로 계산한다.
- `bucket.resetTime`을 reset 기준으로 사용한다.
- source는 `Gemini Code Assist retrieveUserQuota`, confidence는 `0.99`가 정상이다.

### WebView/asset fallback

- `gemini_collector.js`는 APK 분석에서 확인한 구조와 같은 `account.p`, `usage.x[]` 형태를 로컬 asset으로 구현한 fallback이다.
- 지원 label은 반드시 다음 3개만 허용한다.
  - `Gemini Pro`
  - `Gemini Flash`
  - `Gemini Deep Research`
- 중복 라인은 label별로 score가 높은 라인을 선택한다.
- 실제 측정값이 있는 라인, reset 정보가 있는 라인, confidence가 높은 라인을 우선한다.

### 금지 fallback

- `Usage`, `사용량`, `Gemini Usage` 같은 generic label은 저장하면 안 된다.
- `CheckGeminiQuota` 계열에서 confidence가 낮고 100% 남음인 라인은 실제 사용량으로 확정하면 안 된다.
- reset이 "메시지를 보내면 시작"이고 100% 남음인 라인은 실제 사용량보다 낮은 우선순위로 둔다.
- 기존 저장 plan이 `Free`인 경우, 새 refresh에서 plan 근거가 없으면 carry하지 않는다.

### 현재 확인된 정상 예시

- 태블릿에서 확인된 정상 스냅샷:
  - plan: `Gemini Pro`
  - `Gemini Pro`: 약 `98% left`
  - `Gemini Flash`: 약 `99% left`
  - source: `Gemini Code Assist retrieveUserQuota`

## Copilot

### 로그인

- 시작 URL: `https://github.com/settings/copilot`
- GitHub WebView 세션을 사용한다.

### plan 수집

- 우선 `github-copilot/chat/entitlement` 응답의 `plan`, `sku`, `licenseType`, `license_type` 계열 값을 사용한다.
- 표시명은 `GitHub Copilot`이 아니라 `Copilot`으로 통일한다.
- plan label은 provider 원문 값을 titlecase 수준으로만 표시한다.

### 사용량 수집

- 우선 경로: `https://github.com/github-copilot/chat/entitlement`
- `TextUsageExtractor.extractCopilotEntitlementResponse()`가 `quotas.remaining`, `quotas.limits`, `quotas.resetDate`를 해석한다.
- 주요 라인:
  - `Chat`: `remaining.chat`, `limits.chat/messages`, `remaining.chatPercentage`
  - `Completions`: `remaining.completions`, `limits.completions/...`, `remaining.completionsPercentage`
  - `Premium requests`: `remaining.premiumInteractions`, `limits.premiumInteractions`, `remaining.premiumInteractionsPercentage`
- `resetDate`는 월간 reset 기준으로 사용한다.
- Free 또는 `licensed_limited`에서 completions limit이 응답에 없으면 `4000` completions를 기본 cap으로 보정한다.

### 금지 fallback

- `/features/copilot/plans` 같은 마케팅/플랜 페이지 숫자는 사용량으로 저장하면 안 된다.
- URL, sitemap, completed 같은 비사용량 문자열은 line label로 저장하면 안 된다.

## Cursor

### 로그인

- 시작 URL: `https://cursor.com/dashboard`
- Cursor WebView 세션과 dashboard JSON/network/상태 응답을 사용한다.

### plan 수집

- `rememberCursorPlan()`이 다음 키에서 plan 후보를 찾는다.
  - `plan`, `planName`, `plan_label`, `planType`, `membershipType`, `limitType`, `subscription`, `tier`
  - `planInfo.*`, `plan.*`, `individualUsage.*`
- 표시 plan은 provider 원문 값을 titlecase 수준으로 유지한다.

### 사용량 수집

Cursor는 두 구조를 모두 지원해야 한다.

#### 1. planUsage 방식

예시:

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

- `scanCursorPlanUsageObject()`가 `planUsage`, `usage.planUsage`, 또는 객체 자체가 planUsage인 경우를 읽는다.
- `totalSpend`, `totalUsage`, `spend`, `used`, `usage`는 used 후보.
- `limit`, `monthlyLimit`, `totalLimit`는 cap 후보.
- `remaining`, `totalRemaining`, `balance`는 remaining 후보.
- 금액 단위는 cent처럼 큰 정수로 내려오면 `cursorMoneyAmount()`에서 USD로 보정한다.
- 기본 라인은 `Total usage`다.
- 가능한 breakdown:
  - `Auto usage`
  - `API usage`
  - `On-demand`

#### 2. request-based 방식

예시:

```json
{
  "gpt-4": {
    "numRequests": 77,
    "maxRequestUsage": 500
  },
  "startOfMonth": "..."
}
```

- `scanCursorRequestUsage()`가 `numRequests`, `requestUsage`, `currentRequestUsage`, `requests`, `used`, `usage`를 used 후보로 읽는다.
- `maxRequestUsage`, `maxRequests`, `requestLimit`, `limit`, `limitAmount`를 cap 후보로 읽는다.
- 여러 model bucket이 있으면 total used/cap으로 합산하고 `Total usage` 라인으로 저장한다.
- 단위는 `requests`다.

#### 3. individualUsage summary 방식

- `scanCursorUsageSummary()`는 `individualUsage.plan`과 `individualUsage.onDemand`를 읽는다.
- `plan.limit`, `plan.remaining`, `plan.breakdown`을 `Total usage`로 정규화한다.
- `onDemand.limit`, `onDemand.remaining`을 `On-demand usage`로 정규화한다.

### 금지 fallback

- `remainingCap`만 있는 낮은 confidence 라인은 더 신뢰도 높은 `Total usage`가 있으면 우선순위에서 밀려야 한다.
- `completed`, `sitemap`, 마케팅 텍스트는 저장하면 안 된다.
- Cursor는 `isCursorLiveCounterLine()` 기준을 통과해야 live counter로 취급한다.

## 문제 발생 시 확인 절차

1. 태블릿 기준으로 현재 저장 스냅샷을 먼저 확인한다.

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$xml = & $adb -s emulator-5556 exec-out run-as com.aiusage.mobile cat shared_prefs/ai_usage_local_usage.xml
$raw = [regex]::Match($xml, '<string name="provider_snapshots">(.*?)</string>', 'Singleline').Groups[1].Value
$decoded = [System.Net.WebUtility]::HtmlDecode($raw)
$decoded | ConvertFrom-Json | ConvertTo-Json -Depth 20
```

2. 수집 로그를 provider별로 확인한다.

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5556 logcat -d -s AIUsageProvider
```

3. 로그에서 다음 marker를 우선 확인한다.
   - Codex: `collection codexOAuthUsage`
   - Gemini: `collection geminiCliOAuthUsage`
   - Claude: `collection claudeDirectUsageWebView`
   - Copilot: `/github-copilot/chat/entitlement:200`
   - Cursor: `planUsage`, `requestUsage`, `usage-summary`

4. 사용량이 갑자기 generic line으로 바뀌면 먼저 `TextUsageExtractor`와 `ProviderUsageCollectionService.isTrustedCounterLine()`의 필터가 약해졌는지 확인한다.
5. plan만 있고 usage가 없으면 API/network 응답이 비어 있는지, 로그인 세션이 끊겼는지, 기존 신뢰 가능한 스냅샷을 보존하는 로직이 작동했는지 확인한다.
6. Gemini가 `Free` 또는 `사용량 0%`처럼 보이면 이 문서의 Gemini 금지 fallback을 먼저 확인한다.

## 테스트 기준

- Provider model/label 정규화:
  - `./gradlew :app:testDebugUnitTest --tests com.aiusage.mobile.local.ProviderModelsTest`
- Text extractor:
  - `./gradlew :app:testDebugUnitTest --tests com.aiusage.mobile.providers.TextUsageExtractorTest`
- provider local collector:
  - `./gradlew :app:testDebugUnitTest --tests com.aiusage.mobile.providers.ProviderLocalUsageCollectorTest`
- Android 빌드:
  - `./gradlew :app:assembleDebug`

## 유지보수 규칙

- provider API 응답 구조를 새로 확인하면 이 문서를 먼저 갱신한다.
- 수집 로직 변경 시 관련 unit test를 같이 갱신한다.
- provider가 실제 사용량을 노출하지 않는 상태를 임의 숫자로 채우지 않는다.
- 이전 정상 스냅샷보다 신뢰도가 낮은 새 fallback이 들어오면 저장하지 않는다.
- 특히 Gemini는 `Gemini Pro`, `Gemini Flash`, `Gemini Deep Research` 외 label을 usage line으로 저장하지 않는다.
