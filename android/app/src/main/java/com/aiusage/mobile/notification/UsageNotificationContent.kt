package com.aiusage.mobile.notification

import com.aiusage.mobile.widget.WidgetProviderGauge
import com.aiusage.mobile.widget.parseWidgetProviderGauges
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class UsageNotificationContent(
    val title: String,
    val summary: String,
    val compactTitle: String,
    val compactText: String,
    val gauges: List<WidgetProviderGauge>
) {
    val gaugeRows: List<UsageNotificationGaugeRow> = gauges.map { gauge ->
        UsageNotificationGaugeRow(
            providerId = gauge.providerId,
            remainingRatio = gauge.remainingRatio,
            remainingText = gauge.remainingText,
            resetText = gauge.resetText.orEmpty()
        )
    }
}

data class UsageNotificationGaugeRow(
    val providerId: String,
    val remainingRatio: Float,
    val remainingText: String,
    val resetText: String
)

fun buildUsageNotificationContent(
    snapshotJson: String,
    now: Instant = Instant.now()
): UsageNotificationContent {
    val gauges = parseWidgetProviderGauges(snapshotJson, now).take(MAX_NOTIFICATION_GAUGES)
    val summary = notificationSummary(gauges, snapshotJson)
    val compactLines = compactNotificationLines(summary)
    return UsageNotificationContent(
        title = "AI Usage",
        summary = summary,
        compactTitle = compactLines.first,
        compactText = compactLines.second,
        gauges = gauges
    )
}

private fun notificationSummary(gauges: List<WidgetProviderGauge>, snapshotJson: String): String {
    if (gauges.isEmpty()) return "No active usage limits"
    val displayLabels = notificationDisplayLabels(snapshotJson)
    val items = gauges
        .map { gauge ->
            "${displayLabels.nextLabel(gauge.providerId) ?: providerLabel(gauge.providerId)} ${gauge.remainingText.removeSuffix(" left")}"
        }
    if (items.size <= NOTIFICATION_SINGLE_LINE_MAX_ITEMS) return items.joinToString(" | ")
    return items.chunked(NOTIFICATION_SUMMARY_ITEMS_PER_LINE)
        .joinToString("\n") { line -> line.joinToString(" | ") }
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

private fun compactNotificationLines(summary: String): Pair<String, String> {
    val lines = summary.lines()
    return if (lines.size > 1) {
        lines.first() to lines.drop(1).joinToString(" | ")
    } else {
        "AI Usage" to summary
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

private const val MAX_NOTIFICATION_GAUGES = 4
private const val NOTIFICATION_SINGLE_LINE_MAX_ITEMS = 3
private const val NOTIFICATION_SUMMARY_ITEMS_PER_LINE = 2
private const val KEY_PROVIDERS = "providers"
private const val KEY_PROVIDER_ID = "providerId"
private const val KEY_DISPLAY_NAME = "displayName"
private const val KEY_LABEL = "label"
private const val KEY_STATUS = "status"
private const val KEY_ENABLED = "enabled"
private const val KEY_ACTIVE = "active"
private const val KEY_VISIBLE = "visible"
private const val KEY_HIDDEN = "hidden"
private const val UNKNOWN_PROVIDER_ID = "unknown"
