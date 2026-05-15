package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.local.UsageSeverity
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object TextUsageExtractor {
    fun extract(providerId: ProviderId, visibleText: String): ProviderUsageSnapshot {
        val normalized = normalize(visibleText)
        val usageMatch = USAGE_PERCENT.find(normalized.fullText)
            ?: return ProviderUsageSnapshot.unavailable(
                providerId = providerId,
                message = "No visible usage percentage found for ${providerId.displayName}."
            )

        val percent = usageMatch.groupValues[1].toDoubleOrNull() ?: return ProviderUsageSnapshot.unavailable(
            providerId = providerId,
            message = "No readable usage percentage found for ${providerId.displayName}."
        )
        val ratio = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
        val remainingWord = usageMatch.groupValues[2].lowercase(Locale.US)
        val line = ProviderUsageLine(
            label = "Usage",
            remainingPercent = ratio,
            remainingText = "${formatPercent(percent)}% $remainingWord",
            resetText = resetText(normalized),
            severity = severityFor(ratio)
        )

        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.IDLE,
            lines = listOf(line)
        )
    }

    private fun normalize(visibleText: String): NormalizedText {
        val lines = visibleText
            .replace('\u00A0', ' ')
            .lines()
            .map { line -> line.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotBlank() }
        return NormalizedText(lines = lines, fullText = lines.joinToString(" "))
    }

    private fun resetText(normalized: NormalizedText): String? {
        val match = normalized.lines.firstNotNullOfOrNull { line -> RESET_PHRASE.find(line) }
            ?: RESET_PHRASE.find(normalized.fullText)
            ?: return null
        return match.value
            .replace(WHITESPACE, " ")
            .trim()
            .replaceFirstChar { char -> char.uppercase() }
    }

    private fun severityFor(ratio: Float): UsageSeverity {
        return when {
            ratio < DANGER_THRESHOLD -> UsageSeverity.DANGER
            ratio < WARNING_THRESHOLD -> UsageSeverity.WARNING
            else -> UsageSeverity.NORMAL
        }
    }

    private fun formatPercent(value: Double): String {
        return DecimalFormat("0.#", DecimalFormatSymbols(Locale.US)).apply {
            roundingMode = RoundingMode.HALF_UP
        }.format(value)
    }

    private data class NormalizedText(
        val lines: List<String>,
        val fullText: String
    )

    private val WHITESPACE = Regex("\\s+")
    private val USAGE_PERCENT = Regex("""\b([0-9]{1,3}(?:\.[0-9]+)?)\s*%\s*(left|remaining)\b""", RegexOption.IGNORE_CASE)
    private val RESET_PHRASE = Regex("""\b(?:resets?|reset)\b(?:\s+[^.!?;|,]+)?""", RegexOption.IGNORE_CASE)
    private const val DANGER_THRESHOLD = 0.15f
    private const val WARNING_THRESHOLD = 0.35f
}
