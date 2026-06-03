package com.aiquota.mobile.local

import java.time.Instant
import kotlin.math.roundToInt

enum class ProviderId(val storageId: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    CODEX("codex", "Codex"),
    GEMINI("gemini", "Gemini"),
    COPILOT("copilot", "Copilot"),
    ANTIGRAVITY("antigravity", "Antigravity"),
    CURSOR("cursor", "Cursor");

    companion object {
        fun defaultOrder(): List<ProviderId> = listOf(CLAUDE, CODEX, GEMINI, COPILOT, ANTIGRAVITY, CURSOR)

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
    INTERACTIVE_AUTH_REQUIRED,
    UNAVAILABLE,
    ERROR
}

enum class ProviderRefreshState {
    IDLE,
    REFRESHING
}

enum class ProviderConnectionAction {
    CONNECT,
    DISCONNECT,
    NONE
}

enum class UsageSeverity {
    NORMAL,
    WARNING,
    DANGER,
    UNKNOWN
}

fun ProviderUsageSnapshot.primaryConnectionAction(): ProviderConnectionAction {
    return when (connectionState) {
        ProviderConnectionState.DISCONNECTED,
        ProviderConnectionState.NOT_CONNECTED,
        ProviderConnectionState.UNAVAILABLE,
        ProviderConnectionState.ERROR,
        ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> ProviderConnectionAction.CONNECT
        ProviderConnectionState.STALE -> if (lines.isEmpty()) ProviderConnectionAction.CONNECT else ProviderConnectionAction.DISCONNECT
        ProviderConnectionState.CONNECTED -> ProviderConnectionAction.DISCONNECT
        ProviderConnectionState.CONNECTING,
        ProviderConnectionState.COLLECTING -> ProviderConnectionAction.DISCONNECT
    }
}

fun ProviderUsageSnapshot.shouldShowDashboardConnectAction(): Boolean {
    return when (connectionState) {
        ProviderConnectionState.DISCONNECTED,
        ProviderConnectionState.NOT_CONNECTED -> true
        ProviderConnectionState.STALE,
        ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
        ProviderConnectionState.UNAVAILABLE,
        ProviderConnectionState.ERROR -> lines.isEmpty()
        ProviderConnectionState.CONNECTING,
        ProviderConnectionState.CONNECTED,
        ProviderConnectionState.COLLECTING -> false
    }
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
                connectionState = providerConnectionStateAfterPreviousUsageFailure(
                    providerId = providerId,
                    hasPreviousUsage = previous.lines.isNotEmpty(),
                    withoutPreviousUsage = ProviderConnectionState.UNAVAILABLE
                ),
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                message = message
            ) ?: connectedWithoutUsage(providerId, message)
        }

        fun failedKeepingPrevious(providerId: ProviderId, previous: ProviderUsageSnapshot?, message: String): ProviderUsageSnapshot {
            return previous?.copy(
                connectionState = providerConnectionStateAfterPreviousUsageFailure(
                    providerId = providerId,
                    hasPreviousUsage = previous.lines.isNotEmpty(),
                    withoutPreviousUsage = ProviderConnectionState.ERROR
                ),
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                message = message
            ) ?: ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.ERROR,
                message = message
            )
        }

        fun interactiveAuthRequiredKeepingPrevious(
            providerId: ProviderId,
            previous: ProviderUsageSnapshot?,
            message: String
        ): ProviderUsageSnapshot {
            val now = Instant.now().toString()
            return previous?.copy(
                connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = now,
                message = message
            ) ?: ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = now,
                message = message
            )
        }
    }
}

internal fun providerConnectionStateAfterPreviousUsageFailure(
    providerId: ProviderId,
    hasPreviousUsage: Boolean,
    withoutPreviousUsage: ProviderConnectionState
): ProviderConnectionState {
    if (!hasPreviousUsage) return withoutPreviousUsage
    return ProviderConnectionState.CONNECTED
}

internal fun recoverLegacyInteractiveAuthRequired(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
    if (snapshot.connectionState != ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED) return snapshot
    if (snapshot.lines.isEmpty()) return snapshot
    if (!snapshot.message.isLegacyRecoverableAuthMessage()) return snapshot
    return snapshot.copy(
        connectionState = ProviderConnectionState.CONNECTED,
        message = null
    )
}

private fun String?.isLegacyRecoverableAuthMessage(): Boolean {
    val normalized = this?.trim()?.lowercase().orEmpty()
    return normalized in LEGACY_RECOVERABLE_AUTH_MESSAGES
}

private val LEGACY_RECOVERABLE_AUTH_MESSAGES = setOf(
    "connection needs attention",
    "connection needs attention.",
    "previous collection did not finish.",
    "background collector ran. no trusted usage payload found.",
    "collector ran. no trusted cursor usage payload found yet.",
    "no trusted provider usage payload was collected.",
    "background refresh page failed to load.",
    "\uc5f0\uacb0 \ud655\uc778 \ud544\uc694"
)
