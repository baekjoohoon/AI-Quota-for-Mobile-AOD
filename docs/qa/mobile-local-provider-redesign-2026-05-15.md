# Mobile Local Provider Redesign QA - 2026-05-15

## Builds

- Node tests: PASS (`npm test`)
- Node lint: PASS (`npm run lint`)
- Debug unit tests and debug APK: PASS (`gradle-8.10.2 -p android :app:testDebugUnitTest :app:assembleDebug`)
- Release bundle: PASS (`gradle-8.10.2 -p android :app:bundleRelease`)
- Android lint: BLOCKED by existing Glance `RestrictedApi` errors in `AIUsageGlanceWidget.kt` and `ProviderUsageGlanceWidget.kt`
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Release AAB: `android/app/build/outputs/bundle/release/app-release.aab`

## Phone Emulator

- Device: `emulator-5554`, AVD `Pixel_10`, 1080x2424
- Device language: PASS (`persist.sys.locale=ko-KR`)
- Debug APK install: PASS
- Unified dashboard first launch: PASS (`Dashboard` title, no old "이 기기에서 AI 사용 한도를 확인합니다." text)
- Korean-first UI: PASS (`홈`, `설정`, `연결 끊김`, `숨기기`, `사용량 분석` shown in UI hierarchy)
- Dashboard card density: PASS (3 full provider cards visible; fourth card starts below)
- Dashboard card icon treatment: PASS (provider icons replace the old folder icon in the card body and path row)
- Bottom navigation order stability: PASS (dashboard order changed to `Codex, Gemini, Cursor...`; nav stayed `Claude, Codex, Gemini, GitHub Copilot, Cursor`)
- Dashboard shows Claude, Codex, Gemini, GitHub Copilot, Cursor: PASS
- Disconnected provider cards show connect action: PASS
- Provider navigation/detail screen: PASS (old Windows Explorer window styling, provider icon, status, usage, and usage analysis)
- Provider reorder persistence: PASS
- Provider hide persistence: PASS
- Hidden provider remains reachable from navigation: PASS
- Windows setup does not block local dashboard: PASS
- Layout spacing: PASS (content starts below the settings top bar; bottom navigation has 30dp extra bottom clearance and no clipping)
- Evidence: `artifacts/mobile-dashboard-explorer.png`, `artifacts/mobile-provider-detail-explorer.png`, `/sdcard/mobile-dashboard-explorer.png`, `/sdcard/mobile-provider-detail-explorer.png`

## Pixel 5 Provider Login Timing - 2026-05-19

- Device: `emulator-5554`, AVD `Pixel_5`, 1080x2340
- Debug APK install: PASS
- Unified dashboard first launch: PASS (`Dashboard`, `Claude`, `Codex`, `Gemini`, `Copilot`, `Cursor`, and disconnected connect actions visible)
- Measurement method: app data cleared per provider, app launched, provider tab opened, `연결` tapped, then time measured until `WebLoginActivity` showed an `android.webkit.WebView` with provider login-page markers. Login input was not performed.
- Claude: PASS, `5351 ms` to login page marker (`Sign in - Claude`, Google/email login controls)
- Codex: PASS, `7753 ms` to login page marker (OpenAI/ChatGPT auth markers)
- Gemini: PASS, `5195 ms` to login page marker (Google/Gemini auth markers)
- Copilot: PASS, `2616 ms` to login page marker (GitHub/Copilot auth markers)
- Cursor: PASS, `10495 ms` to login page marker (Cursor/WorkOS/email auth markers)
- Manual-login boundary: PASS. Test stopped at provider login page; credential entry and authenticated usage collection remain manual-check scope.

## Tablet Emulator

- Device: `emulator-5556`, AVD `Pixel_Tablet`, 2560x1600
- Device language: PASS (`persist.sys.locale=ko-KR`)
- Debug APK install: PASS
- Unified dashboard first launch: PASS (`Dashboard` title, no old description text)
- Korean-first UI: PASS (`홈`, `설정`, `연결 끊김`, `숨기기` shown in UI hierarchy)
- Dashboard card density: PASS (2 full provider cards visible; third card starts below)
- Dashboard card icon treatment: PASS (provider icons replace the old folder icon)
- Dashboard shows Claude, Codex, Gemini, GitHub Copilot, Cursor: PASS
- Disconnected provider cards show connect action: PASS
- Provider navigation/detail screen: PASS
- Provider reorder persistence: PASS
- Provider hide persistence: PASS
- Hidden provider remains reachable from navigation: PASS
- Windows setup does not block local dashboard: PASS
- Layout spacing: PASS (tablet bottom navigation uses a capped centered width, and all six nav entries fit without over-stretching)
- Evidence: `artifacts/tablet-dashboard-explorer.png`, `/sdcard/tablet-dashboard-explorer.png`

## Widgets

- Unified widget receiver registered on phone/tablet: PASS (`dumpsys appwidget`)
- Provider widget receiver registered on phone/tablet: PASS (`dumpsys appwidget`)
- Unified widget 2x2 through 3x4 layout specs: PASS (`WidgetLayoutSpecTest`)
- Provider widget 2x1 through 3x3 layout specs: PASS (`WidgetLayoutSpecTest`)
- Unified widget payload follows visible dashboard order: PASS (`UnifiedWidgetPayloadTest`)
- Provider widget payload targets one provider: PASS (`ProviderWidgetPayloadTest`)
- Provider widget saved selection can target hidden providers: PASS (`ProviderWidgetSelectionTest`)
- Widget tap routing compiles against Glance 1.1.1 action parameters: PASS (`:app:assembleDebug`)
- Local display cache writes trigger unified and provider Glance refreshes: PASS (`AIUsageAppShell` + `:app:assembleDebug`)
- Launcher add/resize/tap manual UI automation: BLOCKED. The emulator shell returned `No shell command implementation.` for `cmd appwidget help`, so widget placement and resize could not be automated from ADB in this environment.

## Privacy Cache Check

- Widget cache contains provider ids, display names, display text, ratios/status, and timestamps only: PASS
- Hidden provider was removed from the display-only cache after hide persistence test: PASS
- Widget cache does not contain cookies, tokens, raw HTML, raw logs, credential paths, URLs, pages, source, or provider raw responses: PASS

## Notes

- Gradle was run through the local distribution at `.tmp/gradle-8.10.2/bin/gradle.bat` because this checkout does not include `gradlew`.
- Java 17 was provided by `C:\Program Files\Android\Android Studio\jbr`.
