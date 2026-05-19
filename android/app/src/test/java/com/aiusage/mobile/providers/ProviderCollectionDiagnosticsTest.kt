package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCollectionDiagnosticsTest {
    @Test
    fun safeUrlDropsQueryAndFragment() {
        val sanitized = ProviderCollectionDiagnostics.safeUrl(
            "https://chatgpt.com/auth/callback?code=secret-token#access_token=hidden"
        )

        assertEquals("https://chatgpt.com/auth/callback", sanitized)
    }

    @Test
    fun payloadSummaryDoesNotExposeRawPayloadValues() {
        val summary = ProviderCollectionDiagnostics.payloadSummary(
            ProviderId.CODEX,
            """
            {
              "provider": "codex",
              "s": "ok",
              "secret": "secret-token",
              "c": {
                "login": false,
                "providerPage": true,
                "authenticatedApp": true,
                "textLength": 120
              },
              "d": {
                "p": "Plus",
                "x": [
                  {
                    "label": "GPT-5",
                    "remainingText": "24 messages remaining"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertTrue(summary.contains("provider=codex"))
        assertTrue(summary.contains("lines=1"))
        assertTrue(summary.contains("plan=true"))
        assertFalse(summary.contains("secret-token"))
        assertFalse(summary.contains("24 messages remaining"))
    }

    @Test
    fun payloadSummaryTreatsNullPlanAsAbsentAndShowsEndpointCountsOnly() {
        val summary = ProviderCollectionDiagnostics.payloadSummary(
            ProviderId.CODEX,
            """
            {
              "provider": "codex",
              "s": "e",
              "c": {
                "login": false,
                "providerPage": true,
                "authenticatedApp": true,
                "textLength": 101
              },
              "d": {
                "p": null,
                "x": []
              },
              "q": [
                {
                  "e": "/backend-api/accounts/:id/usage",
                  "s": 200,
                  "lk": 3,
                  "rk": 2,
                  "pk": 1,
                  "k": [
                    "accounts[].entitlements.plan_type",
                    "accounts[].subscription.tier"
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(summary.contains("plan=false"))
        assertTrue(summary.contains("/backend-api/accounts/:id/usage:200/l3/r2/u0/p1"))
        assertTrue(summary.contains("accounts[].entitlements.plan_type"))
    }
}
