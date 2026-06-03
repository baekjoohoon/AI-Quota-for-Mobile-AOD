# AI Quota foreground service 선언 초안

작성일: 2026-06-01

## Foreground service type

`dataSync`

## 앱 기능 설명

AI Quota는 사용자가 연결한 AI provider의 quota 사용량을 앱, 홈 화면 위젯, 선택형 상태 표시줄 고정 알림에서 확인할 수 있게 합니다. 사용자가 60초 라이브 위젯 모니터링을 켜고 Android 알림 권한을 허용하면, 앱은 foreground service를 실행해 연결된 provider 사용량을 가져오고 앱, 위젯, 알림 표시용 로컬 cache를 약 60초마다 갱신합니다.

대부분의 WebView provider collection은 사용자가 선택한 provider 세션을 이용해 기기 안에서 수행됩니다. 지원되는 Google OAuth provider는 token exchange, token refresh, provider quota collection을 위해 Firebase Functions를 사용할 수 있습니다.

## 사용자가 기능을 시작하는 방법

- 사용자가 AI Quota를 엽니다.
- 사용자가 알림 권한을 허용합니다.
- 사용자가 하나 이상의 provider를 연결합니다.
- 사용자가 앱에서 60초 라이브 위젯 모니터링을 켭니다.
- 사용자는 위젯 refresh 버튼을 눌러 즉시 provider refresh를 요청할 수도 있습니다.

Passive widget redraw는 foreground service를 시작하지 않습니다. Passive widget update는 신뢰된 로컬 cache만 다시 그립니다.

## 사용자에게 보이는 고지 및 제어

- 서비스 실행 중에는 상태 표시줄에 고정 알림이 표시됩니다.
- 앱은 AI Quota가 지속적으로 사용량 데이터를 수집하려면 상태 표시줄 알림이 필요하다고 설명합니다.
- 알림에는 중지 action이 있습니다.
- 사용자가 라이브 모니터링을 끄면 foreground service가 중지됩니다.
- 알림 권한이 거부되었거나 라이브 모니터링이 꺼져 있으면 위젯은 마지막으로 신뢰된 로컬 cache를 표시할 수 있지만, 자동 provider 사용량 수집은 지연되거나 동작하지 않을 수 있습니다.

## 지연 또는 중단 시 사용자 영향

Android가 foreground service 시작을 지연하거나, 서비스를 중단하거나, 시작을 막는 경우 AI Quota는 앱, 위젯, 알림 화면에서 마지막으로 신뢰된 로컬 cache를 계속 표시할 수 있습니다. 다만 provider 사용량은 사용자가 앱을 열거나, 라이브 모니터링을 다시 켜거나, 위젯 refresh를 실행하기 전까지 오래된 값이 될 수 있습니다.

## dataSync를 사용하는 이유

이 서비스는 network를 통해 provider 사용량 데이터를 가져오고 사용자에게 보이는 앱 화면, 위젯, 알림을 위한 로컬 표시 cache를 갱신합니다. 이는 Android의 `dataSync` foreground service type 중 data fetch 및 device와 cloud/provider service 사이의 데이터 전송 목적에 해당합니다.

## passive widget update가 서비스를 시작하지 않는 이유

Passive `APPWIDGET_UPDATE`는 cached data만 다시 그립니다. Android가 background foreground-service start를 거부하고 process를 종료할 수 있으므로 passive widget update에서는 `startForegroundService()`를 호출하지 않습니다.

## Play Console 영상

Play Console의 foreground service 선언에는 영상 링크가 필요합니다. 영상에는 다음 흐름이 보여야 합니다.

1. AI Quota 열기
2. 알림 권한 허용
3. provider가 연결되어 있음을 표시하거나 하나의 provider 연결
4. 60초 라이브 위젯 모니터링 켜기
5. 상태 표시줄 고정 알림 표시
6. 앱, 위젯 또는 알림에서 provider 사용량이 갱신되는 모습 표시
7. 알림의 중지 action을 눌러 라이브 모니터링이 중지되는 모습 표시

영상 링크는 unlisted YouTube, viewer 접근이 가능한 Google Drive 링크, 또는 reviewer가 접근 가능한 공개 링크를 사용합니다. 영상에는 raw token, OAuth code, cookie, 계정 이메일, 민감한 provider data가 보이지 않도록 합니다.

## Data safety 메모

- Provider WebView cookie, provider API key, auth header, provider credential file, full provider HTML, raw provider response log는 개발자 서버로 의도적으로 전송하지 않습니다.
- Gemini와 Antigravity OAuth flow는 로그인 완료와 사용자가 요청한 quota monitoring connection 유지를 위해 OAuth callback data 또는 refresh-token request를 Firebase Functions로 전송할 수 있습니다.
- Antigravity는 session continuity를 위해 암호화된 provider refresh token을 Firebase에 저장할 수 있습니다.
- 로그에는 raw token, cookie, OAuth code, account identifier, email, username, full provider HTML을 남기지 않습니다.
- 연결 해제는 local provider credential과 provider-scoped WebView session data를 삭제하고, 지원되는 경우 provider-scoped Firebase record도 삭제합니다.

## Play Console 입력 초안

기능 설명:

AI Quota는 사용자가 60초 라이브 모니터링을 켠 경우 `dataSync` foreground service를 사용합니다. 이 서비스는 연결된 AI provider의 사용량 데이터를 가져오고 앱, 홈 화면 위젯, 상태 표시줄 고정 알림에 사용되는 로컬 cache를 갱신합니다.

지연 또는 중단 시 사용자 영향:

서비스가 지연되거나 중단되면 AI Quota는 마지막으로 신뢰된 로컬 cache를 계속 표시하지만, provider 사용량은 사용자가 앱을 열거나 라이브 모니터링을 다시 켜거나 위젯 refresh를 실행하기 전까지 오래된 값이 될 수 있습니다.

사용자 trigger:

사용자가 알림 권한을 허용하고 provider를 연결한 뒤 AI Quota에서 60초 라이브 위젯 모니터링을 켭니다. 사용자는 상태 표시줄 고정 알림의 중지 action 또는 앱 설정에서 라이브 모니터링을 끌 수 있습니다.

## 로컬 검증 메모

- Manifest에 `FOREGROUND_SERVICE` 및 `FOREGROUND_SERVICE_DATA_SYNC` permission이 선언되어 있습니다.
- `ProviderBackgroundRefreshService`만 `android:foregroundServiceType="dataSync"`를 선언합니다.
- `ProviderBackgroundRefreshService`는 `startForeground(..., FOREGROUND_SERVICE_TYPE_DATA_SYNC)`를 호출합니다.
- `ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS`는 60,000 ms입니다.

## 확인한 정책 문서

- Google Play foreground service requirements: https://support.google.com/googleplay/android-developer/answer/13392821
- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android background foreground-service start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

## 하지 않는 주장

- passive widget update가 background collection을 수행한다고 주장하지 않습니다.
- notification permission 없이 collection이 보장된다고 주장하지 않습니다.
- 모든 provider collection이 local-only라고 주장하지 않습니다.
- provider login 또는 interactive auth requirement를 우회한다고 주장하지 않습니다.
