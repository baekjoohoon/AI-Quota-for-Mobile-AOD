package com.aiusage.mobile.local

import java.util.Locale

fun ProviderUsageLine.displayUsageLabel(
    providerId: ProviderId,
    lineIndex: Int,
    locale: Locale = Locale.getDefault()
): String {
    return displayUsageLabel(providerId.storageId, label, lineIndex, locale)
}

fun displayUsageLabel(
    providerId: String,
    rawLabel: String,
    lineIndex: Int,
    locale: Locale = Locale.getDefault()
): String {
    val label = rawLabel.trim().ifBlank { "Usage" }
    val normalized = label
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
        .lowercase(Locale.US)

    if (!locale.isKoreanLanguage()) {
        return englishUsageLabel(providerId, label, normalized, lineIndex)
    }

    if (providerId.equals(ProviderId.CLAUDE.storageId, ignoreCase = true) && normalized == "rate limit") {
        return if (lineIndex == 0) "세션" else "주간 한도"
    }

    return when (normalized) {
        "codex 5 hour limit", "5 hour limit" -> "Codex 5시간 한도"
        "codex weekly limit" -> "Codex 주간 한도"
        "spark weekly" -> "Spark 주간 한도"
        "five hour", "five hour limit" -> "Claude 5시간 한도"
        "seven day", "seven day limit" -> "Claude 주간 한도"
        "seven day omelette", "claude design" -> "Claude Design"
        "session" -> "세션"
        "weekly", "weekly limit" -> "주간 한도"
        "credits" -> "크레딧"
        "chat" -> "채팅"
        "completions", "code completions" -> "자동완성"
        "total usage" -> "전체 사용량"
        "auto usage" -> "자동 사용량"
        "api usage" -> "API 사용량"
        "on demand", "on demand usage" -> "온디맨드 사용량"
        "included usage" -> "포함 사용량"
        "premium requests" -> "프리미엄 요청"
        "fast requests" -> "빠른 요청"
        "messages" -> "메시지"
        "weekly messages" -> "주간 메시지"
        "usage" -> "사용량"
        else -> {
            when {
                normalized.endsWith(" messages") -> {
                    label.replace(Regex("""(?i)\bmessages\b"""), "메시지")
                }
                normalized.endsWith(" requests") -> {
                    label.replace(Regex("""(?i)\brequests\b"""), "요청")
                }
                else -> label
            }
        }
    }
}

fun displayRemainingText(text: String, locale: Locale = Locale.getDefault()): String {
    if (!locale.isKoreanLanguage()) return text
    val value = text.trim()
    if (value.isBlank()) return value

    Regex("""^(.+?)\s+credits?\s+left$""", RegexOption.IGNORE_CASE)
        .matchEntire(value)
        ?.let { return "${it.groupValues[1]} 크레딧 남음" }

    Regex("""^(.+?)\s+left$""", RegexOption.IGNORE_CASE)
        .matchEntire(value)
        ?.let { return "${it.groupValues[1]} 남음" }

    Regex("""^(.+?)\s+remaining$""", RegexOption.IGNORE_CASE)
        .matchEntire(value)
        ?.let { return "${it.groupValues[1]} 남음" }

    return value
}

fun displayResetTextForLocale(text: String?, locale: Locale = Locale.getDefault()): String? {
    val value = text?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (!locale.isKoreanLanguage()) return value

    if (value.equals("Starts when a message is sent", ignoreCase = true)) {
        return "메시지를 보내면 시작"
    }

    Regex("""^Resets in\s+(.+)$""", RegexOption.IGNORE_CASE)
        .matchEntire(value)
        ?.let { return "${durationTextToKorean(it.groupValues[1])} 후 초기화" }

    Regex("""^Runs out in\s+(.+)$""", RegexOption.IGNORE_CASE)
        .matchEntire(value)
        ?.let { return "${durationTextToKorean(it.groupValues[1])} 후 소진" }

    return value
}

fun isKoreanLocale(locale: Locale = Locale.getDefault()): Boolean {
    return locale.isKoreanLanguage()
}

private fun englishUsageLabel(
    providerId: String,
    label: String,
    normalized: String,
    lineIndex: Int
): String {
    if (providerId.equals(ProviderId.CLAUDE.storageId, ignoreCase = true) && normalized == "rate limit") {
        return if (lineIndex == 0) "Session" else "Weekly"
    }
    return when (normalized) {
        "five hour", "five hour limit" -> "Claude 5-hour limit"
        "seven day", "seven day limit" -> "Claude weekly limit"
        "seven day omelette" -> "Claude Design"
        else -> label
    }
}

private fun Locale.isKoreanLanguage(): Boolean {
    return language.equals("ko", ignoreCase = true)
}

private fun durationTextToKorean(value: String): String {
    val parts = Regex("""(\d+)\s*([dhm])""", RegexOption.IGNORE_CASE)
        .findAll(value)
        .map { match ->
            val amount = match.groupValues[1]
            when (match.groupValues[2].lowercase(Locale.US)) {
                "d" -> "${amount}일"
                "h" -> "${amount}시간"
                else -> "${amount}분"
            }
        }
        .toList()
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ") ?: value
}
