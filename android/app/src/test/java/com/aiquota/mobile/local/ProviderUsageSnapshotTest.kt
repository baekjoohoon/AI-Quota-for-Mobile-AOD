package com.aiquota.mobile.local

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
    fun connectedWithoutUsageKeepsPreviousUsageConnected() {
        val previous = ProviderUsageSnapshot(
            providerId = ProviderId.CLAUDE,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(ProviderUsageLine(label = "Claude Session", remainingPercent = 0.8f))
        )

        val snapshot = ProviderUsageSnapshot.connectedWithoutUsage(
            providerId = ProviderId.CLAUDE,
            previous = previous,
            message = "Usage quota is not available yet."
        )

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals(previous.lines, snapshot.lines)
        assertEquals("Usage quota is not available yet.", snapshot.message)
    }

    @Test
    fun codexTransientUsageRefreshFailureKeepsPreviousUsageConnected() {
        val previous = ProviderUsageSnapshot(
            providerId = ProviderId.CODEX,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(ProviderUsageLine(label = "Codex Session", remainingPercent = 0.8f))
        )
        val cursorPrevious = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(ProviderUsageLine(label = "Total usage", remainingPercent = 0.94f))
        )

        val failed = ProviderUsageSnapshot.failedKeepingPrevious(
            providerId = ProviderId.CODEX,
            previous = previous,
            message = "Background collector ran. No trusted usage payload found."
        )
        val unavailable = ProviderUsageSnapshot.connectedWithoutUsage(
            providerId = ProviderId.CODEX,
            previous = previous,
            message = "Provider session reached, but trusted usage payload was not available yet."
        )

        assertEquals(ProviderConnectionState.CONNECTED, failed.connectionState)
        assertEquals(previous.lines, failed.lines)
        assertEquals(ProviderConnectionState.CONNECTED, unavailable.connectionState)
        assertEquals(previous.lines, unavailable.lines)
    }

    @Test
    fun cursorTransientUsageRefreshFailureKeepsPreviousUsageConnected() {
        val cursorPrevious = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(ProviderUsageLine(label = "Total usage", remainingPercent = 0.94f))
        )
        val cursorFailed = ProviderUsageSnapshot.failedKeepingPrevious(
            providerId = ProviderId.CURSOR,
            previous = cursorPrevious,
            message = "Background collector ran. No trusted usage payload found."
        )
        val cursorUnavailable = ProviderUsageSnapshot.connectedWithoutUsage(
            providerId = ProviderId.CURSOR,
            previous = cursorPrevious,
            message = "Provider session reached, but trusted usage payload was not available yet."
        )

        assertEquals(ProviderConnectionState.CONNECTED, cursorFailed.connectionState)
        assertEquals(cursorPrevious.lines, cursorFailed.lines)
        assertEquals(ProviderConnectionState.CONNECTED, cursorUnavailable.connectionState)
        assertEquals(cursorPrevious.lines, cursorUnavailable.lines)
    }

    @Test
    fun collectingSnapshotKeepsDisconnectActionVisible() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.CURSOR,
            connectionState = ProviderConnectionState.COLLECTING,
            refreshState = ProviderRefreshState.REFRESHING,
            lines = listOf(ProviderUsageLine(label = "Total usage", remainingPercent = 0.94f))
        )

        assertEquals(ProviderConnectionAction.DISCONNECT, snapshot.primaryConnectionAction())
        assertFalse(snapshot.shouldShowDashboardConnectAction())
    }

    @Test
    fun connectingSnapshotKeepsDisconnectActionVisible() {
        val snapshot = ProviderUsageSnapshot.connecting(ProviderId.CURSOR)

        assertEquals(ProviderConnectionAction.DISCONNECT, snapshot.primaryConnectionAction())
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

    @Test
    fun refreshingDisconnectedSnapshotKeepsConnectActionVisible() {
        val snapshot = ProviderUsageSnapshot.disconnected(ProviderId.GEMINI).copy(
            refreshState = ProviderRefreshState.REFRESHING
        )

        assertEquals(ProviderConnectionAction.CONNECT, snapshot.primaryConnectionAction())
        assertTrue(snapshot.shouldShowDashboardConnectAction())
    }

    @Test
    fun authRequiredSnapshotWithLastKnownUsageDoesNotShowDashboardConnectAction() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.COPILOT,
            connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
            lines = listOf(ProviderUsageLine(label = "Chat", remainingPercent = 0.88f))
        )

        assertEquals(ProviderConnectionAction.CONNECT, snapshot.primaryConnectionAction())
        assertFalse(snapshot.shouldShowDashboardConnectAction())
    }

    @Test
    fun staleSnapshotWithoutTrustedUsageShowsDashboardConnectAction() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.GEMINI,
            connectionState = ProviderConnectionState.STALE,
            refreshState = ProviderRefreshState.IDLE,
            lines = emptyList()
        )

        assertEquals(ProviderConnectionAction.CONNECT, snapshot.primaryConnectionAction())
        assertTrue(snapshot.shouldShowDashboardConnectAction())
    }
}
