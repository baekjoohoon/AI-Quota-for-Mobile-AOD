package com.aiquota.mobile.local

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
    fun staleWithLinesIsDisplayedAsFreshLastKnownUsage() {
        val snapshot = snapshot(ProviderConnectionState.STALE, updatedAt = "2026-05-21T00:00:00Z")

        assertEquals(
            ProviderUsageFreshness.FRESH,
            snapshot.usageFreshness(now = Instant.parse("2026-05-21T00:01:00Z"))
        )
        assertEquals(false, snapshot.hasLastKnownUsage(now = Instant.parse("2026-05-21T00:01:00Z")))
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
