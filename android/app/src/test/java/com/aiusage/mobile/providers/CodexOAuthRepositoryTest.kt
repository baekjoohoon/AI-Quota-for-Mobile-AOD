package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexOAuthRepositoryTest {
    @Test
    fun authorizationUrlUsesOfficialCodexOAuthParameters() {
        val url = CodexOAuthRepository.buildAuthorizationUrl(
            state = "state-value",
            codeChallenge = "challenge-value"
        )

        assertTrue(url.startsWith("https://auth.openai.com/oauth/authorize?"))
        assertTrue(url.contains("client_id=app_EMoamEEZ73f0CkXaXp7hrann"))
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback"))
        assertTrue(url.contains("scope=openid+profile+email+offline_access+api.connectors.read+api.connectors.invoke"))
        assertTrue(url.contains("code_challenge=challenge-value"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("codex_cli_simplified_flow=true"))
        assertTrue(url.contains("originator=codex_cli_rs"))
        assertTrue(url.contains("state=state-value"))
    }

    @Test
    fun detectsOnlyCodexLocalhostCallbackUrl() {
        assertTrue(CodexOAuthRepository.isCallbackUrl("http://localhost:1455/auth/callback?code=a&state=b"))

        assertFalse(CodexOAuthRepository.isCallbackUrl("https://localhost:1455/auth/callback?code=a"))
        assertFalse(CodexOAuthRepository.isCallbackUrl("http://localhost:3000/auth/callback?code=a"))
        assertFalse(CodexOAuthRepository.isCallbackUrl("http://evil.localhost:1455/auth/callback?code=a"))
    }

    @Test
    fun whamUsagePayloadMapsWindowsAndCreditsToProviderLines() {
        val payload = CodexOAuthRepository.structuredPayloadFromWhamBodyForTest(
            planLabel = null,
            body = """
                {
                  "plan_type": "plus",
                  "rate_limit": {
                    "primary_window": {
                      "used_percent": 25,
                      "reset_at": 1790000000,
                      "limit_window_seconds": 18000
                    },
                    "secondary_window": {
                      "used_percent": 40,
                      "reset_at": 1790500000,
                      "limit_window_seconds": 604800
                    }
                  },
                  "credits": {
                    "has_credits": true,
                    "unlimited": false,
                    "balance": 12.5
                  }
                }
            """.trimIndent()
        )

        val snapshot = TextUsageExtractor.extract(ProviderId.CODEX, payload)

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Plus", snapshot.planLabel)
        assertEquals(3, snapshot.lines.size)
        assertNotNull(snapshot.lines.first { it.label == "Codex 5-hour limit" }.remainingPercent)
        assertEquals(
            0.75,
            (snapshot.lines.first { it.label == "Codex 5-hour limit" }.remainingPercent ?: 0f).toDouble(),
            0.001
        )
        assertNotNull(snapshot.lines.first { it.label == "Codex weekly limit" }.remainingPercent)
        assertEquals(12.5, snapshot.lines.first { it.label == "Credits" }.remainingAmount ?: -1.0, 0.001)
    }

    @Test
    fun whamUsagePayloadMapsSparkWindowsWhenPresent() {
        val payload = CodexOAuthRepository.structuredPayloadFromWhamBodyForTest(
            planLabel = "Pro",
            body = """
                {
                  "spark_rate_limit": {
                    "primary_window": {
                      "used_percent": 0,
                      "reset_after_seconds": 18000,
                      "limit_window_seconds": 18000
                    },
                    "secondary_window": {
                      "used_percent": 10,
                      "reset_after_seconds": 604800,
                      "limit_window_seconds": 604800
                    }
                  }
                }
            """.trimIndent()
        )

        val snapshot = TextUsageExtractor.extract(ProviderId.CODEX, payload)

        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals("Pro", snapshot.planLabel)
        assertEquals(2, snapshot.lines.size)
        assertTrue(snapshot.lines.any { it.label == "Spark" && it.remainingPercent == 1f })
        assertTrue(snapshot.lines.any { it.label == "Spark weekly" })
    }

    @Test
    fun whamUsagePayloadDisplaysProliteAsPro5x() {
        val payload = CodexOAuthRepository.structuredPayloadFromWhamBodyForTest(
            planLabel = null,
            body = """
                {
                  "plan_type": "prolite",
                  "rate_limit": {
                    "primary_window": {
                      "used_percent": 10,
                      "reset_after_seconds": 18000,
                      "limit_window_seconds": 18000
                    }
                  }
                }
            """.trimIndent()
        )

        val snapshot = TextUsageExtractor.extract(ProviderId.CODEX, payload)

        assertEquals("Pro 5x", snapshot.planLabel)
    }
}
