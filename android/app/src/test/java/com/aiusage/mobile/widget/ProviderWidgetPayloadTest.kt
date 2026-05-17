package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ProviderWidgetPayloadTest {
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
    fun returnsSelectedProviderPayload() {
        val snapshotJson = """
            {
              "schema":"local-provider-display-v1",
              "providers":[
                {"providerId":"codex","displayName":"Codex","connectionState":"CONNECTED","visible":true,"lines":[{"label":"Session","remainingPercent":0.7,"remainingText":"70% left","resetText":"Resets in 2h","detailText":"Fast requests","severity":"NORMAL"}]},
                {"providerId":"claude","displayName":"Claude","connectionState":"ERROR","visible":true,"lines":[{"label":"Session","remainingPercent":0.15,"remainingText":"15% left","resetText":"Resets in 20m","detailText":"Rate limited","severity":"DANGER"}]}
              ]
            }
        """.trimIndent()

        val payload = providerWidgetPayload(snapshotJson, "claude")

        assertEquals("claude", payload.providerId)
        assertEquals("Claude", payload.displayName)
        assertEquals("ERROR", payload.status)
        assertTrue(payload.visible)
        assertEquals(1, payload.lines.size)
        assertEquals("Session", payload.lines.single().label)
        assertEquals(0.15f, payload.lines.single().remainingPercent ?: -1f, 0.0001f)
        assertEquals("15% left", payload.lines.single().remainingText)
        assertEquals("Resets in 20m", payload.lines.single().resetText)
        assertEquals("Rate limited", payload.lines.single().detailText)
        assertEquals("DANGER", payload.lines.single().severity)
    }

    @Test
    fun returnsDisconnectedFallbackForMissingProvider() {
        val snapshotJson = """
            {
              "schema":"local-provider-display-v1",
              "providers":[
                {"providerId":"codex","displayName":"Codex","connectionState":"CONNECTED","visible":true,"lines":[]}
              ]
            }
        """.trimIndent()

        val payload = providerWidgetPayload(snapshotJson, "gemini")

        assertEquals("gemini", payload.providerId)
        assertEquals("Gemini", payload.displayName)
        assertEquals("DISCONNECTED", payload.status)
        assertTrue(payload.visible)
        assertEquals(emptyList<ProviderWidgetLine>(), payload.lines)
    }
}
