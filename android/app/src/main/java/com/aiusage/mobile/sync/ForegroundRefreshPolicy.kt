package com.aiusage.mobile.sync

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderUsageSnapshot

object ForegroundRefreshPolicy {
    fun connectedProviders(snapshots: List<ProviderUsageSnapshot>): List<ProviderId> {
        return snapshots
            .filter {
                it.lines.isNotEmpty() &&
                    it.connectionState in setOf(ProviderConnectionState.CONNECTED, ProviderConnectionState.COLLECTING, ProviderConnectionState.STALE)
            }
            .map { it.providerId }
    }
}
