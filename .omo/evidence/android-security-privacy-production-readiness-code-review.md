# Android Security/Privacy Production Readiness Code Review

Scope: `D:\Vibe Project\AI Usage for Mobile`, current dirty worktree.

Mode: read-only review of provider collectors, logging-sensitive flows, WebView JavaScript injection, local encrypted storage, foreground service behavior, Firebase/token flows, and release/build config. No source or test code was modified. This report artifact is the only file written.

Verification note: I inspected code paths and line references directly. I did not run Gradle tests because the user requested read-only review and test execution would create build outputs. Codex Security `security-scan` instructions were consulted and config preflight was run; delegated worker scanning was unavailable, so this is a parent-agent code review rather than a delegated exhaustive scan.

Skill-perspective check: `omo:remove-ai-slops` and `omo:programming` were loaded before judging test relevance and maintainability. The diff violates both perspectives in the source-reading tests called out in HIGH H1: they mirror implementation strings, assert requested removals/presence, and provide brittle false confidence for security behavior.

Status: BLOCK

Recommendation: REQUEST_CHANGES

## CRITICAL

None found.

## HIGH

### H1. Security tests read source text instead of exercising runtime behavior

References:

- `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt:10-30`
- `android/app/src/test/java/com/aiquota/mobile/providers/AntigravityFirebaseGatewayTest.kt:56-82`
- `android/app/src/test/java/com/aiquota/mobile/providers/GeminiCliFirebaseGatewayTest.kt:10-25`
- `android/app/src/test/java/com/aiquota/mobile/providers/GeminiCliFirebaseGatewayTest.kt:27-63`
- `android/app/src/test/java/com/aiquota/mobile/providers/GeminiCliFirebaseGatewayTest.kt:65-103`

These tests load production source files with `File(...).readText()` and assert that specific strings are present or absent: Firebase function names, AppCheck calls, client IDs, activity calls, and literal source snippets. This does not prove that AppCheck tokens are enforced, OAuth callback state is bound, token refresh uses the intended branch at runtime, or secrets cannot reach the app through another constant/name/path. It can pass after a real security regression if the relevant strings remain in the file, and it can fail on harmless refactors.

Security and release impact: high false confidence in OAuth/Firebase/token protection tests. This is exactly the kind of implementation-mirroring test rejected by the loaded `remove-ai-slops` and `programming` perspectives.

Required before approval: replace or augment these with behavior-level tests around the gateway callable wrapper, callback URL handling, stored-token refresh behavior, and secret absence at the configured boundary rather than raw source string matching.

## MEDIUM

### M1. Legacy plaintext token fallback leaves old secrets unencrypted indefinitely

References:

- `android/app/src/main/java/com/aiquota/mobile/providers/SecureStringStore.kt:17-20`
- `android/app/src/main/java/com/aiquota/mobile/providers/SecureStringStore.kt:23-30`
- `android/app/src/main/java/com/aiquota/mobile/providers/SecureStringStore.kt:47-83`
- `android/app/src/main/java/com/aiquota/mobile/providers/GoogleAppAuthTokenStore.kt:17-18`
- `android/app/src/main/java/com/aiquota/mobile/providers/GoogleAppAuthTokenStore.kt:28-36`
- `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt:96-99`
- `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt:252-286`
- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt:94-99`
- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt:158-192`

`SecureStringStore.getString()` returns any value without the `v1:` prefix directly as plaintext. New writes are encrypted with Android Keystore AES/GCM, but old unprefixed access tokens, refresh tokens, ID tokens, account names, and email values are never migrated or deleted on read. The manifest disables backup, which reduces exposure, but app-private plaintext tokens can still remain at rest after an upgrade and in local forensic/debug contexts.

Release impact: privacy hardening is incomplete for existing users upgrading from plaintext storage. A production readiness review should require migration evidence or deliberate deletion of legacy plaintext secrets.

### M2. WebView collector payload path has no total size cap before JavaScript bridge, Intent extra, and JSON parse

References:

- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderWebCollectorScripts.kt:503-525`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:337-348`
- `android/app/src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt:665-677`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:560-585`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:857-871`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt:56-58`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt:124-134`

The collector helper slices each localStorage/sessionStorage/script value to 200,000 characters and page text to 50,000 characters, but there is no total row count or total payload cap before the value crosses `@JavascriptInterface`, is carried as `rawPayload`, is placed into `ProviderUsageCollectionService` as an Intent extra, and is parsed by `ProviderUsageNormalizer`. Allowed provider pages are still remote web content; a large but provider-shaped payload can trigger memory pressure, UI jank, Binder transaction failure, or background refresh churn.

Security and performance impact: this is primarily a release-readiness/performance risk, with privacy relevance because the collector scans script and storage content from authenticated provider pages. The bridge origin checks are useful, but they do not bound payload size.

### M3. One-minute special-use foreground hidden WebView refresh is a Play policy and battery risk

References:

- `android/app/src/main/AndroidManifest.xml:4-6`
- `android/app/src/main/AndroidManifest.xml:78-85`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt:23-29`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt:56-74`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt:88-94`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:205-229`
- `android/app/src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt:236-258`

The app requests `FOREGROUND_SERVICE_SPECIAL_USE` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, declares a `specialUse` foreground service with subtype `usage_monitor`, and schedules automatic refresh at a 60 second cadence. The plan includes hidden WebView collection modes for connected providers. This may be legitimate for the product, but it is a release-readiness risk unless the Play Console declaration, visible user disclosure, opt-in/opt-out behavior, and battery justification are all aligned with the actual hidden WebView/cookie-based monitoring behavior.

Performance impact: a one-minute cadence with provider timeouts up to 75 seconds can keep the service and WebViews busy for long periods on accounts with several connected providers, especially when remote provider pages are slow.

## LOW

### L1. Release build enables minification but disables shrinking and optimization

References:

- `android/app/build.gradle.kts:123-136`
- `android/app/proguard-rules.pro:1-8`
- `android/app/proguard-rules.pro:37-39`

Release config sets `isMinifyEnabled = true`, but ProGuard rules include `-dontshrink` and `-dontoptimize`, while resource shrinking is disabled. Keeping `@JavascriptInterface` members is expected, but disabling shrink/optimize globally undercuts release size, performance, and some hardening benefits normally expected from a production release build.

### L2. Exported OAuth deep-link activities can be externally launched and start stateful network flows

References:

- `android/app/src/main/AndroidManifest.xml:27-52`
- `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:37-65`
- `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt:130-157`
- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:37-65`
- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt:130-157`
- `android/app/src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt:18-42`
- `android/app/src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt:19-43`

The Gemini and Antigravity OAuth activities are exported `BROWSABLE` handlers. On creation they mark the provider connecting and immediately start Firebase-backed OAuth in a WebView. The client-side callback handling only accepts the expected loopback URLs before sending `callbackUrl` to Firebase, which is a meaningful guard. The remaining risk is lower-severity: any app/browser can launch these activities and trigger state changes/network work, while callback/state validation depends on backend behavior that is outside this repository.

## Positive Controls Observed

- `android:allowBackup="false"` is set in the main manifest, reducing backup exposure for app-private preferences.
- WebView login and collector paths gate accepted payloads through provider/page checks before accepting bridge payloads.
- Native Cursor/Copilot bridge fetchers were observed to use endpoint allowlists in the inspected code paths, reducing generic SSRF/cookie-exfiltration risk from the JavaScript bridge.
- Debug-only tooling is separated under debug source sets or debug manifests in the inspected paths.

## Blockers

- H1 must be addressed before approval because the current OAuth/Firebase/security tests are implementation-mirroring source tests that create false confidence and violate both required skill perspectives.
