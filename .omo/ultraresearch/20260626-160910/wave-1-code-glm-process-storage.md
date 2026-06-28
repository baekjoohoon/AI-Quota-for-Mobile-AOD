# Wave 1 - GLM Process And Storage

## Findings
- `GlmWebLoginActivity` runs in `android:process=":glm_webview"`.
- `GlmIsolatedWebSessionService` also runs in `android:process=":glm_webview"`.
- Both login and isolated collection save the web cookie through `GlmUsageRepository.saveWebSessionCookieHeader()`, which uses `SecureStringStore`, which wraps `SharedPreferences`.
- Android's WebView documentation says `setDataDirectorySuffix()` makes WebView data process-specific and cookies are not shared across process boundaries unless explicitly copied.
- Android's `SharedPreferences` documentation says the class does not support multi-process use.

## Sources
- `android/app/src/main/AndroidManifest.xml:19-22`
- `android/app/src/main/AndroidManifest.xml:87-90`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:770-784`
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt:140-163`
- `android/app/src/main/java/com/aiquota/mobile/providers/SecureStringStore.kt:13-31`
- Android WebView `setDataDirectorySuffix`: https://developer.android.com/reference/android/webkit/WebView
- Android SharedPreferences: https://developer.android.com/reference/android/content/SharedPreferences

## EXPAND
- LEAD: copy cookie through a process-safe result boundary instead of SharedPreferences -- WHY: direct fetch must be enabled in the main process -- ANGLE: return cookie header through ResultReceiver and save in main process.
