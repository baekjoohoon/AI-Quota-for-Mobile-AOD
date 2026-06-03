package com.aiquota.mobile.sync

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRefreshPromptPolicyTest {
    @Test
    fun appEntryPromptShowsWhenRefreshableProviderExistsAndLiveRefreshIsOff() {
        assertTrue(
            LiveRefreshPromptPolicy.shouldShowOnAppEntry(
                snapshots = listOf(snapshot(ProviderId.CLAUDE, ProviderConnectionState.CONNECTED)),
                liveMonitoringEnabled = false,
                canPostNotifications = true
            )
        )
    }

    @Test
    fun appEntryPromptShowsWhenNotificationPermissionIsMissing() {
        assertTrue(
            LiveRefreshPromptPolicy.shouldShowOnAppEntry(
                snapshots = listOf(snapshot(ProviderId.CODEX, ProviderConnectionState.CONNECTED)),
                liveMonitoringEnabled = true,
                canPostNotifications = false
            )
        )
    }

    @Test
    fun appEntryPromptIsSkippedWhenNothingCanRefresh() {
        assertFalse(
            LiveRefreshPromptPolicy.shouldShowOnAppEntry(
                snapshots = listOf(snapshot(ProviderId.CURSOR, ProviderConnectionState.DISCONNECTED)),
                liveMonitoringEnabled = false,
                canPostNotifications = false
            )
        )
    }

    @Test
    fun appEntryPromptIsSkippedWhenLiveRefreshCanRun() {
        assertFalse(
            LiveRefreshPromptPolicy.shouldShowOnAppEntry(
                snapshots = listOf(snapshot(ProviderId.COPILOT, ProviderConnectionState.STALE)),
                liveMonitoringEnabled = true,
                canPostNotifications = true
            )
        )
    }

    private fun snapshot(providerId: ProviderId, state: ProviderConnectionState): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = state,
            lines = listOf(ProviderUsageLine(label = "Usage", remainingPercent = 0.5f))
        )
    }
}
