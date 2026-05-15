# Mobile Local Provider Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert AI Usage into a local-first Android app that tracks Claude, Codex, Gemini, GitHub Copilot, and Cursor subscription usage limits, with a unified dashboard, provider detail screens, persisted hide/reorder settings, a unified widget, individual provider widgets, and phone/tablet verification.

**Architecture:** Add a local provider domain beside the existing Windows sync code, make the local dashboard the primary UI, and keep Windows/Firebase sync hidden behind a legacy settings boundary. Provider connectors own login/session/extraction; dashboards, widgets, and notifications consume normalized display-only snapshots.

**Tech Stack:** Kotlin, Jetpack Compose, Glance AppWidget, Android WebView, SharedPreferences, Firebase code retained as hidden legacy code, JUnit4, org.json, existing Gradle Android project.

---

## Implementation Notes

- Do not delete Windows/Firebase sync code in this phase.
- Do not add a backend service.
- Do not store provider cookies, access tokens, refresh tokens, raw HTML, raw provider responses, local credential paths, or raw logs in widget cache, notification cache, screenshots, or Firebase.
- The two widget families are explicit user-facing choices:
  - Unified dashboard widget: 2x2 through 3x4.
  - Individual provider widget: 2x1 through 3x3, configured to one provider.
- Existing untracked screenshot PNG files in the repository root are unrelated to this implementation. Leave them alone.
- Use Java 17 for Gradle commands:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## File Structure

Create these local provider domain files:

- `android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`
  - Stable provider ids, connection/status enums, usage snapshot data classes.
- `android/app/src/main/java/com/aiusage/mobile/local/ProviderPreferencesCodec.kt`
  - Pure functions for default order, reorder, hide/show, and JSON preference encoding.
- `android/app/src/main/java/com/aiusage/mobile/local/ProviderPreferencesRepository.kt`
  - Android SharedPreferences-backed provider order, hidden ids, and provider widget selections.
- `android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`
  - SharedPreferences-backed normalized usage snapshots and display-only widget export.
- `android/app/src/main/java/com/aiusage/mobile/local/WidgetCacheSanitizer.kt`
  - Rejects sensitive keys before writing display-only cache JSON.

Create these connector files:

- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderConnector.kt`
  - Connector interface and connector result types.
- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderConnectorRegistry.kt`
  - Registry for Claude, Codex, Gemini, Copilot, and Cursor connectors.
- `android/app/src/main/java/com/aiusage/mobile/providers/ProviderHostAllowlist.kt`
  - Allowed hosts per provider.
- `android/app/src/main/java/com/aiusage/mobile/providers/WebLoginActivity.kt`
  - WebView login/detail shell for provider sessions.
- `android/app/src/main/java/com/aiusage/mobile/providers/TextUsageExtractor.kt`
  - Deterministic extraction from visible provider text into normalized usage lines.

Create these UI files:

- `android/app/src/main/java/com/aiusage/mobile/ui/AppRoute.kt`
  - Home/provider/settings routes and deep link extras.
- `android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`
  - Local-first app shell, bottom navigation, ad banner integration, route handling.
- `android/app/src/main/java/com/aiusage/mobile/ui/dashboard/UnifiedDashboardScreen.kt`
  - Provider cards, connect buttons, hide/show, long-press reorder.
- `android/app/src/main/java/com/aiusage/mobile/ui/dashboard/ProviderCardOrder.kt`
  - Pure reorder helpers used by UI and tests.
- `android/app/src/main/java/com/aiusage/mobile/ui/provider/ProviderDetailScreen.kt`
  - Provider detail, login, refresh, disconnect, unavailable/error states.
- `android/app/src/main/java/com/aiusage/mobile/ui/settings/LegacyWindowsSyncPanel.kt`
  - Existing Windows sync settings moved behind a legacy panel.

Modify these existing files:

- `android/app/src/main/java/com/aiusage/mobile/MainActivity.kt`
  - Delegate to `AIUsageAppShell`; keep legacy helpers callable from `LegacyWindowsSyncPanel`.
- `android/app/src/main/java/com/aiusage/mobile/widget/WidgetSnapshotCache.kt`
  - Add local display-only cache state and provider widget selection support.
- `android/app/src/main/java/com/aiusage/mobile/widget/WidgetGaugeParser.kt`
  - Parse local provider snapshots, text-only lines, hidden providers, and provider-specific payloads.
- `android/app/src/main/java/com/aiusage/mobile/widget/WidgetGaugeLayout.kt`
  - Add unified and provider widget layout specs for requested size ranges.
- `android/app/src/main/java/com/aiusage/mobile/widget/AIUsageGlanceWidget.kt`
  - Rename behavior to unified dashboard widget and remove Firebase sign-in gating.
- `android/app/src/main/java/com/aiusage/mobile/widget/AIUsageCircularWidgetProvider.kt`
  - Stop registering this as a separate widget; replace its manifest role with the provider widget family.
- `android/app/src/main/java/com/aiusage/mobile/notification/UsageNotificationContent.kt`
  - Use local provider order and hidden state through display-only cache payload.
- `android/app/src/main/AndroidManifest.xml`
  - Add `WebLoginActivity`, provider widget configure activity, unified widget receiver, provider widget receiver.
- `android/app/src/main/res/values/strings.xml`
  - Add provider labels, local dashboard copy, widget labels.
- `android/app/src/main/res/values-ko/strings.xml`
  - Korean strings for the same surfaces.
- `android/app/src/main/res/xml/ai_usage_widget_large.xml`
  - Rename label/metadata semantics to unified dashboard widget while keeping safe dimensions.
- `android/app/src/main/res/xml/ai_usage_widget_provider.xml`
  - New provider widget metadata.

Add tests:

- `android/app/src/test/java/com/aiusage/mobile/local/ProviderModelsTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/local/ProviderPreferencesCodecTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/local/WidgetCacheSanitizerTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/providers/ProviderConnectorRegistryTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/providers/ProviderHostAllowlistTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/providers/TextUsageExtractorTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/ui/dashboard/ProviderCardOrderTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/widget/UnifiedWidgetPayloadTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/widget/ProviderWidgetPayloadTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/widget/WidgetLayoutSpecTest.kt`
- `android/app/src/test/java/com/aiusage/mobile/notification/UsageNotificationContentTest.kt` existing file receives added assertions.

---

### Task 1: Local Provider Domain And Preference Codec

**Files:**
- Create: `android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/local/ProviderPreferencesCodec.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/local/ProviderPreferencesRepository.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/local/ProviderModelsTest.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/local/ProviderPreferencesCodecTest.kt`

- [ ] **Step 1: Write provider model tests**

Create `android/app/src/test/java/com/aiusage/mobile/local/ProviderModelsTest.kt`:

```kotlin
package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModelsTest {
    @Test
    fun defaultProvidersAreStableAndInDashboardOrder() {
        assertEquals(
            listOf(
                ProviderId.CLAUDE,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT,
                ProviderId.CURSOR
            ),
            ProviderId.defaultOrder()
        )
    }

    @Test
    fun providerStorageIdsAreLowercaseAndStable() {
        assertEquals("claude", ProviderId.CLAUDE.storageId)
        assertEquals("codex", ProviderId.CODEX.storageId)
        assertEquals("gemini", ProviderId.GEMINI.storageId)
        assertEquals("copilot", ProviderId.COPILOT.storageId)
        assertEquals("cursor", ProviderId.CURSOR.storageId)
    }

    @Test
    fun disconnectedSnapshotHasLoginActionState() {
        val snapshot = ProviderUsageSnapshot.disconnected(ProviderId.CLAUDE)

        assertEquals(ProviderConnectionState.DISCONNECTED, snapshot.connectionState)
        assertEquals(ProviderRefreshState.IDLE, snapshot.refreshState)
        assertTrue(snapshot.lines.isEmpty())
    }
}
```

- [ ] **Step 2: Run model tests and verify they fail**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderModelsTest"
```

Expected: FAIL because `ProviderId`, `ProviderUsageSnapshot`, `ProviderConnectionState`, and `ProviderRefreshState` do not exist.

- [ ] **Step 3: Add provider models**

Create `android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt`:

```kotlin
package com.aiusage.mobile.local

enum class ProviderId(val storageId: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    CODEX("codex", "Codex"),
    GEMINI("gemini", "Gemini"),
    COPILOT("copilot", "GitHub Copilot"),
    CURSOR("cursor", "Cursor");

    companion object {
        fun defaultOrder(): List<ProviderId> = listOf(CLAUDE, CODEX, GEMINI, COPILOT, CURSOR)

        fun fromStorageId(value: String): ProviderId? {
            return entries.firstOrNull { it.storageId == value.trim().lowercase() }
        }
    }
}

enum class ProviderConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    UNAVAILABLE,
    ERROR
}

enum class ProviderRefreshState {
    IDLE,
    REFRESHING
}

enum class UsageSeverity {
    NORMAL,
    WARNING,
    DANGER,
    UNKNOWN
}

data class ProviderUsageLine(
    val label: String,
    val remainingPercent: Float?,
    val remainingText: String,
    val resetText: String? = null,
    val detailText: String? = null,
    val severity: UsageSeverity = UsageSeverity.UNKNOWN
)

data class ProviderUsageSnapshot(
    val providerId: ProviderId,
    val displayName: String = providerId.displayName,
    val connectionState: ProviderConnectionState,
    val refreshState: ProviderRefreshState,
    val planLabel: String? = null,
    val updatedAt: String = "",
    val lines: List<ProviderUsageLine> = emptyList(),
    val message: String? = null
) {
    companion object {
        fun disconnected(providerId: ProviderId): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.DISCONNECTED,
                refreshState = ProviderRefreshState.IDLE
            )
        }

        fun unavailable(providerId: ProviderId, message: String): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.UNAVAILABLE,
                refreshState = ProviderRefreshState.IDLE,
                message = message
            )
        }
    }
}
```

- [ ] **Step 4: Run model tests and verify they pass**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderModelsTest"
```

Expected: PASS.

- [ ] **Step 5: Write preference codec tests**

Create `android/app/src/test/java/com/aiusage/mobile/local/ProviderPreferencesCodecTest.kt`:

```kotlin
package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPreferencesCodecTest {
    @Test
    fun decodesBlankOrderAsDefaultOrder() {
        assertEquals(ProviderId.defaultOrder(), ProviderPreferencesCodec.decodeOrder(""))
    }

    @Test
    fun decodeOrderDropsUnknownIdsAndAppendsMissingDefaults() {
        val order = ProviderPreferencesCodec.decodeOrder("cursor,unknown,claude")

        assertEquals(
            listOf(
                ProviderId.CURSOR,
                ProviderId.CLAUDE,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            order
        )
    }

    @Test
    fun moveProviderReordersWithinBounds() {
        val moved = ProviderPreferencesCodec.moveProvider(
            order = ProviderId.defaultOrder(),
            providerId = ProviderId.CURSOR,
            targetIndex = 1
        )

        assertEquals(
            listOf(
                ProviderId.CLAUDE,
                ProviderId.CURSOR,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            moved
        )
    }

    @Test
    fun hiddenIdsRoundTripThroughCsv() {
        val hidden = setOf(ProviderId.GEMINI, ProviderId.CURSOR)
        val encoded = ProviderPreferencesCodec.encodeHidden(hidden)

        assertEquals(hidden, ProviderPreferencesCodec.decodeHidden(encoded))
    }

    @Test
    fun visibleProvidersUseOrderAndHiddenState() {
        val visible = ProviderPreferencesCodec.visibleProviders(
            order = ProviderId.defaultOrder(),
            hidden = setOf(ProviderId.CODEX, ProviderId.CURSOR)
        )

        assertFalse(visible.contains(ProviderId.CODEX))
        assertFalse(visible.contains(ProviderId.CURSOR))
        assertTrue(visible.contains(ProviderId.CLAUDE))
        assertEquals(listOf(ProviderId.CLAUDE, ProviderId.GEMINI, ProviderId.COPILOT), visible)
    }
}
```

- [ ] **Step 6: Run codec tests and verify they fail**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.local.ProviderPreferencesCodecTest"
```

Expected: FAIL because `ProviderPreferencesCodec` does not exist.

- [ ] **Step 7: Add preference codec**

Create `android/app/src/main/java/com/aiusage/mobile/local/ProviderPreferencesCodec.kt`:

```kotlin
package com.aiusage.mobile.local

object ProviderPreferencesCodec {
    fun encodeOrder(order: List<ProviderId>): String {
        return order.distinct().joinToString(",") { it.storageId }
    }

    fun decodeOrder(value: String): List<ProviderId> {
        val decoded = value
            .split(",")
            .mapNotNull { ProviderId.fromStorageId(it) }
            .distinct()
        return (decoded + ProviderId.defaultOrder()).distinct()
    }

    fun moveProvider(order: List<ProviderId>, providerId: ProviderId, targetIndex: Int): List<ProviderId> {
        val normalized = (order + ProviderId.defaultOrder()).distinct().toMutableList()
        normalized.remove(providerId)
        normalized.add(targetIndex.coerceIn(0, normalized.size), providerId)
        return normalized
    }

    fun encodeHidden(hidden: Set<ProviderId>): String {
        return hidden.sortedBy { it.storageId }.joinToString(",") { it.storageId }
    }

    fun decodeHidden(value: String): Set<ProviderId> {
        return value
            .split(",")
            .mapNotNull { ProviderId.fromStorageId(it) }
            .toSet()
    }

    fun visibleProviders(order: List<ProviderId>, hidden: Set<ProviderId>): List<ProviderId> {
        return (order + ProviderId.defaultOrder()).distinct().filterNot { hidden.contains(it) }
    }
}
```

- [ ] **Step 8: Add SharedPreferences repository**

Create `android/app/src/main/java/com/aiusage/mobile/local/ProviderPreferencesRepository.kt`:

```kotlin
package com.aiusage.mobile.local

import android.content.Context

class ProviderPreferencesRepository(context: Context) {
    private val preferences = context.getSharedPreferences("ai_usage_provider_preferences", Context.MODE_PRIVATE)

    fun providerOrder(): List<ProviderId> {
        return ProviderPreferencesCodec.decodeOrder(preferences.getString(KEY_PROVIDER_ORDER, "") ?: "")
    }

    fun hiddenProviders(): Set<ProviderId> {
        return ProviderPreferencesCodec.decodeHidden(preferences.getString(KEY_HIDDEN_PROVIDERS, "") ?: "")
    }

    fun visibleProviders(): List<ProviderId> {
        return ProviderPreferencesCodec.visibleProviders(providerOrder(), hiddenProviders())
    }

    fun saveProviderOrder(order: List<ProviderId>) {
        preferences.edit().putString(KEY_PROVIDER_ORDER, ProviderPreferencesCodec.encodeOrder(order)).apply()
    }

    fun moveProvider(providerId: ProviderId, targetIndex: Int) {
        saveProviderOrder(ProviderPreferencesCodec.moveProvider(providerOrder(), providerId, targetIndex))
    }

    fun setProviderHidden(providerId: ProviderId, hidden: Boolean) {
        val next = hiddenProviders().toMutableSet()
        if (hidden) {
            next.add(providerId)
        } else {
            next.remove(providerId)
        }
        preferences.edit().putString(KEY_HIDDEN_PROVIDERS, ProviderPreferencesCodec.encodeHidden(next)).apply()
    }

    fun providerForWidget(appWidgetId: Int): ProviderId? {
        return ProviderId.fromStorageId(preferences.getString("$KEY_WIDGET_PROVIDER_PREFIX$appWidgetId", "") ?: "")
    }

    fun saveProviderForWidget(appWidgetId: Int, providerId: ProviderId) {
        preferences.edit().putString("$KEY_WIDGET_PROVIDER_PREFIX$appWidgetId", providerId.storageId).apply()
    }

    companion object {
        private const val KEY_PROVIDER_ORDER = "provider_order"
        private const val KEY_HIDDEN_PROVIDERS = "hidden_providers"
        private const val KEY_WIDGET_PROVIDER_PREFIX = "provider_widget_"
    }
}
```

- [ ] **Step 9: Run domain tests**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.local.*"
```

Expected: PASS.

- [ ] **Step 10: Commit Task 1**

```powershell
git add -- android/app/src/main/java/com/aiusage/mobile/local/ProviderModels.kt android/app/src/main/java/com/aiusage/mobile/local/ProviderPreferencesCodec.kt android/app/src/main/java/com/aiusage/mobile/local/ProviderPreferencesRepository.kt android/app/src/test/java/com/aiusage/mobile/local/ProviderModelsTest.kt android/app/src/test/java/com/aiusage/mobile/local/ProviderPreferencesCodecTest.kt
git commit -m "Add local provider domain preferences"
```

---

### Task 2: Local Snapshot Storage And Display-Only Cache Sanitizer

**Files:**
- Create: `android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/local/WidgetCacheSanitizer.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/local/WidgetCacheSanitizerTest.kt`
- Modify: `android/app/src/main/java/com/aiusage/mobile/widget/WidgetSnapshotCache.kt`

- [ ] **Step 1: Write sanitizer tests**

Create `android/app/src/test/java/com/aiusage/mobile/local/WidgetCacheSanitizerTest.kt`:

```kotlin
package com.aiusage.mobile.local

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCacheSanitizerTest {
    @Test
    fun exportJsonContainsVisibleProvidersInPreferenceOrder() {
        val snapshots = listOf(
            connectedSnapshot(ProviderId.CURSOR, "Cursor", 0.4f),
            connectedSnapshot(ProviderId.CLAUDE, "Claude", 0.8f),
            connectedSnapshot(ProviderId.GEMINI, "Gemini", 1.0f)
        )

        val json = WidgetCacheSanitizer.toDisplayOnlyJson(
            snapshots = snapshots,
            order = listOf(ProviderId.CLAUDE, ProviderId.CURSOR, ProviderId.GEMINI),
            hidden = setOf(ProviderId.GEMINI),
            updatedAt = "2026-05-15T00:00:00Z"
        )

        val providers = JSONObject(json).getJSONArray("providers")
        assertEquals(2, providers.length())
        assertEquals("claude", providers.getJSONObject(0).getString("providerId"))
        assertEquals("cursor", providers.getJSONObject(1).getString("providerId"))
    }

    @Test
    fun exportJsonDoesNotContainSensitiveKeys() {
        val json = WidgetCacheSanitizer.toDisplayOnlyJson(
            snapshots = listOf(connectedSnapshot(ProviderId.CODEX, "Codex", 0.5f)),
            order = ProviderId.defaultOrder(),
            hidden = emptySet(),
            updatedAt = "2026-05-15T00:00:00Z"
        )

        val lower = json.lowercase()
        assertFalse(lower.contains("cookie"))
        assertFalse(lower.contains("token"))
        assertFalse(lower.contains("rawhtml"))
        assertFalse(lower.contains("credential"))
        assertTrue(lower.contains("providers"))
    }

    private fun connectedSnapshot(providerId: ProviderId, label: String, remaining: Float): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = providerId,
            displayName = label,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            planLabel = "Pro",
            updatedAt = "2026-05-15T00:00:00Z",
            lines = listOf(
                ProviderUsageLine(
                    label = "Session",
                    remainingPercent = remaining,
                    remainingText = "${(remaining * 100).toInt()}% left",
                    resetText = "Resets in 1h"
                )
            )
        )
    }
}
```

- [ ] **Step 2: Run sanitizer tests and verify they fail**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.local.WidgetCacheSanitizerTest"
```

Expected: FAIL because `WidgetCacheSanitizer` does not exist.

- [ ] **Step 3: Add display-only cache sanitizer**

Create `android/app/src/main/java/com/aiusage/mobile/local/WidgetCacheSanitizer.kt`:

```kotlin
package com.aiusage.mobile.local

import org.json.JSONArray
import org.json.JSONObject

object WidgetCacheSanitizer {
    fun toDisplayOnlyJson(
        snapshots: List<ProviderUsageSnapshot>,
        order: List<ProviderId>,
        hidden: Set<ProviderId>,
        updatedAt: String
    ): String {
        val byProvider = snapshots.associateBy { it.providerId }
        val providers = JSONArray()
        ProviderPreferencesCodec.visibleProviders(order, hidden).forEach { providerId ->
            val snapshot = byProvider[providerId] ?: ProviderUsageSnapshot.disconnected(providerId)
            providers.put(snapshot.toDisplayOnlyJson())
        }
        return JSONObject()
            .put("schema", "local-provider-display-v1")
            .put("updatedAt", updatedAt)
            .put("providers", providers)
            .toString()
    }

    private fun ProviderUsageSnapshot.toDisplayOnlyJson(): JSONObject {
        return JSONObject()
            .put("providerId", providerId.storageId)
            .put("displayName", displayName)
            .put("status", connectionState.name.lowercase())
            .put("refreshState", refreshState.name.lowercase())
            .put("plan", planLabel.orEmpty())
            .put("updatedAt", updatedAt)
            .put("message", message.orEmpty())
            .put("visible", true)
            .put("lines", JSONArray().also { array ->
                lines.forEach { line ->
                    array.put(
                        JSONObject()
                            .put("label", line.label)
                            .put("remainingPercent", line.remainingPercent?.toDouble() ?: JSONObject.NULL)
                            .put("remainingText", line.remainingText)
                            .put("resetText", line.resetText.orEmpty())
                            .put("detailText", line.detailText.orEmpty())
                            .put("severity", line.severity.name.lowercase())
                    )
                }
            })
    }
}
```

- [ ] **Step 4: Add local usage repository**

Create `android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt`:

```kotlin
package com.aiusage.mobile.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalUsageRepository(context: Context) {
    private val preferences = context.getSharedPreferences("ai_usage_local_usage", Context.MODE_PRIVATE)

    fun readSnapshots(): List<ProviderUsageSnapshot> {
        val providers = JSONObject(preferences.getString(KEY_SNAPSHOTS, "{}") ?: "{}")
            .optJSONArray("providers") ?: JSONArray()
        return buildList {
            for (index in 0 until providers.length()) {
                val provider = providers.optJSONObject(index) ?: continue
                val providerId = ProviderId.fromStorageId(provider.optString("providerId")) ?: continue
                add(provider.toSnapshot(providerId))
            }
        }
    }

    fun saveSnapshot(snapshot: ProviderUsageSnapshot) {
        val snapshots = readSnapshots().filterNot { it.providerId == snapshot.providerId } + snapshot
        saveSnapshots(snapshots)
    }

    fun saveSnapshots(snapshots: List<ProviderUsageSnapshot>) {
        preferences.edit()
            .putString(KEY_SNAPSHOTS, JSONObject().put("providers", JSONArray().also { array ->
                snapshots.forEach { array.put(it.toJson()) }
            }).toString())
            .apply()
    }

    private fun JSONObject.toSnapshot(providerId: ProviderId): ProviderUsageSnapshot {
        val linesJson = optJSONArray("lines") ?: JSONArray()
        return ProviderUsageSnapshot(
            providerId = providerId,
            displayName = optString("displayName", providerId.displayName),
            connectionState = enumValueOfOrDefault(optString("connectionState"), ProviderConnectionState.DISCONNECTED),
            refreshState = enumValueOfOrDefault(optString("refreshState"), ProviderRefreshState.IDLE),
            planLabel = optString("planLabel").takeIf { it.isNotBlank() },
            updatedAt = optString("updatedAt"),
            message = optString("message").takeIf { it.isNotBlank() },
            lines = buildList {
                for (index in 0 until linesJson.length()) {
                    val line = linesJson.optJSONObject(index) ?: continue
                    add(
                        ProviderUsageLine(
                            label = line.optString("label"),
                            remainingPercent = line.opt("remainingPercent")?.toString()?.toFloatOrNull(),
                            remainingText = line.optString("remainingText"),
                            resetText = line.optString("resetText").takeIf { it.isNotBlank() },
                            detailText = line.optString("detailText").takeIf { it.isNotBlank() },
                            severity = enumValueOfOrDefault(line.optString("severity"), UsageSeverity.UNKNOWN)
                        )
                    )
                }
            }
        )
    }

    private fun ProviderUsageSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("providerId", providerId.storageId)
            .put("displayName", displayName)
            .put("connectionState", connectionState.name)
            .put("refreshState", refreshState.name)
            .put("planLabel", planLabel.orEmpty())
            .put("updatedAt", updatedAt)
            .put("message", message.orEmpty())
            .put("lines", JSONArray().also { array ->
                lines.forEach { line ->
                    array.put(
                        JSONObject()
                            .put("label", line.label)
                            .put("remainingPercent", line.remainingPercent?.toDouble() ?: JSONObject.NULL)
                            .put("remainingText", line.remainingText)
                            .put("resetText", line.resetText.orEmpty())
                            .put("detailText", line.detailText.orEmpty())
                            .put("severity", line.severity.name)
                    )
                }
            })
    }

    private inline fun <reified T : Enum<T>> enumValueOfOrDefault(value: String, default: T): T {
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }

    companion object {
        private const val KEY_SNAPSHOTS = "provider_snapshots"
    }
}
```

- [ ] **Step 5: Extend WidgetSnapshotCache for local display cache**

Modify `android/app/src/main/java/com/aiusage/mobile/widget/WidgetSnapshotCache.kt` by adding these methods inside `WidgetSnapshotCache`:

```kotlin
    fun writeLocalDisplaySnapshot(snapshotJson: String, updatedAt: String = "") {
        write(
            snapshotJson = snapshotJson,
            status = "LocalProviders",
            deviceName = "This device",
            updatedAt = updatedAt
        )
    }
```

- [ ] **Step 6: Run local tests**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.local.*"
```

Expected: PASS.

- [ ] **Step 7: Commit Task 2**

```powershell
git add -- android/app/src/main/java/com/aiusage/mobile/local/LocalUsageRepository.kt android/app/src/main/java/com/aiusage/mobile/local/WidgetCacheSanitizer.kt android/app/src/main/java/com/aiusage/mobile/widget/WidgetSnapshotCache.kt android/app/src/test/java/com/aiusage/mobile/local/WidgetCacheSanitizerTest.kt
git commit -m "Add local display snapshot cache"
```

---

### Task 3: Connector Registry, Host Allowlists, And Text Extraction

**Files:**
- Create: `android/app/src/main/java/com/aiusage/mobile/providers/ProviderConnector.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/providers/ProviderConnectorRegistry.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/providers/ProviderHostAllowlist.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/providers/TextUsageExtractor.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/providers/ProviderConnectorRegistryTest.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/providers/ProviderHostAllowlistTest.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/providers/TextUsageExtractorTest.kt`

- [ ] **Step 1: Write registry and allowlist tests**

Create `android/app/src/test/java/com/aiusage/mobile/providers/ProviderConnectorRegistryTest.kt`:

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProviderConnectorRegistryTest {
    @Test
    fun registryHasOneConnectorPerDefaultProvider() {
        val registry = ProviderConnectorRegistry.default()

        assertEquals(ProviderId.defaultOrder(), registry.connectors.map { it.providerId })
        ProviderId.defaultOrder().forEach { providerId ->
            assertNotNull(registry.connectorFor(providerId))
        }
    }
}
```

Create `android/app/src/test/java/com/aiusage/mobile/providers/ProviderHostAllowlistTest.kt`:

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHostAllowlistTest {
    @Test
    fun allowsExpectedProviderHosts() {
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://claude.ai/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://chatgpt.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.GEMINI, "https://gemini.google.com/app"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.COPILOT, "https://github.com/settings/copilot"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://cursor.com/dashboard"))
    }

    @Test
    fun blocksUnexpectedHosts() {
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://example.com/"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://evil.example/chatgpt.com"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.GEMINI, "file:///android_asset/local.html"))
    }
}
```

- [ ] **Step 2: Run registry tests and verify they fail**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.ProviderConnectorRegistryTest" --tests "com.aiusage.mobile.providers.ProviderHostAllowlistTest"
```

Expected: FAIL because provider connector classes do not exist.

- [ ] **Step 3: Add connector interface, allowlist, and registry**

Create `android/app/src/main/java/com/aiusage/mobile/providers/ProviderConnector.kt`:

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderUsageSnapshot

interface ProviderConnector {
    val providerId: ProviderId
    val startUrl: String
    suspend fun connectionState(): ProviderConnectorState
    suspend fun refreshUsage(visibleText: String): ProviderUsageSnapshot
    suspend fun disconnect()
}

enum class ProviderConnectorState {
    DISCONNECTED,
    CONNECTED,
    UNAVAILABLE
}

class WebTextProviderConnector(
    override val providerId: ProviderId,
    override val startUrl: String,
    private val extractor: TextUsageExtractor = TextUsageExtractor()
) : ProviderConnector {
    override suspend fun connectionState(): ProviderConnectorState {
        return ProviderConnectorState.DISCONNECTED
    }

    override suspend fun refreshUsage(visibleText: String): ProviderUsageSnapshot {
        return extractor.extract(providerId, visibleText)
    }

    override suspend fun disconnect() {
    }
}
```

Create `android/app/src/main/java/com/aiusage/mobile/providers/ProviderHostAllowlist.kt`:

```kotlin
package com.aiusage.mobile.providers

import android.net.Uri
import com.aiusage.mobile.local.ProviderId

object ProviderHostAllowlist {
    private val allowedHosts = mapOf(
        ProviderId.CLAUDE to setOf("claude.ai", "www.claude.ai"),
        ProviderId.CODEX to setOf("chatgpt.com", "auth.openai.com", "platform.openai.com"),
        ProviderId.GEMINI to setOf("gemini.google.com", "accounts.google.com"),
        ProviderId.COPILOT to setOf("github.com", "githubcopilot.com"),
        ProviderId.CURSOR to setOf("cursor.com", "www.cursor.com")
    )

    fun isAllowed(providerId: ProviderId, url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return allowedHosts.getValue(providerId).any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }
}
```

Create `android/app/src/main/java/com/aiusage/mobile/providers/ProviderConnectorRegistry.kt`:

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId

class ProviderConnectorRegistry(val connectors: List<ProviderConnector>) {
    fun connectorFor(providerId: ProviderId): ProviderConnector? {
        return connectors.firstOrNull { it.providerId == providerId }
    }

    companion object {
        fun default(): ProviderConnectorRegistry {
            return ProviderConnectorRegistry(
                listOf(
                    WebTextProviderConnector(ProviderId.CLAUDE, "https://claude.ai/"),
                    WebTextProviderConnector(ProviderId.CODEX, "https://chatgpt.com/"),
                    WebTextProviderConnector(ProviderId.GEMINI, "https://gemini.google.com/app"),
                    WebTextProviderConnector(ProviderId.COPILOT, "https://github.com/settings/copilot"),
                    WebTextProviderConnector(ProviderId.CURSOR, "https://cursor.com/dashboard")
                )
            )
        }
    }
}
```

- [ ] **Step 4: Write extraction tests**

Create `android/app/src/test/java/com/aiusage/mobile/providers/TextUsageExtractorTest.kt`:

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUsageExtractorTest {
    @Test
    fun extractsPercentRemainingText() {
        val snapshot = TextUsageExtractor().extract(
            ProviderId.CLAUDE,
            "Claude Pro Session 80% left Resets in 2h 10m"
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Claude", snapshot.displayName)
        assertEquals(1, snapshot.lines.size)
        assertEquals(0.8f, snapshot.lines.single().remainingPercent)
        assertEquals("80% left", snapshot.lines.single().remainingText)
        assertEquals("Resets in 2h 10m", snapshot.lines.single().resetText)
    }

    @Test
    fun unavailableWhenNoLimitTextIsVisible() {
        val snapshot = TextUsageExtractor().extract(ProviderId.CURSOR, "Account settings Billing Profile")

        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
        assertEquals("No subscription usage limit text was visible.", snapshot.message)
    }
}
```

- [ ] **Step 5: Add text usage extractor**

Create `android/app/src/main/java/com/aiusage/mobile/providers/TextUsageExtractor.kt`:

```kotlin
package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.local.UsageSeverity

class TextUsageExtractor {
    fun extract(providerId: ProviderId, visibleText: String): ProviderUsageSnapshot {
        val normalized = visibleText.replace(Regex("\\s+"), " ").trim()
        val percentMatch = PERCENT_LEFT.find(normalized)
        if (percentMatch == null) {
            return ProviderUsageSnapshot.unavailable(
                providerId = providerId,
                message = "No subscription usage limit text was visible."
            )
        }
        val percent = percentMatch.groupValues[1].toFloat().coerceIn(0f, 100f)
        val resetText = RESET_TEXT.find(normalized)?.value
        val ratio = percent / 100f
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            lines = listOf(
                ProviderUsageLine(
                    label = "Usage",
                    remainingPercent = ratio,
                    remainingText = "${percent.toInt()}% left",
                    resetText = resetText,
                    severity = when {
                        ratio < 0.15f -> UsageSeverity.DANGER
                        ratio < 0.35f -> UsageSeverity.WARNING
                        else -> UsageSeverity.NORMAL
                    }
                )
            )
        )
    }

    companion object {
        private val PERCENT_LEFT = Regex("""(?i)\b(\d{1,3})\s*%\s*(left|remaining)\b""")
        private val RESET_TEXT = Regex("""(?i)Resets?\s+in\s+\d+\s*[dhm](\s+\d+\s*[hm])?""")
    }
}
```

- [ ] **Step 6: Run provider tests**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.providers.*"
```

Expected: PASS.

- [ ] **Step 7: Commit Task 3**

```powershell
git add -- android/app/src/main/java/com/aiusage/mobile/providers android/app/src/test/java/com/aiusage/mobile/providers
git commit -m "Add local provider connector registry"
```

---

### Task 4: Local-First App Shell, Dashboard, Provider Screens, And Legacy Boundary

**Files:**
- Create: `android/app/src/main/java/com/aiusage/mobile/ui/AppRoute.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/ui/dashboard/ProviderCardOrder.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/ui/dashboard/UnifiedDashboardScreen.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/ui/provider/ProviderDetailScreen.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/ui/settings/LegacyWindowsSyncPanel.kt`
- Modify: `android/app/src/main/java/com/aiusage/mobile/MainActivity.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/ui/dashboard/ProviderCardOrderTest.kt`

- [ ] **Step 1: Write provider card order tests**

Create `android/app/src/test/java/com/aiusage/mobile/ui/dashboard/ProviderCardOrderTest.kt`:

```kotlin
package com.aiusage.mobile.ui.dashboard

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderCardOrderTest {
    @Test
    fun moveCardPlacesProviderAtTargetIndex() {
        val next = ProviderCardOrder.move(
            current = ProviderId.defaultOrder(),
            providerId = ProviderId.CURSOR,
            targetIndex = 0
        )

        assertEquals(
            listOf(
                ProviderId.CURSOR,
                ProviderId.CLAUDE,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            next
        )
    }

    @Test
    fun moveCardKeepsMissingProvidersAtEnd() {
        val next = ProviderCardOrder.move(
            current = listOf(ProviderId.CLAUDE, ProviderId.CURSOR),
            providerId = ProviderId.CURSOR,
            targetIndex = 1
        )

        assertEquals(
            listOf(
                ProviderId.CLAUDE,
                ProviderId.CURSOR,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            next
        )
    }
}
```

- [ ] **Step 2: Run provider card order tests and verify they fail**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.ui.dashboard.ProviderCardOrderTest"
```

Expected: FAIL because `ProviderCardOrder` does not exist.

- [ ] **Step 3: Add route and card order helper**

Create `android/app/src/main/java/com/aiusage/mobile/ui/AppRoute.kt`:

```kotlin
package com.aiusage.mobile.ui

import com.aiusage.mobile.local.ProviderId

sealed class AppRoute {
    data object Home : AppRoute()
    data object Settings : AppRoute()
    data class ProviderDetail(val providerId: ProviderId) : AppRoute()
}
```

Create `android/app/src/main/java/com/aiusage/mobile/ui/dashboard/ProviderCardOrder.kt`:

```kotlin
package com.aiusage.mobile.ui.dashboard

import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesCodec

object ProviderCardOrder {
    fun move(current: List<ProviderId>, providerId: ProviderId, targetIndex: Int): List<ProviderId> {
        return ProviderPreferencesCodec.moveProvider(
            order = (current + ProviderId.defaultOrder()).distinct(),
            providerId = providerId,
            targetIndex = targetIndex
        )
    }
}
```

- [ ] **Step 4: Run provider card order tests**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.ui.dashboard.ProviderCardOrderTest"
```

Expected: PASS.

- [ ] **Step 5: Add local-first dashboard composable**

Create `android/app/src/main/java/com/aiusage/mobile/ui/dashboard/UnifiedDashboardScreen.kt`:

```kotlin
package com.aiusage.mobile.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderUsageSnapshot

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedDashboardScreen(
    snapshots: List<ProviderUsageSnapshot>,
    visibleProviders: List<ProviderId>,
    onOpenProvider: (ProviderId) -> Unit,
    onConnectProvider: (ProviderId) -> Unit,
    onHideProvider: (ProviderId) -> Unit,
    onMoveProvider: (ProviderId, Int) -> Unit
) {
    val byProvider = snapshots.associateBy { it.providerId }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Usage Limits", style = MaterialTheme.typography.titleLarge)
        visibleProviders.forEachIndexed { index, providerId ->
            val snapshot = byProvider[providerId] ?: ProviderUsageSnapshot.disconnected(providerId)
            var menuOpen by remember(providerId) { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenProvider(providerId) },
                        onLongClick = { menuOpen = true }
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(snapshot.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(snapshot.connectionState.name.lowercase())
                    }
                    if (snapshot.connectionState == ProviderConnectionState.DISCONNECTED) {
                        Button(onClick = { onConnectProvider(providerId) }) {
                            Text("Connect")
                        }
                    } else {
                        snapshot.lines.take(3).forEach { line ->
                            Text("${line.label}: ${line.remainingText}")
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Move up") }, onClick = {
                            menuOpen = false
                            onMoveProvider(providerId, index - 1)
                        })
                        DropdownMenuItem(text = { Text("Move down") }, onClick = {
                            menuOpen = false
                            onMoveProvider(providerId, index + 1)
                        })
                        DropdownMenuItem(text = { Text("Hide") }, onClick = {
                            menuOpen = false
                            onHideProvider(providerId)
                        })
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Add provider detail screen**

Create `android/app/src/main/java/com/aiusage/mobile/ui/provider/ProviderDetailScreen.kt`:

```kotlin
package com.aiusage.mobile.ui.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderUsageSnapshot

@Composable
fun ProviderDetailScreen(
    providerId: ProviderId,
    snapshot: ProviderUsageSnapshot,
    onConnect: (ProviderId) -> Unit,
    onRefresh: (ProviderId) -> Unit,
    onDisconnect: (ProviderId) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(providerId.displayName, style = MaterialTheme.typography.headlineSmall)
        Text(snapshot.connectionState.name.lowercase())
        snapshot.message?.takeIf { it.isNotBlank() }?.let { Text(it) }
        if (snapshot.connectionState == ProviderConnectionState.DISCONNECTED) {
            Button(onClick = { onConnect(providerId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Connect ${providerId.displayName}")
            }
        } else {
            Button(onClick = { onRefresh(providerId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh")
            }
            Button(onClick = { onDisconnect(providerId) }, modifier = Modifier.fillMaxWidth()) {
                Text("Disconnect")
            }
        }
        snapshot.lines.forEach { line ->
            Text("${line.label}: ${line.remainingText} ${line.resetText.orEmpty()}")
        }
    }
}
```

- [ ] **Step 7: Add local app shell**

Create `android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`:

```kotlin
package com.aiusage.mobile.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesRepository
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.providers.ProviderConnectorRegistry
import com.aiusage.mobile.ui.dashboard.UnifiedDashboardScreen
import com.aiusage.mobile.ui.provider.ProviderDetailScreen

@Composable
fun AIUsageAppShell(
    context: Context,
    preferencesRepository: ProviderPreferencesRepository = ProviderPreferencesRepository(context),
    usageRepository: LocalUsageRepository = LocalUsageRepository(context),
    connectorRegistry: ProviderConnectorRegistry = ProviderConnectorRegistry.default()
) {
    var route by remember { mutableStateOf<AppRoute>(AppRoute.Home) }
    var order by remember { mutableStateOf(preferencesRepository.providerOrder()) }
    var hidden by remember { mutableStateOf(preferencesRepository.hiddenProviders()) }
    var snapshots by remember { mutableStateOf(usageRepository.readSnapshots()) }

    fun visibleProviders(): List<ProviderId> {
        return com.aiusage.mobile.local.ProviderPreferencesCodec.visibleProviders(order, hidden)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Button(onClick = { route = AppRoute.Home }) { Text("Home") }
            ProviderId.defaultOrder().forEach { providerId ->
                Button(onClick = { route = AppRoute.ProviderDetail(providerId) }) {
                    Text(providerId.displayName)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { route = AppRoute.Settings }) { Text("Settings") }
        }
        when (val current = route) {
            AppRoute.Home -> UnifiedDashboardScreen(
                snapshots = snapshots,
                visibleProviders = visibleProviders(),
                onOpenProvider = { route = AppRoute.ProviderDetail(it) },
                onConnectProvider = { providerId -> route = AppRoute.ProviderDetail(providerId) },
                onHideProvider = { providerId ->
                    preferencesRepository.setProviderHidden(providerId, true)
                    hidden = preferencesRepository.hiddenProviders()
                },
                onMoveProvider = { providerId, targetIndex ->
                    preferencesRepository.moveProvider(providerId, targetIndex)
                    order = preferencesRepository.providerOrder()
                }
            )
            is AppRoute.ProviderDetail -> {
                val snapshot = snapshots.firstOrNull { it.providerId == current.providerId }
                    ?: ProviderUsageSnapshot.disconnected(current.providerId)
                ProviderDetailScreen(
                    providerId = current.providerId,
                    snapshot = snapshot,
                    onConnect = { providerId ->
                        connectorRegistry.connectorFor(providerId)
                    },
                    onRefresh = { providerId ->
                        connectorRegistry.connectorFor(providerId)
                    },
                    onDisconnect = { providerId ->
                        usageRepository.saveSnapshot(ProviderUsageSnapshot.disconnected(providerId))
                        snapshots = usageRepository.readSnapshots()
                    }
                )
            }
            AppRoute.Settings -> Text("Legacy Windows Sync")
        }
    }
}
```

- [ ] **Step 8: Wire MainActivity to local shell**

Modify `android/app/src/main/java/com/aiusage/mobile/MainActivity.kt`:

```kotlin
setContent {
    MaterialTheme {
        AIUsageAppShell(context = this)
    }
}
```

Keep the current legacy code in the file or move it to `LegacyWindowsSyncPanel.kt`; do not delete it in this task.

- [ ] **Step 9: Run app shell build**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 10: Commit Task 4**

```powershell
git add -- android/app/src/main/java/com/aiusage/mobile/MainActivity.kt android/app/src/main/java/com/aiusage/mobile/ui android/app/src/test/java/com/aiusage/mobile/ui
git commit -m "Add local first provider dashboard"
```

---

### Task 5: Web Login Activity And Provider Refresh Wiring

**Files:**
- Create: `android/app/src/main/java/com/aiusage/mobile/providers/WebLoginActivity.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/aiusage/mobile/ui/provider/ProviderDetailScreen.kt`
- Modify: `android/app/src/main/java/com/aiusage/mobile/ui/AIUsageAppShell.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Add WebLoginActivity**

Create `android/app/src/main/java/com/aiusage/mobile/providers/WebLoginActivity.kt`:

```kotlin
package com.aiusage.mobile.providers

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aiusage.mobile.local.ProviderId

class WebLoginActivity : Activity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerId = ProviderId.fromStorageId(intent.getStringExtra(EXTRA_PROVIDER_ID).orEmpty())
            ?: run {
                finish()
                return
            }
        val startUrl = intent.getStringExtra(EXTRA_START_URL).orEmpty()
        if (!ProviderHostAllowlist.isAllowed(providerId, startUrl)) {
            finish()
            return
        }
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return if (ProviderHostAllowlist.isAllowed(providerId, url)) {
                    false
                } else {
                    true
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(startUrl)
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "provider_id"
        const val EXTRA_START_URL = "start_url"
    }
}
```

- [ ] **Step 2: Register WebLoginActivity**

Add this activity inside the `<application>` block of `android/app/src/main/AndroidManifest.xml`:

```xml
    <activity
      android:name=".providers.WebLoginActivity"
      android:exported="false" />
```

- [ ] **Step 3: Add provider strings**

Add to `android/app/src/main/res/values/strings.xml`:

```xml
  <string name="provider_connect">Connect</string>
  <string name="provider_refresh">Refresh</string>
  <string name="provider_disconnect">Disconnect</string>
  <string name="provider_unavailable">Usage limits are not visible for this provider right now.</string>
```

Add to `android/app/src/main/res/values-ko/strings.xml`:

```xml
  <string name="provider_connect">연결</string>
  <string name="provider_refresh">새로고침</string>
  <string name="provider_disconnect">연결 해제</string>
  <string name="provider_unavailable">현재 이 provider의 사용 한도를 볼 수 없습니다.</string>
```

- [ ] **Step 4: Wire connect action to WebLoginActivity**

In `AIUsageAppShell.kt`, replace the `onConnect` body with:

```kotlin
onConnect = { providerId ->
    val connector = connectorRegistry.connectorFor(providerId) ?: return@ProviderDetailScreen
    val intent = android.content.Intent(context, WebLoginActivity::class.java)
        .putExtra(WebLoginActivity.EXTRA_PROVIDER_ID, providerId.storageId)
        .putExtra(WebLoginActivity.EXTRA_START_URL, connector.startUrl)
    context.startActivity(intent)
}
```

- [ ] **Step 5: Run debug build**

Run:

```powershell
.\gradlew.bat -p android :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 6: Commit Task 5**

```powershell
git add -- android/app/src/main/java/com/aiusage/mobile/providers/WebLoginActivity.kt android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/aiusage/mobile/ui android/app/src/main/res/values/strings.xml android/app/src/main/res/values-ko/strings.xml
git commit -m "Add provider web login shell"
```

---

### Task 6: Unified Widget Payload, Provider Widget Payload, And Adaptive Layout Specs

**Files:**
- Modify: `android/app/src/main/java/com/aiusage/mobile/widget/WidgetGaugeParser.kt`
- Modify: `android/app/src/main/java/com/aiusage/mobile/widget/WidgetGaugeLayout.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/widget/UnifiedWidgetPayloadTest.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/widget/ProviderWidgetPayloadTest.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/widget/WidgetLayoutSpecTest.kt`

- [ ] **Step 1: Write unified and provider widget tests**

Create `android/app/src/test/java/com/aiusage/mobile/widget/UnifiedWidgetPayloadTest.kt`:

```kotlin
package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedWidgetPayloadTest {
    @Test
    fun unifiedWidgetReadsProvidersInPayloadOrderAndSkipsHidden() {
        val json = """
            {
              "schema":"local-provider-display-v1",
              "providers":[
                {"providerId":"claude","visible":true,"lines":[{"remainingPercent":0.8,"remainingText":"80% left","resetText":"Resets in 1h"}]},
                {"providerId":"codex","visible":false,"lines":[{"remainingPercent":0.7,"remainingText":"70% left"}]},
                {"providerId":"cursor","visible":true,"lines":[{"remainingPercent":0.2,"remainingText":"20% left"}]}
              ]
            }
        """.trimIndent()

        val gauges = parseWidgetProviderGauges(json)

        assertEquals(listOf("claude", "cursor"), gauges.map { it.providerId })
        assertEquals("80% left", gauges.first().remainingText)
    }
}
```

Create `android/app/src/test/java/com/aiusage/mobile/widget/ProviderWidgetPayloadTest.kt`:

```kotlin
package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderWidgetPayloadTest {
    @Test
    fun providerWidgetExtractsOnlySelectedProviderLines() {
        val json = """
            {
              "providers":[
                {"providerId":"claude","displayName":"Claude","lines":[{"label":"Session","remainingPercent":0.8,"remainingText":"80% left","resetText":"Resets in 1h"}]},
                {"providerId":"cursor","displayName":"Cursor","lines":[{"label":"Fast requests","remainingPercent":0.4,"remainingText":"40% left"}]}
              ]
            }
        """.trimIndent()

        val payload = parseProviderWidgetPayload(json, "cursor")

        assertEquals("cursor", payload.providerId)
        assertEquals("Cursor", payload.displayName)
        assertEquals(1, payload.lines.size)
        assertEquals("Fast requests", payload.lines.single().label)
    }
}
```

Create `android/app/src/test/java/com/aiusage/mobile/widget/WidgetLayoutSpecTest.kt`:

```kotlin
package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutSpecTest {
    @Test
    fun unifiedWidgetSupportsPhoneAndTabletWidths() {
        assertEquals(2, unifiedDashboardWidgetLayoutSpec(widthDp = 160, heightDp = 160).columns)
        assertEquals(3, unifiedDashboardWidgetLayoutSpec(widthDp = 300, heightDp = 280).columns)
        assertTrue(unifiedDashboardWidgetLayoutSpec(widthDp = 720, heightDp = 360).gaugeWidthDp >= 500)
    }

    @Test
    fun providerWidgetSupportsCompactAndExpandedSizes() {
        assertEquals(1, providerWidgetLayoutSpec(widthDp = 160, heightDp = 80).visibleLineCount)
        assertEquals(3, providerWidgetLayoutSpec(widthDp = 300, heightDp = 300).visibleLineCount)
        assertTrue(providerWidgetLayoutSpec(widthDp = 480, heightDp = 300).gaugeWidthDp >= 300)
    }
}
```

- [ ] **Step 2: Run widget tests and verify they fail**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.widget.UnifiedWidgetPayloadTest" --tests "com.aiusage.mobile.widget.ProviderWidgetPayloadTest" --tests "com.aiusage.mobile.widget.WidgetLayoutSpecTest"
```

Expected: FAIL because provider widget payload and layout spec APIs do not exist.

- [ ] **Step 3: Extend parser with local percent field and provider payload**

Modify `WidgetGaugeParser.kt`:

```kotlin
data class WidgetProviderLine(
    val label: String,
    val remainingRatio: Float?,
    val remainingText: String,
    val resetText: String?,
    val detailText: String?
)

data class ProviderWidgetPayload(
    val providerId: String,
    val displayName: String,
    val status: String,
    val lines: List<WidgetProviderLine>
)

fun parseProviderWidgetPayload(snapshotJson: String, providerId: String): ProviderWidgetPayload {
    val providers = JSONObject(snapshotJson).optJSONArray("providers") ?: JSONArray()
    for (index in 0 until providers.length()) {
        val provider = providers.optJSONObject(index) ?: continue
        if (provider.optString("providerId") != providerId) continue
        val linesJson = provider.optJSONArray("lines") ?: JSONArray()
        val lines = buildList {
            for (lineIndex in 0 until linesJson.length()) {
                val line = linesJson.optJSONObject(lineIndex) ?: continue
                val percent = line.optNullableDouble("remainingPercent")?.toFloat()
                add(
                    WidgetProviderLine(
                        label = line.optString("label", "Usage"),
                        remainingRatio = percent?.coerceIn(0f, 1f),
                        remainingText = line.optString("remainingText"),
                        resetText = line.optString("resetText").takeIf { it.isNotBlank() },
                        detailText = line.optString("detailText").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        return ProviderWidgetPayload(
            providerId = providerId,
            displayName = provider.optString("displayName", providerId),
            status = provider.optString("status", "disconnected"),
            lines = lines
        )
    }
    return ProviderWidgetPayload(providerId, providerId, "disconnected", emptyList())
}
```

Also update `firstGaugeableLine` to read `remainingPercent` before `remaining/limit`:

```kotlin
        line.optNullableDouble("remainingPercent")?.let { percent ->
            val ratio = percent.coerceIn(0.0, 1.0)
            return ParsedGaugeLine(
                ratio = ratio.toFloat(),
                remainingText = line.optString("remainingText").ifBlank { "${formatNumber(ratio * 100)}% left" },
                resetText = line.optString("resetText").takeIf { it.isNotBlank() }
            )
        }
```

- [ ] **Step 4: Extend widget layout specs**

Modify `WidgetGaugeLayout.kt`:

```kotlin
data class UnifiedDashboardWidgetLayoutSpec(
    val columns: Int,
    val visibleProviderCount: Int,
    val gaugeWidthDp: Int,
    val rowHeightDp: Int,
    val fontSizeSp: Int
)

data class ProviderWidgetLayoutSpec(
    val visibleLineCount: Int,
    val gaugeWidthDp: Int,
    val rowHeightDp: Int,
    val fontSizeSp: Int
)

fun unifiedDashboardWidgetLayoutSpec(widthDp: Int, heightDp: Int): UnifiedDashboardWidgetLayoutSpec {
    val columns = if (widthDp >= 280) 3 else 2
    val visibleCount = when {
        heightDp >= 300 -> 5
        heightDp >= 220 -> 4
        else -> 3
    }
    val gaugeWidth = (widthDp - 48).coerceAtLeast(120)
    return UnifiedDashboardWidgetLayoutSpec(
        columns = columns,
        visibleProviderCount = visibleCount,
        gaugeWidthDp = gaugeWidth,
        rowHeightDp = if (heightDp >= 300) 54 else 42,
        fontSizeSp = if (widthDp >= 300) 13 else 11
    )
}

fun providerWidgetLayoutSpec(widthDp: Int, heightDp: Int): ProviderWidgetLayoutSpec {
    val visibleLineCount = when {
        heightDp >= 260 -> 3
        heightDp >= 150 -> 2
        else -> 1
    }
    return ProviderWidgetLayoutSpec(
        visibleLineCount = visibleLineCount,
        gaugeWidthDp = (widthDp - 56).coerceAtLeast(104),
        rowHeightDp = if (heightDp >= 260) 60 else 44,
        fontSizeSp = if (widthDp >= 280) 13 else 11
    )
}
```

- [ ] **Step 5: Run widget tests**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.widget.*"
```

Expected: PASS.

- [ ] **Step 6: Commit Task 6**

```powershell
git add -- android/app/src/main/java/com/aiusage/mobile/widget/WidgetGaugeParser.kt android/app/src/main/java/com/aiusage/mobile/widget/WidgetGaugeLayout.kt android/app/src/test/java/com/aiusage/mobile/widget
git commit -m "Add local widget payload layout specs"
```

---

### Task 7: Unified Dashboard Widget And Individual Provider Widget Families

**Files:**
- Modify: `android/app/src/main/java/com/aiusage/mobile/widget/AIUsageGlanceWidget.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/widget/ProviderUsageGlanceWidget.kt`
- Create: `android/app/src/main/java/com/aiusage/mobile/widget/ProviderWidgetConfigureActivity.kt`
- Create: `android/app/src/main/res/xml/ai_usage_widget_provider.xml`
- Modify: `android/app/src/main/res/xml/ai_usage_widget_large.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Rename existing large widget semantics to unified dashboard**

Modify `AIUsageGlanceWidget.kt`:

```kotlin
class AIUsageUnifiedGlanceWidget : AIUsageGlanceWidget()

class AIUsageUnifiedGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIUsageUnifiedGlanceWidget()
}
```

Remove Firebase sign-in gating in `provideGlance` and always read `WidgetSnapshotCache(context).read()`. If no gauges exist, show a local disconnected state that opens the app.

- [ ] **Step 2: Add provider widget file**

Create `android/app/src/main/java/com/aiusage/mobile/widget/ProviderUsageGlanceWidget.kt`:

```kotlin
package com.aiusage.mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesRepository

class ProviderUsageGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 80.dp),
            DpSize(160.dp, 160.dp),
            DpSize(240.dp, 160.dp),
            DpSize(300.dp, 300.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshotJson = WidgetSnapshotCache(context).read()
        val providerId = ProviderPreferencesRepository(context).providerForWidget(id.hashCode()) ?: ProviderId.CLAUDE
        val payload = parseProviderWidgetPayload(snapshotJson, providerId.storageId)
        provideContent {
            ProviderWidgetContent(payload)
        }
    }
}

@Composable
private fun ProviderWidgetContent(payload: ProviderWidgetPayload) {
    val spec = providerWidgetLayoutSpec(
        widthDp = LocalSize.current.width.value.toInt(),
        heightDp = LocalSize.current.height.value.toInt()
    )
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(payload.displayName)
        payload.lines.take(spec.visibleLineCount).forEach { line ->
            Text("${line.label}: ${line.remainingText}")
        }
        if (payload.lines.isEmpty()) {
            Text("Connect ${payload.displayName}")
        }
    }
}

class ProviderUsageGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProviderUsageGlanceWidget()
}
```

- [ ] **Step 3: Add provider widget XML**

Create `android/app/src/main/res/xml/ai_usage_widget_provider.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
  android:minWidth="110dp"
  android:minHeight="56dp"
  android:targetCellWidth="2"
  android:targetCellHeight="1"
  android:maxResizeWidth="360dp"
  android:maxResizeHeight="360dp"
  android:resizeMode="horizontal|vertical"
  android:updatePeriodMillis="0"
  android:widgetCategory="home_screen" />
```

- [ ] **Step 4: Update manifest receivers**

In `AndroidManifest.xml`, change the large receiver class to:

```xml
    <receiver
      android:name=".widget.AIUsageUnifiedGlanceWidgetReceiver"
      android:label="@string/widget_label_unified"
      android:exported="false">
      <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
      </intent-filter>
      <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/ai_usage_widget_large" />
    </receiver>
```

Replace the circular receiver manifest entry with:

```xml
    <receiver
      android:name=".widget.ProviderUsageGlanceWidgetReceiver"
      android:label="@string/widget_label_provider"
      android:exported="false">
      <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
      </intent-filter>
      <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/ai_usage_widget_provider" />
    </receiver>
```

- [ ] **Step 5: Add widget labels**

Add to `android/app/src/main/res/values/strings.xml`:

```xml
  <string name="widget_label_unified">AI Usage dashboard</string>
  <string name="widget_label_provider">AI Usage provider</string>
```

Add to `android/app/src/main/res/values-ko/strings.xml`:

```xml
  <string name="widget_label_unified">AI Usage 통합 대시보드</string>
  <string name="widget_label_provider">AI Usage provider</string>
```

- [ ] **Step 6: Run widget build**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 7: Commit Task 7**

```powershell
git add -- android/app/src/main/java/com/aiusage/mobile/widget android/app/src/main/res/xml android/app/src/main/AndroidManifest.xml android/app/src/main/res/values/strings.xml android/app/src/main/res/values-ko/strings.xml
git commit -m "Add unified and provider widgets"
```

---

### Task 8: Notification And Widget Tap Routing

**Files:**
- Modify: `android/app/src/main/java/com/aiusage/mobile/notification/UsageNotificationContent.kt`
- Modify: `android/app/src/main/java/com/aiusage/mobile/widget/AIUsageGlanceWidget.kt`
- Modify: `android/app/src/main/java/com/aiusage/mobile/widget/ProviderUsageGlanceWidget.kt`
- Modify: `android/app/src/main/java/com/aiusage/mobile/MainActivity.kt`
- Test: `android/app/src/test/java/com/aiusage/mobile/notification/UsageNotificationContentTest.kt`

- [ ] **Step 1: Add notification ordering test**

Append to `UsageNotificationContentTest.kt`:

```kotlin
    @Test
    fun localDisplayPayloadKeepsProviderOrderAndSkipsHiddenProviders() {
        val json = """
            {
              "providers": [
                {"providerId":"cursor","visible":true,"lines":[{"remainingPercent":0.4,"remainingText":"40% left"}]},
                {"providerId":"codex","visible":false,"lines":[{"remainingPercent":0.7,"remainingText":"70% left"}]},
                {"providerId":"claude","visible":true,"lines":[{"remainingPercent":0.8,"remainingText":"80% left"}]}
              ]
            }
        """.trimIndent()

        val content = buildUsageNotificationContent(json)

        assertEquals(listOf("cursor", "claude"), content.gauges.map { it.providerId })
        assertEquals("Cursor 40 | Claude 80", content.summary)
    }
```

- [ ] **Step 2: Run notification test**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest --tests "com.aiusage.mobile.notification.UsageNotificationContentTest"
```

Expected: PASS after Task 6 parser changes.

- [ ] **Step 3: Add route extras in MainActivity**

In `MainActivity.kt`, read widget/provider route extras before rendering:

```kotlin
val initialProviderId = ProviderId.fromStorageId(intent.getStringExtra("provider_id").orEmpty())
```

Pass `initialProviderId` into `AIUsageAppShell`, and initialize route to `ProviderDetail(initialProviderId)` when present.

- [ ] **Step 4: Add widget tap actions**

In unified widget content, use an action that opens `MainActivity` with no provider extra.

In provider widget content, use an action that opens `MainActivity` with:

```kotlin
Intent(context, MainActivity::class.java)
    .putExtra("provider_id", payload.providerId)
```

- [ ] **Step 5: Run tests and build**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 6: Commit Task 8**

```powershell
git add -- android/app/src/main/java/com/aiusage/mobile/MainActivity.kt android/app/src/main/java/com/aiusage/mobile/widget android/app/src/main/java/com/aiusage/mobile/notification/UsageNotificationContent.kt android/app/src/test/java/com/aiusage/mobile/notification/UsageNotificationContentTest.kt
git commit -m "Route widgets to local provider screens"
```

---

### Task 9: Source Verification, Release Build, And Emulator QA

**Files:**
- Create: `docs/qa/mobile-local-provider-redesign-2026-05-15.md`

- [ ] **Step 1: Run the full unit test suite**

Run:

```powershell
.\gradlew.bat -p android :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run debug build**

Run:

```powershell
.\gradlew.bat -p android :app:assembleDebug
```

Expected: BUILD SUCCESSFUL and APK at `android/app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Run release bundle build**

Run:

```powershell
.\gradlew.bat -p android :app:bundleRelease
```

Expected: BUILD SUCCESSFUL and AAB at `android/app/build/outputs/bundle/release/app-release.aab`.

- [ ] **Step 4: Install debug APK on phone emulator**

Run:

```powershell
adb -s <phone-device-id> install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s <phone-device-id> shell monkey -p com.aiusage.mobile 1
```

Expected: the first screen is the unified local dashboard and shows Claude, Codex, Gemini, GitHub Copilot, and Cursor cards.

- [ ] **Step 5: Install debug APK on tablet emulator**

Run:

```powershell
adb -s <tablet-device-id> install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s <tablet-device-id> shell monkey -p com.aiusage.mobile 1
```

Expected: the first screen is the unified local dashboard and shows Claude, Codex, Gemini, GitHub Copilot, and Cursor cards without Windows setup as the primary flow.

- [ ] **Step 6: Verify dashboard behavior on both emulators**

On both phone and tablet:

1. Open Home.
2. Confirm disconnected cards show Connect.
3. Open each provider from navigation.
4. Long-press a provider card.
5. Move it up or down.
6. Hide one provider.
7. Reopen the app.

Expected:

- Order persists.
- Hidden provider stays hidden on Home.
- Provider detail screens remain reachable from navigation.
- No Windows PC setup blocks the local dashboard.

- [ ] **Step 7: Verify unified widget on both emulators**

On both phone and tablet:

1. Add `AI Usage dashboard` widget at 2x2.
2. Resize to 3x2.
3. Resize to 3x3.
4. Resize to 3x4.
5. Tap the widget.

Expected:

- Gauge width scales with widget width.
- Text is not clipped.
- Provider order matches dashboard order.
- Hidden providers do not appear.
- Tap opens the Home dashboard.

- [ ] **Step 8: Verify provider widget on both emulators**

On both phone and tablet:

1. Add `AI Usage provider` widget at 2x1.
2. Resize to 2x2.
3. Resize to 3x2.
4. Resize to 3x3.
5. Tap the widget.

Expected:

- Compact size shows primary provider state.
- Larger sizes show more provider lines.
- Text is not clipped.
- Tap opens that provider detail screen.

- [ ] **Step 9: Write QA evidence**

Create `docs/qa/mobile-local-provider-redesign-2026-05-15.md`:

```markdown
# Mobile Local Provider Redesign QA - 2026-05-15

## Builds

- Debug unit tests: PASS
- Debug APK: PASS
- Release bundle: PASS

## Phone Emulator

- Unified dashboard first launch: PASS
- Provider navigation: PASS
- Provider reorder persistence: PASS
- Provider hide persistence: PASS
- Unified widget 2x2, 3x2, 3x3, 3x4: PASS
- Provider widget 2x1, 2x2, 3x2, 3x3: PASS
- Widget tap routing: PASS

## Tablet Emulator

- Unified dashboard first launch: PASS
- Provider navigation: PASS
- Provider reorder persistence: PASS
- Provider hide persistence: PASS
- Unified widget 2x2, 3x2, 3x3, 3x4: PASS
- Provider widget 2x1, 2x2, 3x2, 3x3: PASS
- Widget tap routing: PASS

## Privacy Cache Check

- Widget cache contains provider ids, display names, display text, ratios, status, and timestamps only: PASS
- Widget cache does not contain cookies, tokens, raw HTML, raw logs, credential paths, or provider raw responses: PASS
```

- [ ] **Step 10: Commit Task 9**

```powershell
git add -- docs/qa/mobile-local-provider-redesign-2026-05-15.md
git commit -m "Record mobile local provider QA"
```

---

## Final Verification Checklist

- [x] `ProviderId.defaultOrder()` is Claude, Codex, Gemini, Copilot, Cursor.
- [x] App first launch opens the local unified dashboard.
- [x] Windows sync is hidden from the primary flow and still available only as legacy settings if retained.
- [x] Dashboard cards show Connect when disconnected.
- [x] Provider detail screens are reachable from navigation.
- [x] Hide/show state persists.
- [x] Long-press reorder state persists.
- [x] Unified widget follows dashboard order and hidden state.
- [x] Individual provider widget targets one provider.
- [x] Unified widget supports 2x2 through 3x4.
- [x] Individual provider widget supports 2x1 through 3x3.
- [x] Widget taps open the correct app screen.
- [x] Notification content follows local display-only widget payload.
- [x] Widget and notification cache contain no session material or raw provider data.
- [x] Phone emulator QA is recorded.
- [x] Tablet emulator QA is recorded.
- [x] `.\gradlew.bat -p android :app:testDebugUnitTest` passes.
- [x] `.\gradlew.bat -p android :app:assembleDebug` passes.
- [x] `.\gradlew.bat -p android :app:bundleRelease` passes.

## Official Reference Links To Recheck During Implementation

- Claude app and account surface: https://claude.ai/
- OpenAI ChatGPT/Codex account surface: https://chatgpt.com/
- Gemini app surface: https://gemini.google.com/
- GitHub Copilot settings surface: https://github.com/settings/copilot
- Cursor dashboard surface: https://cursor.com/dashboard

Before enabling live extraction for a provider, open its current official page in the app WebView, confirm the host remains inside `ProviderHostAllowlist`, and verify that visible subscription limit text can be extracted without storing raw page content.
