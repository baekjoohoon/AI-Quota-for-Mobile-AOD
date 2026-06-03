package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleWebSessionCodeAssistFetcherTest {
    @Test
    fun sapisidHashAuthorizationUsesOriginAndNeverClientSecret() {
        val header = GoogleWebSessionCodeAssistFetcher.sapisidAuthorizationHeader(
            timestampSeconds = 1_779_812_400L,
            sapisid = "sapisid-value",
            origin = "https://gemini.google.com"
        )

        assertTrue(header.startsWith("SAPISIDHASH 1779812400_"))
        assertFalse(header.contains("sapisid-value"))
        assertFalse(header.contains("client_secret"))
    }

    @Test
    fun googleCookieHeadersAreMergedWithoutLeakingDuplicates() {
        val merged = GoogleWebSessionCodeAssistFetcher.mergeCookieHeaders(
            listOf(
                "SID=sid-a; SAPISID=sapisid-a",
                "__Secure-1PAPISID=sapisid-b; SID=sid-b",
                "__Secure-3PAPISID=sapisid-c",
                ""
            )
        )

        assertEquals("SID=sid-a; SAPISID=sapisid-a; __Secure-1PAPISID=sapisid-b; __Secure-3PAPISID=sapisid-c", merged)
        assertEquals(
            "sapisid-c",
            GoogleWebSessionCodeAssistFetcher.sapisidFromCookieHeader(merged)
        )
        assertEquals(
            listOf("__Secure-3PAPISID", "__Secure-1PAPISID", "SAPISID"),
            GoogleWebSessionCodeAssistFetcher.sapisidCredentialsFromCookieHeader(merged).map { it.label }
        )
    }

    @Test
    fun codeAssistBridgeRestrictsProviderMethods() {
        assertTrue(GoogleWebSessionCodeAssistFetcher.isAllowedMethod(ProviderId.GEMINI, "loadCodeAssist"))
        assertTrue(GoogleWebSessionCodeAssistFetcher.isAllowedMethod(ProviderId.GEMINI, "retrieveUserQuota"))
        assertTrue(GoogleWebSessionCodeAssistFetcher.isAllowedMethod(ProviderId.ANTIGRAVITY, "fetchAvailableModels"))

        assertFalse(GoogleWebSessionCodeAssistFetcher.isAllowedMethod(ProviderId.GEMINI, "fetchAvailableModels"))
        assertFalse(GoogleWebSessionCodeAssistFetcher.isAllowedMethod(ProviderId.ANTIGRAVITY, "retrieveUserQuota"))
        assertFalse(GoogleWebSessionCodeAssistFetcher.isAllowedMethod(ProviderId.CODEX, "loadCodeAssist"))
    }

    @Test
    fun googleWebSessionRequestsTryProviderAndApiOriginsWithoutClientSecret() {
        val geminiProfiles = GoogleWebSessionCodeAssistFetcher.requestProfilesFor(ProviderId.GEMINI)
        val antigravityProfiles = GoogleWebSessionCodeAssistFetcher.requestProfilesFor(ProviderId.ANTIGRAVITY)

        assertEquals("browser_api_x_origin", geminiProfiles.first().label)
        assertTrue(geminiProfiles.any { it.authOrigin == "https://cloudcode-pa.googleapis.com" })
        assertTrue(geminiProfiles.any { it.originHeader == "https://gemini.google.com" })
        assertTrue(geminiProfiles.any { it.originHeader == "https://gemini.google.com" && it.xOriginHeader == "https://cloudcode-pa.googleapis.com" })
        assertTrue(geminiProfiles.any { it.label == "api_origin_provider_auth" && it.authOrigin == "https://gemini.google.com" && it.originHeader == "https://cloudcode-pa.googleapis.com" && it.xOriginHeader == "https://cloudcode-pa.googleapis.com" })
        assertTrue(antigravityProfiles.any { it.originHeader == "https://antigravity.google" })

        val serialized = (geminiProfiles + antigravityProfiles).joinToString("|")
        assertFalse(serialized.contains("client_secret"))
        assertFalse(serialized.contains("clientSecret"))
    }
}
