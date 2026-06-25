package com.aiquota.mobile.notification

import com.aiquota.mobile.widget.WidgetProviderGauge
import com.aiquota.mobile.widget.parseUnifiedWidgetPayload
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class UsageNotificationContent(
    val title: String,
    val summary: String,
    val compactTitle: String,
    val compactText: String,
    val updateMessage: String?,
    val gauges: List<WidgetProviderGauge>
) {
    val gaugeRows: List<UsageNotificationGaugeRow> = gauges.map { gauge ->
        UsageNotificationGaugeRow(
            providerId = gauge.providerId,
            remainingRatio = gauge.remainingRatio,
            compactRemainingText = gauge.remainingText.compactGaugeRemainingText(),
            remainingText = gauge.remainingText,
            resetText = gauge.resetText.orEmpty(),
            gaugeColorHex = gauge.gaugeColorHex
        )
    }
}

data class UsageNotificationGaugeRow(
    val providerId: String,
    val remainingRatio: Float,
    val compactRemainingText: String,
    val remainingText: String,
    val resetText: String,
    val gaugeColorHex: String?
)

fun buildUsageNotificationContent(
    snapshotJson: String,
    now: Instant = Instant.now(),
    updateMessage: String? = null
): UsageNotificationContent {
    val connectedProviderKeys = connectedNotificationProviderKeys(snapshotJson)
    val gauges = parseUnifiedWidgetPayload(snapshotJson, now).gauges
        .filter { gauge -> connectedProviderKeys.isEmpty() || gauge.providerId.notificationKey() in connectedProviderKeys }
        .take(MAX_NOTIFICATION_GAUGES)
    val summary = notificationSummary(gauges, snapshotJson)
        .withUpdateMessage(updateMessage)
    val compactLines = compactNotificationLines(summary)
    return UsageNotificationContent(
        title = "AI Quota",
        summary = summary,
        compactTitle = compactLines.first,
        compactText = compactLines.second,
        updateMessage = updateMessage?.trim()?.takeIf { it.isNotBlank() },
        gauges = gauges
    )
}

private fun String.withUpdateMessage(updateMessage: String?): String {
    val message = updateMessage?.trim()?.takeIf { it.isNotBlank() } ?: return this
    return "$this\n$message"
}

private fun notificationSummary(gauges: List<WidgetProviderGauge>, snapshotJson: String): String {
    if (gauges.isEmpty()) return "No active usage limits"
    val displayLabels = notificationDisplayLabels(snapshotJson)
    val items = gauges
        .map { gauge ->
            val label = compactNotificationLabel(
                providerId = gauge.providerId,
                label = displayLabels.nextLabel(gauge.providerId)
            )
            "$label ${gauge.remainingText.compactRemainingText()}"
        }
    if (items.size <= NOTIFICATION_SINGLE_LINE_MAX_ITEMS) return items.joinToString(" | ")
    return items.chunked(NOTIFICATION_SUMMARY_ITEMS_PER_LINE)
        .joinToString("\n") { line -> line.joinToString(" | ") }
}

private fun connectedNotificationProviderKeys(snapshotJson: String): Set<String> {
    if (snapshotJson.isBlank()) return emptySet()
    return runCatching {
        val providers = JSONObject(snapshotJson).optJSONArray(KEY_PROVIDERS) ?: JSONArray()
        buildSet {
            for (index in 0 until providers.length()) {
                val provider = providers.optJSONObject(index) ?: continue
                if (!provider.isVisibleNotificationProvider()) continue
                if (!provider.hasActiveNotificationConnection()) continue
                val providerId = provider.optionalString(KEY_PROVIDER_ID).orEmpty().ifBlank { UNKNOWN_PROVIDER_ID }
                add(providerId.notificationKey())
            }
        }
    }.getOrDefault(emptySet())
}

private fun notificationDisplayLabels(snapshotJson: String): NotificationDisplayLabels {
    if (snapshotJson.isBlank()) return NotificationDisplayLabels.EMPTY
    return runCatching {
        val providers = JSONObject(snapshotJson).optJSONArray(KEY_PROVIDERS) ?: JSONArray()
        val labelsByProviderId = mutableMapOf<String, ArrayDeque<String>>()
        for (index in 0 until providers.length()) {
            val provider = providers.optJSONObject(index) ?: continue
            if (!provider.isVisibleNotificationProvider()) continue
            val providerId = provider.optionalString(KEY_PROVIDER_ID).orEmpty().ifBlank { UNKNOWN_PROVIDER_ID }
            val label = provider.optionalString(KEY_DISPLAY_NAME)
                ?: provider.optionalString(KEY_LABEL)
                ?: continue
            labelsByProviderId.getOrPut(providerId.notificationKey()) { ArrayDeque() }.add(label)
        }
        NotificationDisplayLabels(labelsByProviderId)
    }.getOrDefault(NotificationDisplayLabels.EMPTY)
}

private class NotificationDisplayLabels(
    private val labelsByProviderId: Map<String, ArrayDeque<String>>
) {
    fun nextLabel(providerId: String): String? {
        return labelsByProviderId[providerId.notificationKey()]?.removeFirstOrNull()
    }

    companion object {
        val EMPTY = NotificationDisplayLabels(emptyMap())
    }
}

private fun JSONObject.isVisibleNotificationProvider(): Boolean {
    val status = optionalString(KEY_STATUS).orEmpty().trim().lowercase()
    if (status == "disabled") return false
    if (has(KEY_ENABLED) && !optBoolean(KEY_ENABLED, true)) return false
    if (has(KEY_ACTIVE) && !optBoolean(KEY_ACTIVE, true)) return false
    if (has(KEY_VISIBLE) && !optBoolean(KEY_VISIBLE, true)) return false
    if (has(KEY_HIDDEN) && optBoolean(KEY_HIDDEN, false)) return false
    return true
}

private fun JSONObject.hasActiveNotificationConnection(): Boolean {
    val connectionState = (optionalString(KEY_CONNECTION_STATE) ?: optionalString(KEY_STATUS))
        .orEmpty()
        .trim()
        .lowercase()
    if (connectionState.isBlank()) return true
    return connectionState in ACTIVE_NOTIFICATION_CONNECTION_STATES
}

private fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return when (val value = opt(name)) {
        is String -> value
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> null
    }?.takeIf { it.isNotBlank() }
}

private fun String.notificationKey(): String {
    return trim().lowercase()
}

private fun String.compactRemainingText(): String {
    return trim()
        .removeSuffix(" 남음")
        .removeSuffix(" left")
        .removeSuffix(" remaining")
        .removeSuffix("남음")
        .removeSuffix("left")
        .removeSuffix("remaining")
        .trim()
}

private fun String.compactGaugeRemainingText(): String {
    val value = compactRemainingText()
    return if (value.isBlank()) value else "$value 남음"
}

private fun compactNotificationLines(summary: String): Pair<String, String> {
    val lines = summary.lines()
    return if (lines.size > 1) {
        lines.first() to lines.drop(1).joinToString(" | ")
    } else {
        "AI Quota" to summary
    }
}

private fun providerLabel(providerId: String): String {
    return when (providerId.lowercase()) {
        "claude" -> "Claude"
        "codex", "openai" -> "Codex"
        "gemini" -> "Gemini"
        "copilot", "github-copilot", "github_copilot" -> "Copilot"
        "antigravity" -> "Antigravity"
        else -> providerId.replaceFirstChar { char -> char.uppercase() }
    }
}

private fun compactNotificationLabel(providerId: String, label: String?): String {
    val normalizedProviderId = providerId.lowercase()
    val value = label?.trim()?.takeIf { it.isNotBlank() } ?: providerLabel(providerId)
    return when {
        normalizedProviderId == "antigravity" ||
            value.equals("antigravity", ignoreCase = true) -> "AntiG"
        normalizedProviderId == "github-copilot" ||
            normalizedProviderId == "github_copilot" ||
            value.equals("GitHub Copilot", ignoreCase = true) -> "Copilot"
        else -> value
    }
}

private const val MAX_NOTIFICATION_GAUGES = 8
private const val NOTIFICATION_SINGLE_LINE_MAX_ITEMS = 3
private const val NOTIFICATION_SUMMARY_ITEMS_PER_LINE = 3
private const val KEY_PROVIDERS = "providers"
private const val KEY_PROVIDER_ID = "providerId"
private const val KEY_DISPLAY_NAME = "displayName"
private const val KEY_LABEL = "label"
private const val KEY_CONNECTION_STATE = "connectionState"
private const val KEY_STATUS = "status"
private const val KEY_ENABLED = "enabled"
private const val KEY_ACTIVE = "active"
private const val KEY_VISIBLE = "visible"
private const val KEY_HIDDEN = "hidden"
private const val UNKNOWN_PROVIDER_ID = "unknown"
private val ACTIVE_NOTIFICATION_CONNECTION_STATES = setOf("connected", "stale", "collecting", "connecting")
