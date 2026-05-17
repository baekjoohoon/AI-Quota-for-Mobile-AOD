package com.aiusage.mobile.local

enum class ProviderId(val storageId: String, val displayName: String) {
    CLAUDE("claude", "Claude"),
    CODEX("codex", "Codex"),
    GEMINI("gemini", "Gemini"),
    COPILOT("copilot", "Copilot"),
    CURSOR("cursor", "Cursor");

    companion object {
        fun defaultOrder(): List<ProviderId> {
            return listOf(CLAUDE, CODEX, GEMINI, COPILOT, CURSOR)
        }

        fun fromStorageId(value: String): ProviderId? {
            val normalized = value.trim()
            if (normalized.isEmpty()) return null
            return entries.firstOrNull { it.storageId.equals(normalized, ignoreCase = true) }
        }
    }
}

enum class ProviderConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
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
    val remainingPercent: Float?,
    val remainingText: String,
    val resetText: String? = null,
    val detailText: String? = null,
    val severity: UsageSeverity = UsageSeverity.UNKNOWN,
    val usedAmount: Double? = null,
    val limitAmount: Double? = null,
    val remainingAmount: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val windowText: String? = null,
    val startsAt: String? = null,
    val resetsAt: String? = null,
    val sourceLabel: String? = null,
    val confidence: Float? = null
)

data class ProviderUsageSnapshot(
    val providerId: ProviderId,
    val displayName: String = providerId.displayName,
    val connectionState: ProviderConnectionState,
    val refreshState: ProviderRefreshState,
    val planLabel: String? = null,
    val updatedAt: String = "",
    val lines: List<ProviderUsageLine> = emptyList(),
    val message: String? = null
) {
    companion object {
        fun disconnected(providerId: ProviderId): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.DISCONNECTED,
                refreshState = ProviderRefreshState.IDLE
            )
        }

        fun unavailable(providerId: ProviderId, message: String): ProviderUsageSnapshot {
            return ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.UNAVAILABLE,
                refreshState = ProviderRefreshState.IDLE,
                message = message
            )
        }
    }
}
