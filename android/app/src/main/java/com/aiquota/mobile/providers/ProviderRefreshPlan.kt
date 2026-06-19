package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageSnapshot
import com.aiquota.mobile.sync.ForegroundRefreshPolicy
import java.time.Duration
import java.time.Instant

enum class ProviderRefreshMode {
    NATIVE_API,
    HIDDEN_WEB_COLLECTOR
}

data class ProviderRefreshJob(
    val providerId: ProviderId,
    val mode: ProviderRefreshMode,
    val startUrl: String,
    val qos: Int = ProviderRefreshPlan.NORMAL_REFRESH_QOS
)

object ProviderRefreshPlan {
    const val AUTO_REFRESH_INTERVAL_MILLIS = 60_000L
    const val MIN_AUTO_REFRESH_DELAY_MILLIS = 5_000L
    const val PROVIDER_REFRESH_TIMEOUT_MILLIS = 10_000L
    const val CODEX_REFRESH_TIMEOUT_MILLIS = 60_000L
    const val OPENCODE_REFRESH_TIMEOUT_MILLIS = 20_000L
    const val GOOGLE_REFRESH_TIMEOUT_MILLIS = 75_000L
    const val RESET_REFRESH_QOS = 1
    const val NORMAL_REFRESH_QOS = 5

    fun timeoutMillisFor(providerId: ProviderId): Long {
        return when (providerId) {
            ProviderId.CODEX -> CODEX_REFRESH_TIMEOUT_MILLIS
            ProviderId.OPENCODE -> OPENCODE_REFRESH_TIMEOUT_MILLIS
            ProviderId.GLM -> PROVIDER_REFRESH_TIMEOUT_MILLIS
            ProviderId.GEMINI,
            ProviderId.ANTIGRAVITY -> GOOGLE_REFRESH_TIMEOUT_MILLIS
            else -> PROVIDER_REFRESH_TIMEOUT_MILLIS
        }
    }

    fun nextAutoRefreshDelayMillis(elapsedMillis: Long): Long {
        return (AUTO_REFRESH_INTERVAL_MILLIS - elapsedMillis).coerceAtLeast(MIN_AUTO_REFRESH_DELAY_MILLIS)
    }

    fun manualJobFor(providerId: ProviderId): ProviderRefreshJob {
        return jobFor(providerId, NORMAL_REFRESH_QOS)
    }

    fun resetJobFor(providerId: ProviderId): ProviderRefreshJob {
        return jobFor(providerId, RESET_REFRESH_QOS)
    }

    fun automaticJobsFor(
        snapshots: List<ProviderUsageSnapshot>,
        now: Instant = Instant.now()
    ): List<ProviderRefreshJob> {
        val refreshingProviders = snapshots
            .filter { it.refreshState == ProviderRefreshState.REFRESHING }
            .map { it.providerId }
            .toSet()
        val snapshotsByProvider = snapshots.associateBy { it.providerId }
        val resetJobs = resetJobsFor(snapshots, now)
        val resetProviders = resetJobs.map { it.providerId }.toSet()
        val normalJobs = ForegroundRefreshPolicy.connectedProviders(snapshots)
            .filterNot { it in refreshingProviders }
            .filterNot { it in resetProviders }
            .filter { providerId -> snapshotsByProvider[providerId]?.connectionState != ProviderConnectionState.COLLECTING }
            .filter { providerId -> snapshotsByProvider[providerId]?.isAutomaticRefreshEligible(now) != false }
            .map(::manualJobFor)
        return resetJobs + normalJobs
    }

    fun manualCycleJobsFor(
        manualProviderId: ProviderId,
        snapshots: List<ProviderUsageSnapshot>,
        now: Instant = Instant.now()
    ): List<ProviderRefreshJob> {
        val resetJobs = resetJobsFor(snapshots, now)
        val resetProviders = resetJobs.map { it.providerId }.toSet()
        val manualJobs = listOf(manualJobFor(manualProviderId))
            .filterNot { it.providerId in resetProviders }
        return resetJobs + manualJobs
    }

    private fun refreshModeFor(providerId: ProviderId): ProviderRefreshMode {
        return when (ProviderDefinitionRegistry.definitionFor(providerId).collectionKind) {
            ProviderCollectionKind.WEBVIEW_COLLECTOR -> ProviderRefreshMode.HIDDEN_WEB_COLLECTOR
            ProviderCollectionKind.NATIVE_API_WITH_WEBVIEW_FALLBACK -> ProviderRefreshMode.HIDDEN_WEB_COLLECTOR
            ProviderCollectionKind.NATIVE_API -> ProviderRefreshMode.NATIVE_API
            ProviderCollectionKind.BACKEND_GATEWAY -> ProviderRefreshMode.NATIVE_API
        }
    }

    private fun hiddenCollectorUrl(providerId: ProviderId): String {
        return when (providerId) {
            ProviderId.CLAUDE -> "https://claude.ai/"
            ProviderId.CODEX -> ProviderLoginStrategy.CODEX_CALLBACK_RECOVERY_URL
            ProviderId.GLM -> GlmProviderUrls.WEB_OAUTH_URL
            ProviderId.OPENCODE -> "https://opencode.ai/auth"
            ProviderId.COPILOT -> "https://github.com/settings/copilot/features"
            ProviderId.ANTIGRAVITY -> "https://antigravity.google/"
            ProviderId.CURSOR -> "https://cursor.com/dashboard"
            ProviderId.GEMINI -> "https://gemini.google.com/usage"
        }
    }

    private fun resetJobsFor(snapshots: List<ProviderUsageSnapshot>, now: Instant): List<ProviderRefreshJob> {
        return ProviderResetRefreshPolicy.requests(snapshots, now)
            .map { it.providerId }
            .distinct()
            .map(::resetJobFor)
    }

    private fun jobFor(providerId: ProviderId, qos: Int): ProviderRefreshJob {
        val mode = refreshModeFor(providerId)
        return ProviderRefreshJob(
            providerId = providerId,
            mode = mode,
            startUrl = if (mode == ProviderRefreshMode.NATIVE_API) "" else hiddenCollectorUrl(providerId),
            qos = qos
        )
    }

    private fun ProviderUsageSnapshot.isAutomaticRefreshEligible(now: Instant): Boolean {
        if (providerId == ProviderId.GLM && GlmNoSubscriptionPolicy.isNoSubscriptionSnapshot(this)) return false
        if (isRecentAutomaticFailureBackedOff(now)) return false
        if (providerId != ProviderId.GEMINI && providerId != ProviderId.ANTIGRAVITY) return true
        if (connectionState != ProviderConnectionState.STALE) return true
        if (lines.isNotEmpty()) return true
        return GoogleUsagePendingRetryPolicy.retryDelayMillis(this) != null
    }

    private fun ProviderUsageSnapshot.isRecentAutomaticFailureBackedOff(now: Instant): Boolean {
        if (message != LOGIN_PAGE_REACHED_MESSAGE && !isCodexNoFinishFailure()) return false
        val statusAt = runCatching { Instant.parse(statusUpdatedAt) }.getOrNull() ?: return false
        return Duration.between(statusAt, now) < AUTOMATIC_FAILURE_BACKOFF
    }

    private fun ProviderUsageSnapshot.isCodexNoFinishFailure(): Boolean {
        if (providerId != ProviderId.CODEX) return false
        return message == BACKGROUND_REFRESH_TIMEOUT_MESSAGE ||
            message == PREVIOUS_COLLECTION_DID_NOT_FINISH_MESSAGE ||
            message == BACKGROUND_NO_TRUSTED_PAYLOAD_MESSAGE ||
            message?.startsWith(CODEX_USAGE_UNAVAILABLE_MESSAGE_PREFIX) == true
    }

    private val AUTOMATIC_FAILURE_BACKOFF: Duration = Duration.ofMinutes(15)
    private const val LOGIN_PAGE_REACHED_MESSAGE = "Background refresh reached a provider login page."
    private const val BACKGROUND_REFRESH_TIMEOUT_MESSAGE = "Background refresh timed out."
    private const val PREVIOUS_COLLECTION_DID_NOT_FINISH_MESSAGE = "Previous collection did not finish."
    private const val BACKGROUND_NO_TRUSTED_PAYLOAD_MESSAGE = "Background collector ran. No trusted usage payload found."
    private const val CODEX_USAGE_UNAVAILABLE_MESSAGE_PREFIX = "Codex session reached, but trusted usage payload was not available."

}
