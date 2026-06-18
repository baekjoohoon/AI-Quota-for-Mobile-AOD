package com.aiquota.mobile.sync

import com.aiquota.mobile.local.ProviderUsageSnapshot

object LiveRefreshPromptPolicy {
    fun shouldShowOnAppEntry(
        snapshots: List<ProviderUsageSnapshot>,
        liveMonitoringEnabled: Boolean,
        canPostNotifications: Boolean,
        batteryOptimizationExempt: Boolean
    ): Boolean {
        if (ForegroundRefreshPolicy.connectedProviders(snapshots).isEmpty()) return false
        return !liveMonitoringEnabled || !canPostNotifications || !batteryOptimizationExempt
    }
}
