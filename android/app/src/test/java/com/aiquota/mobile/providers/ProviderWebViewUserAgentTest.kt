package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWebViewUserAgentTest {
    @Test
    fun loginUserAgentUsesCurrentWebViewDefaultWithoutEmbeddedMarker() {
        val source = File("src/main/java/com/aiquota/mobile/providers/ProviderWebViewUserAgent.kt").readText()

        assertTrue(source.contains("WebSettings.getDefaultUserAgent(context)"))
        assertTrue(source.contains(".replace(\"; wv\", \"\")"))
        assertTrue(source.contains(".replace(\"Version/4.0 \", \"\")"))
        assertFalse(source.contains("Chrome/119.0.0.0"))
    }

    @Test
    fun fallbackLoginUserAgentLooksLikeChromeNotEmbeddedWebView() {
        val userAgent = ProviderWebViewUserAgent.loginUserAgent()

        assertTrue(userAgent.contains("Chrome/"))
        assertTrue(userAgent.contains("Mobile Safari/"))
        assertFalse(userAgent.contains("; wv"))
        assertFalse(userAgent.contains("AIQuotaMobile"))
    }

    @Test
    fun hiddenCollectorsUseSameUserAgentAsLoginWebView() {
        ProviderId.entries
            .filterNot { it == ProviderId.GEMINI || it == ProviderId.ANTIGRAVITY }
            .forEach { providerId ->
            assertTrue(
                ProviderWebViewUserAgent.hiddenCollectorUserAgent(providerId) ==
                    ProviderWebViewUserAgent.loginUserAgent()
            )
        }
    }

    @Test
    fun otherHiddenCollectorsKeepLoginUserAgent() {
        assertTrue(ProviderWebViewUserAgent.hiddenCollectorUserAgent(ProviderId.CLAUDE).contains("Mobile Safari/"))
        assertTrue(ProviderWebViewUserAgent.hiddenCollectorUserAgent(ProviderId.COPILOT).contains("Mobile Safari/"))
    }

    @Test
    fun geminiHiddenCollectorUsesDesktopUserAgentForUsagePageParity() {
        val userAgent = ProviderWebViewUserAgent.hiddenCollectorUserAgent(ProviderId.GEMINI)

        assertTrue(userAgent.contains("Windows NT"))
        assertTrue(userAgent.contains("Safari/"))
        assertFalse(userAgent.contains("Mobile Safari/"))
    }
}
