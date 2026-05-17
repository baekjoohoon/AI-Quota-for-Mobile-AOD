package com.aiusage.mobile.local

import java.time.Duration
import java.time.Instant

fun ProviderUsageLine.effectiveResetText(now: Instant = Instant.now()): String? {
    return displayResetText(resetText, resetsAt, now)
}

fun displayResetText(resetText: String?, resetsAt: String?, now: Instant = Instant.now()): String? {
    val explicit = resetText?.trim()?.takeIf { it.isNotBlank() }
    if (explicit != null) {
        if (explicit.startsWith("reset", ignoreCase = true) ||
            explicit.startsWith("starts", ignoreCase = true)
        ) {
            return explicit
        }
        parseInstantLike(explicit)?.let { return resetTextForInstant(it, now) }
        return explicit
    }
    return resetsAt
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { value -> parseInstantLike(value)?.let { resetTextForInstant(it, now) } ?: value }
}

fun resetTextForInstant(resetAt: Instant, now: Instant = Instant.now()): String {
    val seconds = Duration.between(now, resetAt).seconds
    if (seconds <= 0L) return "Starts when a message is sent"
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

private fun parseInstantLike(value: String): Instant? {
    runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
    val epoch = value.toLongOrNull() ?: return null
    return when {
        epoch > 10_000_000_000L -> Instant.ofEpochMilli(epoch)
        epoch > 0L -> Instant.ofEpochSecond(epoch)
        else -> null
    }
}
