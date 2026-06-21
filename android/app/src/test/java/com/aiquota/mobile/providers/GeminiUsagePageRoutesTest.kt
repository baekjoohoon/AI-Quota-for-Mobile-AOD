package com.aiquota.mobile.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiUsagePageRoutesTest {
    @Test
    fun geminiHomeAndAccountShellRedirectToUsagePage() {
        assertEquals(
            GeminiUsagePageRoutes.USAGE_URL,
            GeminiUsagePageRoutes.usageUrlFrom("https://gemini.google.com/")
        )
        assertEquals(
            GeminiUsagePageRoutes.USAGE_URL,
            GeminiUsagePageRoutes.usageUrlFrom("https://gemini.google.com/app")
        )
        assertEquals(
            GeminiUsagePageRoutes.USAGE_URL,
            GeminiUsagePageRoutes.usageUrlFrom("https://gemini.google.com/u/0/app")
        )
        assertEquals(
            GeminiUsagePageRoutes.USAGE_URL,
            GeminiUsagePageRoutes.usageUrlFrom("https://gemini.google.com/u/1/app?hl=ko")
        )
        assertEquals(
            GeminiUsagePageRoutes.USAGE_URL,
            GeminiUsagePageRoutes.usageUrlFrom("https://myaccount.google.com/security")
        )
    }

    @Test
    fun usagePageAndLoginHostDoNotRedirectAgain() {
        assertNull(GeminiUsagePageRoutes.usageUrlFrom("https://gemini.google.com/usage"))
        assertNull(GeminiUsagePageRoutes.usageUrlFrom("https://gemini.google.com/usage?hl=ko"))
        assertNull(GeminiUsagePageRoutes.usageUrlFrom("https://accounts.google.com/v3/signin/identifier"))
        assertNull(GeminiUsagePageRoutes.usageUrlFrom("https://example.com/app"))
    }
}
