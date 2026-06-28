package com.aiquota.mobile.providers

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AntigravityOAuthRepositoryTest {
    @Test
    fun repositoryUsesFirebaseTokenExchangeWithoutClientSecret() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt").readText()
        val build = java.io.File("build.gradle.kts").readText()

        assertTrue(source.contains("fetchUsagePayloadFromGatewayTokenResult"))
        assertTrue(source.contains("AntigravityFirebaseGateway(appContext).refreshAccessToken(refreshToken)"))
        assertTrue(source.contains("http://127.0.0.1:8080/callback"))
        assertTrue(build.contains("1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"))
        assertFalse(source.contains("loopbackAuthorizationUrl"))
        assertFalse(source.contains("fetchUsagePayloadFromLoopbackCallback"))
        assertFalse(source.contains("https://oauth2.googleapis.com/token"))
        assertFalse(source.contains("BuildConfig.ANTIGRAVITY_OAUTH_CLIENT_ID"))
        assertFalse(source.contains("client_secret"))
        assertFalse(source.contains("BuildConfig.ANTIGRAVITY_OAUTH_SECRET"))
        assertFalse(build.contains("aiquota.antigravityOAuthSecret"))
        assertFalse(build.contains("ANTIGRAVITY_OAUTH_SECRET"))
    }

    @Test
    fun fetchAvailableModelsPayloadUsesProjectWhenKnown() {
        val payload = JSONObject(AntigravityOAuthRepository.fetchAvailableModelsPayload("project-123"))

        assertEquals("project-123", payload.getString("project"))
    }

    @Test
    fun loadCodeAssistPayloadMatchesAntigravityMobileCollectorShape() {
        val payload = JSONObject(AntigravityOAuthRepository.loadCodeAssistPayload())
        val metadata = payload.getJSONObject("metadata")

        assertEquals("FULL_ELIGIBILITY_CHECK", payload.getString("mode"))
        assertEquals("antigravity", metadata.getString("ideName"))
        assertEquals("ANTIGRAVITY", metadata.getString("ideType"))
        assertEquals("2.0.0", metadata.getString("ideVersion"))
        assertEquals("2.0.0", metadata.getString("pluginVersion"))
        assertEquals("stable", metadata.getString("updateChannel"))
        assertEquals("GEMINI", metadata.getString("pluginType"))
    }


    @Test
    fun availableModelsResponseNormalizesToTrustedAntigravityUsage() {
        val payload = AntigravityOAuthRepository.structuredPayloadFromAvailableModels(
            modelsJson = """
                {
                  "models": {
                    "gemini-3-pro-high": {
                      "displayName": "Gemini 3 Pro",
                      "quotaInfo": {
                        "remainingFraction": 0.83,
                        "resetTime": "2026-06-01T00:00:00Z"
                      }
                    },
                    "claude-opus-4-5-thinking": {
                      "displayName": "Claude Opus 4.5",
                      "quotaInfo": {
                        "remainingFraction": 0.74,
                        "resetTime": "2026-06-01T01:00:00Z"
                      }
                    }
                  }
                }
            """.trimIndent(),
            account = null,
            plan = "Google AI Pro"
        )

        val snapshot = ProviderUsageNormalizer.normalize(
            com.aiquota.mobile.local.ProviderId.ANTIGRAVITY,
            payload ?: "",
            ProviderPayloadSource.PROVIDER_API
        )

        assertNotNull(snapshot)
        assertEquals("AI Pro", snapshot!!.plan)
        assertEquals(listOf("Gemini 3 Pro", "Claude Opus 4.5"), snapshot.lines.map { it.label })
        assertEquals(0.83f, snapshot.lines[0].remainingPercent ?: 0f, 0.001f)
        assertEquals("2026-06-01T00:00:00Z", snapshot.lines[0].resetsAt)
    }

    @Test
    fun repositoryKeepsOnlyCodeAssistApiFetchAndPayloadHelpers() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt").readText()

        assertTrue(source.contains("fetchUsagePayloadWithAccessToken(accessToken: String, email: String?)"))
        assertTrue(source.contains("loadCodeAssistPayload"))
        assertTrue(source.contains("fetchAvailableModelsPayload"))
        assertTrue(source.contains("structuredPayloadFromAvailableModels"))
        assertTrue(source.contains("https://daily-cloudcode-pa.googleapis.com"))
        assertTrue(source.contains("secureStore.remove("))
        assertTrue(source.contains("KEY_REFRESH_TOKEN"))
        assertTrue(source.contains("KEY_ACCESS_TOKEN"))
        assertFalse(source.contains("AccountManager"))
        assertFalse(source.contains("completeAccountAuthorization"))
    }
}
