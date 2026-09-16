# AI Quota for Mobile

[English](#english) | [한국어](#korean)

---

## English

AI Quota for Mobile is an Android app for checking AI provider usage limits from one place. It supports a local-first dashboard, home screen widgets, and an optional pinned foreground refresh notification.

This fork adds a personal Galaxy S26 / One UI 8.5 experiment for showing the Codex quota as a compact widget that can be selected from Samsung Good Lock / LockStar where third-party AppWidgets are supported. It is based on the upstream `datell1357/AI-Quota-for-Mobile` project and does not guarantee AOD or lock-screen support on other Galaxy models, One UI versions, launchers, or LockStar releases.

The fork remains MIT-licensed. You may fork it again and adapt it for your own device while preserving the original copyright and license notices.

### Current Status

The Android app is being prepared for Google Play internal testing.

Current upload artifact:

```text
android/app/build/outputs/bundle/release/app-release.aab
```

### Features

- Local-first provider usage dashboard.
- Home screen widgets for quick quota checks.
- Optional pinned notification for foreground refresh.
- Manual and foreground-service refresh paths.
- Provider hide/reorder settings.
- Korean and English UI strings.
- Google Play release signing and store listing assets prepared.

### Supported Providers

| Provider | Status |
| --- | --- |
| Claude | Supported |
| Codex | Supported |
| Gemini | Supported |
| GitHub Copilot | Supported |
| Antigravity | Supported |
| Cursor | Supported |

### Android Package

Google Play package name:

```text
com.aiquota.mobile
```

The Kotlin namespace is still `com.aiquota.mobile` internally. That is an implementation detail and does not change the Google Play package name.

### Privacy

- [Privacy Policy](docs/privacy-policy.html)
- [Account and Data Deletion](docs/account-deletion.html)

### Build From Source

Requirements:

- Windows development machine.
- Android Studio with Android SDK.
- JDK 17. Android Studio JBR works.
- Firebase project configured for Android package `com.aiquota.mobile`.
- `android/app/google-services.json`.
- Existing Gradle wrapper distribution or Gradle 8.10.2.

Run tests:

```powershell
npm.cmd test
```

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest
```

Build debug APK:

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:assembleDebug
```

Debug APK output:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Install the debug APK on a connected device:

```powershell
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Galaxy S26 / One UI 8.5 check:

1. Open AI Quota, connect Codex, and refresh until the 5-hour and weekly quota data is visible in the app.
2. Confirm the `AI Quota Codex AOD` widget appears in the Android widget picker.
3. Open Samsung Good Lock / LockStar and edit the lock screen or AOD layout available on your installed version.
4. Add the `AI Quota Codex AOD` widget and confirm the `5H` and `W` percentages plus reset text are readable while the phone is on the standing charger.
5. Treat the result as device/version specific until it has been checked on the exact Galaxy S26 / One UI / LockStar combination you use.

Build Google Play AAB:

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:bundleRelease
```

Output:

```text
android/app/build/outputs/bundle/release/app-release.aab
```

### License

MIT

---

## Korean

AI Quota for Mobile은 여러 AI provider의 사용량을 한곳에서 확인하기 위한 Android 앱입니다. 로컬 우선 대시보드, 홈 화면 위젯, 선택 가능한 고정 알림 기반 foreground refresh를 제공합니다.

이 fork는 upstream `datell1357/AI-Quota-for-Mobile`을 기반으로 Galaxy S26 / One UI 8.5에서 Codex quota를 Samsung Good Lock / LockStar를 통해 잠금화면 또는 AOD에 배치해 보기 위한 개인용 실험을 추가합니다. 다른 Galaxy 모델, One UI 버전, 런처, LockStar 버전에서의 AOD/잠금화면 동작은 보장하지 않습니다.

라이선스는 기존과 동일한 MIT이며, 원 저작권과 라이선스 고지를 유지하는 범위에서 자유롭게 다시 fork하여 본인 기기에 맞게 수정할 수 있습니다.

### 현재 상태

Android 앱은 Google Play 내부 테스트 등록을 준비 중입니다.

현재 업로드 산출물:

```text
android/app/build/outputs/bundle/release/app-release.aab
```

### 주요 기능

- 로컬 우선 provider 사용량 대시보드.
- 빠른 quota 확인을 위한 홈 화면 위젯.
- foreground refresh를 위한 선택 가능한 고정 알림.
- 수동 refresh와 foreground service refresh.
- provider 숨김 및 순서 변경 설정.
- 한국어와 영어 UI 문자열.
- Google Play release signing 및 스토어 등록 asset 준비.

### 지원 Provider

| Provider | 상태 |
| --- | --- |
| Claude | 지원 |
| Codex | 지원 |
| Gemini | 지원 |
| GitHub Copilot | 지원 |
| Antigravity | 지원 |
| Cursor | 지원 |

### Android 패키지

Google Play 패키지 이름:

```text
com.aiquota.mobile
```

Kotlin namespace는 내부 구현 사항으로 `com.aiquota.mobile`을 유지합니다. Google Play 패키지 이름과는 별개입니다.

### 개인정보 및 데이터 삭제

- [개인정보처리방침](docs/privacy-policy.html)
- [계정 및 데이터 삭제 안내](docs/account-deletion.html)

### 소스에서 빌드

필수 조건:

- Windows 개발 환경.
- Android Studio 및 Android SDK.
- JDK 17. Android Studio JBR 사용 가능.
- Android 패키지 `com.aiquota.mobile`로 설정된 Firebase 프로젝트.
- `android/app/google-services.json`.
- 기존 Gradle wrapper 배포본 또는 Gradle 8.10.2.

테스트:

```powershell
npm.cmd test
```

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest
```

Debug APK 빌드:

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:assembleDebug
```

Debug APK 출력:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

연결된 기기에 Debug APK 설치:

```powershell
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Galaxy S26 / One UI 8.5 확인 절차:

1. AI Quota를 열어 Codex를 연결하고 앱에서 5시간/주간 quota 데이터가 보일 때까지 갱신합니다.
2. Android 위젯 선택 화면에 `AI Quota Codex AOD`가 표시되는지 확인합니다.
3. Samsung Good Lock / LockStar에서 현재 버전이 제공하는 잠금화면 또는 AOD 편집 화면을 엽니다.
4. `AI Quota Codex AOD` 위젯을 추가하고 스탠딩 충전 상태에서 `5H`, `W`, 각 reset 정보가 읽을 수 있게 표시되는지 확인합니다.
5. 실제 사용 중인 Galaxy S26 / One UI / LockStar 조합에서 직접 확인하기 전에는 범용 호환으로 보지 않습니다.

Google Play AAB 빌드:

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:bundleRelease
```

출력:

```text
android/app/build/outputs/bundle/release/app-release.aab
```

### 라이선스

MIT
