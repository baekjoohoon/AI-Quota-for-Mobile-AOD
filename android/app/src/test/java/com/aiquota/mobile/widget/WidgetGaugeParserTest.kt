package com.aiquota.mobile.widget

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WidgetGaugeParserTest {
    @Test
    fun providerWidgetRemovesProviderNameFromUsageLineLabels() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.KOREAN)
        try {
            val payload = providerWidgetPayload(
                snapshotJson = """
                {
                  "providers": [
                    {
                      "providerId": "codex",
                      "displayName": "Codex",
                      "connectionState": "CONNECTED",
                      "lines": [
                        {
                          "label": "Codex Session",
                          "remainingPercent": 0.8,
                          "remainingText": "80% left",
                          "resetText": "Resets in 4h"
                        },
                        {
                          "label": "Codex Weekly",
                          "remainingPercent": 0.6,
                          "remainingText": "60% left",
                          "resetText": "Resets in 4d"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
                providerId = "codex"
            )

            assertEquals(
                listOf("5시간 세션", "주간 세션"),
                payload.lines.map { it.label }
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun codexProviderWidgetDoesNotBorrowWeeklyResetForIdleSessionGauge() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.KOREAN)
        try {
            val snapshotJson = """
                {
                  "providers": [
                    {
                      "providerId": "codex",
                      "displayName": "Codex",
                      "connectionState": "CONNECTED",
                      "lines": [
                        {
                          "label": "Codex Session",
                          "remainingPercent": 1.0,
                          "remainingText": "100% left"
                        },
                        {
                          "label": "Codex Weekly",
                          "remainingPercent": 0.9,
                          "remainingText": "90% left",
                          "resetText": "Resets in 6d 2h"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            val providerPayload = providerWidgetPayload(snapshotJson, providerId = "codex")
            val gauge = parseWidgetProviderGauges(snapshotJson).single()

            assertNull(providerPayload.lines[0].resetText)
            assertEquals("6일 2시간 후 초기화", providerPayload.lines[1].resetText)
            assertNull(gauge.resetText)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun antigravityProviderWidgetUsesCompactModelLabels() {
        val payload = providerWidgetPayload(
            snapshotJson = """
            {
              "providers": [
                {
                  "providerId": "antigravity",
                  "displayName": "Antigravity",
                  "connectionState": "CONNECTED",
                  "lines": [
                    {
                      "label": "Gemini 3.5 Flash (High)",
                      "remainingPercent": 1.0,
                      "remainingText": "100% left",
                      "resetText": "Resets in 4h 55m"
                    },
                    {
                      "label": "Gemini 3.5 Flash (Low)",
                      "remainingPercent": 1.0,
                      "remainingText": "100% left",
                      "resetText": "Resets in 4h 55m"
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
            providerId = "antigravity"
        )

        assertEquals(
            listOf("3.5 Flash(H)", "3.5 Flash(L)"),
            payload.lines.map { it.label }
        )
    }

    @Test
    fun dashboardWidgetPayloadAppliesWidgetSpecificOrderAndHiddenProviders() {
        val payload = dashboardWidgetPayload(
            snapshotJson = """
            {
              "providers": [
                {
                  "providerId": "claude",
                  "displayName": "Claude",
                  "connectionState": "CONNECTED",
                  "lines": [
                    { "label": "5h", "remainingPercent": 0.6, "remainingText": "60% left" }
                  ]
                },
                {
                  "providerId": "codex",
                  "displayName": "Codex",
                  "connectionState": "CONNECTED",
                  "lines": [
                    { "label": "5h", "remainingPercent": 0.7, "remainingText": "70% left" }
                  ]
                },
                {
                  "providerId": "gemini",
                  "displayName": "Gemini",
                  "connectionState": "CONNECTED",
                  "lines": [
                    { "label": "daily", "remainingPercent": 0.8, "remainingText": "80% left" }
                  ]
                }
              ]
            }
            """.trimIndent(),
            order = listOf(ProviderId.GEMINI, ProviderId.CLAUDE, ProviderId.CODEX),
            hidden = setOf(ProviderId.CODEX)
        )

        assertEquals(listOf("gemini", "claude"), payload.providers.map { it.providerId })
        assertEquals(listOf("gemini", "claude"), payload.gauges.map { it.providerId })
    }

    @Test
    fun widgetPayloadKeepsProviderGaugeColor() {
        val snapshotJson = """
            {
              "providers": [
                {
                  "providerId": "claude",
                  "displayName": "Claude",
                  "connectionState": "CONNECTED",
                  "gaugeColor": "#4f6fa8",
                  "lines": [
                    { "label": "5h", "remainingPercent": 0.6, "remainingText": "60% left" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val payload = providerWidgetPayload(snapshotJson, providerId = "claude")
        val gauge = parseUnifiedWidgetPayload(snapshotJson).gauges.single()

        assertEquals("#4F6FA8", payload.gaugeColorHex)
        assertEquals("#4F6FA8", gauge.gaugeColorHex)
    }
}
