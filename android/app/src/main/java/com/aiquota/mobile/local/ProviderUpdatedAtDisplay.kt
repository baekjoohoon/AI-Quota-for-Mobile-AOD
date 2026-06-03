package com.aiquota.mobile.local

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun displaySnapshotUpdatedAt(updatedAt: String): String {
    val value = updatedAt.trim()
    if (value.isBlank()) return value
    val instant = parseSnapshotUpdatedAt(value) ?: return value
    return SNAPSHOT_UPDATED_AT_FORMATTER.format(instant)
}

fun displaySnapshotUpdatedAtLine(updatedAt: String): String {
    return displaySnapshotUpdatedAt(updatedAt)
}

private fun parseSnapshotUpdatedAt(value: String): Instant? {
    runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
    val normalizedFraction = value.replace(
        Regex("""(T\d{2}:\d{2}:\d{2})\s+(\d{1,9}Z)$"""),
        "$1.$2"
    )
    return runCatching { Instant.parse(normalizedFraction) }.getOrNull()
}

private val SNAPSHOT_UPDATED_AT_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yy-MM-dd HH:mm")
        .withZone(ZoneId.of("Asia/Seoul"))
