# Provider Usage Freshness and Auth Continuity impl Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provider usage를 1분 단위로 수집하되, 실패한 refresh 뒤 과거 값을 최신값처럼 보여주지 않고 stale/expired 상태를 명확히 표시한다.

**Architecture:** 데이터 신뢰성 계층을 먼저 추가한다. Snapshot은 trusted usage row 저장 성공 후에만 `CONNECTED`가 되고, 실패 시 이전 row는 보존할 수 있지만 `STALE`로 표시한다. Provider별 인증 저장소와 collection strategy를 명시해 WebView provider는 같은 앱 WebView profile을 쓰고, token/API provider는 native token store를 통해 수집한다.

**Tech Stack:** Kotlin, Android WebView, Android Service, Compose, SharedPreferences snapshot cache, JUnit.

## Source Spec

`D:/Vibe Project/AI Usage for Mobile/docs/superpowers/specs/2026-05-21-provider-usage-freshness-auth-continuity-spec.md`

## Progress Journal

모든 구현 단계, 실패, 테스트 결과, 트러블 슈팅은 아래 문서에 기록한다.

`D:/Vibe Project/AI Usage for Mobile/docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md`

## File Structure

Create:

- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/ProviderUsageFreshness.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/ProviderVolatileUsagePolicy.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderAuthStrategy.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshFailure.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/local/ProviderUsageFreshnessTest.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/local/ProviderVolatileUsagePolicyTest.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/providers/ProviderAuthStrategyTest.kt`

Modify:

- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderDefinitions.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshPlan.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderUsageCollectionService.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/BackgroundProviderWebCollector.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/dashboard/UnifiedDashboardScreen.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/provider/ProviderDetailScreen.kt`
- `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/res/values/strings.xml`
- existing focused tests under `android/app/src/test/java/com/aiusage/mobile/**`

## Task 1: Freshness State Model

- Create: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/ProviderUsageFreshness.kt`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`
- Test: `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/local/ProviderUsageFreshnessTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.aiusage.mobile.local

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUsageFreshnessTest {
    @Test
    fun connectedWithLinesIsFresh() {
        val snapshot = snapshot(ProviderConnectionState.CONNECTED, updatedAt = "2026-05-21T00:00:00Z")

        assertEquals(
            ProviderUsageFreshness.FRESH,
            snapshot.usageFreshness(now = Instant.parse("2026-05-21T00:01:00Z"))
        )
    }

    @Test
    fun staleWithLinesIsDisplayedAsStaleNotFresh() {
        val snapshot = snapshot(ProviderConnectionState.STALE, updatedAt = "2026-05-21T00:00:00Z")

        assertEquals(
            ProviderUsageFreshness.STALE,
            snapshot.usageFreshness(now = Instant.parse("2026-05-21T00:01:00Z"))
        )
        assertTrue(snapshot.hasLastKnownUsage(now = Instant.parse("2026-05-21T00:01:00Z")))
    }

    @Test
    fun interactiveAuthRequiredKeepsLastKnownUsageButNeedsReconnect() {
        val snapshot = snapshot(ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED)

        assertEquals(
            ProviderUsageFreshness.AUTH_REQUIRED,
            snapshot.usageFreshness(now = Instant.parse("2026-05-21T00:01:00Z"))
        )
        assertEquals(ProviderConnectionAction.CONNECT, snapshot.primaryConnectionAction())
    }

    private fun snapshot(
        state: ProviderConnectionState,
        updatedAt: String = "2026-05-21T00:00:00Z"
    ): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = ProviderId.CLAUDE,
            connectionState = state,
            updatedAt = updatedAt,
            lines = listOf(ProviderUsageLine(label = "Claude Session", key = "claude:session", remainingPercent = 0.5f))
        )
    }
}
```

- [ ] **Step 2: Run tests and verify red**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderUsageFreshnessTest"
```

Expected: compile failure for `INTERACTIVE_AUTH_REQUIRED`, `ProviderUsageFreshness`, `usageFreshness`, or `hasLastKnownUsage`.

- [ ] **Step 3: Add model and helper**

In `ProviderModels.kt`, add enum value:

```kotlin
enum class ProviderConnectionState {
    DISCONNECTED,
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    COLLECTING,
    STALE,
    INTERACTIVE_AUTH_REQUIRED,
    UNAVAILABLE,
    ERROR
}
```

Update `primaryConnectionAction()`:

```kotlin
ProviderConnectionState.DISCONNECTED,
ProviderConnectionState.NOT_CONNECTED,
ProviderConnectionState.UNAVAILABLE,
ProviderConnectionState.ERROR,
ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> ProviderConnectionAction.CONNECT
```

Add to `ProviderUsageSnapshot.Companion`:

```kotlin
fun interactiveAuthRequiredKeepingPrevious(
    providerId: ProviderId,
    previous: ProviderUsageSnapshot?,
    message: String
): ProviderUsageSnapshot {
    val now = Instant.now().toString()
    return previous?.copy(
        connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
        refreshState = ProviderRefreshState.IDLE,
        updatedAt = now,
        message = message
    ) ?: ProviderUsageSnapshot(
        providerId = providerId,
        connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
        refreshState = ProviderRefreshState.IDLE,
        updatedAt = now,
        message = message
    )
}
```

Create `ProviderUsageFreshness.kt`:

```kotlin
package com.aiusage.mobile.local

import java.time.Instant

enum class ProviderUsageFreshness {
    NO_DATA,
    FRESH,
    COLLECTING,
    STALE,
    AUTH_REQUIRED,
    ERROR
}

fun ProviderUsageSnapshot.usageFreshness(now: Instant = Instant.now()): ProviderUsageFreshness {
    if (refreshState == ProviderRefreshState.REFRESHING || connectionState == ProviderConnectionState.COLLECTING) {
        return ProviderUsageFreshness.COLLECTING
    }
    if (lines.isEmpty()) {
        return when (connectionState) {
            ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> ProviderUsageFreshness.AUTH_REQUIRED
            ProviderConnectionState.ERROR -> ProviderUsageFreshness.ERROR
            else -> ProviderUsageFreshness.NO_DATA
        }
    }
    return when (connectionState) {
        ProviderConnectionState.CONNECTED -> ProviderUsageFreshness.FRESH
        ProviderConnectionState.STALE -> ProviderUsageFreshness.STALE
        ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> ProviderUsageFreshness.AUTH_REQUIRED
        ProviderConnectionState.ERROR,
        ProviderConnectionState.UNAVAILABLE -> ProviderUsageFreshness.ERROR
        else -> ProviderUsageFreshness.STALE
    }
}

fun ProviderUsageSnapshot.hasLastKnownUsage(now: Instant = Instant.now()): Boolean {
    return lines.isNotEmpty() && usageFreshness(now) in setOf(
        ProviderUsageFreshness.STALE,
        ProviderUsageFreshness.AUTH_REQUIRED,
        ProviderUsageFreshness.ERROR
    )
}
```

- [ ] **Step 4: Run tests and verify green**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderUsageFreshnessTest"
```

Expected: PASS.

## Task 2: Volatile Row Expiry Policy

- Create: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/ProviderVolatileUsagePolicy.kt`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`
- Test: `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/local/ProviderVolatileUsagePolicyTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.aiusage.mobile.local

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderVolatileUsagePolicyTest {
    @Test
    fun expiredCopilotRowsAreRemovedAfterReset() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.COPILOT,
            connectionState = ProviderConnectionState.STALE,
            lines = listOf(
                ProviderUsageLine(key = "copilot:completions", label = "Inline suggestions", remainingPercent = 0.4f, resetsAt = "2026-05-21T00:00:00Z"),
                ProviderUsageLine(key = "copilot:chat", label = "Chat", remainingPercent = 0.8f, resetsAt = "2026-05-22T00:00:00Z")
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(now = Instant.parse("2026-05-21T00:01:00Z"))

        assertEquals(listOf("Chat"), cleaned.lines.map { it.label })
        assertFalse(cleaned.lines.any { it.key == "copilot:completions" })
    }

    @Test
    fun allExpiredRowsProduceStaleNoDataSnapshot() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.GEMINI,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(
                ProviderUsageLine(key = "gemini:gemini_pro", label = "Gemini Pro", remainingPercent = 0.1f, resetsAt = "2026-05-20T00:00:00Z")
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(now = Instant.parse("2026-05-21T00:01:00Z"))

        assertEquals(ProviderConnectionState.STALE, cleaned.connectionState)
        assertEquals(emptyList<ProviderUsageLine>(), cleaned.lines)
    }
}
```

- [ ] **Step 2: Run tests and verify red**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderVolatileUsagePolicyTest"
```

Expected: FAIL because Copilot/Gemini expired policy is not implemented.

- [ ] **Step 3: Create volatile policy**

```kotlin
package com.aiusage.mobile.local

import java.time.Instant

object ProviderVolatileUsagePolicy {
    fun removeExpiredLines(snapshot: ProviderUsageSnapshot, now: Instant): ProviderUsageSnapshot {
        val lines = snapshot.lines.filterNot { line -> shouldRemove(snapshot.providerId, line, now) }
        if (lines.size == snapshot.lines.size) return snapshot
        return snapshot.copy(
            connectionState = if (lines.isEmpty()) ProviderConnectionState.STALE else snapshot.connectionState,
            lines = lines,
            message = "${snapshot.providerId.displayName} usage needs a fresh provider update."
        )
    }

    private fun shouldRemove(providerId: ProviderId, line: ProviderUsageLine, now: Instant): Boolean {
        if (!hasExpired(line, now)) return false
        return when (providerId) {
            ProviderId.CLAUDE -> line.key in setOf("claude:session")
            ProviderId.CODEX -> line.key in setOf("codex:primary_window")
            ProviderId.COPILOT -> line.key in setOf("copilot:chat", "copilot:completions", "copilot:premium_requests")
            ProviderId.GEMINI -> line.key.startsWith("gemini:")
            ProviderId.CURSOR -> line.resetsAt != null
        }
    }

    private fun hasExpired(line: ProviderUsageLine, now: Instant): Boolean {
        return line.resetsAt?.let(::parseInstantLike)?.let { !it.isAfter(now) } == true
    }
}

internal fun parseInstantLike(value: String): Instant? {
    runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
    val epoch = value.toLongOrNull() ?: return null
    return when {
        epoch > 10_000_000_000L -> Instant.ofEpochMilli(epoch)
        epoch > 0L -> Instant.ofEpochSecond(epoch)
        else -> null
    }
}
```

Update `LocalUsageRepository.kt`:

```kotlin
private fun clearExpiredProviderSpecificLines(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    return ProviderVolatileUsagePolicy.removeExpiredLines(snapshot, Instant.now())
}
```

Remove the old `VOLATILE_WINDOW_KEYS` map and duplicate `parseInstantLike` from `LocalUsageRepository.kt`.

- [ ] **Step 4: Run tests and verify green**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderVolatileUsagePolicyTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.LocalUsageRepositoryTest"
```

Expected: PASS.

## Task 3: Stale Display UI

- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/res/values/strings.xml`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/dashboard/UnifiedDashboardScreen.kt`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/provider/ProviderDetailScreen.kt`

- [ ] **Step 1: Add strings**

Add these strings near existing provider status strings:

```xml
<string name="provider_status_stale">Needs refresh</string>
<string name="provider_status_auth_required">Connection needs attention</string>
<string name="provider_last_known_usage">Last known usage. Updated %1$s</string>
```

- [ ] **Step 2: Update dashboard status mapping**

In `UnifiedDashboardScreen.kt`, update `ProviderUsageSnapshot.statusLabel()`:

```kotlin
return when (connectionState) {
    ProviderConnectionState.DISCONNECTED -> stringResource(R.string.provider_status_disconnected)
    ProviderConnectionState.CONNECTING -> stringResource(R.string.provider_status_connecting)
    ProviderConnectionState.CONNECTED -> stringResource(R.string.provider_status_connected)
    ProviderConnectionState.COLLECTING -> stringResource(R.string.provider_status_collecting)
    ProviderConnectionState.STALE -> stringResource(R.string.provider_status_stale)
    ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> stringResource(R.string.provider_status_auth_required)
    ProviderConnectionState.UNAVAILABLE -> stringResource(R.string.provider_unavailable)
    ProviderConnectionState.ERROR -> stringResource(R.string.provider_status_error)
    ProviderConnectionState.NOT_CONNECTED -> stringResource(R.string.provider_status_disconnected)
}
```

- [ ] **Step 3: Update detail status mapping**

In `ProviderDetailScreen.kt`, update the matching private status helper:

```kotlin
return when (snapshot.connectionState) {
    ProviderConnectionState.DISCONNECTED -> stringResource(R.string.provider_status_disconnected)
    ProviderConnectionState.CONNECTING -> stringResource(R.string.provider_status_connecting)
    ProviderConnectionState.CONNECTED -> stringResource(R.string.provider_status_connected)
    ProviderConnectionState.COLLECTING -> stringResource(R.string.provider_status_collecting)
    ProviderConnectionState.STALE -> stringResource(R.string.provider_status_stale)
    ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> stringResource(R.string.provider_status_auth_required)
    ProviderConnectionState.UNAVAILABLE -> stringResource(R.string.provider_unavailable)
    ProviderConnectionState.ERROR -> stringResource(R.string.provider_status_error)
    ProviderConnectionState.NOT_CONNECTED -> stringResource(R.string.provider_status_disconnected)
}
```

- [ ] **Step 4: Show last-known marker in detail**

In `ProviderSummaryBlock`, replace the updated line with:

```kotlin
snapshot.updatedAt.takeIf { it.isNotBlank() }?.let { updatedAt ->
    val updatedText = if (snapshot.hasLastKnownUsage()) {
        stringResource(R.string.provider_last_known_usage, updatedAt)
    } else {
        stringResource(R.string.provider_updated, updatedAt)
    }
    ClassicInfoLine(text = updatedText)
}
```

- [ ] **Step 5: Run focused compile/tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderUsageFreshnessTest"
```

Expected: PASS. If Compose compile errors appear during full test, fix imports for `hasLastKnownUsage`.

## Task 4: Connected Gate After Trusted Usage

- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderUsageCollectionService.kt`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`
- Test: existing provider normalizer and repository tests.

- [ ] **Step 1: Add repository method**

In `LocalUsageRepository.kt`:

```kotlin
fun markInteractiveAuthRequired(providerId: ProviderId, message: String) {
    saveSnapshot(
        ProviderUsageSnapshot.interactiveAuthRequiredKeepingPrevious(
            providerId = providerId,
            previous = readSnapshots().firstOrNull { it.providerId == providerId },
            message = message
        )
    )
}
```

- [ ] **Step 2: Keep no-payload login out of CONNECTED**

In `ProviderUsageCollectionService.kt`, keep this invariant:

```kotlin
if (snapshot != null) {
    repository.saveSnapshot(snapshot)
} else {
    repository.failKeepingPrevious(providerId, "No trusted provider usage payload was collected.")
}
```

Do not call any method that stores `CONNECTED` when `snapshot == null`.

- [ ] **Step 3: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderUsageNormalizerTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderUsageFreshnessTest"
```

Expected: PASS. If `ProviderUsageNormalizerTest` class name differs, run `.\gradlew.bat :app:testDebugUnitTest --tests "*ProviderUsageNormalizer*"` and record exact class name in the progress journal.

## Task 5: Provider Auth Strategy Map

- Create: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderAuthStrategy.kt`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderDefinitions.kt`
- Test: `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/providers/ProviderAuthStrategyTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderAuthStrategyTest {
    @Test
    fun providerStrategiesMatchFreshnessSpec() {
        assertEquals(ProviderAuthStoreKind.WEBVIEW_PROFILE, ProviderDefinitionRegistry.definitionFor(ProviderId.CLAUDE).authStoreKind)
        assertEquals(ProviderAuthStoreKind.WEBVIEW_PROFILE, ProviderDefinitionRegistry.definitionFor(ProviderId.CODEX).authStoreKind)
        assertEquals(ProviderAuthStoreKind.NATIVE_TOKEN, ProviderDefinitionRegistry.definitionFor(ProviderId.COPILOT).authStoreKind)
        assertEquals(ProviderAuthStoreKind.NATIVE_TOKEN, ProviderDefinitionRegistry.definitionFor(ProviderId.GEMINI).authStoreKind)
        assertEquals(ProviderAuthStoreKind.TOKEN_OR_WEBVIEW_FALLBACK, ProviderDefinitionRegistry.definitionFor(ProviderId.CURSOR).authStoreKind)
    }
}
```

- [ ] **Step 2: Run red**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderAuthStrategyTest"
```

Expected: compile failure for `ProviderAuthStoreKind` or `authStoreKind`.

- [ ] **Step 3: Add strategy types**

```kotlin
package com.aiusage.mobile.providers

enum class ProviderAuthStoreKind {
    WEBVIEW_PROFILE,
    NATIVE_TOKEN,
    TOKEN_OR_WEBVIEW_FALLBACK
}

enum class ProviderCollectionKind {
    WEBVIEW_COLLECTOR,
    NATIVE_API,
    NATIVE_API_WITH_WEBVIEW_FALLBACK
}
```

Update `ProviderDefinition`:

```kotlin
data class ProviderDefinition(
    val providerId: ProviderId,
    val loginStartUrl: String,
    val allowedHosts: Set<String>,
    val preferredUsageEndpoint: String,
    val authStoreKind: ProviderAuthStoreKind,
    val collectionKind: ProviderCollectionKind,
    val sessionProbeUrl: String
)
```

Set definitions:

```kotlin
authStoreKind = ProviderAuthStoreKind.WEBVIEW_PROFILE,
collectionKind = ProviderCollectionKind.WEBVIEW_COLLECTOR,
sessionProbeUrl = "https://claude.ai/"
```

For Codex:

```kotlin
authStoreKind = ProviderAuthStoreKind.WEBVIEW_PROFILE,
collectionKind = ProviderCollectionKind.WEBVIEW_COLLECTOR,
sessionProbeUrl = "https://chatgpt.com/backend-api/wham/usage"
```

For Copilot:

```kotlin
authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN,
collectionKind = ProviderCollectionKind.NATIVE_API,
sessionProbeUrl = "https://api.github.com/user"
```

For Gemini:

```kotlin
authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN,
collectionKind = ProviderCollectionKind.NATIVE_API,
sessionProbeUrl = "https://cloudcode-pa.googleapis.com/"
```

For Cursor:

```kotlin
authStoreKind = ProviderAuthStoreKind.TOKEN_OR_WEBVIEW_FALLBACK,
collectionKind = ProviderCollectionKind.NATIVE_API_WITH_WEBVIEW_FALLBACK,
sessionProbeUrl = "https://cursor.com/dashboard"
```

- [ ] **Step 4: Run green**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderAuthStrategyTest"
```

Expected: PASS.

## Task 6: Refresh Failure Kinds and Auth-Required Routing

- Create: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshFailure.kt`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/BackgroundProviderWebCollector.kt`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`

- [ ] **Step 1: Add failure model**

```kotlin
package com.aiusage.mobile.providers

enum class ProviderRefreshFailureKind {
    TRANSIENT_PAGE_LOAD,
    TRANSIENT_HTTP,
    TIMEOUT,
    COLLECTOR_ERROR,
    NO_TRUSTED_PAYLOAD,
    INTERACTIVE_AUTH_REQUIRED
}

data class ProviderRefreshFailure(
    val kind: ProviderRefreshFailureKind,
    val message: String
) {
    companion object {
        fun interactiveAuthRequired(): ProviderRefreshFailure =
            ProviderRefreshFailure(
                ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED,
                "Connection needs attention."
            )
    }
}
```

- [ ] **Step 2: Change hidden collector callback type**

Change collector `onError` type from `String` to `ProviderRefreshFailure`.

Example:

```kotlin
private val onError: (QueuedProviderRefreshJob, ProviderRefreshFailure) -> Unit
```

Map old failures:

```kotlin
ProviderRefreshFailure(ProviderRefreshFailureKind.TIMEOUT, "Background refresh timed out.")
ProviderRefreshFailure(ProviderRefreshFailureKind.TRANSIENT_PAGE_LOAD, "Background refresh page failed to load.")
ProviderRefreshFailure(ProviderRefreshFailureKind.TRANSIENT_HTTP, "Background refresh returned HTTP ${errorResponse.statusCode}.")
```

Login/auth redirect after bounded silent recovery:

```kotlin
finishWithErrorOnce(job, ProviderRefreshFailure.interactiveAuthRequired())
```

- [ ] **Step 3: Route in shell**

In `AIUsageAppShell.kt`:

```kotlin
onError = { queuedJob, failure ->
    when (failure.kind) {
        ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED -> {
            localUsageRepository.markInteractiveAuthRequired(queuedJob.job.providerId, failure.message)
        }
        else -> {
            localUsageRepository.failKeepingPrevious(queuedJob.job.providerId, failure.message)
        }
    }
    finishProviderRefreshSurfaces()
}
```

- [ ] **Step 4: Run compile-focused tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderRefreshPlanTest"
```

Expected: PASS or compile errors only in files touched by callback type migration. Fix all callback type mismatches.

## Task 7: Refresh Policy Keeps Recoverable Providers, Stops Auth-Required Providers

- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/sync/ForegroundRefreshPolicy.kt`
- Test: `D:/Vibe Project/AI Usage for Mobile/android/app/src/test/java/com/aiusage/mobile/sync/ForegroundRefreshPolicyTest.kt`

- [ ] **Step 1: Add tests**

```kotlin
@Test
fun staleProvidersRemainRefreshableButAuthRequiredProvidersStop() {
    val stale = snapshot(ProviderId.CLAUDE, ProviderConnectionState.STALE)
    val authRequired = snapshot(ProviderId.COPILOT, ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED)

    assertEquals(
        listOf(ProviderId.CLAUDE),
        ForegroundRefreshPolicy.connectedProviders(listOf(stale, authRequired))
    )
}
```

- [ ] **Step 2: Implement explicit refreshable set**

```kotlin
private val REFRESHABLE_STATES = setOf(
    ProviderConnectionState.CONNECTED,
    ProviderConnectionState.COLLECTING,
    ProviderConnectionState.STALE
)
```

Use it:

```kotlin
it.lines.isNotEmpty() && it.connectionState in REFRESHABLE_STATES
```

- [ ] **Step 3: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.sync.ForegroundRefreshPolicyTest"
```

Expected: PASS.

## Task 8: Provider-Specific Collection Direction

- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/ProviderRefreshPlan.kt`
- Modify: `D:/Vibe Project/AI Usage for Mobile/android/app/src/main/java/com/aiusage/mobile/providers/CopilotNativeUsageFetcher.kt`
- Modify: Gemini token/API files only after reading their current contracts.
- Test: provider refresh and fetcher tests.

- [ ] **Step 1: Drive refresh mode from strategy**

In `ProviderRefreshPlan.kt`:

```kotlin
private fun refreshModeFor(providerId: ProviderId): ProviderRefreshMode {
    return when (ProviderDefinitionRegistry.definitionFor(providerId).collectionKind) {
        ProviderCollectionKind.WEBVIEW_COLLECTOR,
        ProviderCollectionKind.NATIVE_API_WITH_WEBVIEW_FALLBACK -> ProviderRefreshMode.HIDDEN_WEB_COLLECTOR
        ProviderCollectionKind.NATIVE_API -> ProviderRefreshMode.NATIVE_API
    }
}
```

- [ ] **Step 2: Keep current WebView providers in WebView collector**

Expected:

- Claude -> `HIDDEN_WEB_COLLECTOR`
- Codex -> `HIDDEN_WEB_COLLECTOR`
- Cursor -> fallback `HIDDEN_WEB_COLLECTOR` until token path is implemented
- Gemini -> `NATIVE_API`
- Copilot -> `NATIVE_API`

- [ ] **Step 3: Update tests**

In `ProviderRefreshPlanTest`, change Copilot expectation:

```kotlin
assertEquals(ProviderRefreshMode.NATIVE_API, ProviderRefreshPlan.manualJobFor(ProviderId.COPILOT).mode)
assertTrue(ProviderRefreshPlan.manualJobFor(ProviderId.COPILOT).startUrl.isBlank())
```

- [ ] **Step 4: Copilot native collection gate**

Before using `CopilotNativeUsageFetcher.fetchUsagePayload(...)`, require token-backed auth path or captured API authorization from same session. If neither exists, return auth-required rather than scraping stale settings HTML as fresh proof.

Implementation rule:

```kotlin
val payload = CopilotNativeUsageFetcher.fetchUsagePayload(githubAccessToken = tokenFromNativeStore)
if (payload == null) {
    localUsageRepository.markInteractiveAuthRequired(ProviderId.COPILOT, "Connection needs attention.")
} else {
    ProviderUsageCollectionService.start(appContext, ProviderId.COPILOT, ProviderUsageCollectionService.SOURCE_PAYLOAD, payload)
}
```

Do not pass token values into logs or docs.

- [ ] **Step 5: Run focused tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderRefreshPlanTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.CopilotNativeUsageFetcherTest"
```

Expected: PASS.

## Task 9: Runtime QA and Journal Updates

- Modify: `D:/Vibe Project/AI Usage for Mobile/docs/qa/provider-usage-freshness-auth-continuity-progress-2026-05-21.md`
- Modify: `D:/Vibe Project/AI Usage for Mobile/docs/qa/store-apk-parity-gap-tracker-2026-05-20.md` only if a parity gap row changes.

- [ ] **Step 1: Run full unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Runtime checks**

Use emulator/device:

1. Login to Claude in app WebView.
2. Confirm usage row saved and status is `Connected`.
3. Force a refresh failure without clearing saved rows.
4. Confirm dashboard/detail status changes to `Needs refresh` and last-known wording appears.
5. Set or simulate expired `resetsAt` for volatile row.
6. Confirm expired row disappears instead of showing as current usage.
7. Repeat equivalent checks for Codex and Copilot paths available in current build.

- [ ] **Step 3: Journal entry format**

Append entries like this:

```markdown
## 2026-05-21 HH:mm - <short action>

Action:
- <what changed or what was tested>

Result:
- <pass/fail and exact behavior>

Troubleshooting:
- <failure cause and chosen fix>

Files:
- `<path>`

Commands:
- `<command>`

Next:
- <next concrete action>
```

Do not record raw tokens, cookies, auth headers, account identifiers, OAuth codes, emails, usernames, or full provider HTML.

## Final Verification

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest
git diff --check
```

Expected:

- unit tests pass,
- diff check clean,
- stale values are marked,
- expired volatile rows are removed,
- `CONNECTED` means trusted usage row stored,
- Copilot refresh direction no longer depends on Chrome/Custom Tab cookies feeding app WebView collector.

## Self-Review

- Spec coverage: Tasks 1-3 cover stale display and row expiry. Tasks 4-8 cover connected gate, auth-store alignment, provider strategy, and collection direction. Task 9 covers runtime verification and progress logging.
- Placeholder scan: no placeholder markers are required for execution.
- Type consistency: `INTERACTIVE_AUTH_REQUIRED`, `ProviderUsageFreshness`, `ProviderAuthStoreKind`, `ProviderCollectionKind`, `ProviderRefreshFailureKind`, and `markInteractiveAuthRequired` are defined before use.
