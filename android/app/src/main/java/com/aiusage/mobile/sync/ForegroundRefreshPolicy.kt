package com.aiusage.mobile.sync

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderUsageSnapshot

object ForegroundRefreshPolicy {
    const val REFRESH_INTERVAL_MS = 60_000L
    const val PROVIDER_REFRESH_TIMEOUT_MS = 45_000L
    const val PROVIDER_REFRESH_POLL_MS = 1_000L
    const val PROVIDER_REFRESH_START_GRACE_MS = 2_000L

    fun connectedProviders(snapshots: List<ProviderUsageSnapshot>): List<ProviderId> {
        val connected = snapshots
            .filter { it.connectionState == ProviderConnectionState.CONNECTED }
            .map { it.providerId }
            .toSet()
        return ProviderId.defaultOrder().filter { it in connected }
    }
}
