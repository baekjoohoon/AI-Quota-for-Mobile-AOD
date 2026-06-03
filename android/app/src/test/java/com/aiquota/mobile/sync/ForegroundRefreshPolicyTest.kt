package com.aiquota.mobile.sync

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRefreshPolicyTest {
    @Test
    fun connectedProvidersIncludeConnectedStaleAndRetryableFailureStates() {
        val snapshots = listOf(
            ProviderUsageSnapshot.connectedWithoutUsage(ProviderId.CODEX, "Usage unavailable"),
            ProviderUsageSnapshot(
                providerId = ProviderId.GEMINI,
                connectionState = ProviderConnectionState.STALE,
                message = "Provider session reached, but trusted usage payload was not available yet."
            ),
            ProviderUsageSnapshot(
                providerId = ProviderId.CLAUDE,
                connectionState = ProviderConnectionState.CONNECTED,
                lines = listOf(ProviderUsageLine(label = "Claude Session", remainingPercent = 0.7f))
            ),
            ProviderUsageSnapshot(
                providerId = ProviderId.COPILOT,
                connectionState = ProviderConnectionState.STALE,
                lines = listOf(ProviderUsageLine(label = "Premium requests", remainingPercent = 0.8f))
            )
        )

        assertEquals(
            listOf(ProviderId.CODEX, ProviderId.GEMINI, ProviderId.CLAUDE, ProviderId.COPILOT),
            ForegroundRefreshPolicy.connectedProviders(snapshots)
        )
    }

    @Test
    fun collectionFailuresWithoutSessionExpiryStayRefreshable() {
        val unavailable = ProviderUsageSnapshot.connectedWithoutUsage(
            providerId = ProviderId.CURSOR,
            message = "Usage quota is not available yet."
        )
        val error = ProviderUsageSnapshot.failedKeepingPrevious(
            providerId = ProviderId.CODEX,
            previous = null,
            message = "Background collector ran. No trusted usage payload found."
        )
        val disconnected = ProviderUsageSnapshot.disconnected(ProviderId.GEMINI)

        assertEquals(
            listOf(ProviderId.CURSOR, ProviderId.CODEX),
            ForegroundRefreshPolicy.connectedProviders(listOf(unavailable, error, disconnected))
        )
    }

    @Test
    fun staleProvidersRemainRefreshableEvenWithoutTrustedLines() {
        val pendingGemini = ProviderUsageSnapshot(
            providerId = ProviderId.GEMINI,
            connectionState = ProviderConnectionState.STALE,
            message = "Provider session reached, but trusted usage payload was not available yet."
        )
        val pendingClaude = ProviderUsageSnapshot(
            providerId = ProviderId.CLAUDE,
            connectionState = ProviderConnectionState.STALE,
            message = "No usage yet."
        )

        assertEquals(
            listOf(ProviderId.GEMINI, ProviderId.CLAUDE),
            ForegroundRefreshPolicy.connectedProviders(listOf(pendingGemini, pendingClaude))
        )
    }

    @Test
    fun authRequiredProvidersAreSkippedByAutomaticRefresh() {
        val stale = snapshot(ProviderId.CLAUDE, ProviderConnectionState.STALE)
        val authRequired = snapshot(ProviderId.COPILOT, ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED)

        assertEquals(
            listOf(ProviderId.CLAUDE),
            ForegroundRefreshPolicy.connectedProviders(listOf(stale, authRequired))
        )
    }

    @Test
    fun foregroundLoopRequiresNotificationOptInAndPermission() {
        val policySource = java.io.File("src/main/java/com/aiquota/mobile/sync/ForegroundRefreshPolicy.kt").readText()
        val snapshots = listOf(snapshot(ProviderId.CLAUDE, ProviderConnectionState.CONNECTED))

        assertTrue(policySource.contains("liveMonitoringEnabled"))
        assertEquals(
            true,
            ForegroundRefreshPolicy.shouldRunForegroundLoop(
                snapshots = snapshots,
                liveMonitoringEnabled = true,
                canPostNotifications = true
            )
        )
        assertEquals(
            false,
            ForegroundRefreshPolicy.shouldRunForegroundLoop(
                snapshots = snapshots,
                liveMonitoringEnabled = false,
                canPostNotifications = true
            )
        )
        assertEquals(
            false,
            ForegroundRefreshPolicy.shouldRunForegroundLoop(
                snapshots = snapshots,
                liveMonitoringEnabled = true,
                canPostNotifications = false
            )
        )
    }

    @Test
    fun foregroundLoopKeepsRunningDuringActiveCollection() {
        val snapshots = listOf(snapshot(ProviderId.CLAUDE, ProviderConnectionState.COLLECTING))

        assertEquals(
            listOf(ProviderId.CLAUDE),
            ForegroundRefreshPolicy.connectedProviders(snapshots)
        )
        assertTrue(
            ForegroundRefreshPolicy.shouldRunForegroundLoop(
                snapshots = snapshots,
                liveMonitoringEnabled = true,
                canPostNotifications = true
            )
        )
    }

    @Test
    fun foregroundLoopRequiresLiveMonitoringApproval() {
        val policySource = java.io.File("src/main/java/com/aiquota/mobile/sync/ForegroundRefreshPolicy.kt").readText()

        assertTrue(policySource.contains("liveMonitoringEnabled"))
        assertFalse(policySource.contains("notificationEnabled &&"))
    }

    private fun snapshot(providerId: ProviderId, state: ProviderConnectionState): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = state,
            lines = listOf(ProviderUsageLine(label = "Usage", remainingPercent = 0.5f))
        )
    }
}
