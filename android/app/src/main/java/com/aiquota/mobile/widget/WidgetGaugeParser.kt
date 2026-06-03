package com.aiquota.mobile.widget

import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesCodec
import com.aiquota.mobile.local.displayResetText
import com.aiquota.mobile.local.displayRemainingText
import com.aiquota.mobile.local.displayResetTextForLocale
import com.aiquota.mobile.local.displayUsageLabel
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
    val severity: String,
    val usedAmount: Double? = null,
    val limitAmount: Double? = null,
    val remainingAmount: Double? = null,
    val unit: String? = null,
    val category: String? = null,
    val windowText: String? = null,
    val startsAt: String? = null,
    val resetsAt: String? = null,
    val confidence: Float? = null
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
                add(
                    WidgetProviderGauge(
                        providerId = providerId,
                        remainingRatio = line.ratio,
                        remainingText = displayRemainingText(line.remainingText),
                        resetText = displayResetTextForLocale(line.resetText)
                    )
                )
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
            providerPayloads.add(provider.toProviderWidgetPayload(providerId, now))
            provider.toWidgetGauge(providerId, now)?.let { gauges.add(it) }
        }
        UnifiedWidgetPayload(providers = providerPayloads, gauges = gauges)
    }.getOrDefault(UnifiedWidgetPayload.EMPTY)
}

fun dashboardWidgetPayload(
    snapshotJson: String,
    order: List<ProviderId>,
    hidden: Set<ProviderId>,
    now: Instant = Instant.now()
): UnifiedWidgetPayload {
    return parseUnifiedWidgetPayload(snapshotJson, now).withProviderOrder(order, hidden)
}

fun providerWidgetPayload(
    snapshotJson: String,
    providerId: String,
    now: Instant = Instant.now()
): ProviderWidgetPayload {
    val requestedProviderId = providerId.requestedProviderId()
    if (snapshotJson.isBlank()) return disconnectedProviderPayload(requestedProviderId)
    return runCatching {
        val providers = JSONObject(snapshotJson).optJSONArray(KEY_PROVIDERS) ?: JSONArray()
        var selected: ProviderWidgetPayload? = null
        for (index in 0 until providers.length()) {
            val provider = providers.optJSONObject(index) ?: continue
            val currentProviderId = provider.providerId()
            if (currentProviderId.equals(requestedProviderId, ignoreCase = true)) {
                selected = provider.toProviderWidgetPayload(currentProviderId, now)
                break
            }
        }
        selected ?: disconnectedProviderPayload(requestedProviderId)
    }.getOrDefault(disconnectedProviderPayload(requestedProviderId))
}

private fun UnifiedWidgetPayload.withProviderOrder(
    order: List<ProviderId>,
    hidden: Set<ProviderId>
): UnifiedWidgetPayload {
    val hiddenIds = hidden.map { it.storageId.lowercase(Locale.US) }.toSet()
    val orderedIds = ProviderPreferencesCodec.visibleProviders(order, hidden)
        .map { it.storageId.lowercase(Locale.US) }
    val orderedIdSet = orderedIds.toSet()
    val providersById = providers.associateBy { it.providerId.lowercase(Locale.US) }
    val gaugesById = gauges.associateBy { it.providerId.lowercase(Locale.US) }
    val orderedProviders = orderedIds.mapNotNull { providersById[it] } +
        providers.filter { provider ->
            val id = provider.providerId.lowercase(Locale.US)
            id !in orderedIdSet && id !in hiddenIds
        }
    val orderedGauges = orderedIds.mapNotNull { gaugesById[it] } +
        gauges.filter { gauge ->
            val id = gauge.providerId.lowercase(Locale.US)
            id !in orderedIdSet && id !in hiddenIds
        }
    return copy(providers = orderedProviders, gauges = orderedGauges)
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
        line.displayOnlyGaugeLine(now)?.let { return it }
        val limit = line.optNullableDouble(KEY_LIMIT) ?: continue
        if (limit <= 0.0) continue
        val remaining = line.optNullableDouble(KEY_REMAINING)
            ?: line.optNullableDouble(KEY_USED)?.let { used -> limit - used }
            ?: continue
        val ratio = (remaining / limit).coerceIn(0.0, 1.0)
        val unit = line.optionalString(KEY_UNIT)
        return ParsedGaugeLine(
            ratio = ratio.toFloat(),
            remainingText = amountRemainingText(remaining, limit, unit),
            resetText = line.optionalString(KEY_RESETS_AT)?.let { resetText(it, now) }
        )
    }
    return null
}

private fun JSONObject.displayOnlyGaugeLine(now: Instant): ParsedGaugeLine? {
    val remainingPercent = optNullableDouble(KEY_REMAINING_PERCENT) ?: return null
    val ratio = remainingPercent.coerceIn(0.0, 1.0)
    return ParsedGaugeLine(
        ratio = ratio.toFloat(),
        remainingText = optionalString(KEY_REMAINING_TEXT) ?: "${formatNumber(ratio * 100)}% left",
        resetText = displayResetText(optionalString(KEY_RESET_TEXT), optionalString(KEY_RESETS_AT), now)
    )
}

private fun JSONObject.toWidgetGauge(providerId: String, now: Instant): WidgetProviderGauge? {
    val line = firstGaugeableLine(optJSONArray(KEY_LINES) ?: JSONArray(), now)
        ?: errorGaugeLine()
        ?: return null
    return WidgetProviderGauge(
        providerId = providerId,
        remainingRatio = line.ratio,
        remainingText = displayRemainingText(line.remainingText),
        resetText = displayResetTextForLocale(line.resetText)
    )
}

private fun JSONObject.toProviderWidgetPayload(providerId: String, now: Instant): ProviderWidgetPayload {
    val linesJson = optJSONArray(KEY_LINES) ?: JSONArray()
    return ProviderWidgetPayload(
        providerId = providerId,
        displayName = normalizedProviderDisplayName(providerId, optionalString(KEY_DISPLAY_NAME)),
        status = optionalString(KEY_CONNECTION_STATE) ?: optionalString(KEY_STATUS) ?: DISCONNECTED_STATUS,
        visible = isVisibleProvider(),
        lines = buildList {
            for (index in 0 until linesJson.length()) {
                val line = linesJson.optJSONObject(index) ?: continue
                add(line.toProviderWidgetLine(providerId, index, now))
            }
        }
    )
}

private fun JSONObject.toProviderWidgetLine(providerId: String, lineIndex: Int, now: Instant): ProviderWidgetLine {
    val used = optNullableDouble(KEY_USED)
    val limit = optNullableDouble(KEY_LIMIT)
    val remaining = optNullableDouble(KEY_REMAINING)
        ?: if (used != null && limit != null) limit - used else null
    val remainingPercent = optNullableDouble(KEY_REMAINING_PERCENT)?.coerceIn(0.0, 1.0)?.toFloat()
        ?: if (remaining != null && limit != null && limit > 0.0) {
            (remaining / limit).coerceIn(0.0, 1.0).toFloat()
        } else {
            null
        }
    val unit = optionalString(KEY_UNIT)
    return ProviderWidgetLine(
        label = compactWidgetUsageLabel(
            providerId,
            displayUsageLabel(providerId, optionalString(KEY_LABEL) ?: DEFAULT_LINE_LABEL, lineIndex)
        ),
        remainingPercent = remainingPercent,
        remainingText = displayRemainingText(optionalString(KEY_REMAINING_TEXT)
            ?: if (remaining != null && limit != null && limit > 0.0) amountRemainingText(remaining, limit, unit) else null
            ?: remainingPercent?.let { "${formatNumber(it.toDouble() * 100)}% left" }
            ?: ""),
        resetText = displayResetTextForLocale(displayResetText(optionalString(KEY_RESET_TEXT), optionalString(KEY_RESETS_AT), now)),
        detailText = optionalString(KEY_DETAIL_TEXT),
        severity = optionalString(KEY_SEVERITY) ?: UNKNOWN_SEVERITY,
        usedAmount = used,
        limitAmount = limit,
        remainingAmount = remaining,
        unit = unit,
        category = optionalString(KEY_CATEGORY),
        windowText = optionalString(KEY_WINDOW_TEXT),
        startsAt = optionalString(KEY_STARTS_AT),
        resetsAt = optionalString(KEY_RESETS_AT),
        confidence = optNullableDouble(KEY_CONFIDENCE)?.coerceIn(0.0, 1.0)?.toFloat()
    )
}

private fun compactWidgetUsageLabel(providerId: String, label: String): String {
    val labelWithoutProvider = stripProviderNamePrefix(providerId, label)
    if (!providerId.equals("antigravity", ignoreCase = true)) return labelWithoutProvider
    val compactGeminiFlash = Regex(
        """^Gemini\s+(3\.5)\s+Flash\s+\((High|Medium|Low)\)$""",
        RegexOption.IGNORE_CASE
    ).replace(labelWithoutProvider) { match ->
        val tier = when (match.groupValues[2].lowercase(Locale.US)) {
            "high" -> "H"
            "medium" -> "M"
            else -> "L"
        }
        "${match.groupValues[1]} Flash($tier)"
    }
    return compactGeminiFlash
        .replace(Regex("""\s+\((High)\)$""", RegexOption.IGNORE_CASE), " (H)")
        .replace(Regex("""\s+\((Medium)\)$""", RegexOption.IGNORE_CASE), " (M)")
        .replace(Regex("""\s+\((Low)\)$""", RegexOption.IGNORE_CASE), " (L)")
        .replace(Regex("""\s+\((Thinking)\)$""", RegexOption.IGNORE_CASE), " (T)")
        .replace("Flash Image", "Flash Img")
}

private fun stripProviderNamePrefix(providerId: String, label: String): String {
    val value = label.trim()
    val providerAliases = providerLabelPrefixes(providerId)
    providerAliases.forEach { prefix ->
        val pattern = Regex("""^\s*${Regex.escape(prefix)}(?:\s*[:/\-]\s*|\s+)(.+)$""", RegexOption.IGNORE_CASE)
        val stripped = pattern.matchEntire(value)?.groupValues?.getOrNull(1)?.trim()
        if (!stripped.isNullOrBlank()) return stripped
    }
    return value
}

private fun providerLabelPrefixes(providerId: String): List<String> {
    return when (providerId.trim().lowercase(Locale.US)) {
        "claude" -> listOf("Claude")
        "codex", "openai" -> listOf("Codex", "OpenAI")
        "gemini" -> listOf("Gemini", "Google Gemini")
        "copilot", "github-copilot", "github_copilot" -> listOf("Copilot", "GitHub Copilot")
        "antigravity" -> listOf("Antigravity")
        "cursor" -> listOf("Cursor")
        else -> listOf(providerId)
    }
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
        "copilot" -> "Copilot"
        "cursor" -> "Cursor"
        UNKNOWN_PROVIDER_ID -> "Unknown"
        else -> providerId.ifBlank { "Unknown" }
    }
}

private fun normalizedProviderDisplayName(providerId: String, displayName: String?): String {
    if (
        providerId.trim().equals("copilot", ignoreCase = true) &&
        displayName?.trim().equals("GitHub Copilot", ignoreCase = true)
    ) {
        return defaultProviderDisplayName(providerId)
    }
    return displayName?.trim()?.takeIf { it.isNotBlank() } ?: defaultProviderDisplayName(providerId)
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

private fun amountRemainingText(remaining: Double, limit: Double, unit: String?): String {
    val unitText = unit?.trim().orEmpty()
    if (unitText.isBlank()) {
        val ratio = if (limit > 0.0) remaining / limit else 0.0
        return "${formatNumber(ratio.coerceIn(0.0, 1.0) * 100)}% left"
    }
    val unitSuffix = if (unitText.isNotBlank()) " $unitText" else ""
    return "${formatNumber(remaining.coerceAtLeast(0.0))} of ${formatNumber(limit)}$unitSuffix left"
}

private data class ParsedGaugeLine(
    val ratio: Float,
    val remainingText: String,
    val resetText: String?
)

private const val MAX_WIDGET_GAUGES = 6
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
private const val KEY_UNIT = "unit"
private const val KEY_CATEGORY = "category"
private const val KEY_WINDOW_TEXT = "windowText"
private const val KEY_STARTS_AT = "startsAt"
private const val KEY_RESETS_AT = "resetsAt"
private const val KEY_CONFIDENCE = "confidence"
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
