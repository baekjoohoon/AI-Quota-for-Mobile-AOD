package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLoginStrategyTest {
    @Test
    fun detectsProviderSpecificSuccessSignals() {
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CLAUDE, "https://claude.ai/new", mapOf("lastActiveOrg" to "org_123"), ""))
        assertTrue(ProviderLoginStrategy.isLoginComplete(ProviderId.GEMINI, "http://127.0.0.1:46417/oauth2callback?code=abc&state=xyz", emptyMap(), ""))
        assertTrue(ProviderLoginStrategy.isLoginComplete(ProviderId.GEMINI, "http://127.0.0.1:14641/oauth2callback?code=abc&state=xyz", emptyMap(), ""))
    }

    @Test
    fun doesNotTreatLoginPagesAsSuccess() {
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CLAUDE, "https://claude.ai/login", emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CLAUDE, "https://claude.ai/", emptyMap(), "Claude"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CLAUDE, "https://claude.ai/new", mapOf("lastActiveOrg" to "org_123"), "\"lastActiveOrg\""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.GEMINI, "https://accounts.google.com/signin", emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.COPILOT, "https://github.com/login", emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CLAUDE, "https://claude.ai/new", emptyMap(), "Claude"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.GEMINI, "https://gemini.google.com/app", emptyMap(), "Gemini"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.COPILOT, "https://github.com/settings/copilot", emptyMap(), "Copilot plan"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CURSOR, "https://cursor.com/dashboard", emptyMap(), "__NEXT_DATA__"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, "https://chatgpt.com/auth/login", emptyMap(), "ChatGPT"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, "https://chatgpt.com/", emptyMap(), "ChatGPT"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, "aiusage://codex/oauth?code=abc", emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, "http://localhost:1455/auth/callback?code=abc", emptyMap(), ""))
    }

    @Test
    fun oauthTransientErrorsAreRecoverable() {
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("https://accounts.google.com/o/oauth2/v2/auth", -2))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("https://github.com/login/oauth/authorize", -6))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("https://authenticator.cursor.sh/oauth/authorize", -2))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("http://localhost:1455/auth/callback?code=abc", -6))
        assertFalse(ProviderLoginStrategy.isTransientNavigationError("https://malicious.example", -10))
    }

    @Test
    fun mainFrameHttpAccessBlocksAreNotLeftConnecting() {
        assertTrue(ProviderLoginStrategy.isBlockingHttpError("https://accounts.google.com/o/oauth2/v2/auth", 403))
        assertTrue(ProviderLoginStrategy.isBlockingHttpError("https://auth.workos.com/sso/authorize", 403))
        assertFalse(ProviderLoginStrategy.isBlockingHttpError("https://claude.ai/login", 403))
        assertFalse(ProviderLoginStrategy.isBlockingHttpError("https://accounts.google.com/o/oauth2/v2/auth", 302))
    }
}
