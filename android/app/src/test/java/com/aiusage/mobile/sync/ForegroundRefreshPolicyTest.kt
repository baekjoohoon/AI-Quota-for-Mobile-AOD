package com.aiusage.mobile.sync

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundRefreshPolicyTest {
    @Test
    fun refreshesConnectedProvidersEveryMinute() {
        val connectedProviders = ForegroundRefreshPolicy.connectedProviders(
            listOf(
                ProviderUsageSnapshot(
                    providerId = ProviderId.CLAUDE,
                    connectionState = ProviderConnectionState.CONNECTED,
                    refreshState = ProviderRefreshState.IDLE
                ),
                ProviderUsageSnapshot.disconnected(ProviderId.CODEX),
                ProviderUsageSnapshot(
                    providerId = ProviderId.GEMINI,
                    connectionState = ProviderConnectionState.CONNECTED,
                    refreshState = ProviderRefreshState.REFRESHING
                ),
                ProviderUsageSnapshot.unavailable(ProviderId.CURSOR, "Not logged in")
            )
        )

        assertEquals(60_000L, ForegroundRefreshPolicy.REFRESH_INTERVAL_MS)
        assertEquals(listOf(ProviderId.CLAUDE, ProviderId.GEMINI), connectedProviders)
    }
}
