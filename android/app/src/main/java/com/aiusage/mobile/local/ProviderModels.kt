package com.aiusage.mobile.local

import java.time.Instant
import kotlin.math.roundToInt

enum class ProviderId(val storageId: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    CODEX("codex", "Codex"),
    GEMINI("gemini", "Gemini"),
    COPILOT("copilot", "Copilot"),
    CURSOR("cursor", "Cursor");

    companion object {
        fun defaultOrder(): List<ProviderId> = listOf(CLAUDE, CODEX, GEMINI, COPILOT, CURSOR)

        fun fromStorageId(value: String?): ProviderId? {
            val normalized = value?.trim().orEmpty()
            return entries.firstOrNull { it.storageId.equals(normalized, ignoreCase = true) }
        }
    }
}

enum class ProviderConnectionState {
    DISCONNECTED,
    NOT_CONNECTED,
    CONNECTING,
    CONNECTED,
    COLLECTING,
    STALE,
    UNAVAILABLE,
    ERROR
}

enum class ProviderRefreshState {
    IDLE,
    REFRESHING
}

enum class UsageSeverity {
    NORMAL,
    WARNING,
    DANGER,
    UNKNOWN
}

data class ProviderUsageLine(
    val label: String,
    val remainingPercent: Float? = null,
    val remainingText: String = remainingPercent?.let { "${(it.coerceIn(0f, 1f) * 100f).roundToInt()}% left" }.orEmpty(),
    val resetText: String? = null,
    val detailText: String? = null,
    val severity: UsageSeverity = UsageSeverity.UNKNOWN,
    val usedAmount: Double? = null,
    val limitAmount: Double? = null,
    val remainingAmount: Double? = null,
    val unit: String? = "percent",
    val category: String? = null,
    val windowText: String? = null,
    val startsAt: String? = null,
    val resetsAt: String? = null,
    val sourceLabel: String? = null,
    val confidence: Float? = null,
    val key: String = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
) {
    val usedPercent: Int?
        get() = remainingPercent?.let { (100f - it.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100) }

    val source: String
        get() = sourceLabel.orEmpty()
}

data class ProviderUsageSnapshot(
    val providerId: ProviderId,
    val displayName: String = providerId.displayName,
    val connectionState: ProviderConnectionState,
    val refreshState: ProviderRefreshState = ProviderRefreshState.IDLE,
    val planLabel: String? = null,
    val account: String? = null,
    val updatedAt: String = Instant.now().toString(),
    val message: String? = null,
    val lines: List<ProviderUsageLine> = emptyList()
) {
    val plan: String?
        get() = planLabel

    val fetchedAt: String
        get() = updatedAt

    companion object {
        fun disconnected(providerId: ProviderId): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.DISCONNECTED,
                message = "Sign in required"
            )
        }

        fun notConnected(providerId: ProviderId): ProviderUsageSnapshot = disconnected(providerId)

        fun unavailable(providerId: ProviderId, message: String): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.UNAVAILABLE,
                message = message
            )
        }

        fun connecting(providerId: ProviderId): ProviderUsageSnapshot {
            return disconnected(providerId).copy(
                connectionState = ProviderConnectionState.CONNECTING,
                refreshState = ProviderRefreshState.REFRESHING,
                message = "Opening provider login"
            )
        }

        fun collecting(previous: ProviderUsageSnapshot): ProviderUsageSnapshot {
            return previous.copy(
                connectionState = ProviderConnectionState.COLLECTING,
                refreshState = ProviderRefreshState.REFRESHING,
                updatedAt = Instant.now().toString(),
                message = "Collecting usage"
            )
        }

        fun connectedWithoutUsage(providerId: ProviderId, message: String): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.UNAVAILABLE,
                refreshState = ProviderRefreshState.IDLE,
                message = message
            )
        }

        fun connectedWithoutUsage(providerId: ProviderId, previous: ProviderUsageSnapshot?, message: String): ProviderUsageSnapshot {
            return previous?.copy(
                connectionState = if (previous.lines.isNotEmpty()) ProviderConnectionState.STALE else ProviderConnectionState.UNAVAILABLE,
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                message = message
            ) ?: connectedWithoutUsage(providerId, message)
        }

        fun failedKeepingPrevious(providerId: ProviderId, previous: ProviderUsageSnapshot?, message: String): ProviderUsageSnapshot {
            return previous?.copy(
                connectionState = if (previous.lines.isNotEmpty()) ProviderConnectionState.STALE else ProviderConnectionState.ERROR,
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                message = message
            ) ?: ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.ERROR,
                message = message
            )
        }
    }
}
