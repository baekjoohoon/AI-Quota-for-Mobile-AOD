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
}
