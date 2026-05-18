package com.aiusage.mobile.local

import java.time.Duration
import java.time.Instant
import java.util.Locale

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

fun ProviderId.normalizedPlanLabelForDisplay(planLabel: String?): String? {
    val value = planLabel?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val compact = value.lowercase(Locale.US)
        .replace(Regex("""[^a-z0-9]+"""), "")
    return when (this) {
        ProviderId.CODEX -> when (compact) {
            "prolite" -> "Pro 5x"
            "pro" -> "Pro 20x"
            else -> value.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
        }
        ProviderId.GEMINI -> when (compact) {
            "basic", "geminibasic" -> "Basic"
            "plus", "aiplus", "googleaiplus" -> "Google AI Plus"
            "pro", "aipro", "googleaipro" -> "Google AI Pro"
            "ultra", "aiultra", "googleaiultra" -> "Google AI Ultra"
            "advanced", "geminiadvanced" -> "Gemini Advanced"
            "aipremium", "googleoneaipremium" -> "Google One AI Premium"
            "free", "geminifree" -> "Free"
            else -> value
        }
        ProviderId.CLAUDE -> value
        ProviderId.COPILOT,
        ProviderId.CURSOR -> value.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
    }
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

internal fun ProviderUsageSnapshot.withRecoveredStaleProgress(
    now: Instant = Instant.now()
): ProviderUsageSnapshot {
    if (refreshState != ProviderRefreshState.REFRESHING && connectionState != ProviderConnectionState.CONNECTING) {
        return this
    }
    val updated = runCatching { Instant.parse(updatedAt) }.getOrNull() ?: return this
    val ageMillis = Duration.between(updated, now).toMillis()
    if (ageMillis < STALE_PROVIDER_PROGRESS_MS) return this

    return copy(
        connectionState = if (connectionState == ProviderConnectionState.CONNECTED || lines.isNotEmpty()) {
            ProviderConnectionState.CONNECTED
        } else {
            ProviderConnectionState.UNAVAILABLE
        },
        refreshState = ProviderRefreshState.IDLE,
        message = null
    )
}

private const val STALE_PROVIDER_PROGRESS_MS = 5 * 60 * 1000L
