package com.aiusage.mobile.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexOAuthRepositoryTest {
    @Test
    fun defaultMobileLoginUsesChatGptSubscriptionSession() {
        assertEquals("https://chatgpt.com/auth/login", CodexOAuthRepository.defaultLoginStartUrl())
    }

    @Test
    fun defaultMobileLoginDoesNotOpenOpenAiApiOrOAuthProductPage() {
        val url = CodexOAuthRepository.defaultLoginStartUrl()
        assertTrue(url.startsWith("https://chatgpt.com/"))
        assertFalse(url.contains("platform.openai.com"))
        assertFalse(url.contains("auth.openai.com/oauth/authorize"))
    }
}
