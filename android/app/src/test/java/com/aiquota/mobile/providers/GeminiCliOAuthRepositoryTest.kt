package com.aiquota.mobile.providers

import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiCliOAuthRepositoryTest {
    @Test
    fun repositoryDoesNotContainDesktopClientSecret() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt").readText()
        val build = java.io.File("build.gradle.kts").readText()

        assertFalse(source.contains("client_secret"))
        assertFalse(source.contains("loopbackAuthorizationUrl"))
        assertFalse(source.contains("fetchUsagePayloadFromLoopbackCallback"))
        assertFalse(source.contains("https://oauth2.googleapis.com/token"))
        assertFalse(source.contains("BuildConfig.GEMINI_OAUTH_SECRET"))
        assertFalse(source.contains("BuildConfig.GEMINI_CLI_OAUTH_CLIENT_ID"))
        assertFalse(source.contains("GEMINI_CLI_GOOGLE_OAUTH_CLIENT_SECRET"))
        assertFalse(build.contains("aiquota.geminiOAuthSecret"))
        assertFalse(build.contains("GEMINI_OAUTH_SECRET"))
        assertFalse(build.contains("GEMINI_CLI_GOOGLE_OAUTH_CLIENT_SECRET"))
    }

    @Test
    fun usagePayloadFetchFailuresReturnNull() {
        val payload = GeminiCliOAuthRepository.usagePayloadOrNull {
            throw UnknownHostException("network unavailable")
        }

        assertNull(payload)
    }

    @Test
    fun repositoryKeepsOnlyCodeAssistApiFetchAndPayloadHelpers() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt").readText()

        assertTrue(source.contains("fetchUsagePayloadWithAccessToken(accessToken: String, email: String?)"))
        assertTrue(source.contains("loadCodeAssistPayload"))
        assertTrue(source.contains("retrieveUserQuotaPayload"))
        assertTrue(source.contains("structuredPayloadFromCodeAssist"))
        assertTrue(source.contains("https://cloudcode-pa.googleapis.com/v1internal"))
        assertTrue(source.contains("secureStore.remove("))
        assertTrue(source.contains("KEY_REFRESH_TOKEN"))
        assertTrue(source.contains("KEY_ACCESS_TOKEN"))
        assertFalse(source.contains("AccountManager"))
        assertFalse(source.contains("completeAccountAuthorization"))
    }
}
