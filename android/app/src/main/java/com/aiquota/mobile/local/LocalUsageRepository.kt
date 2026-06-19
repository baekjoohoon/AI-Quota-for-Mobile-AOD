package com.aiquota.mobile.local

import android.content.Context
import android.content.SharedPreferences
import com.aiquota.mobile.providers.GlmNoSubscriptionPolicy
import com.aiquota.mobile.providers.ProviderSnapshotCodec
import com.aiquota.mobile.providers.ProviderScopedStateRepository
import java.time.Duration
import java.time.Instant

class LocalUsageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scopedStateRepository = ProviderScopedStateRepository(appContext)

    fun readSnapshots(): List<ProviderUsageSnapshot> {
        val stored = ProviderSnapshotCodec.decode(preferences.getString(KEY_SNAPSHOTS, "").orEmpty())
        val cleaned = stored
            .map(::normalizeGoogleUsagePendingMessage)
            .map(::recoverLegacyInteractiveAuthRequired)
            .map(::recoverSessionExpiredInteractiveAuthRequired)
            .map(::normalizeGeminiLegacyUsageLabels)
            .map(::normalizeGeminiLegacyPlanLabel)
            .map(::recoverStoppedBackgroundRefreshWithPreviousUsage)
            .map(::recoverGoogleCollectingWithoutTrustedUsage)
            .map(::recoverGoogleRecoverableUsageFailure)
            .map(::recoverGlmNoSubscriptionPlan)
            .map(::clearStaleRefreshing)
            .map(::clearExpiredProviderSpecificLines)
        if (cleaned != stored) {
            saveSnapshots(cleaned)
        } else {
            scopedStateRepository.saveSnapshots(cleaned)
        }
        return cleaned
    }

    private fun clearStaleRefreshing(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
        if (snapshot.refreshState != ProviderRefreshState.REFRESHING) return snapshot
        val statusUpdatedAt = runCatching { Instant.parse(snapshot.statusUpdatedAt) }.getOrNull() ?: return snapshot
        val staleTimeout = when {
            snapshot.providerId == ProviderId.GEMINI || snapshot.providerId == ProviderId.ANTIGRAVITY ->
                GOOGLE_STALE_REFRESH_TIMEOUT
            snapshot.providerId == ProviderId.OPENCODE -> OPENCODE_STALE_REFRESH_TIMEOUT
            snapshot.connectionState == ProviderConnectionState.CONNECTING -> STALE_CONNECTING_TIMEOUT
            snapshot.providerId == ProviderId.CODEX -> CODEX_STALE_REFRESH_TIMEOUT
            else -> STALE_REFRESH_TIMEOUT
        }
        val now = Instant.now()
        if (Duration.between(statusUpdatedAt, now) < staleTimeout) return snapshot
        val nextConnectionState = when (snapshot.connectionState) {
            ProviderConnectionState.CONNECTING,
            ProviderConnectionState.COLLECTING -> providerConnectionStateAfterPreviousUsageFailure(
                providerId = snapshot.providerId,
                hasPreviousUsage = snapshot.lines.isNotEmpty(),
                withoutPreviousUsage = ProviderConnectionState.DISCONNECTED
            )
            else -> snapshot.connectionState
        }
        return snapshot.copy(
            connectionState = nextConnectionState,
            refreshState = ProviderRefreshState.IDLE,
            statusUpdatedAt = now.toString(),
            message = "Previous collection did not finish."
        )
    }

    private fun clearExpiredProviderSpecificLines(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
        return ProviderVolatileUsagePolicy.removeExpiredLines(snapshot, Instant.now())
    }

    fun saveSnapshot(snapshot: ProviderUsageSnapshot) {
        val current = readSnapshots()
        val previous = current.firstOrNull { it.providerId == snapshot.providerId }
        val snapshotToSave = mergeFreshSnapshotWithPreviousLines(snapshot, previous)
        val next = current.filterNot { it.providerId == snapshot.providerId } + snapshotToSave
        saveSnapshots(next)
    }

    fun saveSnapshots(snapshots: List<ProviderUsageSnapshot>) {
        val ordered = ProviderId.defaultOrder().mapNotNull { provider ->
            snapshots.lastOrNull { it.providerId == provider }
        }
        scopedStateRepository.saveSnapshots(ordered)
        val encoded = ProviderSnapshotCodec.encode(ordered)
        if (preferences.getString(KEY_SNAPSHOTS, "") == encoded) return
        preferences.edit()
            .putString(KEY_SNAPSHOTS, encoded)
            .apply()
    }

    fun markConnecting(providerId: ProviderId) {
        val current = readSnapshots().firstOrNull { it.providerId == providerId }
        val now = Instant.now().toString()
        saveSnapshot(
            current?.copy(
                connectionState = ProviderConnectionState.CONNECTING,
                refreshState = ProviderRefreshState.REFRESHING,
                updatedAt = snapshotUpdatedAtForStatusTransition(current, now),
                statusUpdatedAt = now,
                message = "Opening provider login"
            ) ?: ProviderUsageSnapshot.connecting(providerId)
        )
    }

    fun markLoginCancelled(providerId: ProviderId, message: String) {
        val current = readSnapshots().firstOrNull { it.providerId == providerId } ?: return
        if (current.connectionState != ProviderConnectionState.CONNECTING ||
            current.refreshState != ProviderRefreshState.REFRESHING
        ) {
            return
        }
        val now = Instant.now().toString()
        saveSnapshot(
            current.copy(
                connectionState = providerConnectionStateAfterPreviousUsageFailure(
                    providerId = providerId,
                    hasPreviousUsage = current.lines.isNotEmpty(),
                    withoutPreviousUsage = ProviderConnectionState.DISCONNECTED
                ),
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = snapshotUpdatedAtForStatusTransition(current, now),
                statusUpdatedAt = now,
                message = message
            )
        )
    }

    fun markCollecting(providerId: ProviderId) {
        val current = readSnapshots().firstOrNull { it.providerId == providerId }
            ?: ProviderUsageSnapshot.notConnected(providerId)
        saveSnapshot(ProviderUsageSnapshot.collecting(current))
    }

    fun markConnectedWithoutUsage(providerId: ProviderId, message: String) {
        saveSnapshot(
            ProviderUsageSnapshot.connectedWithoutUsage(
                providerId = providerId,
                previous = readSnapshots().firstOrNull { it.providerId == providerId },
                message = message
            )
        )
    }

    fun markConnectedWithoutPlan(providerId: ProviderId, planLabel: String, message: String) {
        saveSnapshot(
            ProviderUsageSnapshot.connectedWithoutPlan(
                providerId = providerId,
                previous = readSnapshots().firstOrNull { it.providerId == providerId },
                planLabel = planLabel,
                message = message
            )
        )
    }

    fun markGoogleUsagePending(providerId: ProviderId, message: String) {
        if (providerId != ProviderId.GEMINI && providerId != ProviderId.ANTIGRAVITY) {
            markConnectedWithoutUsage(providerId, message)
            return
        }
        val current = readSnapshots().firstOrNull { it.providerId == providerId }
        val base = current ?: ProviderUsageSnapshot.disconnected(providerId)
        val now = Instant.now().toString()
        saveSnapshot(
            base.copy(
                connectionState = if (base.lines.isEmpty()) {
                    ProviderConnectionState.UNAVAILABLE
                } else {
                    ProviderConnectionState.CONNECTED
                },
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = snapshotUpdatedAtForStatusTransition(base, now),
                statusUpdatedAt = now,
                message = message
            )
        )
    }

    fun failKeepingPrevious(providerId: ProviderId, message: String) {
        if (providerId.isGoogleProvider() && message.isRecoverableGoogleUsageFailureMessage()) {
            markGoogleUsagePending(providerId, GOOGLE_USAGE_PENDING_MESSAGE)
            return
        }
        saveSnapshot(
            ProviderUsageSnapshot.failedKeepingPrevious(
                providerId = providerId,
                previous = readSnapshots().firstOrNull { it.providerId == providerId },
                message = message
            )
        )
    }

    fun markInteractiveAuthRequired(providerId: ProviderId, message: String) {
        markSessionExpired(providerId, message)
    }

    fun markSessionExpired(providerId: ProviderId, message: String) {
        val now = Instant.now().toString()
        saveSnapshot(
            ProviderUsageSnapshot.disconnected(providerId).copy(
                updatedAt = now,
                statusUpdatedAt = now,
                message = message
            )
        )
    }

    fun exportDisplayJson(): String = ProviderSnapshotCodec.encode(readSnapshots())

    fun exportDisplayOnlyCache(
        order: List<ProviderId>,
        hidden: Set<ProviderId>,
        updatedAt: String
    ): String {
        return WidgetCacheSanitizer.toDisplayOnlyJson(
            snapshots = readSnapshots(),
            order = order,
            hidden = hidden,
            updatedAt = updatedAt
        )
    }

    fun removeProviderSnapshot(providerId: ProviderId) {
        saveSnapshots(readSnapshots().filterNot { it.providerId == providerId })
    }

    fun registerSnapshotListener(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SNAPSHOTS) onChanged()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterSnapshotListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val PREFERENCES_NAME = "ai_quota_local_usage"
        const val KEY_SNAPSHOTS = "provider_snapshots"
        const val GOOGLE_USAGE_PENDING_MESSAGE = "Provider session reached, but trusted usage payload was not available yet."
        val STALE_CONNECTING_TIMEOUT: Duration = Duration.ofMinutes(15)
        val STALE_REFRESH_TIMEOUT: Duration = Duration.ofSeconds(10)
        val OPENCODE_STALE_REFRESH_TIMEOUT: Duration = Duration.ofSeconds(30)
        val CODEX_STALE_REFRESH_TIMEOUT: Duration = Duration.ofSeconds(45)
        val GOOGLE_STALE_REFRESH_TIMEOUT: Duration = Duration.ofSeconds(90)
    }
}

private fun recoverGoogleRecoverableUsageFailure(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (!snapshot.providerId.isGoogleProvider()) return snapshot
    if (snapshot.connectionState != ProviderConnectionState.ERROR &&
        snapshot.connectionState != ProviderConnectionState.UNAVAILABLE
    ) {
        return snapshot
    }
    if (!snapshot.message.orEmpty().isRecoverableGoogleUsageFailureMessage()) return snapshot
    if (
        snapshot.message == GOOGLE_USAGE_PENDING_MESSAGE &&
        snapshot.refreshState == ProviderRefreshState.IDLE
    ) {
        return snapshot
    }
    val now = Instant.now().toString()
    return snapshot.copy(
        connectionState = if (snapshot.lines.isEmpty()) {
            ProviderConnectionState.UNAVAILABLE
        } else {
            ProviderConnectionState.CONNECTED
        },
        refreshState = ProviderRefreshState.IDLE,
        updatedAt = snapshotUpdatedAtForStatusTransition(snapshot, now),
        statusUpdatedAt = now,
        message = "Provider session reached, but trusted usage payload was not available yet."
    )
}

internal fun recoverGlmNoSubscriptionPlan(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (!GlmNoSubscriptionPolicy.isNoSubscriptionSnapshot(snapshot)) return snapshot
    return snapshot.copy(
        connectionState = ProviderConnectionState.CONNECTED,
        refreshState = ProviderRefreshState.IDLE,
        planLabel = GlmNoSubscriptionPolicy.PLAN_LABEL,
        message = GlmNoSubscriptionPolicy.MESSAGE,
        lines = emptyList()
    )
}

internal fun mergeFreshSnapshotWithPreviousLines(
    snapshot: ProviderUsageSnapshot,
    previous: ProviderUsageSnapshot?
): ProviderUsageSnapshot {
    if (previous == null) return snapshot
    if (previous.providerId != snapshot.providerId) return snapshot
    if (snapshot.connectionState != ProviderConnectionState.CONNECTED) return snapshot
    if (snapshot.lines.isEmpty() || previous.lines.isEmpty()) return snapshot

    val incomingByKey = snapshot.lines.associateBy { it.mergeKey() }
    val previousKeys = previous.lines.map { it.mergeKey() }.toSet()
    val mergedLines = previous.lines.map { line ->
        incomingByKey[line.mergeKey()] ?: line
    } + snapshot.lines.filter { it.mergeKey() !in previousKeys }

    if (mergedLines == snapshot.lines) return snapshot
    return snapshot.copy(lines = mergedLines)
}

private fun ProviderUsageLine.mergeKey(): String {
    return key.ifBlank { label }
}

internal fun recoverGoogleCollectingWithoutTrustedUsage(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (!snapshot.providerId.isGoogleProvider()) return snapshot
    if (snapshot.lines.isNotEmpty()) return snapshot
    if (snapshot.connectionState == ProviderConnectionState.CONNECTING) return snapshot
    if (snapshot.connectionState != ProviderConnectionState.COLLECTING &&
        snapshot.refreshState != ProviderRefreshState.REFRESHING
    ) {
        return snapshot
    }
    val now = Instant.now().toString()
    return snapshot.copy(
        connectionState = ProviderConnectionState.UNAVAILABLE,
        refreshState = ProviderRefreshState.IDLE,
        updatedAt = now,
        statusUpdatedAt = now,
        message = GOOGLE_USAGE_PENDING_MESSAGE
    )
}

internal fun recoverStoppedBackgroundRefreshWithPreviousUsage(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (snapshot.connectionState != ProviderConnectionState.STALE) return snapshot
    if (snapshot.lines.isEmpty()) return snapshot
    if (snapshot.message != BACKGROUND_REFRESH_STOPPED_MESSAGE) return snapshot
    return snapshot.copy(
        connectionState = ProviderConnectionState.CONNECTED,
        refreshState = ProviderRefreshState.IDLE,
        message = null
    )
}

internal fun normalizeGeminiLegacyUsageLabels(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (snapshot.providerId != ProviderId.GEMINI) return snapshot
    val lines = snapshot.lines.filterNot { it.isLegacyGeminiCollapsedLine() }
    if (lines.isEmpty()) return snapshot
    if (lines == snapshot.lines) return snapshot
    return snapshot.copy(lines = lines)
}

private fun ProviderUsageLine.isLegacyGeminiCollapsedLine(): Boolean {
    val normalized = label.trim()
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .lowercase()
    val normalizedKey = key.trim()
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .lowercase()
    return normalized in LegacyGeminiCollapsedLabels ||
        normalizedKey in LegacyGeminiCollapsedKeys
}

private val LegacyGeminiCollapsedLabels = setOf(
    "5 hour limit",
    "five hour limit",
    "gemini pro",
    "gemini flash",
    "gemini weekly",
    "weekly limit",
    "seven day limit"
)

private val LegacyGeminiCollapsedKeys = setOf(
    "gemini 5 hour limit",
    "gemini gemini pro",
    "gemini gemini flash",
    "gemini weekly limit",
    "gemini gemini weekly"
)

internal fun normalizeGeminiLegacyPlanLabel(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (snapshot.providerId != ProviderId.GEMINI) return snapshot
    val plan = when (snapshot.planLabel?.trim()?.lowercase()) {
        "gemini plus" -> "Plus"
        "gemini pro" -> "Pro"
        "gemini ultra" -> "Ultra"
        "gemini free" -> "Free"
        else -> return snapshot
    }
    return snapshot.copy(planLabel = plan)
}

private fun normalizeGoogleUsagePendingMessage(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (!snapshot.providerId.isGoogleProvider()) return snapshot
    if (snapshot.message != LEGACY_GOOGLE_USAGE_PENDING_MESSAGE) return snapshot
    return snapshot.copy(message = GOOGLE_USAGE_PENDING_MESSAGE)
}

internal fun recoverSessionExpiredInteractiveAuthRequired(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (snapshot.connectionState != ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED) return snapshot
    return ProviderUsageSnapshot.disconnected(snapshot.providerId).copy(
        updatedAt = snapshot.updatedAt,
        message = snapshot.message ?: "Provider session requires sign-in."
    )
}

private fun ProviderId.isGoogleProvider(): Boolean {
    return this == ProviderId.GEMINI || this == ProviderId.ANTIGRAVITY
}

private fun String.isRecoverableGoogleUsageFailureMessage(): Boolean {
    val normalized = trim().lowercase()
    if (normalized == "google authorization succeeded. usage collection will retry.") return true
    if (normalized == "provider session reached, but trusted usage payload was not available yet.") return true
    if (normalized == "previous collection did not finish.") return true
    return listOf(
        "provider login page failed to load",
        "provider login returned http",
        "trusted usage payload",
        "usage payload was not available",
        "quota payload was not available",
        "loadcodeassist returned http",
        "retrieveuserquota returned http",
        "fetchavailablemodels returned http",
        "no trusted quota",
        "missing_google_web_session_cookie",
        "background refresh page failed to load",
        "background refresh stopped",
        "collection failed"
    ).any { normalized.contains(it) }
}

private const val GOOGLE_USAGE_PENDING_MESSAGE =
    "Provider session reached, but trusted usage payload was not available yet."
private const val LEGACY_GOOGLE_USAGE_PENDING_MESSAGE =
    "Google authorization succeeded. Usage collection will retry."
private const val BACKGROUND_REFRESH_STOPPED_MESSAGE = "Background refresh stopped."
