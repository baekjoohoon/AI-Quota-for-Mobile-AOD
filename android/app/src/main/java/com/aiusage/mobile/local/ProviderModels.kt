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
            "plus", "aiplus", "googleaiplus", "googleoneaiplus", "geminiplus" -> "Gemini Plus"
            "pro", "aipro", "googleaipro", "googleoneaipro", "geminipro",
            "g1protier", "geminicodeassistingoogleoneaipro" -> "Gemini Pro"
            "ultra", "aiultra", "googleaiultra", "googleoneaiultra", "geminiultra",
            "g1ultratier", "geminicodeassistingoogleoneaiultra" -> "Gemini Ultra"
            "advanced", "geminiadvanced" -> "Gemini Advanced"
            "aipremium", "googleoneaipremium" -> "Google One AI Premium"
            "free", "geminifree", "googleaifree" -> "Gemini Free"
            "unknown", "geminiunknown" -> "Gemini Unknown"
            else -> value
        }
        ProviderId.CLAUDE -> value
        ProviderId.COPILOT,
        ProviderId.CURSOR -> value.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
    }
}

fun ProviderId.normalizedUsageLineLabelForDisplay(label: String): String {
    val value = label.trim().takeIf { it.isNotBlank() } ?: return "Usage"
    if (this != ProviderId.GEMINI) return value
    val compact = value.lowercase(Locale.US)
        .replace(Regex("""[^a-z0-9]+"""), "")
    return when {
        compact == "pro" || compact == "geminipro" -> "Gemini Pro"
        compact == "flash" || compact == "geminiflash" -> "Gemini Flash"
        compact == "deepresearch" || compact == "geminideepresearch" -> "Gemini Deep Research"
        else -> value
    }
}

fun ProviderId.isSupportedUsageLineLabel(label: String): Boolean {
    if (this != ProviderId.GEMINI) return true
    return when (normalizedUsageLineLabelForDisplay(label).lowercase(Locale.US)) {
        "gemini pro",
        "gemini flash",
        "gemini deep research" -> true
        else -> false
    }
}

fun ProviderUsageLine.hasStartOnMessageReset(): Boolean {
    val value = resetText?.trim()?.takeIf { it.isNotBlank() } ?: return false
    val normalized = value.lowercase(Locale.US)
    val isStartOnMessage = normalized == "starts when a message is sent" ||
        ("message" in normalized && "sent" in normalized && "start" in normalized) ||
        (value.contains("메시지") && value.contains("시작")) ||
        (value.contains("메세지") && value.contains("시작"))
    return isStartOnMessage && (remainingPercent == null || remainingPercent >= 0.995f)
}

fun ProviderId.deduplicateUsageLinesForStorage(lines: List<ProviderUsageLine>): List<ProviderUsageLine> {
    val deduped = LinkedHashMap<String, ProviderUsageLine>()
    lines.forEach { line ->
        val normalizedLine = if (this == ProviderId.GEMINI) {
            line.copy(label = normalizedUsageLineLabelForDisplay(line.label))
        } else {
            line
        }
        val key = usageLineDeduplicationKey(normalizedLine)
        val existing = deduped[key]
        if (existing == null || normalizedLine.isBetterStoredUsageLineThan(existing, this)) {
            deduped[key] = normalizedLine
        }
    }
    return deduped.values.toList()
}

private fun ProviderId.usageLineDeduplicationKey(line: ProviderUsageLine): String {
    if (this == ProviderId.GEMINI) {
        return normalizedUsageLineLabelForDisplay(line.label).lowercase(Locale.US)
    }
    if (this == ProviderId.CLAUDE) {
        claudeUsageWindowKey(line)?.let { return it }
    }
    if (this == ProviderId.COPILOT) {
        copilotUsageQuotaKey(line)?.let { return it }
    }
    return listOf(
        line.label,
        line.windowText.orEmpty(),
        line.category.orEmpty(),
        line.unit.orEmpty(),
        normalizedUsageSource(line.sourceLabel)
    ).joinToString("|").lowercase(Locale.US)
}

private fun claudeUsageWindowKey(line: ProviderUsageLine): String? {
    val label = line.label
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase(Locale.US)
    val compact = label.replace(Regex("""[^a-z0-9가-힣]+"""), "")
    val window = line.windowText.orEmpty().lowercase(Locale.US)
    return when {
        "omelette" in compact || "claudedesign" in compact || "design" in label -> "claude:seven_day_omelette"
        "fivehour" in compact || "5hour" in compact || "5시간" in compact || "5 hour" in window -> {
            "claude:five_hour"
        }
        "sevenday" in compact || "weekly" in compact || "주간" in compact || "7 day" in window -> {
            "claude:seven_day"
        }
        else -> null
    }
}

private fun copilotUsageQuotaKey(line: ProviderUsageLine): String? {
    val label = line.label
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase(Locale.US)
    val category = line.category.orEmpty().lowercase(Locale.US)
    return when {
        category == "messages" || label == "chat" -> "copilot:chat"
        category == "completions" || "completion" in label -> "copilot:completions"
        category == "premium_requests" || "premium" in label -> "copilot:premium_requests"
        else -> null
    }
}

private fun normalizedUsageSource(sourceLabel: String?): String {
    return sourceLabel.orEmpty()
        .replace(Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"""), ":id")
        .replace(Regex("""[A-Za-z0-9_-]{18,}"""), ":id")
}

private fun ProviderUsageLine.isBetterStoredUsageLineThan(
    existing: ProviderUsageLine,
    providerId: ProviderId
): Boolean {
    val score = storedUsageLineScore(providerId)
    val existingScore = existing.storedUsageLineScore(providerId)
    return when {
        score != existingScore -> score > existingScore
        (confidence ?: 0f) != (existing.confidence ?: 0f) -> (confidence ?: 0f) > (existing.confidence ?: 0f)
        else -> false
    }
}

private fun ProviderUsageLine.storedUsageLineScore(providerId: ProviderId): Int {
    var score = 0
    val source = sourceLabel.orEmpty().lowercase(Locale.US)
    if (providerId == ProviderId.CLAUDE && source.contains(Regex("""/api/organizations/[^/?#]+/usage(?:[?#]|$)"""))) {
        score += 64
    }
    if ((remainingPercent ?: 1f) < 0.995f) score += 32
    if (remainingPercent != null) score += 16
    if (!resetsAt.isNullOrBlank()) score += 8
    if (!resetText.isNullOrBlank()) score += 4
    if (!sourceLabel.isNullOrBlank()) score += 2
    if (hasStartOnMessageReset()) score -= 16
    return score
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
