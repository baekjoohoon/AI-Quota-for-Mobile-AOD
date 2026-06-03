package com.aiquota.mobile.local

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderVolatileUsagePolicyTest {
    @Test
    fun expiredCopilotRowsAreKeptUntilFreshProviderPayloadArrives() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.COPILOT,
            connectionState = ProviderConnectionState.STALE,
            lines = listOf(
                ProviderUsageLine(
                    key = "copilot:completions",
                    label = "Inline suggestions",
                    remainingPercent = 0.4f,
                    resetsAt = "2026-05-21T00:00:00Z"
                ),
                ProviderUsageLine(
                    key = "copilot:chat",
                    label = "Chat",
                    remainingPercent = 0.8f,
                    resetsAt = "2026-05-22T00:00:00Z"
                )
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(now = Instant.parse("2026-05-21T00:01:00Z"))

        assertEquals(ProviderConnectionState.STALE, cleaned.connectionState)
        assertEquals(listOf("Inline suggestions", "Chat"), cleaned.lines.map { it.label })
        assertFalse(cleaned.message.orEmpty().contains("fresh provider update", ignoreCase = true))
    }

    @Test
    fun allExpiredRowsKeepLatestDisplayedUsage() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.GEMINI,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(
                ProviderUsageLine(
                    key = "gemini:gemini_pro",
                    label = "Gemini Pro",
                    remainingPercent = 0.1f,
                    resetsAt = "2026-05-20T00:00:00Z"
                )
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(now = Instant.parse("2026-05-21T00:01:00Z"))

        assertEquals(ProviderConnectionState.CONNECTED, cleaned.connectionState)
        assertEquals(listOf("Gemini Pro"), cleaned.lines.map { it.label })
    }

    @Test
    fun codexSessionIsKeptEvenWhenResetTimestampHasPassed() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CODEX,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(
                ProviderUsageLine(
                    key = "codex:primary_window",
                    label = "Codex Session",
                    remainingPercent = 0.79f,
                    resetsAt = "2026-05-21T00:00:00Z"
                )
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(now = Instant.parse("2026-05-21T00:01:00Z"))

        assertEquals(ProviderConnectionState.CONNECTED, cleaned.connectionState)
        assertEquals(listOf("Codex Session"), cleaned.lines.map { it.label })
        assertEquals(0.79f, cleaned.lines.single().remainingPercent ?: 0f, 0.001f)
    }
}
