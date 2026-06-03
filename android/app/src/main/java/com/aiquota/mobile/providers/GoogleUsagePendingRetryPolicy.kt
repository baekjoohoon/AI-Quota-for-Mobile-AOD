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
        if (snapshot.connectionState != ProviderConnectionState.STALE ||
            snapshot.refreshState == ProviderRefreshState.REFRESHING
        ) {
            return null
        }
        if (snapshot.lines.isEmpty()) return null
        if (snapshot.message != PENDING_MESSAGE && snapshot.message != LEGACY_PENDING_MESSAGE) return null
        val updatedAt = runCatching { Instant.parse(snapshot.updatedAt) }.getOrNull() ?: return 0L
        val elapsedMillis = Duration.between(updatedAt, now).toMillis().coerceAtLeast(0L)
        return (RETRY_DELAY_MILLIS - elapsedMillis).coerceAtLeast(0L)
    }
}
