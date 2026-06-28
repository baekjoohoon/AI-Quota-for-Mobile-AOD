package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageSnapshot
import java.time.Duration
import java.time.Instant

object GoogleUsagePendingRetryPolicy {
    const val RETRY_DELAY_MILLIS = ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS
    const val PENDING_MESSAGE = "Provider session reached, but trusted usage payload was not available yet."
    private const val LEGACY_PENDING_MESSAGE = "Google authorization succeeded. Usage collection will retry."

    fun retryDelayMillis(snapshot: ProviderUsageSnapshot, now: Instant = Instant.now()): Long? {
        if (snapshot.providerId != ProviderId.GEMINI && snapshot.providerId != ProviderId.ANTIGRAVITY) {
            return null
        }
        if (!snapshot.isPendingRetryState() || snapshot.refreshState == ProviderRefreshState.REFRESHING) {
            return null
        }
        if (snapshot.lines.isEmpty() && snapshot.providerId != ProviderId.GEMINI) return null
        if (!snapshot.message.orEmpty().isPendingRetryMessage()) return null
        val statusUpdatedAt = runCatching { Instant.parse(snapshot.statusUpdatedAt) }
            .getOrElse { runCatching { Instant.parse(snapshot.updatedAt) }.getOrNull() }
            ?: return 0L
        val elapsedMillis = Duration.between(statusUpdatedAt, now).toMillis().coerceAtLeast(0L)
        return (RETRY_DELAY_MILLIS - elapsedMillis).coerceAtLeast(0L)
    }

    private fun ProviderUsageSnapshot.isPendingRetryState(): Boolean {
        return when (providerId) {
            ProviderId.GEMINI ->
                connectionState == ProviderConnectionState.CONNECTED ||
                    connectionState == ProviderConnectionState.STALE ||
                    connectionState == ProviderConnectionState.UNAVAILABLE
            ProviderId.ANTIGRAVITY ->
                connectionState == ProviderConnectionState.STALE
            else -> false
        }
    }

    private fun String.isPendingRetryMessage(): Boolean {
        val normalized = trim().lowercase()
        if (normalized == PENDING_MESSAGE.lowercase()) return true
        if (normalized == LEGACY_PENDING_MESSAGE.lowercase()) return true
        return normalized.contains("usage payload was not available") ||
            normalized.contains("trusted usage payload") ||
            normalized.contains("background refresh reached a provider login page") ||
            normalized.contains("gemini login is required")
    }
}
