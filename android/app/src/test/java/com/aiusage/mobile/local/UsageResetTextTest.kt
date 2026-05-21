package com.aiusage.mobile.local

import java.time.Instant
import org.junit.Assert.assertNull
import org.junit.Test

class UsageResetTextTest {
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
