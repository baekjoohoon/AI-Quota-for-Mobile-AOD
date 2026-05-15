package com.aiusage.mobile.widget

import org.json.JSONArray
import org.json.JSONObject
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale

data class WidgetProviderGauge(
    val providerId: String,
    val remainingRatio: Float,
    val remainingText: String,
    val resetText: String?
)

data class UnifiedWidgetPayload(
    val providers: List<ProviderWidgetPayload>,
    val gauges: List<WidgetProviderGauge>
) {
    companion object {
        val EMPTY = UnifiedWidgetPayload(providers = emptyList(), gauges = emptyList())
    }
}

data class ProviderWidgetPayload(
    val providerId: String,
    val displayName: String,
    val status: String,
    val visible: Boolean,
    val lines: List<ProviderWidgetLine>
)

data class ProviderWidgetLine(
    val label: String,
    val remainingPercent: Float?,
    val remainingText: String,
    val resetText: String?,
    val detailText: String?,
    val severity: String
)

fun parseWidgetProviderGauges(snapshotJson: String, now: Instant = Instant.now()): List<WidgetProviderGauge> {
    if (snapshotJson.isBlank()) return emptyList()
    return runCatching {
        val providers = JSONObject(snapshotJson).optJSONArray(KEY_PROVIDERS) ?: JSONArray()
        buildList {
            for (index in 0 until providers.length()) {
                if (size == MAX_WIDGET_GAUGES) break
                val provider = providers.optJSONObject(index) ?: continue
                if (!provider.isVisibleProvider()) continue
                val providerId = provider.providerId()
                val line = firstGaugeableLine(provider.optJSONArray(KEY_LINES) ?: JSONArray(), now)
                    ?: provider.errorGaugeLine()
                    ?: continue
                add(WidgetProviderGauge(providerId = providerId, remainingRatio = line.ratio, remainingText = line.remainingText, resetText = line.resetText))
            }
        }
    }.getOrDefault(emptyList())
}

fun parseUnifiedWidgetPayload(snapshotJson: String, now: Instant = Instant.now()): UnifiedWidgetPayload {
    if (snapshotJson.isBlank()) return UnifiedWidgetPayload.EMPTY
    return runCatching {
        val providers = JSONObject(snapshotJson).optJSONArray(KEY_PROVIDERS) ?: JSONArray()
        val providerPayloads = mutableListOf<ProviderWidgetPayload>()
        val gauges = mutableListOf<WidgetProviderGauge>()
        for (index in 0 until providers.length()) {
            val provider = providers.optJSONObject(index) ?: continue
            if (!provider.isVisibleProvider()) continue
            val providerId = provider.providerId()
            providerPayloads.add(provider.toProviderWidgetPayload(providerId))
            provider.toWidgetGauge(providerId, now)?.let { gauges.add(it) }
        }
        UnifiedWidgetPayload(providers = providerPayloads, gauges = gauges)
    }.getOrDefault(UnifiedWidgetPayload.EMPTY)
}

fun providerWidgetPayload(snapshotJson: String, providerId: String): ProviderWidgetPayload {
    val requestedProviderId = providerId.requestedProviderId()
    if (snapshotJson.isBlank()) return disconnectedProviderPayload(requestedProviderId)
    return runCatching {
        val providers = JSONObject(snapshotJson).optJSONArray(KEY_PROVIDERS) ?: JSONArray()
        var selected: ProviderWidgetPayload? = null
        for (index in 0 until providers.length()) {
            val provider = providers.optJSONObject(index) ?: continue
            val currentProviderId = provider.providerId()
            if (currentProviderId.equals(requestedProviderId, ignoreCase = true)) {
                selected = provider.toProviderWidgetPayload(currentProviderId)
                break
            }
        }
        selected ?: disconnectedProviderPayload(requestedProviderId)
    }.getOrDefault(disconnectedProviderPayload(requestedProviderId))
}

private fun JSONObject.isVisibleProvider(): Boolean {
    val status = optionalString(KEY_STATUS).orEmpty().trim().lowercase(Locale.US)
    if (status == "disabled") return false
    if (has(KEY_ENABLED) && !optBoolean(KEY_ENABLED, true)) return false
    if (has(KEY_ACTIVE) && !optBoolean(KEY_ACTIVE, true)) return false
    if (has(KEY_VISIBLE) && !optBoolean(KEY_VISIBLE, true)) return false
    if (has(KEY_HIDDEN) && optBoolean(KEY_HIDDEN, false)) return false
    return true
}

private fun JSONObject.errorGaugeLine(): ParsedGaugeLine? {
    if (!optionalString(KEY_STATUS).orEmpty().equals("error", ignoreCase = true)) return null
    return ParsedGaugeLine(
        ratio = 0f,
        remainingText = "Error",
        resetText = null
    )
}

private fun firstGaugeableLine(lines: JSONArray, now: Instant): ParsedGaugeLine? {
    for (index in 0 until lines.length()) {
        val line = lines.optJSONObject(index) ?: continue
        line.displayOnlyGaugeLine()?.let { return it }
        val limit = line.optNullableDouble(KEY_LIMIT) ?: continue
        if (limit <= 0.0) continue
        val remaining = line.optNullableDouble(KEY_REMAINING)
            ?: line.optNullableDouble(KEY_USED)?.let { used -> limit - used }
            ?: continue
        val ratio = (remaining / limit).coerceIn(0.0, 1.0)
        return ParsedGaugeLine(
            ratio = ratio.toFloat(),
            remainingText = "${formatNumber(ratio * 100)}% left",
            resetText = line.optionalString(KEY_RESETS_AT)?.let { resetText(it, now) }
        )
    }
    return null
}

private fun JSONObject.displayOnlyGaugeLine(): ParsedGaugeLine? {
    val remainingPercent = optNullableDouble(KEY_REMAINING_PERCENT) ?: return null
    val ratio = remainingPercent.coerceIn(0.0, 1.0)
    return ParsedGaugeLine(
        ratio = ratio.toFloat(),
        remainingText = optionalString(KEY_REMAINING_TEXT) ?: "${formatNumber(ratio * 100)}% left",
        resetText = optionalString(KEY_RESET_TEXT)
    )
}

private fun JSONObject.toWidgetGauge(providerId: String, now: Instant): WidgetProviderGauge? {
    val line = firstGaugeableLine(optJSONArray(KEY_LINES) ?: JSONArray(), now)
        ?: errorGaugeLine()
        ?: return null
    return WidgetProviderGauge(
        providerId = providerId,
        remainingRatio = line.ratio,
        remainingText = line.remainingText,
        resetText = line.resetText
    )
}

private fun JSONObject.toProviderWidgetPayload(providerId: String): ProviderWidgetPayload {
    val linesJson = optJSONArray(KEY_LINES) ?: JSONArray()
    return ProviderWidgetPayload(
        providerId = providerId,
        displayName = optionalString(KEY_DISPLAY_NAME) ?: defaultProviderDisplayName(providerId),
        status = optionalString(KEY_CONNECTION_STATE) ?: optionalString(KEY_STATUS) ?: DISCONNECTED_STATUS,
        visible = isVisibleProvider(),
        lines = buildList {
            for (index in 0 until linesJson.length()) {
                val line = linesJson.optJSONObject(index) ?: continue
                add(line.toProviderWidgetLine())
            }
        }
    )
}

private fun JSONObject.toProviderWidgetLine(): ProviderWidgetLine {
    val remainingPercent = optNullableDouble(KEY_REMAINING_PERCENT)?.coerceIn(0.0, 1.0)?.toFloat()
    return ProviderWidgetLine(
        label = optionalString(KEY_LABEL) ?: DEFAULT_LINE_LABEL,
        remainingPercent = remainingPercent,
        remainingText = optionalString(KEY_REMAINING_TEXT)
            ?: remainingPercent?.let { "${formatNumber(it.toDouble() * 100)}% left" }
            ?: "",
        resetText = optionalString(KEY_RESET_TEXT),
        detailText = optionalString(KEY_DETAIL_TEXT),
        severity = optionalString(KEY_SEVERITY) ?: UNKNOWN_SEVERITY
    )
}

private fun JSONObject.optNullableDouble(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    return opt(name)?.toString()?.toDoubleOrNull()
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

private fun JSONObject.providerId(): String {
    val value = optionalString(KEY_PROVIDER_ID)
    return if (value.isNullOrBlank()) UNKNOWN_PROVIDER_ID else value
}

private fun String.requestedProviderId(): String {
    return trim().ifBlank { UNKNOWN_PROVIDER_ID }
}

private fun disconnectedProviderPayload(providerId: String): ProviderWidgetPayload {
    return ProviderWidgetPayload(
        providerId = providerId,
        displayName = defaultProviderDisplayName(providerId),
        status = DISCONNECTED_STATUS,
        visible = true,
        lines = emptyList()
    )
}

private fun defaultProviderDisplayName(providerId: String): String {
    return when (providerId.trim().lowercase(Locale.US)) {
        "claude" -> "Claude"
        "codex" -> "Codex"
        "gemini" -> "Gemini"
        "copilot" -> "GitHub Copilot"
        "cursor" -> "Cursor"
        UNKNOWN_PROVIDER_ID -> "Unknown"
        else -> providerId.ifBlank { "Unknown" }
    }
}

private fun resetText(value: String, now: Instant): String? {
    return runCatching {
        val seconds = (Instant.parse(value).epochSecond - now.epochSecond).coerceAtLeast(0L)
        val days = seconds / (24 * 60 * 60)
        val hours = (seconds % (24 * 60 * 60)) / (60 * 60)
        val minutes = (seconds % (60 * 60)) / 60
        when {
            days > 0 -> "Resets in ${days}d ${hours}h"
            hours > 0 -> "Resets in ${hours}h ${minutes}m"
            else -> "Resets in ${minutes}m"
        }
    }.getOrNull()
}

private fun formatNumber(value: Double): String {
    return DECIMAL_FORMAT.format(value)
}

private data class ParsedGaugeLine(
    val ratio: Float,
    val remainingText: String,
    val resetText: String?
)

private const val MAX_WIDGET_GAUGES = 4
private const val KEY_PROVIDERS = "providers"
private const val KEY_PROVIDER_ID = "providerId"
private const val KEY_DISPLAY_NAME = "displayName"
private const val KEY_CONNECTION_STATE = "connectionState"
private const val KEY_STATUS = "status"
private const val KEY_ENABLED = "enabled"
private const val KEY_ACTIVE = "active"
private const val KEY_VISIBLE = "visible"
private const val KEY_HIDDEN = "hidden"
private const val KEY_LINES = "lines"
private const val KEY_LABEL = "label"
private const val KEY_LIMIT = "limit"
private const val KEY_USED = "used"
private const val KEY_REMAINING = "remaining"
private const val KEY_RESETS_AT = "resetsAt"
private const val KEY_REMAINING_PERCENT = "remainingPercent"
private const val KEY_REMAINING_TEXT = "remainingText"
private const val KEY_RESET_TEXT = "resetText"
private const val KEY_DETAIL_TEXT = "detailText"
private const val KEY_SEVERITY = "severity"
private const val UNKNOWN_PROVIDER_ID = "unknown"
private const val DISCONNECTED_STATUS = "DISCONNECTED"
private const val DEFAULT_LINE_LABEL = "Usage"
private const val UNKNOWN_SEVERITY = "UNKNOWN"
private val DECIMAL_FORMAT = DecimalFormat("0.#", DecimalFormatSymbols(Locale.US)).apply {
    roundingMode = RoundingMode.HALF_UP
}
