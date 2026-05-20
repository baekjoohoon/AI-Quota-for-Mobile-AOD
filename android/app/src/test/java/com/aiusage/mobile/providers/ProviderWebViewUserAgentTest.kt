package com.aiusage.mobile.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWebViewUserAgentTest {
    @Test
    fun loginUserAgentLooksLikeChromeNotEmbeddedWebView() {
        val userAgent = ProviderWebViewUserAgent.loginUserAgent()

        assertTrue(userAgent.contains("Chrome/"))
        assertTrue(userAgent.contains("Mobile Safari/"))
        assertFalse(userAgent.contains("; wv"))
        assertFalse(userAgent.contains("AIUsageMobile"))
    }
}
