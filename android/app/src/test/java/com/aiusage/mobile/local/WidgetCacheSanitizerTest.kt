package com.aiusage.mobile.local

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetCacheSanitizerTest {
    @Test
    fun exportJsonContainsSchemaAndVisibleProvidersInPreferenceOrder() {
        val order = listOf(
            ProviderId.CURSOR,
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.GEMINI,
            ProviderId.COPILOT
        )
        val hidden = setOf(ProviderId.GEMINI, ProviderId.COPILOT)

        val json = WidgetCacheSanitizer.toDisplayOnlyJson(
            snapshots = listOf(
                connectedSnapshot(ProviderId.CLAUDE, "Claude", 0.8f),
                connectedSnapshot(ProviderId.CURSOR, "Cursor", 0.4f)
            ),
            order = order,
            hidden = hidden,
            updatedAt = UPDATED_AT
        )

        val root = JSONObject(json)
        val providers = root.getJSONArray("providers")

        assertEquals("local-provider-display-v1", root.getString("schema"))
        assertEquals(UPDATED_AT, root.getString("updatedAt"))
        assertEquals(listOf("cursor", "claude", "codex"), providers.providerIds())
    }

    @Test
    fun exportJsonAddsDisconnectedPlaceholderForMissingVisibleProviders() {
        val json = WidgetCacheSanitizer.toDisplayOnlyJson(
            snapshots = emptyList(),
            order = ProviderId.defaultOrder(),
            hidden = ProviderId.defaultOrder().filterNot { it == ProviderId.CODEX }.toSet(),
            updatedAt = UPDATED_AT
        )

        val provider = JSONObject(json).getJSONArray("providers").getJSONObject(0)

        assertEquals("codex", provider.getString("providerId"))
        assertEquals("Codex", provider.getString("displayName"))
        assertEquals(ProviderConnectionState.DISCONNECTED.name, provider.getString("connectionState"))
        assertEquals(ProviderRefreshState.IDLE.name, provider.getString("refreshState"))
        assertTrue(provider.getBoolean("visible"))
        assertEquals(0, provider.getJSONArray("lines").length())
    }

    @Test
    fun exportJsonNormalizesLegacyCopilotDisplayName() {
        val json = WidgetCacheSanitizer.toDisplayOnlyJson(
            snapshots = listOf(connectedSnapshot(ProviderId.COPILOT, "GitHub Copilot", 0.6f)),
            order = listOf(ProviderId.COPILOT),
            hidden = ProviderId.defaultOrder().filterNot { it == ProviderId.COPILOT }.toSet(),
            updatedAt = UPDATED_AT
        )

        val provider = JSONObject(json).getJSONArray("providers").getJSONObject(0)

        assertEquals("copilot", provider.getString("providerId"))
        assertEquals("Copilot", provider.getString("displayName"))
    }

    @Test
    fun exportJsonUsesOnlyDisplayProviderAndLineKeys() {
        val json = WidgetCacheSanitizer.toDisplayOnlyJson(
            snapshots = listOf(connectedSnapshot(ProviderId.CURSOR, "Cursor", 0.4f)),
            order = listOf(ProviderId.CURSOR),
            hidden = ProviderId.defaultOrder().filterNot { it == ProviderId.CURSOR }.toSet(),
            updatedAt = UPDATED_AT
        )

        val provider = JSONObject(json).getJSONArray("providers").getJSONObject(0)
        val line = provider.getJSONArray("lines").getJSONObject(0)

        assertEquals(
            setOf(
                "providerId",
                "displayName",
                "connectionState",
                "refreshState",
                "planLabel",
                "updatedAt",
                "message",
                "visible",
                "lines"
            ),
            provider.namesSet()
        )
        assertEquals(
            setOf(
                "label",
                "remainingPercent",
                "remainingText",
                "resetText",
                "detailText",
                "severity",
                "used",
                "limit",
                "remaining",
                "unit",
                "category",
                "windowText",
                "startsAt",
                "resetsAt",
                "confidence"
            ),
            line.namesSet()
        )
        assertEquals(12.0, line.getDouble("used"), 0.0001)
        assertEquals(40.0, line.getDouble("limit"), 0.0001)
        assertEquals(28.0, line.getDouble("remaining"), 0.0001)
        assertEquals("requests", line.getString("unit"))
        assertEquals("fast_requests", line.getString("category"))
        assertEquals("5h", line.getString("windowText"))
    }

    @Test
    fun sanitizeDisplayOnlyJsonRemovesSensitiveKeysBeforeWriting() {
        val original = JSONObject(
            WidgetCacheSanitizer.toDisplayOnlyJson(
                snapshots = listOf(connectedSnapshot(ProviderId.CLAUDE, "Claude", 0.8f)),
                order = listOf(ProviderId.CLAUDE),
                hidden = ProviderId.defaultOrder().filterNot { it == ProviderId.CLAUDE }.toSet(),
                updatedAt = UPDATED_AT
            )
        )
        val provider = original.getJSONArray("providers").getJSONObject(0)
        val line = provider.getJSONArray("lines").getJSONObject(0)
        original.put("accessToken", "secret")
        provider.put("refreshToken", "secret")
        provider.put("apiKey", "secret")
        provider.put("token", "secret")
        provider.put("secret", "secret")
        provider.put("cookie", "secret")
        provider.put("session", "secret")
        provider.put("raw", "secret")
        provider.put("rawProviderData", JSONObject().put("token", "secret"))
        line.put("cookie", "secret")

        val sanitized = JSONObject(WidgetCacheSanitizer.sanitizeDisplayOnlyJson(original.toString()))
        val keys = sanitized.allKeys().map { it.lowercase() }.toSet()

        listOf(
            "accesstoken",
            "refreshtoken",
            "apikey",
            "token",
            "secret",
            "cookie",
            "session",
            "raw",
            "rawproviderdata"
        ).forEach { sensitiveKey ->
            assertFalse("$sensitiveKey should not be present", keys.contains(sensitiveKey))
        }
    }

    private fun connectedSnapshot(
        providerId: ProviderId,
        label: String,
        remaining: Float
    ): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = providerId,
            displayName = label,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            planLabel = "Pro",
            updatedAt = UPDATED_AT,
            lines = listOf(
                ProviderUsageLine(
                    label = "Session",
                    remainingPercent = remaining,
                    remainingText = "${(remaining * 100).toInt()}% left",
                    resetText = "Resets in 1h",
                    detailText = "Fast requests",
                    severity = UsageSeverity.WARNING,
                    usedAmount = 12.0,
                    limitAmount = 40.0,
                    remainingAmount = 28.0,
                    unit = "requests",
                    category = "fast_requests",
                    windowText = "5h",
                    startsAt = "2026-05-15T00:00:00Z",
                    resetsAt = "2026-05-15T05:00:00Z",
                    sourceLabel = "dashboard",
                    confidence = 0.9f
                )
            ),
            message = "Ready"
        )
    }

    private fun JSONArray.providerIds(): List<String> {
        return buildList {
            for (index in 0 until length()) {
                add(getJSONObject(index).getString("providerId"))
            }
        }
    }

    private fun JSONObject.namesSet(): Set<String> {
        val names = names() ?: return emptySet()
        return buildSet {
            for (index in 0 until names.length()) {
                add(names.getString(index))
            }
        }
    }

    private fun JSONObject.allKeys(): List<String> {
        val collected = mutableListOf<String>()
        fun collect(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        collected.add(key)
                        collect(value.opt(key))
                    }
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        collect(value.opt(index))
                    }
                }
            }
        }
        collect(this)
        return collected
    }

    private companion object {
        const val UPDATED_AT = "2026-05-15T00:00:00Z"
    }
}
