package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderId
import java.util.Locale

internal fun ProviderUsageLine.isTrustedCursorUsageLine(): Boolean {
    val normalizedLabel = label
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase(Locale.US)
    val normalizedUnit = unit.orEmpty().trim().lowercase(Locale.US)
    val normalizedSource = sourceLabel.orEmpty().trim().lowercase(Locale.US)
    val allText = listOf(normalizedLabel, normalizedUnit, normalizedSource).joinToString(" ")

    if (Regex("""\b(sitemap|completed)\b""").containsMatchIn(allText)) return false
    if (normalizedLabel == "md" || normalizedUnit == "md") return false
    if (normalizedSource == "/" && normalizedLabel !in CURSOR_USAGE_LABELS) return false

    val modelRequestLine = normalizedLabel.matches(Regex("""gpt\s*\d.*""")) ||
        normalizedLabel.matches(Regex("""claude\s*\d.*""")) ||
        normalizedLabel in setOf("request usage", "requests")
    val knownUsageLine = normalizedLabel in CURSOR_USAGE_LABELS || modelRequestLine
    if (!knownUsageLine) return false

    val unsupportedDashboardPercentLine = remainingPercent != null &&
        usedAmount == null &&
        limitAmount == null &&
        remainingAmount == null &&
        normalizedSource == "/dashboard"
    if (unsupportedDashboardPercentLine) return false

    val trustedSource = CURSOR_USAGE_SOURCES.any { it in normalizedSource }
    val hasWindowEvidence = !startsAt.isNullOrBlank() ||
        !resetsAt.isNullOrBlank() ||
        !resetText.isNullOrBlank()
    val hasAmountEvidence = (limitAmount != null && limitAmount >= 0.0) ||
        (remainingAmount != null && remainingAmount >= 0.0) ||
        (usedAmount != null && usedAmount >= 0.0) ||
        remainingPercent != null
    val hasSchemaContext = normalizedUnit in setOf("usd", "requests", "request", "percent") ||
        category.orEmpty().lowercase(Locale.US) in CURSOR_USAGE_CATEGORIES

    return hasAmountEvidence && (trustedSource || hasWindowEvidence || hasSchemaContext)
}

internal fun isCursorAuthenticatorUrl(provider: ProviderId, url: String?): Boolean {
    if (provider != ProviderId.CURSOR) return false
    val candidateUrl = url ?: return false
    return CURSOR_AUTHENTICATOR_HOST.containsMatchIn(candidateUrl)
}

private val CURSOR_USAGE_LABELS = setOf(
    "included usage",
    "planusage",
    "plan usage",
    "total usage",
    "auto usage",
    "api usage",
    "on demand",
    "on demand usage",
    "on-demand",
    "on-demand usage"
)

private val CURSOR_USAGE_SOURCES = listOf(
    "/dashboard",
    "/api/dashboard",
    "/api/usage",
    "/api/usage-summary",
    "/api/subscription",
    "/auth/usage",
    "planusage",
    "requestusage",
    "individualusage"
)

private val CURSOR_USAGE_CATEGORIES = setOf(
    "usage_window",
    "included_usage",
    "fast_requests",
    "requests",
    "messages"
)

private val CURSOR_AUTHENTICATOR_HOST = Regex(
    pattern = """^https://authenticator\.cursor\.sh(?:[/?#]|$)""",
    option = RegexOption.IGNORE_CASE
)
