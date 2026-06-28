package com.aiquota.mobile.local

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

fun ProviderUsageLine.effectiveResetText(now: Instant = Instant.now()): String? {
    val text = displayResetText(resetText, resetsAt, now)
    if (
        text == RESET_STARTS_WHEN_MESSAGE_SENT &&
        remainingPercent != null &&
        remainingPercent < 0.995f
    ) {
        return null
    }
    return text
}

fun displayResetText(resetText: String?, resetsAt: String?, now: Instant = Instant.now()): String? {
    val explicit = resetText?.trim()?.takeIf { it.isNotBlank() }
    if (explicit != null) {
        if (explicit.startsWith("reset", ignoreCase = true) ||
            explicit.startsWith("starts", ignoreCase = true)
        ) {
            return canonicalRelativeResetText(explicit) ?: explicit
        }
        parseInstantLike(explicit)?.let { return resetTextForInstant(it, now) }
        parseKoreanResetInstant(explicit, now)?.let { return resetTextForInstant(it, now) }
        return explicit
    }
    return resetsAt
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { value -> parseInstantLike(value)?.let { resetTextForInstant(it, now) } ?: value }
}

fun resetTextForInstant(resetAt: Instant, now: Instant = Instant.now()): String {
    val seconds = Duration.between(now, resetAt).seconds
    if (seconds <= 0L) return RESET_STARTS_WHEN_MESSAGE_SENT
    val minutesTotal = (seconds / 60L).coerceAtLeast(1L)
    val days = minutesTotal / (24L * 60L)
    val hours = (minutesTotal % (24L * 60L)) / 60L
    val minutes = minutesTotal % 60L
    return when {
        days > 0L -> "Resets in ${days}d ${hours}h"
        hours > 0L -> "Resets in ${hours}h ${minutes}m"
        else -> "Resets in ${minutes}m"
    }
}

private const val RESET_STARTS_WHEN_MESSAGE_SENT = "Starts when a message is sent"

private data class KoreanResetDate(
    val date: LocalDate,
    val explicitYear: Boolean
)

private fun parseKoreanResetInstant(value: String, now: Instant): Instant? {
    if (!value.contains("초기화")) return null
    val zone = ZoneId.systemDefault()
    val nowDateTime = LocalDateTime.ofInstant(now, zone)
    val resetTime = parseKoreanResetTime(value) ?: return null
    val resetDate = parseKoreanResetDate(value, nowDateTime.toLocalDate())
    var resetDateTime = LocalDateTime.of(resetDate?.date ?: nowDateTime.toLocalDate(), resetTime)
    if (!resetDateTime.isAfter(nowDateTime)) {
        resetDateTime = when {
            resetDate == null -> resetDateTime.plusDays(1)
            resetDate.explicitYear -> resetDateTime
            else -> resetDateTime.plusYears(1)
        }
    }
    return resetDateTime.atZone(zone).toInstant()
}

private fun parseKoreanResetDate(value: String, nowDate: LocalDate): KoreanResetDate? {
    val match = KoreanMonthDayRegex.find(value) ?: KoreanDottedDateRegex.find(value) ?: return null
    val yearText = match.groupValues[1].takeIf { it.isNotBlank() }
    val year = yearText?.toIntOrNull() ?: nowDate.year
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
    return KoreanResetDate(date, explicitYear = yearText != null)
}

private fun parseKoreanResetTime(value: String): LocalTime? {
    val match = KoreanTimeRegex.find(value) ?: return null
    val marker = match.groupValues[1]
    val rawHour = match.groupValues[2].toIntOrNull() ?: return null
    val minute = match.groupValues[3].toIntOrNull() ?: return null
    if (rawHour !in 1..12 || minute !in 0..59) return null
    val hour = when (marker) {
        "오전" -> rawHour % 12
        else -> (rawHour % 12) + 12
    }
    return LocalTime.of(hour, minute)
}

private fun canonicalRelativeResetText(value: String): String? {
    val match = Regex("""^Resets in\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(value) ?: return null
    val minutesTotal = durationMinutes(match.groupValues[1]) ?: return null
    val days = minutesTotal / (24L * 60L)
    if (days <= 0L) return null
    val hours = (minutesTotal % (24L * 60L)) / 60L
    return "Resets in ${days}d ${hours}h"
}

private fun durationMinutes(value: String): Long? {
    val matches = Regex("""(\d+)\s*([dhm])""", RegexOption.IGNORE_CASE).findAll(value).toList()
    if (matches.isEmpty()) return null
    return matches.sumOf { match ->
        val amount = match.groupValues[1].toLong()
        when (match.groupValues[2].lowercase()) {
            "d" -> amount * 24L * 60L
            "h" -> amount * 60L
            else -> amount
        }
    }
}

private fun parseInstantLike(value: String): Instant? {
    runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
    val epoch = value.toLongOrNull() ?: return null
    return when {
        epoch > 10_000_000_000L -> Instant.ofEpochMilli(epoch)
        epoch > 0L -> Instant.ofEpochSecond(epoch)
        else -> null
    }
}

private val KoreanMonthDayRegex = Regex("""(?:(\d{4})\s*[년./-]\s*)?(\d{1,2})\s*월\s*(\d{1,2})\s*일""")
private val KoreanDottedDateRegex = Regex("""(?:(\d{4})\s*[년./-]\s*)?(\d{1,2})\s*[./]\s*(\d{1,2})\s*[.]?""")
private val KoreanTimeRegex = Regex("""(오전|오후)\s*(\d{1,2})\s*[:：]\s*(\d{1,2})""")
