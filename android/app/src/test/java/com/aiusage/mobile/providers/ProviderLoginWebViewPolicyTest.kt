package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLoginWebViewPolicyTest {
    @Test
    fun codexLoginNavigationAllowsChatGptAuthAndChallengeRedirects() {
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CODEX, "https://chatgpt.com/auth/login"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CODEX, "https://auth.openai.com/authorize"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CODEX, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CODEX, "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b"))
    }

    @Test
    fun codexLoginNavigationStillBlocksProductAndRelayDestinations() {
        assertTrue(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CODEX, "https://platform.openai.com/usage"))
        assertTrue(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CODEX, "https://cloudfunctions.net/relay"))
    }

    @Test
    fun claudeLoginNavigationDoesNotBlockGoogleChallengeOrCallbackUrls() {
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/sms"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/pwd"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CLAUDE, "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CLAUDE, "https://www.recaptcha.net/recaptcha/enterprise/anchor"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CLAUDE, "https://claude.ai/api/organizations"))
    }

    @Test
    fun geminiLoginNavigationDoesNotBlockGoogleChallengeOrCallbackUrls() {
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.GEMINI, "https://accounts.google.com/signin/v2/challenge/sms"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.GEMINI, "https://accounts.google.com/signin/v2/challenge/pwd"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.GEMINI, "https://www.recaptcha.net/recaptcha/enterprise/anchor"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.GEMINI, "https://gemini.google.com/app"))
    }

    @Test
    fun cursorLoginNavigationAllowsGoogleWorkosAndCookieSyncRedirects() {
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://cursor.com/dashboard"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://cursor.com/api/auth/login?redirect_uri=https%3A%2F%2Fcursor.com%2Fdashboard"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://cursor.com/api/auth/callback?code=abc"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://authenticate.cursor.sh/user_management/authorize"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://authenticator.cursor.sh/bootstrap"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://accounts.google.com/signin/v2/challenge/sms"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://accounts.youtube.com/accounts/SetSID?ssdc=1"))
        assertFalse(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://play.google.com/log?format=json"))
    }

    @Test
    fun cursorLoginNavigationBlocksUsageApiMainFrameProbes() {
        assertTrue(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://cursor.com/api/usage?user=user_123"))
        assertTrue(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://cursor.com/api/auth/stripe"))
        assertTrue(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://cursor.com/api/dashboard/get-credit-grants-balance"))
        assertTrue(ProviderLoginWebViewPolicy.shouldOverrideNavigation(ProviderId.CURSOR, "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage"))
    }

    @Test
    fun loginResourceRequestsAreNeverIntercepted() {
        assertFalse(ProviderLoginWebViewPolicy.shouldInterceptRequest(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/sms"))
        assertFalse(ProviderLoginWebViewPolicy.shouldInterceptRequest(ProviderId.CODEX, "https://chatgpt.com/auth/login"))
    }

    @Test
    fun providerLoginWebViewEnablesStatefulJavascriptLoginFeatures() {
        val capabilities = ProviderLoginWebViewPolicy.capabilities()

        assertTrue(capabilities.databaseEnabled)
        assertTrue(capabilities.javaScriptCanOpenWindowsAutomatically)
        assertTrue(capabilities.supportMultipleWindows)
        assertTrue(capabilities.acceptThirdPartyCookies)
    }
}
