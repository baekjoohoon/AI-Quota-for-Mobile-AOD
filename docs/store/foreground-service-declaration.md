# AI Quota foreground service declaration

작성일: 2026-06-07

## Service type

`specialUse`

## Special use subtype

Manifest property:

```xml
<property
  android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
  android:value="usage_monitor" />
```

Subtype value: `usage_monitor`

## 앱 기능 설명

AI Quota는 사용자가 연결한 AI provider의 quota 사용량을 앱, 홈 화면 위젯, 선택형 상태 표시줄 고정 알림에서 확인할 수 있게 합니다. 사용자가 알림 권한을 허용하고 앱에서 라이브 모니터링을 켜면, 앱은 foreground service를 실행해 연결된 provider 사용량을 가져오고 앱, 위젯, 알림 표시용 로컬 cache를 약 60초 주기로 갱신하려고 시도합니다.

이 동작은 사용자가 켠 동안만 보이는 고정 알림과 함께 실행됩니다. Android 시스템 상태, 배터리 정책, 네트워크, provider 세션 상태에 따라 시작이 지연되거나 중단될 수 있으므로 24시간/365일 실행이나 정확한 60초 갱신을 보장하지 않습니다.

대부분의 WebView provider collection은 사용자가 선택한 provider 세션을 이용해 기기 안에서 수행됩니다. 지원되는 Google OAuth provider는 token exchange, token refresh, provider quota collection을 위해 Firebase Functions를 사용할 수 있습니다.

## 사용자가 기능을 시작하는 방법

- 사용자가 AI Quota를 엽니다.
- 사용자가 Android 알림 권한을 허용합니다.
- 사용자가 하나 이상의 provider를 연결합니다.
- 사용자가 앱에서 라이브 모니터링을 켭니다.
- 사용자는 홈 화면 위젯 refresh 버튼을 눌러 즉시 provider refresh를 요청할 수도 있습니다.

Passive widget redraw는 foreground service를 시작하지 않습니다. Passive widget update는 신뢰된 로컬 cache만 다시 그립니다.

## 사용자에게 보이는 고지 및 제어

- 서비스 실행 중에는 상태 표시줄에 고정 알림이 표시됩니다.
- 앱은 AI Quota가 지속적으로 사용량 데이터를 갱신하려면 알림 권한과 라이브 모니터링이 필요하다고 설명합니다.
- 알림은 ongoing, silent, low-priority 고정 알림입니다.
- 알림 자체에는 별도 중지 버튼을 제공하지 않습니다.
- 사용자는 앱의 라이브 모니터링 설정에서 foreground service를 끌 수 있습니다.
- 알림 권한이 거부되었거나 라이브 모니터링이 꺼져 있으면 위젯은 마지막으로 신뢰된 로컬 cache를 표시할 수 있지만, 자동 provider 사용량 수집은 지연되거나 동작하지 않을 수 있습니다.

## 지연 또는 중단 시 사용자 영향

Android가 foreground service 시작을 지연하거나, 서비스를 중단하거나, 시작을 막는 경우 AI Quota는 앱, 위젯, 알림 화면에서 마지막으로 신뢰된 로컬 cache를 계속 표시할 수 있습니다. 다만 provider 사용량은 사용자가 앱을 열거나, 라이브 모니터링을 다시 켜거나, 위젯 refresh를 실행하기 전까지 오래된 값이 될 수 있습니다.

서비스가 재시작되더라도 provider 세션이 만료되었거나 네트워크가 unavailable 상태이면 새 사용량 수집은 실패할 수 있습니다. 이 경우 앱은 기존 cache와 오류 상태를 표시하고 interactive login requirement를 우회하지 않습니다.

## specialUse를 사용하는 이유

AI Quota의 라이브 모니터링은 사용자가 직접 켜고, 실행 중 고정 알림으로 인지할 수 있으며, 연결된 provider 사용량을 앱과 위젯에 계속 표시하기 위한 기능입니다. Android의 다른 foreground service type이 이 quota monitoring use case를 정확히 설명하지 않으므로 `specialUse`와 subtype `usage_monitor`로 선언합니다.

이 선언은 Play review 대상입니다. 승인 또는 플랫폼의 장기 실행 허용을 보장하지 않습니다.

## passive widget update가 서비스를 시작하지 않는 이유

Passive `APPWIDGET_UPDATE`는 cached data만 다시 그립니다. Android가 background foreground-service start를 거부하고 process를 종료할 수 있으므로 passive widget update에서는 `startForegroundService()`를 호출하지 않습니다.

## Play Console 영상 스크립트

Play Console의 foreground service 선언에는 reviewer가 접근 가능한 영상 링크가 필요합니다. 영상에는 다음 흐름을 보여줍니다.

1. AI Quota 열기
2. Android 알림 권한 허용
3. provider가 연결된 상태 표시 또는 하나의 provider 연결 흐름 진입
4. 앱 설정에서 라이브 모니터링 켜기
5. 상태 표시줄 고정 알림 표시
6. 앱 또는 위젯에서 provider 사용량 cache가 갱신되는 모습 표시
7. 앱 설정에서 라이브 모니터링 끄기
8. foreground service와 고정 알림이 중지되는 모습 표시

영상 링크는 unlisted YouTube, viewer 접근이 가능한 Google Drive 링크, 또는 reviewer가 접근 가능한 공개 링크를 사용합니다. 영상에는 raw token, OAuth code, cookie, 계정 이메일, 민감한 provider data가 보이지 않도록 합니다.

## Data safety 메모

- Provider WebView cookie, provider API key, auth header, provider credential file, full provider HTML, raw provider response log는 개발자 서버로 의도적으로 전송하지 않습니다.
- Gemini와 Antigravity OAuth flow는 로그인 완료와 사용자가 요청한 quota monitoring connection 유지를 위해 OAuth callback data 또는 refresh-token request를 Firebase Functions로 전송할 수 있습니다.
- Antigravity는 session continuity를 위해 암호화된 provider refresh token을 Firebase에 저장할 수 있습니다.
- 로그에는 raw token, cookie, OAuth code, account identifier, email, username, full provider HTML을 남기지 않습니다.
- 연결 해제는 local provider credential과 provider-scoped WebView session data를 삭제하고, 지원되는 경우 provider-scoped Firebase record도 삭제합니다.

## Play Console 입력 초안

기능 설명:

AI Quota는 사용자가 라이브 모니터링을 켠 경우 `specialUse` foreground service subtype `usage_monitor`를 사용합니다. 이 서비스는 연결된 AI provider의 사용량 데이터를 가져오고 앱, 홈 화면 위젯, 상태 표시줄 고정 알림에 사용되는 로컬 cache를 갱신하려고 시도합니다. 서비스 실행 중에는 사용자가 볼 수 있는 고정 알림이 표시됩니다.

지연 또는 중단 시 사용자 영향:

서비스가 지연되거나 중단되면 AI Quota는 마지막으로 신뢰된 로컬 cache를 계속 표시하지만, provider 사용량은 사용자가 앱을 열거나 라이브 모니터링을 다시 켜거나 위젯 refresh를 실행하기 전까지 오래된 값이 될 수 있습니다. 이 기능은 24시간/365일 실행이나 정확한 60초 refresh를 보장하지 않습니다.

사용자 trigger:

사용자가 알림 권한을 허용하고 provider를 연결한 뒤 AI Quota에서 라이브 모니터링을 켭니다. 사용자는 앱 설정에서 라이브 모니터링을 끌 수 있습니다. 위젯 refresh 버튼은 사용자가 직접 누른 경우 즉시 refresh를 요청할 수 있습니다.

## 로컬 검증 메모

- Manifest에 `FOREGROUND_SERVICE` 및 `FOREGROUND_SERVICE_SPECIAL_USE` permission이 선언되어 있습니다.
- `ProviderBackgroundRefreshService`만 `android:foregroundServiceType="specialUse"`를 선언합니다.
- `ProviderBackgroundRefreshService`는 `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value `usage_monitor`를 선언합니다.
- `ProviderBackgroundRefreshService`는 `startForeground(..., FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`를 호출합니다.
- `ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS`는 60,000 ms입니다.
- Passive widget update는 local cache만 다시 그립니다.

## Play rejection fallback

Play review에서 `specialUse usage_monitor`가 승인되지 않으면 이 migration의 manifest/runtime/test/doc 변경을 되돌리고, 별도 계획에서 Play가 승인 가능한 foreground service type 또는 WorkManager 기반 bounded refresh 설계로 재제출합니다. 이 fallback은 이번 작업 범위에 포함하지 않습니다.

## 확인한 정책 문서

- Google Play foreground service requirements: https://support.google.com/googleplay/android-developer/answer/13392821
- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- Android foreground service type requirements: https://developer.android.com/about/versions/14/changes/fgs-types-required

## 하지 않는 주장

- passive widget update가 background collection을 수행한다고 주장하지 않습니다.
- notification permission 없이 collection이 보장된다고 주장하지 않습니다.
- 모든 provider collection이 local-only라고 주장하지 않습니다.
- provider login 또는 interactive auth requirement를 우회한다고 주장하지 않습니다.
- 24시간/365일 실행 또는 정확한 60초 refresh를 보장한다고 주장하지 않습니다.
