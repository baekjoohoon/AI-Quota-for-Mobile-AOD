package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun collectingSnapshotHasNoPrimaryConnectionAction() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.COLLECTING,
            refreshState = ProviderRefreshState.REFRESHING,
            lines = listOf(ProviderUsageLine(label = "Total usage", remainingPercent = 0.94f))
        )

        assertEquals(ProviderConnectionAction.NONE, snapshot.primaryConnectionAction())
        assertFalse(snapshot.shouldShowDashboardConnectAction())
    }

    @Test
    fun connectingSnapshotHasNoPrimaryConnectionAction() {
        val snapshot = ProviderUsageSnapshot.connecting(ProviderId.CURSOR)

        assertEquals(ProviderConnectionAction.NONE, snapshot.primaryConnectionAction())
        assertFalse(snapshot.shouldShowDashboardConnectAction())
    }

    @Test
    fun idleSnapshotsExposeOnlyValidPrimaryAction() {
        val connected = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(ProviderUsageLine(label = "Total usage", remainingPercent = 0.94f))
        )
        val disconnected = ProviderUsageSnapshot.disconnected(ProviderId.CURSOR)

        assertEquals(ProviderConnectionAction.DISCONNECT, connected.primaryConnectionAction())
        assertFalse(connected.shouldShowDashboardConnectAction())
        assertEquals(ProviderConnectionAction.CONNECT, disconnected.primaryConnectionAction())
        assertTrue(disconnected.shouldShowDashboardConnectAction())
    }
}
