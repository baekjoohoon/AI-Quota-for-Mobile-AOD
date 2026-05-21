package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.sync.ForegroundRefreshPolicy

enum class ProviderRefreshMode {
    NATIVE_API,
    HIDDEN_WEB_COLLECTOR
}

data class ProviderRefreshJob(
    val providerId: ProviderId,
    val mode: ProviderRefreshMode,
    val startUrl: String
)

object ProviderRefreshPlan {
    const val AUTO_REFRESH_INTERVAL_MILLIS = 60_000L

    fun manualJobFor(providerId: ProviderId): ProviderRefreshJob {
        val mode = refreshModeFor(providerId)
        return ProviderRefreshJob(
            providerId = providerId,
            mode = mode,
            startUrl = if (mode == ProviderRefreshMode.NATIVE_API) "" else hiddenCollectorUrl(providerId)
        )
    }

    fun automaticJobsFor(snapshots: List<ProviderUsageSnapshot>): List<ProviderRefreshJob> {
        val refreshingProviders = snapshots
            .filter { it.refreshState == ProviderRefreshState.REFRESHING }
            .map { it.providerId }
            .toSet()
        return ForegroundRefreshPolicy.connectedProviders(snapshots)
            .filterNot { it in refreshingProviders }
            .map(::manualJobFor)
    }

    private fun refreshModeFor(providerId: ProviderId): ProviderRefreshMode {
        return when (providerId) {
            ProviderId.GEMINI -> ProviderRefreshMode.NATIVE_API
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.COPILOT,
            ProviderId.CURSOR -> ProviderRefreshMode.HIDDEN_WEB_COLLECTOR
        }
    }

    private fun hiddenCollectorUrl(providerId: ProviderId): String {
        return when (providerId) {
            ProviderId.CLAUDE -> "https://claude.ai/"
            ProviderId.CODEX -> "https://chatgpt.com/"
            ProviderId.COPILOT -> "https://github.com/settings/copilot"
            ProviderId.CURSOR -> "https://cursor.com/dashboard"
            ProviderId.GEMINI -> ""
        }
    }
}
