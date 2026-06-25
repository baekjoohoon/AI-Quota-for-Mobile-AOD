package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleUsagePendingRetryPolicyTest {
    @Test
    fun pendingGoogleUsageDoesNotRetryBeforeSixtySecondCooldown() {
        val now = Instant.parse("2026-05-27T06:37:00Z")
        val snapshot = googlePendingSnapshot(
            providerId = ProviderId.GEMINI,
            statusUpdatedAt = now.minusSeconds(30).toString()
        ).copy(lines = listOf(ProviderUsageLine(label = "Gemini 3.5 Flash", remainingPercent = 0.8f)))

        assertEquals(
            30_000L,
            GoogleUsagePendingRetryPolicy.retryDelayMillis(snapshot, now)
        )
    }

    @Test
    fun pendingGoogleUsageRetriesAfterCooldown() {
        val now = Instant.parse("2026-05-27T06:37:00Z")
        val snapshot = googlePendingSnapshot(
            providerId = ProviderId.ANTIGRAVITY,
            statusUpdatedAt = now.minusSeconds(60).toString()
        ).copy(lines = listOf(ProviderUsageLine(label = "Gemini 3.5 Flash", remainingPercent = 0.8f)))

        assertEquals(0L, GoogleUsagePendingRetryPolicy.retryDelayMillis(snapshot, now))
    }

    @Test
    fun pendingGeminiUsageWithoutTrustedLinesRetriesAfterCooldown() {
        val now = Instant.parse("2026-05-27T06:37:00Z")
        val snapshot = googlePendingSnapshot(
            providerId = ProviderId.GEMINI,
            connectionState = ProviderConnectionState.UNAVAILABLE,
            statusUpdatedAt = now.minusSeconds(120).toString()
        )

        assertEquals(0L, GoogleUsagePendingRetryPolicy.retryDelayMillis(snapshot, now))
    }

    @Test
    fun pendingAntigravityUsageWithoutTrustedLinesDoesNotAutoRetry() {
        val now = Instant.parse("2026-05-27T06:37:00Z")
        val snapshot = googlePendingSnapshot(
            providerId = ProviderId.ANTIGRAVITY,
            statusUpdatedAt = now.minusSeconds(120).toString()
        )

        assertNull(GoogleUsagePendingRetryPolicy.retryDelayMillis(snapshot, now))
    }

    @Test
    fun collectingOrNonGoogleSnapshotsDoNotUsePendingRetryPolicy() {
        val now = Instant.parse("2026-05-27T06:37:00Z")
        val collecting = googlePendingSnapshot(
            providerId = ProviderId.GEMINI,
            statusUpdatedAt = now.minusSeconds(120).toString()
        ).copy(refreshState = ProviderRefreshState.REFRESHING)
        val claude = ProviderUsageSnapshot(
            providerId = ProviderId.CLAUDE,
            connectionState = ProviderConnectionState.STALE,
            updatedAt = now.minusSeconds(120).toString()
        )

        assertNull(GoogleUsagePendingRetryPolicy.retryDelayMillis(collecting, now))
        assertNull(GoogleUsagePendingRetryPolicy.retryDelayMillis(claude, now))
    }

    @Test
    fun serviceDisabledGoogleUsageDoesNotAutoRetry() {
        val now = Instant.parse("2026-05-27T06:37:00Z")
        val snapshot = googlePendingSnapshot(
            providerId = ProviderId.GEMINI,
            statusUpdatedAt = now.minusSeconds(120).toString()
        ).copy(message = "Gemini Cloud Code Private API is disabled for this OAuth project.")

        assertNull(GoogleUsagePendingRetryPolicy.retryDelayMillis(snapshot, now))
    }

    private fun googlePendingSnapshot(
        providerId: ProviderId,
        statusUpdatedAt: String,
        connectionState: ProviderConnectionState = ProviderConnectionState.STALE
    ): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = connectionState,
            refreshState = ProviderRefreshState.IDLE,
            updatedAt = statusUpdatedAt,
            statusUpdatedAt = statusUpdatedAt,
            message = GoogleUsagePendingRetryPolicy.PENDING_MESSAGE
        )
    }
}
