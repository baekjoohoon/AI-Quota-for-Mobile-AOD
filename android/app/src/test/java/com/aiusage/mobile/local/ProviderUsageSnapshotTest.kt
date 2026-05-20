package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUsageSnapshotTest {
    @Test
    fun connectedWithoutUsageDoesNotMarkProviderConnected() {
        val snapshot = ProviderUsageSnapshot.connectedWithoutUsage(
            providerId = ProviderId.COPILOT,
            message = "Signed in. Usage quota is not available yet."
        )

        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
        assertEquals("Signed in. Usage quota is not available yet.", snapshot.message)
    }

    @Test
    fun connectedWithoutUsageKeepsPreviousUsageAsStale() {
        val previous = ProviderUsageSnapshot(
            providerId = ProviderId.CODEX,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(ProviderUsageLine(label = "Codex Session", remainingPercent = 0.8f))
        )

        val snapshot = ProviderUsageSnapshot.connectedWithoutUsage(
            providerId = ProviderId.CODEX,
            previous = previous,
            message = "Usage quota is not available yet."
        )

        assertEquals(ProviderConnectionState.STALE, snapshot.connectionState)
        assertEquals(previous.lines, snapshot.lines)
        assertEquals("Usage quota is not available yet.", snapshot.message)
    }
}
