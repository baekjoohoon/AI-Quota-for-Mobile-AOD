package com.aiusage.mobile.notification

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Locale

class UsageNotificationContentTest {
    private lateinit var previousLocale: Locale

    @Before
    fun useEnglishLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun buildsGaugeNotificationContentFromActiveProvidersOnly() {
        val snapshotJson = """
            {
              "providers": [
                {"providerId":"claude","status":"ok","lines":[{"label":"Session","used":22,"limit":100,"remaining":78,"resetsAt":"2026-05-06T03:00:00Z"}]},
                {"providerId":"gemini","status":"disabled","lines":[{"label":"Pro","used":0,"limit":100,"remaining":100}]},
                {"providerId":"codex","status":"ok","lines":[{"label":"Session","used":19,"limit":100,"remaining":81,"resetsAt":"2026-05-06T04:00:00Z"}]},
                {"providerId":"copilot","status":"ok","lines":[{"label":"Premium","used":36,"limit":100,"remaining":64,"resetsAt":"2026-05-06T05:00:00Z"}]}
              ]
            }
        """.trimIndent()

        val content = buildUsageNotificationContent(snapshotJson, Instant.parse("2026-05-06T02:00:00Z"))

        assertEquals("AI Usage", content.title)
        assertEquals("Claude 78% | Codex 81% | Copilot 64%", content.summary)
        assertEquals(listOf("claude", "codex", "copilot"), content.gauges.map { it.providerId })
        assertEquals(listOf("78% left", "81% left", "64% left"), content.gaugeRows.map { it.remainingText })
        assertEquals(listOf("Resets in 1h 0m", "Resets in 2h 0m", "Resets in 3h 0m"), content.gaugeRows.map { it.resetText })
    }

    @Test
    fun buildsLocalDisplayOnlyNotificationInVisibleProviderOrder() {
        val snapshotJson = """
            {
              "schema":"local-provider-display-v1",
              "providers":[
                {"providerId":"cursor","displayName":"Cursor","connectionState":"CONNECTED","visible":true,"lines":[{"label":"Fast requests","remainingPercent":0.4,"remainingText":"40","resetText":"Resets in 2h","severity":"WARNING"}]},
                {"providerId":"codex","displayName":"Codex","connectionState":"CONNECTED","visible":false,"lines":[{"label":"Session","remainingPercent":0.2,"remainingText":"20","resetText":"Resets in 1h","severity":"DANGER"}]},
                {"providerId":"gemini","displayName":"Gemini","connectionState":"CONNECTED","hidden":true,"lines":[{"label":"Pro","remainingPercent":1.0,"remainingText":"100","resetText":"Tomorrow","severity":"NORMAL"}]},
                {"providerId":"claude","displayName":"Claude","connectionState":"CONNECTED","visible":true,"lines":[{"label":"Session","remainingPercent":0.8,"remainingText":"80","resetText":"Resets in 4h","severity":"NORMAL"}]}
              ]
            }
        """.trimIndent()

        val content = buildUsageNotificationContent(snapshotJson, Instant.parse("2026-05-15T00:00:00Z"))

        assertEquals(listOf("cursor", "claude"), content.gauges.map { it.providerId })
        assertEquals("Cursor 40 | Claude 80", content.summary)
        assertEquals(listOf("40", "80"), content.gaugeRows.map { it.remainingText })
        assertEquals(listOf("Resets in 2h", "Resets in 4h"), content.gaugeRows.map { it.resetText })
    }

    @Test
    fun splitsFourProviderNotificationSummaryAcrossTwoLines() {
        val snapshotJson = """
            {
              "providers": [
                {"providerId":"claude","status":"ok","lines":[{"label":"Session","used":0,"limit":100}]},
                {"providerId":"codex","status":"ok","lines":[{"label":"Session","used":8,"limit":100}]},
                {"providerId":"gemini","status":"ok","lines":[{"label":"Pro","used":0,"limit":100}]},
                {"providerId":"copilot","status":"error","lines":[]}
              ]
            }
        """.trimIndent()

        val content = buildUsageNotificationContent(snapshotJson, Instant.parse("2026-05-06T02:00:00Z"))

        assertEquals("Claude 100% | Codex 92%\nGemini 100% | Copilot Error", content.summary)
        assertEquals("Claude 100% | Codex 92%", content.compactTitle)
        assertEquals("Gemini 100% | Copilot Error", content.compactText)
    }
}
