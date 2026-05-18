package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class UsageResetTextTest {
    @Test
    fun formatsIsoResetAsRelativeResetTime() {
        val now = Instant.parse("2026-05-17T07:50:00Z")

        assertEquals(
            "Resets in 22m",
            displayResetText("2026-05-17T08:12:25Z", null, now)
        )
    }

    @Test
    fun formatsLongResetAsDaysAndHours() {
        val now = Instant.parse("2026-05-17T07:50:00Z")

        assertEquals(
            "Resets in 6d 14h",
            displayResetText(null, "2026-05-23T22:12:07Z", now)
        )
    }

    @Test
    fun keepsAlreadyFormattedResetText() {
        assertEquals(
            "Resets in 2h",
            displayResetText("Resets in 2h", "2026-05-17T09:00:00Z", Instant.parse("2026-05-17T07:00:00Z"))
        )
    }

    @Test
    fun hidesPastResetStartTextWhenUsageWindowIsPartiallyUsed() {
        val line = ProviderUsageLine(
            label = "Codex 5-hour limit",
            remainingPercent = 0.77f,
            remainingText = "77% left",
            resetsAt = "2026-05-17T09:00:00Z"
        )

        assertEquals(null, line.effectiveResetText(Instant.parse("2026-05-17T10:00:00Z")))
    }

    @Test
    fun hidesExplicitStartTextWhenUsageWindowIsPartiallyUsed() {
        val line = ProviderUsageLine(
            label = "Codex 5-hour limit",
            remainingPercent = 0.77f,
            remainingText = "77% left",
            resetText = "Starts when a message is sent"
        )

        assertEquals(null, line.effectiveResetText(Instant.parse("2026-05-17T10:00:00Z")))
    }

    @Test
    fun keepsPastResetStartTextWhenUsageWindowHasNotStarted() {
        val line = ProviderUsageLine(
            label = "Pro",
            remainingPercent = 1f,
            remainingText = "5 of 5 requests left",
            resetsAt = "2026-05-17T09:00:00Z"
        )

        assertEquals(
            "Starts when a message is sent",
            line.effectiveResetText(Instant.parse("2026-05-17T10:00:00Z"))
        )
    }
}
