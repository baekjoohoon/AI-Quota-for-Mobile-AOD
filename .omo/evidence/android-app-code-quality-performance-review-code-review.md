# Android App Code Quality and Performance Review

Date: 2026-06-24

Scope: `D:\Vibe Project\AI Usage for Mobile`, current Android app worktree. Read-only review of Kotlin/Android hot paths, WebView lifecycle, service loops, persistence writes, JSON parsing, widget rendering, notification updates, coroutines, and tests.

User constraint: code review only. No app source files were modified. This artifact is the requested review record.

Inputs: no user-supplied diff, notepad path, or external evidence paths were provided. I inspected current source and current dirty worktree state directly. `git diff --check` passed, with only LF-to-CRLF warnings.

Skill perspective check: ran by reading `omo:remove-ai-slops` and `omo:programming` skill instructions before judging maintainability/test relevance. The diff/current tree violates both perspectives:
- `remove-ai-slops`: dead production code, implementation-mirroring source-text tests, deletion-only/removal assertions, repeated parsing/serialization in hot paths.
- `programming`: oversized modules far above the 250 pure-LOC ceiling, brittle prompt/source tests, needless production complexity that is no longer wired into the app.

## CRITICAL

None found.

## HIGH

### H1 - Browser storage cleanup reports completion before asynchronous stores are known to be cleared

References:
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt:164`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt:165`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt:166`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt:213`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt:223`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt:230`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt:238`

`clearProviderBrowserStorageWithWebView` calls `evaluateJavascript(browserStorageCleanupScript())` and immediately calls `finish()` in the Java callback. The script clears `localStorage` and `sessionStorage` synchronously, but IndexedDB, Cache Storage, and service-worker unregister work is put into `Promise` chains at lines 213-238. The Android callback is tied to script evaluation, not to an explicit app-side callback after those Promise tasks complete, and `finish()` destroys the WebView at lines 149-156. That can leave durable browser state behind while the caller believes disconnect cleanup completed.

Optimization/fix suggestion:
- Add a one-shot cleanup bridge or `WebMessagePort` callback invoked from `Promise.all(tasks).then(...)`, and only resume/destroy the WebView from that callback.
- Keep the existing timeout as the outer failure bound, but treat timeout as cleanup incomplete and log/return that state.
- Add an instrumentation or Robolectric-style WebView test that seeds IndexedDB/cache/localStorage for a cleanup URL and verifies removal after `clearProviderWebSessionAndWait`.

### H2 - A full hidden WebView collector remains in production code with no production call site

References:
- `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt:52`
- `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt:58`
- `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt:73`
- `android/app/src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt:76`
- `android/app/src/test/java/com/aiquota/mobile/providers/GoogleProviderWebViewFirstCollectionTest.kt:45`
- `android/app/src/test/java/com/aiquota/mobile/providers/ProviderRefreshSessionPolicyTest.kt:37`

`BackgroundProviderWebCollector` defines retained WebViews, JavaScript bridges, timeout logic, native fetch bridges, and collector injection, but production search found no call site outside its declaration. Tests explicitly assert that `AIQuotaAppShell` does not contain `BackgroundProviderWebCollector(`. This leaves a second hidden-collector implementation in production that cannot execute, increasing review surface and making WebView lifecycle/security/performance reasoning harder.

Optimization/fix suggestion:
- If `ProviderBackgroundRefreshService` is now the single collection path, delete `BackgroundProviderWebCollector.kt` and replace source-text tests with behavior tests around the service/refresh plan.
- If the Compose collector is still required, wire it through one clear owner and remove the duplicate service path for the same responsibility.
- Add a simple static ownership test only after the dead path is removed: one hidden collector entry point per provider refresh mode.

### H3 - Many tests assert source substrings instead of behavior, creating false confidence for lifecycle and service changes

References:
- `android/app/src/test/java/com/aiquota/mobile/providers/GlmWebSessionClearPolicyTest.kt:94`
- `android/app/src/test/java/com/aiquota/mobile/providers/GlmWebSessionClearPolicyTest.kt:120`
- `android/app/src/test/java/com/aiquota/mobile/providers/GlmWebSessionClearPolicyTest.kt:131`
- `android/app/src/test/java/com/aiquota/mobile/providers/GlmWebSessionClearPolicyTest.kt:157`
- `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:10`
- `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:79`
- `android/app/src/test/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshServicePolicyTest.kt:301`
- `android/app/src/test/java/com/aiquota/mobile/notification/NotificationSixProvidersTest.kt:60`
- `android/app/src/test/java/com/aiquota/mobile/notification/NotificationSixProvidersTest.kt:90`
- `android/app/src/test/java/com/aiquota/mobile/providers/GoogleProviderLoginRoutingTest.kt:44`

The tests repeatedly read source files with `File(...).readText()` and assert that implementation strings exist or do not exist. Several are deletion-only or architecture-string assertions, such as checking that `BackgroundProviderWebCollector(` is absent or that specific service branches contain exact method calls. These tests will pass while runtime behavior is broken, and fail under harmless refactors.

Optimization/fix suggestion:
- Replace policy checks with direct calls to policy classes and state machines.
- Replace service routing string checks with tests over `ProviderRefreshPlan`, fake repositories, and explicit service outcome handling.
- Keep only a small number of true architecture-boundary tests, preferably implemented as a lint/custom static rule rather than substring tests.
- For WebView/session behavior, use an instrumented WebView test or an injectable collector/session boundary instead of testing for text snippets.

## MEDIUM

### M1 - GLM isolated session service uses shared, untagged Handler callbacks across collect, clear, and cancel operations

References:
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt:31`
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt:53`
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt:79`
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt:144`
- `android/app/src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt:147`

`startCollection` posts a timeout, `clearSession` posts a fallback clear callback, and `sendResult` removes all callbacks with `removeCallbacksAndMessages(null)`. The service also reuses mutable `resultReceiver` and `completed` state for every `onStartCommand`. A quick clear/cancel/start sequence can let an old delayed callback complete a newer operation, or a newer completion remove callbacks that belong to an operation just started.

Optimization/fix suggestion:
- Introduce an operation generation token or operation object containing receiver, timeout runnable, and fallback runnable.
- Remove only the current operation's callbacks.
- Ignore callbacks whose generation does not match the active operation.
- Prefer binding/queueing one GLM operation at a time, or make `onStartCommand` reject/finish the prior operation deterministically before accepting the next.

### M2 - Surface refresh repeatedly decodes, normalizes, encodes, and writes the same snapshot JSON

References:
- `android/app/src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt:41`
- `android/app/src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt:44`
- `android/app/src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt:49`
- `android/app/src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt:55`
- `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt:16`
- `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt:30`
- `android/app/src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt:80`
- `android/app/src/main/java/com/aiquota/mobile/widget/WidgetSnapshotCache.kt:16`
- `android/app/src/main/java/com/aiquota/mobile/widget/WidgetSnapshotCache.kt:25`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageNotificationContent.kt:41`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageNotificationContent.kt:42`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageNotificationContent.kt:63`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageNotificationContent.kt:95`

One `UsageSurfaceRefresher.refresh` call exports display JSON twice and full display JSON once. Each export calls `readSnapshots()`, which decodes persisted JSON and may write normalized data back. The notification builder then parses the same snapshot JSON multiple times to compute connected keys, gauges, and labels. This path runs after `markCollecting`, after provider save/failure, after widget refresh, and from UI state changes.

Optimization/fix suggestion:
- Read snapshots once per refresh and pass the typed list to display/widget/notification encoders.
- Make `exportDisplayOnlyCache` accept already-read snapshots.
- Write widget cache only when the value changed, or batch `latest_snapshot` and `local_display_snapshot` in one editor where possible.
- Parse notification content once into a provider model and derive keys, gauges, and labels from that model.

### M3 - Legacy circular widget allocates fresh bitmaps and paints for every gauge and every size-mapped RemoteViews build

References:
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:119`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:131`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:206`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:227`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:255`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:260`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:273`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:279`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:321`

For Android S size-mapped widgets, the provider builds a `RemoteViews` per reported size and creates a new bitmap, canvas, rect, two paints, and mutated drawable per visible gauge. The helper estimates six gauges at 256-512 KiB of bitmap payload before multiplying by size variants and widget instances.

Optimization/fix suggestion:
- Cache generated gauge bitmaps within an update batch by `(providerId, theme, remainingRatio bucket, canvasSize)`.
- Reuse static `Paint` style setup or create a small renderer object per theme.
- Consider vector/level-list/progress-based rendering instead of pushing bitmaps through `RemoteViews`.
- Cap size-mapped variants if launchers report many sizes, or skip duplicate dimensions.

### M4 - Core provider files are far beyond the 250 pure-LOC maintainability ceiling

References:
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:23`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:45`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:37`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt:50`
- `android/app/src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt:52`
- `android/app/src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt:28`

Measured pure LOC:
- `ProviderUsageNormalizer.kt`: 1791
- `ProviderBackgroundRefreshService.kt`: 996
- `WebLoginActivity.kt`: 840
- `AIQuotaGlanceWidget.kt`: 422
- `ProviderUsageGlanceWidget.kt`: 348
- `AIQuotaCircularWidgetProvider.kt`: 447

This creates a practical review problem: provider parsing, lifecycle orchestration, and rendering concerns are packed into files too large to safely modify. The normalizer in particular mixes all provider schemas, plan parsing, line construction, reset parsing, and provider-specific fallbacks.

Optimization/fix suggestion:
- Split normalizers by provider with a shared typed `ProviderUsageLineBuilder`/parsing utility module.
- Split `ProviderBackgroundRefreshService` into refresh loop, WebView collector, native fetcher, and state-transition writer.
- Split widget files by layout selection, payload parsing, and rendering primitives.
- Add a size gate for new/modified Kotlin files, with explicit exceptions only for generated or pure data-table files.

## LOW

### L1 - Regex and small map allocations recur in normalizer helper paths

References:
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:80`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:82`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:91`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:92`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:283`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:285`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:1722`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageNormalizer.kt:1724`

Several helpers instantiate `Regex("[^a-z0-9]+")` and small ordering maps during normalization. The absolute cost is small, but it sits on provider refresh paths and is easy to remove.

Optimization/fix suggestion:
- Promote common regexes and order maps to private constants/properties.
- Use one `compactAlnumLowercase()` helper to make plan parsing cheaper and more consistent.

### L2 - Notification update recreates the channel and reparses content on every debounced pinned update

References:
- `android/app/src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt:96`
- `android/app/src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt:103`
- `android/app/src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt:112`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt:77`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt:81`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt:82`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt:118`
- `android/app/src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt:129`

The notification update is debounced, but every update still builds notification content and calls `createNotificationChannel`. Channel creation is idempotent, yet it is still a binder call and does not need to happen for every content update.

Optimization/fix suggestion:
- Lazily create channels once per process or during application startup.
- Store the last notification content hash and skip `notify()` when content is unchanged.

## Verification

- `git status --short` showed a dirty worktree with many Android changes and untracked `.omo/` plus GLM isolated session files.
- `git diff --check` passed. Output only warned that LF will be replaced by CRLF the next time Git touches current files.
- Gradle tests were not run for this review. Findings are source-inspection findings with exact file/line references.

## Result

codeQualityStatus: BLOCK

recommendation: REQUEST_CHANGES

reportPath: `.omo/evidence/android-app-code-quality-performance-review-code-review.md`

blockers:
- Fix `ProviderWebSessionCleaner` so async browser storage cleanup has a real completion signal before destroying the cleanup WebView.
- Remove or rewire the unused `BackgroundProviderWebCollector` production path.
- Replace implementation-mirroring/source-text tests around GLM WebView isolation, refresh service policy, notification layout, and routing with behavior-level tests.

