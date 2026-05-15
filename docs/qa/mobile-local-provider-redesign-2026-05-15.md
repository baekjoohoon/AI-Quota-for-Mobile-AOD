# Mobile Local Provider Redesign QA - 2026-05-15

## Builds

- Node tests: PASS (`npm test`)
- Node lint: PASS (`npm run lint`)
- Debug unit tests and debug APK: PASS (`gradle-8.10.2 -p android :app:testDebugUnitTest :app:assembleDebug`)
- Release bundle: PASS (`gradle-8.10.2 -p android :app:bundleRelease`)
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Release AAB: `android/app/build/outputs/bundle/release/app-release.aab`

## Phone Emulator

- Device: `emulator-5554`, AVD `Pixel_10`, 1080x2424
- Debug APK install: PASS
- Unified dashboard first launch: PASS
- Dashboard shows Claude, Codex, Gemini, GitHub Copilot, Cursor: PASS
- Disconnected provider cards show connect action: PASS
- Provider navigation/detail screen: PASS
- Provider reorder persistence: PASS
- Provider hide persistence: PASS
- Hidden provider remains reachable from navigation: PASS
- Windows setup does not block local dashboard: PASS
- Evidence: `screenshot/qa-phone-final.png`, `screenshot/qa-phone-window.xml`

## Tablet Emulator

- Device: `emulator-5556`, AVD `Pixel_Tablet`, 2560x1600
- Debug APK install: PASS
- Unified dashboard first launch: PASS
- Dashboard shows Claude, Codex, Gemini, GitHub Copilot, Cursor: PASS
- Disconnected provider cards show connect action: PASS
- Provider navigation/detail screen: PASS
- Provider reorder persistence: PASS
- Provider hide persistence: PASS
- Hidden provider remains reachable from navigation: PASS
- Windows setup does not block local dashboard: PASS
- Evidence: `screenshot/qa-tablet-final.png`, `screenshot/qa-tablet-window.xml`

## Widgets

- Unified widget receiver registered on phone/tablet: PASS (`dumpsys appwidget`)
- Provider widget receiver registered on phone/tablet: PASS (`dumpsys appwidget`)
- Unified widget 2x2 through 3x4 layout specs: PASS (`WidgetLayoutSpecTest`)
- Provider widget 2x1 through 3x3 layout specs: PASS (`WidgetLayoutSpecTest`)
- Unified widget payload follows visible dashboard order: PASS (`UnifiedWidgetPayloadTest`)
- Provider widget payload targets one provider: PASS (`ProviderWidgetPayloadTest`)
- Provider widget saved selection ignores hidden providers: PASS (`ProviderWidgetSelectionTest`)
- Widget tap routing compiles against Glance 1.1.1 action parameters: PASS (`:app:assembleDebug`)
- Local display cache writes trigger unified and provider Glance refreshes: PASS (`AIUsageAppShell` + `:app:assembleDebug`)
- Legacy Windows sync writes preserve existing local display cache and refresh notifications from cache: PASS (`WidgetSnapshotCache`, `SnapshotRepository`)
- Launcher add/resize/tap manual UI automation: BLOCKED. The emulator shell returned `No shell command implementation.` for `cmd appwidget help`, so widget placement and resize could not be automated from ADB in this environment.

## Privacy Cache Check

- Widget cache contains provider ids, display names, display text, ratios/status, and timestamps only: PASS
- Hidden provider was removed from the display-only cache after hide persistence test: PASS
- Widget cache does not contain cookies, tokens, raw HTML, raw logs, credential paths, URLs, pages, source, or provider raw responses: PASS

## Notes

- Gradle was run through the cached wrapper distribution at `C:\Users\datell1357\.gradle\wrapper\dists\gradle-8.10.2-bin\...\gradle-8.10.2\bin\gradle.bat`.
- Java 17 was provided by `C:\Program Files\Android\Android Studio\jbr`.
