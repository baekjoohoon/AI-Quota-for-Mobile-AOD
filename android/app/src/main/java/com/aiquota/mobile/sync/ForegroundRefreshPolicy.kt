package com.aiquota.mobile.sync

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageSnapshot

object ForegroundRefreshPolicy {
    fun shouldRunForegroundLoop(
        snapshots: List<ProviderUsageSnapshot>,
        liveMonitoringEnabled: Boolean,
        canPostNotifications: Boolean
    ): Boolean {
        return liveMonitoringEnabled &&
            canPostNotifications &&
            connectedProviders(snapshots).isNotEmpty()
    }

    fun connectedProviders(snapshots: List<ProviderUsageSnapshot>): List<ProviderId> {
        return snapshots
            .filter {
                it.connectionState == ProviderConnectionState.CONNECTED ||
                    it.connectionState == ProviderConnectionState.STALE ||
                    it.connectionState == ProviderConnectionState.COLLECTING ||
                    it.connectionState == ProviderConnectionState.UNAVAILABLE ||
                    it.connectionState == ProviderConnectionState.ERROR
            }
            .map { it.providerId }
    }

}
