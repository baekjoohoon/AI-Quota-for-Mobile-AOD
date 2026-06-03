# AI Quota for Mobile

[English](#english) | [?쒓뎅??(#korean)

---

## English

AI Quota for Mobile is an Android app for checking AI provider usage limits from one place. It supports a local-first dashboard, home screen widgets, and an optional pinned foreground refresh notification.

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

AI Quota for Mobile? ?щ윭 AI provider???⑥? ?ъ슜?됱쓣 ?쒓납?먯꽌 ?뺤씤?섍린 ?꾪븳 Android ?깆엯?덈떎. ????쒕낫?? ???붾㈃ ?꾩젽, ?좏깮 媛?ν븳 怨좎젙 ?뚮┝ 湲곕컲 foreground refresh瑜??쒓났?⑸땲??

### ?꾩옱 ?곹깭

Android ?깆? Google Play ?대? ?뚯뒪???깅줉??以鍮?以묒엯?덈떎.

?꾩옱 ?낅줈???곗텧臾?

```text
android/app/build/outputs/bundle/release/app-release.aab
```

### 二쇱슂 湲곕뒫

- 濡쒖뺄 ?곗꽑 provider ?ъ슜????쒕낫??
- 鍮좊Ⅸ quota ?뺤씤???꾪븳 ???붾㈃ ?꾩젽.
- foreground refresh瑜??꾪븳 ?좏깮??怨좎젙 ?뚮┝.
- ?섎룞 refresh? foreground service refresh.
- provider ?④? 諛??쒖꽌 蹂寃??ㅼ젙.
- ?쒓뎅?댁? ?곸뼱 UI 臾몄옄??
- Google Play release signing 諛??ㅽ넗???깅줉 asset 以鍮?

### 吏??Provider

| Provider | ?곹깭 |
| --- | --- |
| Claude | 吏??|
| Codex | 吏??|
| Gemini | 吏??|
| GitHub Copilot | 吏??|
| Antigravity | 吏??|
| Cursor | 吏??|

### Android ?⑦궎吏

Google Play ?⑦궎吏 ?대쫫:

```text
com.aiquota.mobile
```

Kotlin namespace???대? 援ы쁽 ?몃??ы빆?쇰줈 `com.aiquota.mobile`???좎??⑸땲?? Google Play ?⑦궎吏 ?대쫫怨쇰뒗 蹂꾧컻?낅땲??

### 媛쒖씤?뺣낫 諛??곗씠????젣

- [媛쒖씤?뺣낫 泥섎━諛⑹묠](docs/privacy-policy.html)
- [怨꾩젙 諛??곗씠????젣 ?덈궡](docs/account-deletion.html)

### ?뚯뒪?먯꽌 鍮뚮뱶

?꾩닔 議곌굔:

- Windows 媛쒕컻 ?섍꼍.
- Android Studio 諛?Android SDK.
- JDK 17. Android Studio JBR ?ъ슜 媛??
- Android ?⑦궎吏 `com.aiquota.mobile`濡??ㅼ젙??Firebase ?꾨줈?앺듃.
- `android/app/google-services.json`.
- 湲곗〈 Gradle wrapper 諛고룷蹂??먮뒗 Gradle 8.10.2.

?뚯뒪??

```powershell
npm.cmd test
```

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:testDebugUnitTest
```

Debug APK 鍮뚮뱶:

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:assembleDebug
```

Google Play AAB 鍮뚮뱶:

```powershell
& '.\.tmp\tools\gradle-8.10.2\bin\gradle.bat' -p android :app:bundleRelease
```

異쒕젰:

```text
android/app/build/outputs/bundle/release/app-release.aab
```

### ?쇱씠?좎뒪

MIT
