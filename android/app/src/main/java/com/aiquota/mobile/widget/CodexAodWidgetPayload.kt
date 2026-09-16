package com.aiquota.mobile.widget

import com.aiquota.mobile.local.ProviderId
import java.util.Locale
import kotlin.math.roundToInt

internal data class CodexAodQuotaRow(
    val percentText: String,
    val resetText: String
)

internal data class CodexAodWidgetPayload(
    val fiveHour: CodexAodQuotaRow,
    val weekly: CodexAodQuotaRow
)

internal fun codexAodWidgetPayload(snapshotJson: String): CodexAodWidgetPayload {
    val provider = providerWidgetPayload(
        snapshotJson = snapshotJson,
        providerId = ProviderId.CODEX.storageId
    )
    return CodexAodWidgetPayload(
        fiveHour = findFiveHourLine(provider.lines).toAodQuotaRow(),
        weekly = findWeeklyLine(provider.lines).toAodQuotaRow()
    )
}

internal fun findFiveHourLine(lines: List<ProviderWidgetLine>): ProviderWidgetLine? {
    return lines.firstOrNull { line ->
        val label = line.label.normalizedAodLabel()
        !label.contains("spark") &&
            !label.isWeeklyLabel() &&
            (label.contains("session") ||
                label.contains("5h") ||
                label.contains("5 hour") ||
                label.contains("5-hour") ||
                label.contains("5시간") ||
                label.contains("세션"))
    } ?: lines.firstOrNull { line ->
        val label = line.label.normalizedAodLabel()
        !label.contains("spark") && !label.isWeeklyLabel()
    }
}

internal fun findWeeklyLine(lines: List<ProviderWidgetLine>): ProviderWidgetLine? {
    return lines.firstOrNull { line ->
        val label = line.label.normalizedAodLabel()
        !label.contains("spark") && label.isWeeklyLabel()
    }
}

internal fun compactResetText(resetText: String?): String {
    val text = resetText?.trim().orEmpty()
    if (text.isBlank()) return ""
    return text
        .replace(Regex("^Resets\\s+in\\s+", RegexOption.IGNORE_CASE), "")
        .removeSuffix(" 후 초기화")
        .trim()
}

private fun ProviderWidgetLine?.toAodQuotaRow(): CodexAodQuotaRow {
    return CodexAodQuotaRow(
        percentText = this?.remainingPercent
            ?.let { ratio -> "${(ratio.coerceIn(0f, 1f) * 100f).roundToInt()}%" }
            ?: "--",
        resetText = compactResetText(this?.resetText)
    )
}

private fun String.normalizedAodLabel(): String = trim().lowercase(Locale.US)

private fun String.isWeeklyLabel(): Boolean {
    return contains("weekly") || contains("week") || contains("주간")
}
