package com.aiquota.mobile.local

import java.time.Instant
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
    fun expiredResetWithNonFullRemainingDoesNotShowStartMessage() {
        val line = ProviderUsageLine(
            label = "Claude Session",
            remainingPercent = 0.91f,
            resetsAt = "2026-05-20T17:10:01Z"
        )

        assertNull(line.effectiveResetText(now = Instant.parse("2026-05-20T21:24:16Z")))
    }
}
