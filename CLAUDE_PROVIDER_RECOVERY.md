# Claude Provider Recovery Notes

최종 정리 기준: 2026-05-19

이 문서는 Claude provider의 현재 로그인, 세션 검증, 사용량 수집, 중복 방지 구현 상태를 복구용으로 정리한 문서다.
Claude 관련 코드가 꼬이면 이 문서와 `PROVIDER_USAGE_COLLECTION.md`의 Claude 섹션을 먼저 비교한다.

## 현재 상태

- Claude reset timer 문제는 해결됐다.
- reset 값은 `https://claude.ai/api/organizations/{organizationId}/usage`의 `resets_at`을 기준으로 표시한다.
- `리셋 타이머 대기 중`은 실제 reset 값이 없는 line에만 표시해야 한다.
- Claude 로그인은 WebView 내부 Google OAuth를 사용한다.
- Google OAuth 이후 Claude가 `/login`에 머물거나 앱 shell로 늦게 넘어가는 경우가 있으므로 URL/DOM만으로 완료 판단하지 않는다.
- Claude 사용량 line 중복은 canonical quota key로 제거한다.

## 관련 파일

| 역할 | 파일 |
| --- | --- |
| 로그인 WebView 및 Claude 세션 검증 | `android/app/src/main/java/com/aiusage/mobile/providers/WebLoginActivity.kt` |
| 로그인 transient error 복구 정책 | `android/app/src/main/java/com/aiusage/mobile/providers/ProviderLoginRecoveryPolicy.kt` |
| Claude 로그인 URL rewrite | `android/app/src/main/java/com/aiusage/mobile/providers/ProviderLoginUrlRewriter.kt` |
| Claude 로그인 전 세션 정리 | `android/app/src/main/java/com/aiusage/mobile/providers/ProviderLoginSessionPreparer.kt` |
| Claude direct usage API 수집 | `android/app/src/main/java/com/aiusage/mobile/providers/ProviderUsageCollectionService.kt` |
| Claude usage API payload 파싱 | `android/app/src/main/java/com/aiusage/mobile/providers/TextUsageExtractor.kt` |
| Claude line dedupe 공통 로직 | `android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt` |
| 저장소 read/write normalization | `android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt` |
| Claude/WebLogin 단위 테스트 | `android/app/src/test/java/com/aiusage/mobile/providers/WebLoginActivityTest.kt` |
| provider model/dedupe 단위 테스트 | `android/app/src/test/java/com/aiusage/mobile/local/ProviderModelsTest.kt` |
| 수집 서비스 단위 테스트 | `android/app/src/test/java/com/aiusage/mobile/providers/ProviderUsageCollectionServiceTest.kt` |

## 로그인 흐름

```text
Claude 연결 버튼
  -> WebLoginActivity
  -> ProviderLoginSessionPreparer.prepare(CLAUDE)
  -> Claude 쿠키/WebStorage 중 sessionKey, lastActiveOrg, activitySessionId 정리
  -> cf_clearance, __cf_bm 보존
  -> https://claude.ai/ 로드
  -> Google OAuth 시작 URL 감지
  -> ProviderLoginUrlRewriter가 prompt=select_account 추가
  -> Google 계정 선택/로그인
  -> Google OAuth main-frame transient error 발생 시 최대 2회 https://claude.ai/ 로 복구 로드
  -> Claude host로 복귀
  -> /api/organizations, /api/organizations/me 세션 검증
  -> organization id 확인
  -> lastActiveOrg 쿠키 보강 저장
  -> 앱으로 복귀
  -> ProviderUsageCollectionService가 background usage 수집
```

## 로그인 완료 판단 우선순위

1. WebView 내부 fetch로 `/api/organizations` 또는 `/api/organizations/me`가 200이고 organization id를 찾음
2. Claude 앱 경로(`/new` 등)로 이동했고 `lastActiveOrg` 쿠키가 있음
3. JS payload가 `authenticatedApp=true`, `appShellConfirmed=true`, 또는 limit data를 제공함
4. main frame error가 발생했지만 `lastActiveOrg` 쿠키가 있음
5. Google OAuth host에서 main frame error가 발생했지만 아직 세션 검증 전이면 즉시 실패 저장하지 않고 `https://claude.ai/`로 복구함

`/login` URL 자체는 완료 처리하지 않는다.
`/logout` URL은 완료 처리하지 않는다.

## Claude 세션 검증 코드 계약

- `WebLoginActivity.maybeVerifyClaudeSessionWithApi()`가 Claude host에서만 실행된다.
- `claudeSessionVerificationScript()`는 WebView origin에서 다음 endpoint를 순서대로 호출한다.
  - `/api/organizations`
  - `/api/organizations/me`
- fetch 옵션은 `credentials: "include"`를 사용한다.
- `claudeOrganizationIdFromVerificationPayload()`가 JSON body에서 UUID 형식 organization id를 찾는다.
- organization id를 찾으면 `lastActiveOrg={organizationId}; Path=/; Secure; SameSite=Lax` 쿠키를 저장한다.

정상 로그 marker:

```text
login claudeApiSessionVerified provider=claude org={organizationId}
login claudeOrgStored provider=claude
```

## 사용량 수집 흐름

```text
ProviderUsageCollectionService
  -> CookieManager에서 lastActiveOrg 확인
  -> https://claude.ai/api/organizations/{organizationId}/usage 로드
  -> TextUsageExtractor.extractClaudeUsageApiResponse()
  -> ProviderUsageLine 3개 정규화
  -> ProviderId.CLAUDE.deduplicateUsageLinesForStorage()
  -> LocalUsageRepository.saveSnapshot()
```

정상 usage line:

| API key | 표시 | window | reset |
| --- | --- | --- | --- |
| `five_hour` | Claude 5시간 한도 | `5 hours` | `resets_at` |
| `seven_day` | Claude 주간 한도 | `7 days` | `resets_at` |
| `seven_day_omelette` | Claude Design | `7 days` | 없을 수 있음 |

정상 로그 marker:

```text
collection claudeDirectUsageWebView
/api/organizations/{organizationId}/usage
```

## 중복 발생 원인

같은 Claude usage API 값이 서로 다른 raw label/source 형태로 들어오면 기존 dedupe key가 다르게 계산될 수 있었다.

예시:

```text
Five_hour | 5 hours | /api/organizations/:id/usage
Claude 5시간 한도 | 5 hours | /api/organizations/{organizationId}/usage
```

UI에서는 둘 다 `Claude 5시간 한도`로 표시되므로 중복처럼 보인다.
실제 진짜 값은 둘 중 하나가 아니라 같은 `/usage` 값이 두 번 저장된 것이다.

## 중복 방지 계약

Claude line은 저장 전 다음 canonical key로 dedupe한다.

| canonical key | 허용 raw 형태 |
| --- | --- |
| `claude:five_hour` | `Five_hour`, `five_hour`, `Claude 5시간 한도`, `5 hours` |
| `claude:seven_day` | `Seven_day`, `seven_day`, `Claude 주간 한도`, `weekly`, `7 days` |
| `claude:seven_day_omelette` | `Seven_day_omelette`, `Claude Design`, `omelette`, `design` |

동일 canonical key가 여러 개이면 더 신뢰도 높은 line을 남긴다.

우선순위:

1. `/api/organizations/{id}/usage` source
2. `remainingPercent` 있음
3. `resetsAt` 있음
4. `resetText` 있음
5. `sourceLabel` 있음
6. confidence 높음

이 로직은 수집 단계와 저장소 normalization 단계 모두에서 적용한다.
따라서 이미 6개 line이 저장되어 있어도 앱이 snapshot을 다시 읽으면 3개로 정리되어야 한다.

## 금지 사항

- Claude `/new`, `/`, 빈 source에서 나온 `Session`, `Weekly` placeholder line을 저장하지 않는다.
- 정상 `/usage` line을 빈 fallback으로 덮어쓰지 않는다.
- 실제 `resetsAt`이 있는데 `리셋 타이머 대기 중`으로 대체하지 않는다.
- `/login` URL만 보고 로그인 완료 처리하지 않는다.
- Claude OAuth에서 Google `AccountChooser` wrapper를 다시 사용하지 않는다.

## 검증 명령

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:testDebugUnitTest `
  --tests com.aiusage.mobile.local.ProviderModelsTest `
  --tests com.aiusage.mobile.providers.WebLoginActivityTest `
  --tests com.aiusage.mobile.providers.ProviderUsageCollectionServiceTest `
  --tests com.aiusage.mobile.providers.TextUsageExtractorTest

.\.tmp\gradle-8.10.2\bin\gradle.bat -p android :app:assembleDebug
```

## 에뮬레이터 snapshot 확인

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$xml = & $adb -s emulator-5556 exec-out run-as com.aiusage.mobile cat shared_prefs/ai_usage_local_usage.xml
$raw = [regex]::Match(($xml -join ''), '<string name="provider_snapshots">(.*?)</string>', 'Singleline').Groups[1].Value
$decoded = [System.Net.WebUtility]::HtmlDecode($raw)
$json = $decoded | ConvertFrom-Json
$json.providers | Where-Object providerId -eq 'claude' | ConvertTo-Json -Depth 20
```

정상 상태:

- Claude line은 3개다.
- `Five_hour` 또는 `Claude 5시간 한도` 계열이 1개만 있다.
- `Seven_day` 또는 `Claude 주간 한도` 계열이 1개만 있다.
- `Seven_day_omelette` 또는 `Claude Design` 계열이 1개만 있다.
- 5시간/주간 line은 실제 `resetsAt`이 있어야 한다.
