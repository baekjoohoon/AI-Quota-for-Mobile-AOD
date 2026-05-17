package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Locale

class WidgetGaugeParserTest {
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
    fun parsesUpToFourProviderGaugesInSnapshotOrder() {
        val now = Instant.parse("2026-05-06T00:00:00Z")
        val snapshotJson = """
            {
              "providers": [
                {"providerId":"claude","displayName":"Claude","lines":[{"label":"Session","used":20,"limit":100,"remaining":80,"resetsAt":"2026-05-06T01:30:00Z"}]},
                {"providerId":"codex","displayName":"Codex","lines":[{"label":"Session","used":10,"limit":100,"remaining":90,"resetsAt":"2026-05-07T02:00:00Z"}]},
                {"providerId":"gemini","displayName":"Gemini","lines":[{"label":"Pro","used":0,"limit":100,"remaining":100,"resetsAt":"2026-05-06T00:20:00Z"}]},
                {"providerId":"copilot","displayName":"Copilot","lines":[{"label":"Premium","used":40,"limit":100,"remaining":60}]},
                {"providerId":"extra","displayName":"Extra","lines":[{"label":"Daily","used":99,"limit":100,"remaining":1}]}
              ]
            }
        """.trimIndent()

        val gauges = parseWidgetProviderGauges(snapshotJson, now)

        assertEquals(listOf("claude", "codex", "gemini", "copilot"), gauges.map { it.providerId })
        assertEquals(0.8f, gauges[0].remainingRatio)
        assertEquals(0.9f, gauges[1].remainingRatio)
        assertEquals(1.0f, gauges[2].remainingRatio)
        assertEquals(0.6f, gauges[3].remainingRatio)
        assertEquals("80% left", gauges[0].remainingText)
        assertEquals("Resets in 1h 30m", gauges[0].resetText)
        assertEquals("Resets in 1d 2h", gauges[1].resetText)
        assertEquals("Resets in 20m", gauges[2].resetText)
    }

    @Test
    fun fallsBackToUsedAndLimitWhenRemainingIsMissing() {
        val snapshotJson = """
            {
              "providers": [
                {"providerId":"codex","lines":[{"label":"Session","used":25,"limit":100}]}
              ]
            }
        """.trimIndent()

        val gauges = parseWidgetProviderGauges(snapshotJson)

        assertEquals(1, gauges.size)
        assertEquals(0.75f, gauges.single().remainingRatio)
        assertEquals("75% left", gauges.single().remainingText)
    }

    @Test
    fun parsesLocalDisplayOnlyGaugesInPayloadOrderAndSkipsHiddenProviders() {
        val snapshotJson = """
            {
              "schema":"local-provider-display-v1",
              "updatedAt":"2026-05-15T00:00:00Z",
              "providers":[
                {"providerId":"claude","displayName":"Claude","connectionState":"CONNECTED","refreshState":"IDLE","planLabel":"Pro","updatedAt":"2026-05-15T00:00:00Z","message":null,"visible":true,"lines":[{"label":"Session","remainingPercent":0.8,"remainingText":"80% left","resetText":"Resets in 1h","detailText":"Fast requests","severity":"NORMAL"}]},
                {"providerId":"codex","displayName":"Codex","connectionState":"CONNECTED","refreshState":"IDLE","planLabel":"Pro","updatedAt":"2026-05-15T00:00:00Z","message":null,"visible":false,"lines":[{"label":"Session","remainingPercent":0.7,"remainingText":"70% left","resetText":"Resets in 2h","detailText":null,"severity":"WARNING"}]},
                {"providerId":"cursor","displayName":"Cursor","connectionState":"CONNECTED","refreshState":"IDLE","planLabel":"Pro","updatedAt":"2026-05-15T00:00:00Z","message":null,"visible":true,"lines":[{"label":"Fast requests","remainingPercent":0.25,"remainingText":"25% left","resetText":"Resets in 3h","detailText":null,"severity":"DANGER"}]}
              ]
            }
        """.trimIndent()

        val gauges = parseWidgetProviderGauges(snapshotJson)

        assertEquals(listOf("claude", "cursor"), gauges.map { it.providerId })
        assertEquals(0.8f, gauges[0].remainingRatio)
        assertEquals("80% left", gauges[0].remainingText)
        assertEquals("Resets in 1h", gauges[0].resetText)
        assertEquals(0.25f, gauges[1].remainingRatio)
        assertEquals("25% left", gauges[1].remainingText)
        assertEquals("Resets in 3h", gauges[1].resetText)
    }

    @Test
    fun skipsProvidersWithoutAGaugeableLimitLine() {
        val snapshotJson = """
            {
              "providers": [
                {"providerId":"empty","lines":[]},
                {"providerId":"claude","lines":[{"label":"Session","used":20,"limit":100,"remaining":80}]}
              ]
            }
        """.trimIndent()

        val gauges = parseWidgetProviderGauges(snapshotJson)

        assertEquals(1, gauges.size)
        assertEquals("claude", gauges.single().providerId)
        assertTrue(gauges.single().remainingRatio > 0f)
    }

    @Test
    fun includesErrorProvidersWithoutLinesAsErrorGauges() {
        val snapshotJson = """
            {
              "providers": [
                {"providerId":"claude","status":"ok","lines":[{"label":"Session","used":20,"limit":100}]},
                {"providerId":"codex","status":"ok","lines":[{"label":"Session","used":2,"limit":100}]},
                {"providerId":"gemini","status":"ok","lines":[{"label":"Pro","used":0,"limit":100}]},
                {"providerId":"copilot","status":"error","lines":[]}
              ]
            }
        """.trimIndent()

        val gauges = parseWidgetProviderGauges(snapshotJson)

        assertEquals(listOf("claude", "codex", "gemini", "copilot"), gauges.map { it.providerId })
        assertEquals(0f, gauges.last().remainingRatio)
        assertEquals("Error", gauges.last().remainingText)
        assertEquals(null, gauges.last().resetText)
    }

    @Test
    fun skipsDisabledProvidersFromWindowsSettings() {
        val snapshotJson = """
            {
              "providers": [
                {"providerId":"claude","status":"ok","lines":[{"label":"Session","used":20,"limit":100,"remaining":80}]},
                {"providerId":"gemini","status":"disabled","lines":[{"label":"Pro","used":0,"limit":100,"remaining":100}]},
                {"providerId":"codex","status":"ok","lines":[{"label":"Session","used":10,"limit":100,"remaining":90}]}
              ]
            }
        """.trimIndent()

        val gauges = parseWidgetProviderGauges(snapshotJson)

        assertEquals(listOf("claude", "codex"), gauges.map { it.providerId })
    }

    @Test
    fun growsWidgetGaugeHeightWhenFewerProvidersAreVisible() {
        assertEquals(12, compactGaugeLayoutSpec(1).gaugeHeightDp)
        assertEquals(9, compactGaugeLayoutSpec(2).gaugeHeightDp)
        assertEquals(7, compactGaugeLayoutSpec(3).gaugeHeightDp)
        assertEquals(6, compactGaugeLayoutSpec(4).gaugeHeightDp)

        assertEquals(14, expandedGaugeLayoutSpec(1).gaugeHeightDp)
        assertEquals(12, expandedGaugeLayoutSpec(2).gaugeHeightDp)
        assertEquals(10, expandedGaugeLayoutSpec(3).gaugeHeightDp)
        assertEquals(10, expandedGaugeLayoutSpec(4).gaugeHeightDp)
    }

    @Test
    fun compactWidgetGaugeWidthIsTwentyPercentLonger() {
        assertEquals(51, compactGaugeLayoutSpec(1).gaugeWidthDp)
        assertEquals(51, compactGaugeLayoutSpec(2).gaugeWidthDp)
        assertEquals(53, compactGaugeLayoutSpec(3).gaugeWidthDp)
        assertEquals(53, compactGaugeLayoutSpec(4).gaugeWidthDp)
    }

    @Test
    fun expandedWidgetGaugeWidthAdaptsToAvailableWidgetWidth() {
        assertEquals(204, expandedGaugeLayoutSpec(4, widgetWidthDp = 240).gaugeWidthDp)
        assertEquals(314, expandedGaugeLayoutSpec(4, widgetWidthDp = 360).gaugeWidthDp)
        assertEquals(434, expandedGaugeLayoutSpec(4, widgetWidthDp = 480).gaugeWidthDp)
        assertEquals(474, expandedGaugeLayoutSpec(4, widgetWidthDp = 520).gaugeWidthDp)
        assertEquals(674, expandedGaugeLayoutSpec(4, widgetWidthDp = 720).gaugeWidthDp)
    }

    @Test
    fun compactWidgetProviderIconsAreLarger() {
        assertEquals(12, compactGaugeLayoutSpec(1).iconSizeDp)
        assertEquals(12, compactGaugeLayoutSpec(2).iconSizeDp)
        assertEquals(11, compactGaugeLayoutSpec(3).iconSizeDp)
        assertEquals(11, compactGaugeLayoutSpec(4).iconSizeDp)
    }
}
