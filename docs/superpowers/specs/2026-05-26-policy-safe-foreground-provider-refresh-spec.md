# Policy Safe Foreground Provider Refresh Spec

Date: 2026-05-26

## Objective

AI Quota Android 앱은 사용자가 알림 권한과 라이브 모니터링을 허용한 경우에만 상단 고정 알림 기반 foreground refresh service를 유지하고, 연결된 provider 사용량을 약 90초마다 수집해야 한다.

위젯의 시스템 자동 업데이트는 실제 수집을 새로 시작하지 않고, 서비스 또는 앱이 만든 최신 로컬 캐시만 다시 그려야 한다. 실제 수집은 앱 foreground, 사용자 수동 refresh, 또는 이미 살아 있는 user-approved foreground refresh loop에서만 수행한다.

## Primary Plan

Implementation plan:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/plans/2026-05-26-policy-safe-foreground-provider-refresh.md`

## Required Documentation Outputs

이 작업은 코드 변경만으로 완료하지 않는다. 구현 중 아래 3개 문서를 반드시 작성하고 갱신해야 한다.

### 1. 작업 진행 문서

Path:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/policy-safe-foreground-provider-refresh-progress-2026-05-26.md`

Purpose:

- 작업이 어떤 순서로 진행됐는지 추적한다.
- 테스트 실패와 통과, 적용한 파일, 남은 작업을 한눈에 확인할 수 있게 한다.
- 다음 세션이나 goal 재개 시 현재 위치를 복구할 수 있게 한다.

Required entries:

- 시간
- 작업 내용
- 변경 파일
- 실행 명령
- 결과
- 다음 작업

Entry format:

```markdown
## YYYY-MM-DD HH:mm KST

**작업:** <무엇을 했는지>

**변경 파일:**
- `<path>`

**실행 명령:**
```powershell
<command>
```

**결과:** <PASS/FAIL/blocked>

**다음 작업:** <다음으로 해야 할 일>
```
```

Update timing:

- 테스트를 추가한 직후
- 구현 단위를 완료한 직후
- focused test를 실행한 직후
- 전체 검증을 실행한 직후
- 에뮬레이터 검증을 실행한 직후

### 2. 문제 해결 문서

Path:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/policy-safe-foreground-provider-refresh-troubleshooting-2026-05-26.md`

Purpose:

- foreground service, 위젯 refresh, 알림 권한, provider 수집 실패를 같은 문제로 섞지 않고 분리한다.
- 실패 원인과 조치 근거를 남겨 이후 같은 문제가 반복될 때 빠르게 판단한다.
- Play 정책/Android 제한과 실제 구현 버그를 구분한다.

Required entries:

- 증상
- 재현 조건
- 관련 로그 또는 테스트 실패
- 원인 가설
- 확인한 사실
- 조치
- 남은 위험

Entry format:

```markdown
## Issue N: <짧은 제목>

**증상:** <사용자 또는 테스트에서 보인 현상>

**재현 조건:** <어떤 상태에서 발생했는지>

**증거:**
```text
<redacted log or test output>
```

**원인:** <확정 또는 현재 가설>

**조치:** <적용한 변경>

**검증:** <실행한 테스트/명령과 결과>

**남은 위험:** <아직 남은 제약 또는 확인 필요 사항>
```
```

Redaction rule:

- raw token, cookie, OAuth code, auth header, account id, email, username, full provider HTML은 기록하지 않는다.
- provider id, endpoint path, HTTP status, error kind, timeout 여부, selector/API shape 변경 여부는 기록할 수 있다.

### 3. 제한 사항 문서

Path:

`D:/Vibe Project/AI Usage for Mobile/docs/qa/policy-safe-foreground-provider-refresh-limitations-2026-05-26.md`

Purpose:

- 이 구조로 보장 가능한 것과 불가능한 것을 명확히 남긴다.
- Store 심사, Android OS 제한, provider 인증 제한을 과장하지 않는다.
- 사용자에게 설명해야 하는 운영 한계를 정리한다.

Required sections:

- Android foreground service 제한
- 알림 권한 거부 시 동작
- 사용자가 앱을 강제 종료한 경우
- 기기 재부팅 후 동작
- 배터리 최적화/OEM 제한
- passive widget update의 역할
- widget manual refresh의 역할
- Gemini/Antigravity OAuth secret-free 전환 한계
- provider 세션 만료와 collector 실패의 구분

Minimum limitations to document:

- 사용자가 앱을 force-stop하면 앱은 스스로 다시 시작할 수 없다.
- Android 15+ target에서 `BOOT_COMPLETED`로 `dataSync` foreground service를 새로 시작하는 경로는 사용하지 않는다.
- 알림 권한이 없으면 고정 알림 기반 90초 refresh loop를 보장하지 않는다.
- passive widget update는 캐시 표시만 담당한다.
- 수동 위젯 refresh는 사용자 액션이므로 즉시 수집 요청 경로가 될 수 있다.
- provider가 실제 interactive auth를 요구하면 앱이 이를 우회하지 않는다.
- Gemini/Antigravity는 `client_secret`을 APK에 다시 넣는 방식으로 해결하지 않는다.

## Current Baseline

Existing implementation already has these important pieces:

- `ProviderBackgroundRefreshService` runs provider refresh work.
- `ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS` is `90_000L`.
- `WidgetAutoRefreshStarter` redraws cached surfaces and must not start `startForegroundService()`.
- `WidgetManualRefreshReceiver` is a user action path.
- `ForegroundRefreshPolicy` only treats trusted usage rows in refreshable states as refresh candidates.
- `CONNECTED` must mean trusted usage row save success, not just login page/session cookie presence.

## Requirements

### R1. User-Approved Live Monitoring

Foreground refresh loop must run only when:

- user has allowed Android notifications,
- user has enabled live monitoring or equivalent pinned notification refresh setting,
- at least one provider has trusted usage rows in a refreshable state.

The app must not silently start long-running provider collection just because a widget exists.

### R2. 90-Second Copy And Cadence

All user-facing copy must say "about every 90 seconds" or Korean equivalent.

Do not claim exact timing. Android scheduling, provider latency, network conditions, and service interruption can shift actual collection time.

### R3. Foreground Service Type

Use `dataSync` foreground service type:

- keep `android.permission.FOREGROUND_SERVICE`,
- keep `android.permission.FOREGROUND_SERVICE_DATA_SYNC`,
- keep `android:foregroundServiceType="dataSync"`,
- call typed `startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` where supported.

Do not add `FOREGROUND_SERVICE_SPECIAL_USE` unless a separate review explicitly approves that tradeoff.

### R4. Passive Widget Update Boundary

Passive widget update must:

- read local display-only cache,
- redraw widget surfaces,
- not start foreground service,
- not show manual spinner feedback,
- not call provider collectors.

### R5. Manual Widget Refresh Boundary

Manual widget refresh may:

- mark only the pressed widget as refreshing,
- send a user-action refresh request,
- start `ProviderBackgroundRefreshService.ACTION_REFRESH`,
- target a selected provider when the widget is provider-specific.

Manual refresh must clear spinner feedback if Android rejects the foreground service start.

### R6. Notification Control

Pinned notification must:

- clearly represent live monitoring,
- be silent/low priority,
- include a stop action,
- stop the foreground loop when the user disables live monitoring.

If the user clears all notifications and Android allows the foreground service to continue, the service should keep or restore its foreground notification according to OS behavior. If the service is stopped, the app foreground path should detect stale heartbeat and restart only when policy allows it.

### R7. Service Heartbeat

The service must write a non-sensitive heartbeat:

- service started,
- last heartbeat time,
- last stopped time,
- last failure kind.

The app foreground path should detect a stale heartbeat and restart the service only if live monitoring is enabled and notification permission is available.

### R8. Provider Trust Model

Provider state must keep the trust-first model:

- `CONNECTED` only after trusted usage row save success.
- Timeout, no payload, selector mismatch, and API shape changes are not automatically auth required.
- `INTERACTIVE_AUTH_REQUIRED` is only for clear login redirect, main-frame 401/403, explicit collector auth-required result, or confirmed OAuth/token refresh failure.
- Stale/old usage must not look like fresh current usage.

### R9. Security And Privacy

Implementation and docs must not store or log:

- raw access token,
- refresh token,
- cookie,
- OAuth code,
- auth header,
- account id,
- email,
- username,
- full provider HTML.

Allowed diagnostics:

- provider id,
- login redirect yes/no,
- main-frame 401/403 yes/no,
- session probe success/failure,
- usage page reached yes/no,
- payload emitted yes/no,
- normalizer accepted/rejected,
- timeout yes/no,
- selector/API shape failure category.

### R10. Play Store Documentation

Create or update:

`D:/Vibe Project/AI Usage for Mobile/docs/store/foreground-service-declaration.md`

It must include:

- foreground service type,
- app functionality,
- user action that starts the feature,
- user-visible disclosure and control,
- impact if deferred/interrupted,
- why passive widget update does not start service,
- data safety notes,
- review video checklist.

## Acceptance Criteria

- Spec, plan, progress, troubleshooting, and limitations documents exist.
- Passive widget updates remain cache-only.
- Manual widget refresh remains a user action collection path.
- Foreground loop starts only from allowed user-visible or foreground app paths.
- Live monitoring copy says approximately 90 seconds.
- Notification has a stop path.
- Service heartbeat is non-sensitive and usable for app-foreground restart detection.
- `dataSync` foreground service type remains the default.
- No `specialUse`, exact alarm, boot-started dataSync FGS, or embedded OAuth client secret is added.
- `:app:testDebugUnitTest`, `:app:assembleDebug`, `git diff --check`, and emulator refresh log checks pass before implementation is considered complete.

## Non-Goals

- Do not bypass Android background execution limits.
- Do not bypass provider interactive authentication.
- Do not make passive widget update perform hidden collection.
- Do not add backend token broker in this task.
- Do not re-add Google OAuth `client_secret` to the APK.
- Do not claim Gemini/Antigravity release reliability until their secret-free OAuth path is separately fixed and verified.

## Goal Prompt

Use the companion prompt:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/prompts/2026-05-26-policy-safe-foreground-provider-refresh-goal-prompt.md`
