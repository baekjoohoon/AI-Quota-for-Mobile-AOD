package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class ProviderResetRefreshRequest(
    val providerId: ProviderId,
    val resetKey: String
)

object ProviderResetRefreshPolicy {
    fun requests(
        snapshots: List<ProviderUsageSnapshot>,
        now: Instant = Instant.now()
    ): List<ProviderResetRefreshRequest> {
        return snapshots.mapNotNull { snapshot ->
            if (!shouldConsider(snapshot)) return@mapNotNull null
            val latestExpiredReset = snapshot.lines
                .mapNotNull { line ->
                    val resetAt = resetInstant(line.resetsAt)
                    if (resetAt == null || resetAt.isAfter(now)) {
                        null
                    } else {
                        line.key to resetAt
                    }
                }
                .maxByOrNull { it.second }
                ?: return@mapNotNull null

            ProviderResetRefreshRequest(
                providerId = snapshot.providerId,
                resetKey = "${snapshot.providerId.storageId}:${latestExpiredReset.first}:${latestExpiredReset.second.toEpochMilli()}"
            )
        }
    }

    fun nextResetDelayMillis(
        snapshots: List<ProviderUsageSnapshot>,
        now: Instant = Instant.now()
    ): Long? {
        return snapshots
            .asSequence()
            .filter(::shouldConsider)
            .flatMap { snapshot -> snapshot.lines.asSequence() }
            .mapNotNull { line -> resetInstant(line.resetsAt) }
            .filter { resetAt -> resetAt.isAfter(now) }
            .map { resetAt -> Duration.between(now, resetAt).toMillis().coerceAtLeast(0L) }
            .minOrNull()
    }

    internal fun resetInstant(value: String?): Instant? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { Instant.parse(trimmed) }.getOrNull()?.let { return it }
        runCatching {
            LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant()
        }.getOrNull()?.let { return it }

        val numeric = trimmed.toLongOrNull() ?: return null
        if (numeric <= 0L) return null
        val epochMillis = if (trimmed.length >= 13 || numeric > EPOCH_SECONDS_UPPER_BOUND) {
            numeric
        } else {
            numeric * 1000L
        }
        return runCatching { Instant.ofEpochMilli(epochMillis) }.getOrNull()
    }

    private fun shouldConsider(snapshot: ProviderUsageSnapshot): Boolean {
        if (snapshot.lines.isEmpty()) return false
        if (snapshot.refreshState == ProviderRefreshState.REFRESHING) return false
        return snapshot.connectionState == ProviderConnectionState.CONNECTED ||
            snapshot.connectionState == ProviderConnectionState.STALE
    }

    private const val EPOCH_SECONDS_UPPER_BOUND = 10_000_000_000L
}
