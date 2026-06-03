package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLoginStrategyTest {
    @Test
    fun detectsProviderSpecificSuccessSignals() {
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CLAUDE, "https://claude.ai/new", mapOf("lastActiveOrg" to "org_123"), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.GEMINI, "http://127.0.0.1:46417/oauth2callback?code=abc&state=xyz", emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.ANTIGRAVITY, "http://127.0.0.1:8080/callback?code=abc", emptyMap(), ""))
    }

    @Test
    fun codexLocalOAuthCallbackRecoversToChatGptShellWithoutCompletingLogin() {
        val callbackUrl = "http://localhost:1455/auth/callback?code=redacted&state=redacted"

        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, callbackUrl, emptyMap(), ""))
        assertTrue(ProviderLoginStrategy.shouldRecoverCodexLocalAuthCallback(callbackUrl))
        assertTrue(ProviderLoginStrategy.CODEX_CALLBACK_RECOVERY_URL.startsWith("https://chatgpt.com/"))
        assertTrue(ProviderLoginStrategy.CODEX_CALLBACK_RECOVERY_URL.contains("/codex/cloud/settings/analytics"))
        assertTrue(
            ProviderWebCollectorScripts.shouldRunCollector(
                ProviderId.CODEX,
                ProviderLoginStrategy.CODEX_CALLBACK_RECOVERY_URL,
                emptyMap(),
                "ChatGPT"
            )
        )
    }

    @Test
    fun googleProviderLoopbackCallbacksAreNotWebViewSuccessSignals() {
        val geminiCallbackUrl = "http://127.0.0.1:46417/oauth2callback?code=redacted&state=redacted"
        val antigravityCallbackUrl = "http://127.0.0.1:8080/callback?code=redacted&scope=redacted"

        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.GEMINI, geminiCallbackUrl, emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.ANTIGRAVITY, antigravityCallbackUrl, emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.GEMINI, "http://localhost:46417/oauth2callback?code=redacted", emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.ANTIGRAVITY, "http://localhost:8080/callback#error=access_denied", emptyMap(), ""))
    }

    @Test
    fun geminiSignedInWebResourcesAreInteractiveLoginSuccessSignals() {
        assertTrue(
            ProviderLoginStrategy.isInteractiveLoginSessionReached(
                ProviderId.GEMINI,
                "https://analytics.google.com/g/collect?up.signed_in=yes&dl=https%3A%2F%2Fgemini.google.com%2Fapp"
            )
        )
        assertTrue(
            ProviderLoginStrategy.isInteractiveLoginSessionReached(
                ProviderId.GEMINI,
                "https://play.google.com/log?hasfast=true&auth=SAPISIDHASH+redacted&authuser=0&format=json"
            )
        )
        assertFalse(
            ProviderLoginStrategy.isInteractiveLoginSessionReached(
                ProviderId.GEMINI,
                "https://analytics.google.com/g/collect?up.signed_in=no&dl=https%3A%2F%2Fgemini.google.com%2Fapp"
            )
        )
        assertFalse(
            ProviderLoginStrategy.isInteractiveLoginSessionReached(
                ProviderId.CLAUDE,
                "https://analytics.google.com/g/collect?up.signed_in=yes"
            )
        )
    }

    @Test
    fun geminiStaysOpenAfterGoogleSessionSignalUntilUsagePayloadArrives() {
        assertTrue(
            ProviderLoginStrategy.isInteractiveLoginSessionReached(
                ProviderId.GEMINI,
                "https://analytics.google.com/g/collect?up.signed_in=yes"
            )
        )
        assertFalse(ProviderLoginStrategy.shouldFinishOnInteractiveLoginSessionReached(ProviderId.GEMINI))
        assertTrue(ProviderLoginStrategy.shouldFinishOnInteractiveLoginSessionReached(ProviderId.ANTIGRAVITY))
    }

    @Test
    fun claudeGoogleSsoIsNotBlockedByLoginActivity() {
        val source = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()

        assertFalse(source.contains("blockedEmbeddedGoogleSso"))
        assertFalse(source.contains("Google sign-in is blocked"))
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
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.ANTIGRAVITY, "https://antigravity.google/docs/plans", emptyMap(), "Antigravity Plus"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.ANTIGRAVITY, "https://accounts.google.com/signin", emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CURSOR, "https://cursor.com/dashboard", emptyMap(), "__NEXT_DATA__"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, "https://chatgpt.com/auth/login", emptyMap(), "ChatGPT"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, "https://chatgpt.com/", emptyMap(), "ChatGPT"))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, "aiquota://codex/oauth?code=abc", emptyMap(), ""))
        assertFalse(ProviderLoginStrategy.isLoginComplete(ProviderId.CODEX, "http://localhost:1455/auth/callback?code=abc", emptyMap(), ""))
    }

    @Test
    fun copilotRedirectsSignedInGithubDashboardBackToSettings() {
        assertTrue(
            ProviderLoginStrategy.shouldRedirectCopilotToSettings(
                "https://github.com/dashboard",
                "Dashboard Home Top Repositories Pull requests"
            )
        )
        assertTrue(
            ProviderLoginStrategy.shouldRedirectCopilotToSettings(
                "https://github.com/",
                "Home Top Repositories"
            )
        )
        assertFalse(
            ProviderLoginStrategy.shouldRedirectCopilotToSettings(
                "https://github.com/login",
                "Sign in to GitHub"
            )
        )
        assertFalse(
            ProviderLoginStrategy.shouldRedirectCopilotToSettings(
                "https://github.com/settings/copilot",
                "Copilot"
            )
        )
    }

    @Test
    fun oauthTransientErrorsAreRecoverable() {
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("https://accounts.google.com/o/oauth2/v2/auth", -2))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("https://github.com/login/oauth/authorize", -6))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("https://authenticator.cursor.sh/oauth/authorize", -2))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("https://antigravity.google/docs/plans", -2))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("http://localhost:1455/auth/callback?code=abc", -6))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("http://127.0.0.1:8080/callback?code=abc", -6))
        assertTrue(ProviderLoginStrategy.isTransientNavigationError("https://accounts.google.co.in/accounts/SetSID", -2))
        assertFalse(ProviderLoginStrategy.isTransientNavigationError("https://malicious.example", -10))
    }

    @Test
    fun mainFrameHttpAccessBlocksAreNotLeftConnecting() {
        assertTrue(ProviderLoginStrategy.isBlockingHttpError("https://accounts.google.com/o/oauth2/v2/auth", 403))
        assertTrue(ProviderLoginStrategy.isBlockingHttpError("https://auth.workos.com/sso/authorize", 403))
        assertFalse(ProviderLoginStrategy.isBlockingHttpError("https://claude.ai/login", 403))
        assertFalse(ProviderLoginStrategy.isBlockingHttpError("https://authenticator.cursor.sh/?client_id=redacted", 403))
        assertFalse(ProviderLoginStrategy.isBlockingHttpError("https://authenticator.cursor.sh/?client_id=redacted", 401))
        assertFalse(ProviderLoginStrategy.isBlockingHttpError("https://accounts.google.com/o/oauth2/v2/auth", 302))
    }

}
