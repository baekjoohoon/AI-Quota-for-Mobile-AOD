package com.aiusage.mobile.local

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalUsageRepositoryTest {
    @Test
    fun expiredClaudeSessionLineIsRemovedInsteadOfMixingOldPercentWithExpiredReset() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CLAUDE,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(
                ProviderUsageLine(
                    key = "claude:session",
                    label = "Claude Session",
                    remainingPercent = 0.91f,
                    resetsAt = "2026-05-20T17:10:01Z"
                ),
                ProviderUsageLine(
                    key = "claude:weekly",
                    label = "Claude Weekly",
                    remainingPercent = 0.86f,
                    resetsAt = "2026-05-25T20:00:00Z"
                )
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(
            now = Instant.parse("2026-05-20T21:24:16Z")
        )

        assertEquals(listOf("Claude Weekly"), cleaned.lines.map { it.label })
        assertFalse(cleaned.lines.any { it.key == "claude:session" })
    }

    @Test
    fun expiredCodexSessionLineIsRemovedInsteadOfKeepingStaleWindow() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CODEX,
            connectionState = ProviderConnectionState.STALE,
            lines = listOf(
                ProviderUsageLine(
                    key = "codex:primary_window",
                    label = "Codex Session",
                    remainingPercent = 0.79f,
                    resetsAt = "1779298708"
                ),
                ProviderUsageLine(
                    key = "codex:secondary_window",
                    label = "Codex Weekly",
                    remainingPercent = 0.79f,
                    resetsAt = "1779831098"
                )
            )
        )

        val cleaned = snapshot.withExpiredProviderSpecificLinesRemoved(
            now = Instant.parse("2026-05-21T00:06:49Z")
        )

        assertEquals(listOf("Codex Weekly"), cleaned.lines.map { it.label })
        assertFalse(cleaned.lines.any { it.key == "codex:primary_window" })
    }
}
