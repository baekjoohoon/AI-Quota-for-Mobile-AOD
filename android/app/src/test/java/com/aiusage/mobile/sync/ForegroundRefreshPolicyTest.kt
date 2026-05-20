package com.aiusage.mobile.sync

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundRefreshPolicyTest {
    @Test
    fun connectedProvidersRequireTrustedUsageLines() {
        val snapshots = listOf(
            ProviderUsageSnapshot.connectedWithoutUsage(ProviderId.CODEX, "Usage unavailable"),
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

        assertEquals(listOf(ProviderId.CLAUDE, ProviderId.COPILOT), ForegroundRefreshPolicy.connectedProviders(snapshots))
    }
}
