package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class UnifiedWidgetPayloadTest {
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
    fun returnsVisibleProvidersAndGaugesInJsonOrder() {
        val snapshotJson = """
            {
              "schema":"local-provider-display-v1",
              "providers":[
                {"providerId":"codex","displayName":"Codex","connectionState":"CONNECTED","visible":true,"lines":[{"label":"Session","remainingPercent":0.7,"remainingText":"70% left","resetText":"Resets in 2h","detailText":"Fast requests","severity":"NORMAL"}]},
                {"providerId":"claude","displayName":"Claude","connectionState":"CONNECTED","visible":false,"lines":[{"label":"Session","remainingPercent":0.8,"remainingText":"80% left","resetText":"Resets in 1h","detailText":null,"severity":"NORMAL"}]},
                {"providerId":"cursor","displayName":"Cursor","connectionState":"CONNECTED","visible":true,"lines":[{"label":"Fast requests","remainingPercent":0.25,"remainingText":"25% left","resetText":"Resets in 3h","detailText":"Usage window","severity":"DANGER"}]},
                {"providerId":"copilot","displayName":"Copilot","connectionState":"CONNECTED","visible":true,"lines":[{"label":"Premium requests","remaining":180,"limit":300,"used":120,"unit":"premium requests","category":"premium_requests","windowText":"monthly","resetsAt":"2026-06-01T00:00:00Z","severity":"NORMAL"}]},
                {"providerId":"gemini","displayName":"Gemini","connectionState":"CONNECTED","hidden":true,"lines":[{"label":"Pro","remainingPercent":1.0,"remainingText":"100% left","resetText":null,"detailText":null,"severity":"NORMAL"}]},
                {"providerId":"claude","displayName":"Claude","connectionState":"DISCONNECTED","visible":true,"lines":[]}
              ]
            }
        """.trimIndent()

        val payload = parseUnifiedWidgetPayload(snapshotJson)

        assertEquals(listOf("codex", "cursor", "copilot", "claude"), payload.providers.map { it.providerId })
        assertEquals(listOf("codex", "cursor", "copilot"), payload.gauges.map { it.providerId })
        assertEquals("Codex", payload.providers[0].displayName)
        assertEquals("CONNECTED", payload.providers[0].status)
        assertEquals("Session", payload.providers[0].lines.single().label)
        assertEquals(0.7f, payload.providers[0].lines.single().remainingPercent ?: -1f, 0.0001f)
        assertEquals("Copilot", payload.providers[2].displayName)
        assertEquals("premium_requests", payload.providers[2].lines.single().category)
        assertEquals("premium requests", payload.providers[2].lines.single().unit)
        assertEquals(180.0, payload.providers[2].lines.single().remainingAmount ?: -1.0, 0.0001)
        assertEquals("70% left", payload.gauges[0].remainingText)
        assertEquals(0.25f, payload.gauges[1].remainingRatio)
        assertEquals("180 of 300 premium requests left", payload.gauges[2].remainingText)
        assertEquals(0.6f, payload.gauges[2].remainingRatio)
    }
}
