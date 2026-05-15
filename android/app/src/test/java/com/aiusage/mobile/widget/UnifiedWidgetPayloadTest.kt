package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedWidgetPayloadTest {
    @Test
    fun returnsVisibleProvidersAndGaugesInJsonOrder() {
        val snapshotJson = """
            {
              "schema":"local-provider-display-v1",
              "providers":[
                {"providerId":"codex","displayName":"Codex","connectionState":"CONNECTED","visible":true,"lines":[{"label":"Session","remainingPercent":0.7,"remainingText":"70% left","resetText":"Resets in 2h","detailText":"Fast requests","severity":"NORMAL"}]},
                {"providerId":"claude","displayName":"Claude","connectionState":"CONNECTED","visible":false,"lines":[{"label":"Session","remainingPercent":0.8,"remainingText":"80% left","resetText":"Resets in 1h","detailText":null,"severity":"NORMAL"}]},
                {"providerId":"cursor","displayName":"Cursor","connectionState":"CONNECTED","visible":true,"lines":[{"label":"Fast requests","remainingPercent":0.25,"remainingText":"25% left","resetText":"Resets in 3h","detailText":"Usage window","severity":"DANGER"}]},
                {"providerId":"gemini","displayName":"Gemini","connectionState":"CONNECTED","hidden":true,"lines":[{"label":"Pro","remainingPercent":1.0,"remainingText":"100% left","resetText":null,"detailText":null,"severity":"NORMAL"}]},
                {"providerId":"copilot","displayName":"GitHub Copilot","connectionState":"DISCONNECTED","visible":true,"lines":[]}
              ]
            }
        """.trimIndent()

        val payload = parseUnifiedWidgetPayload(snapshotJson)

        assertEquals(listOf("codex", "cursor", "copilot"), payload.providers.map { it.providerId })
        assertEquals(listOf("codex", "cursor"), payload.gauges.map { it.providerId })
        assertEquals("Codex", payload.providers[0].displayName)
        assertEquals("CONNECTED", payload.providers[0].status)
        assertEquals("Session", payload.providers[0].lines.single().label)
        assertEquals(0.7f, payload.providers[0].lines.single().remainingPercent ?: -1f, 0.0001f)
        assertEquals("70% left", payload.gauges[0].remainingText)
        assertEquals(0.25f, payload.gauges[1].remainingRatio)
    }
}
