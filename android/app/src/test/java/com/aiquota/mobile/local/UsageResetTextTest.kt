package com.aiquota.mobile.local

import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageResetTextTest {
    @Test
    fun longExplicitResetTextIsFoldedIntoDaysAndHours() {
        assertEquals(
            "Resets in 5d 16h",
            displayResetText(
                resetText = "Resets in 136h 48m",
                resetsAt = null,
                now = Instant.parse("2026-05-21T00:00:00Z")
            )
        )
    }

    @Test
    fun koreanTimeOnlyResetTextBecomesRelativeHoursAndMinutes() {
        withKoreaTimeZone {
            val text = displayResetText(
                resetText = "오후 2:52에 초기화",
                resetsAt = null,
                now = Instant.parse("2026-06-21T02:37:00Z")
            )

            assertEquals("Resets in 3h 15m", text)
            assertEquals(
                "3시간 15분 후 초기화",
                displayResetTextForLocale(text, Locale.KOREAN)
            )
        }
    }

    @Test
    fun koreanDatedResetTextBecomesRelativeDaysAndHours() {
        withKoreaTimeZone {
            val text = displayResetText(
                resetText = "6월 25일 오후 2:52에 초기화",
                resetsAt = null,
                now = Instant.parse("2026-06-21T02:37:00Z")
            )

            assertEquals("Resets in 4d 3h", text)
            assertEquals(
                "4일 3시간 후 초기화",
                displayResetTextForLocale(text, Locale.KOREAN)
            )
        }
    }

    @Test
    fun expiredResetWithNonFullRemainingDoesNotShowStartMessage() {
        val line = ProviderUsageLine(
            label = "Claude Session",
            remainingPercent = 0.91f,
            resetsAt = "2026-05-20T17:10:01Z"
        )

        assertNull(line.effectiveResetText(now = Instant.parse("2026-05-20T21:24:16Z")))
    }

    private fun withKoreaTimeZone(block: () -> Unit) {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
