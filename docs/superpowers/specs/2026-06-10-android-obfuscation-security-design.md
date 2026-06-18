# Android Stable Obfuscation Security Design Spec

Date: 2026-06-10

## Objective

Reduce release APK/AAB class-name exposure with the lowest practical runtime risk.

This phase keeps R8 minification enabled, keeps `-dontshrink`, keeps `-dontoptimize`, and narrows release keep rules so Android entry points remain loadable while internal implementation classes and members can still be obfuscated.

Primary plan:

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/plans/2026-06-10-android-obfuscation-security-design.md`

## In Scope

1. Update `android/app/proguard-rules.pro`.
2. Preserve manifest/runtime entry points required by Android, WorkManager, Firebase, OAuth, AppAuth, and Glance widgets.
3. Preserve JavaScript bridge method names used by `AIQuotaCollectorBridge`.
4. Add static guard tests that prove the release obfuscation posture stays conservative.
5. Add or strengthen diagnostics tests so tokens, cookies, authorization headers, OAuth codes, account emails, and raw provider secrets are not emitted.
6. Generate a release AAB and inspect `mapping.txt` plus the packaged manifest.

## Out Of Scope

1. Do not enable shrink.
2. Do not enable optimize.
3. Do not enable resource shrinking.
4. Do not minify, encode, rename, move, or otherwise transform `gemini_collector.js` or `antigravity_collector.js`.
5. Do not change provider collector behavior or OAuth behavior except for tests that prove existing behavior is still guarded.
6. Do not claim Play Console, iOS, or Mac validation from this work.

## Current State

Release builds use `isMinifyEnabled = true` and `isShrinkResources = false`.

`android/app/proguard-rules.pro` currently includes:

- `-dontshrink`
- `-dontoptimize`
- broad keep rules for Activity, Service, BroadcastReceiver, AppWidgetProvider, and GlanceAppWidgetReceiver subclasses
- a WorkManager constructor keep for `ForegroundRefreshHealthWorker`
- a `@android.webkit.JavascriptInterface <methods>;` keepclassmembers rule

The broad entrypoint rules are stable but expose more app class names than needed. This phase replaces those broad rules with explicit runtime entrypoint rules.

## Required Runtime Keep Surface

Release manifest entry points:

- `com.aiquota.mobile.AIQuotaApplication`
- `com.aiquota.mobile.MainActivity`
- `com.aiquota.mobile.providers.WebLoginActivity`
- `com.aiquota.mobile.providers.GeminiCliLoopbackOAuthActivity`
- `com.aiquota.mobile.providers.AntigravityLoopbackOAuthActivity`
- `com.aiquota.mobile.providers.GoogleAuthorizationActivity`
- `net.openid.appauth.RedirectUriReceiverActivity`
- `com.aiquota.mobile.widget.ProviderWidgetConfigureActivity`
- `com.aiquota.mobile.widget.DashboardWidgetConfigureActivity`
- `com.aiquota.mobile.providers.ProviderUsageCollectionService`
- `com.aiquota.mobile.providers.ProviderBackgroundRefreshService`
- `com.aiquota.mobile.widget.WidgetManualRefreshReceiver`
- `com.aiquota.mobile.providers.ProviderRefreshReceiver`
- `com.aiquota.mobile.widget.AIQuotaUnifiedGlanceWidgetReceiver`
- `com.aiquota.mobile.widget.AIQuotaCircularWidgetProvider`
- `com.aiquota.mobile.widget.ProviderUsageWidgetProvider`

Debug-only manifest entry point:

- `com.aiquota.mobile.debug.LiveRefreshIssueDebugReceiver`

WorkManager constructor:

- `com.aiquota.mobile.sync.ForegroundRefreshHealthWorker(android.content.Context, androidx.work.WorkerParameters)`

JavaScript bridge member names:

- `postUsagePayload`
- `postCollectorError`
- `fetchCopilotJson`
- `fetchCopilotJsonWithAuthorization`
- `fetchCursorJson`

The bridge object name `AIQuotaCollectorBridge` and the bridge method names are string-called from injected JavaScript, so method-name obfuscation is not allowed for `@JavascriptInterface` members.

## Verification

Test-first sequence:

1. Add static tests in `test/mobile-source.test.js` that fail while broad app-wide R8 keep rules remain.
2. Narrow `android/app/proguard-rules.pro`.
3. Re-run the same tests and capture GREEN evidence.
4. Run sensitive diagnostic tests and static secret scans.
5. Build release AAB with the local Gradle distribution when Gradle is not on PATH:
   - `.tmp/tools/gradle-8.10.2/bin/gradle.bat -p android :app:bundleRelease`
6. Inspect:
   - `android/app/build/outputs/bundle/release/app-release.aab`
   - `android/app/build/outputs/mapping/release/mapping.txt`
   - packaged manifest dump, using `bundletool` if available or a documented fallback.

## Rollback Criteria

Rollback the R8 rule change if any of these happen:

- Release build fails because an Android/runtime entrypoint is removed or renamed.
- `mapping.txt` shows any required `@JavascriptInterface` bridge method is renamed.
- Provider collector tests regress.
- Firebase gateway tests regress.
- Dashboard or widget tests regress.
- AAB manifest inspection cannot prove runtime entry points remain loadable.

Rollback means restore the previous broad keep rules while keeping the newly added tests and evidence, then isolate the missing keep rule before trying again.
