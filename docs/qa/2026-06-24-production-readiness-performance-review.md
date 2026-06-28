# 2026-06-24 프로덕션 전 성능 및 안정성 리뷰

## 결론

현재 코드 기준 프로덕션 전 상태는 `REQUEST_CHANGES`입니다.

소스 코드는 수정하지 않고 리뷰만 수행했습니다. 검토 결과, 기능 자체보다 장시간 백그라운드 refresh, FGS 시작 조건, WebView 세션 정리, snapshot 저장 비용 쪽에서 모바일 성능 및 안정성 리스크가 남아 있습니다.

## 검토 범위

- Android Provider refresh 및 Foreground Service 흐름
- WebView 기반 provider 세션 유지 및 정리 흐름
- 위젯/알림/로컬 snapshot 저장 흐름
- 보안/개인정보/릴리즈 빌드 설정
- GLM, OpenCode 신규 provider 자체 구현과 설정의 연결해제 세부 이슈는 별도 이슈로 보고, 이번 리뷰의 주요 결론에서는 제외

## 주요 Findings

### High: Live monitoring 중지 실패 가능성

`ForegroundRefreshController.stopPreciseRefresh()`가 인메모리 플래그인 `preciseRefreshRequested`에 의존합니다.

컨트롤러가 재생성된 뒤에는 실제 FGS가 실행 중이어도 플래그가 false일 수 있고, 이 경우 `ACTION_STOP`을 보내지 않고 반환할 수 있습니다. 사용자가 live monitoring을 끈 뒤에도 서비스가 계속 남는 문제가 생길 수 있습니다.

관련 파일:

- `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshController.kt`

### High: Android 12+ 백그라운드 FGS 시작 제한 리스크

targetSdk 35 환경에서 백그라운드 상태의 FGS 시작은 제한됩니다. 현재 지연된 위젯 configure refresh나 health worker 재시작 경로가 백그라운드에서 FGS를 시작할 수 있어 `ForegroundServiceStartNotAllowedException` 위험이 있습니다.

관련 파일:

- `android/app/src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureRefreshRequester.kt`
- `android/app/src/main/java/com/aiquota/mobile/sync/ForegroundRefreshHealthWorker.kt`

참고:

- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

### High: GLM/Z.AI 세션 정리 완료 보장 부족

GLM 세션 정리에서 WebView를 destroy 하기 전에 IndexedDB, CacheStorage, ServiceWorker 정리 작업이 실제로 완료됐는지 보장되지 않을 수 있습니다.

세션 만료 자체는 GLM 전용 범위로 좁힌 방향이 맞지만, 비동기 브라우저 storage cleanup 완료 신호 없이 WebView 수명 종료가 앞서면 z.ai 세션 잔존 문제가 재발할 수 있습니다.

관련 파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt`

### Medium: 60초 refresh loop와 긴 provider timeout 조합

refresh 주기는 60초인데 provider별 수집이 직렬이고, WebView provider의 timeout이 길 수 있습니다. 네트워크 지연이나 특정 provider 지연이 생기면 refresh cycle이 다음 cycle과 맞물려 사실상 연속 작업처럼 동작할 수 있습니다.

사용자 체감상 다른 provider까지 늦게 갱신되는 문제, 배터리 사용량 증가, WebView 프로세스 유지 시간이 늘어나는 문제가 생길 수 있습니다.

관련 파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt`

### Medium: collectorInjectionKeys 장기 증가 가능성

서비스 수명 동안 `collectorInjectionKeys`가 provider와 URL 조합 기준으로 계속 남을 수 있습니다. 장시간 foreground refresh 환경에서 URL 변화가 많은 provider가 있으면 메모리 유지량이 증가할 수 있습니다.

관련 파일:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt`

### Medium: snapshot refresh 비용 반복

`UsageSurfaceRefresher.refresh()` 경로에서 snapshot JSON decode/export/write가 반복됩니다. provider 수와 usage line 수가 늘어난 현재 구조에서는 위젯/알림 갱신 때 CPU 및 I/O 비용이 커질 수 있습니다.

관련 파일:

- `android/app/src/main/java/com/aiquota/mobile/local/UsageSurfaceRefresher.kt`
- `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt`

### Medium: 릴리즈 난독화/최적화 설정

release 빌드에서 shrink 계열 설정은 있으나 optimize까지 강하게 켠 구성은 아닙니다. 보안 관점에서는 secret key 직접 노출은 별도로 확인해야 하며, 현재 설정만으로 난독화/최적화가 충분하다고 보기는 어렵습니다.

관련 파일:

- `android/app/build.gradle.kts`
- `android/app/proguard-rules.pro`

## 추가 확인: 현재 GLM Web OAuth 세션 상태

요청 시점에 GLM Web OAuth 세션이 끊긴 것처럼 보인다는 보고가 있어 에뮬레이터의 실제 저장 상태와 logcat을 읽기 전용으로 확인했습니다.

확인 시각:

- 호스트 기준: `2026-06-24T11:27:01+09:00`
- 디바이스: `emulator-5554`
- 앱 패키지: `com.aiquota.mobile`

관찰값:

- 앱 프로세스 실행 중: `com.aiquota.mobile`
- GLM isolated WebView 프로세스 실행 중: `com.aiquota.mobile:glm_webview`
- `usage_data_glm.xml`의 최신 `updated_at`: `2026-06-24T02:27:09.417886Z`
- GLM snapshot 상태: `CONNECTED`
- GLM refresh 상태: `IDLE`
- 플랜: `Lite`
- 수집 source: `visible-dom`
- 수집 항목:
  - `5시간 한도`: 97% 남음
  - `주간 한도`: 99% 남음
  - `월간 한도`: 100% 남음

판정:

- 확인 시점 기준 GLM Web OAuth 세션은 끊긴 상태가 아닙니다.
- GLM snapshot은 최신으로 저장되고 있었습니다.
- 직전 관찰 중 `account_data_glm.xml`이 일시적으로 `COLLECTING`으로 보인 시점이 있었으나, 다음 확인에서 `CONNECTED`로 돌아왔습니다.
- background refresh state에는 `last_failure=TIMEOUT`이 남아 있었지만, 같은 시점 이후 GLM snapshot은 정상 갱신됐습니다. 따라서 이 값만으로 GLM 세션 만료라고 판단하면 안 됩니다.

## 권장 조치

1. Live monitoring 중지 경로를 인메모리 플래그가 아니라 실제 서비스 상태 기준으로 보강합니다.
2. Android 12+ 백그라운드 FGS 시작 가능 경로를 분리하고, 가능한 경우 WorkManager/알림 상호작용/앱 foreground 조건을 기준으로 시작합니다.
3. GLM/Z.AI 세션 정리는 WebView destroy 전에 IndexedDB, CacheStorage, ServiceWorker cleanup 완료를 명시적으로 기다리도록 보강합니다.
4. provider별 refresh 시간이 60초 주기를 초과할 때 다음 cycle과 겹치지 않도록 backpressure 또는 cycle skip 정책을 명확히 둡니다.
5. `collectorInjectionKeys`는 request scope 또는 bounded cache로 제한합니다.
6. snapshot export/write는 변경 감지 또는 coalescing을 적용해 반복 I/O를 줄입니다.
7. release 빌드 보안 설정은 AAB 생성 전 별도 체크리스트로 검증합니다.

## 미검증 항목

- 실제 15분 이상 장시간 에뮬레이터 refresh QA는 이번 요청 범위에서 수행하지 않았습니다.
- 실제 배터리 소모량, 메모리 peak, WebView 프로세스 장기 유지량은 측정하지 않았습니다.
- 릴리즈 AAB의 최종 난독화 산출물 역분석은 수행하지 않았습니다.

## 참고 evidence

상세 리뷰 evidence 파일:

- `.omo/evidence/android-app-code-quality-performance-review-code-review.md`
- `.omo/evidence/android-security-privacy-production-readiness-code-review.md`

이번 GLM 세션 점검에서 확인한 저장소:

- `shared_prefs/usage_data_glm.xml`
- `shared_prefs/account_data_glm.xml`
- `shared_prefs/ai_quota_local_usage.xml`
- `logcat`
