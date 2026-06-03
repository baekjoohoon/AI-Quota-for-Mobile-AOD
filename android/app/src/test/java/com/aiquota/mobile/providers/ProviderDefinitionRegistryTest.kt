package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDefinitionRegistryTest {
    @Test
    fun definitionsMatchTheStandaloneLoginHandoff() {
        val definitions = ProviderDefinitionRegistry.all()

        assertEquals(ProviderId.defaultOrder(), definitions.map { it.providerId })
        assertEquals("https://claude.ai/login", definitions[0].loginStartUrl)
        assertEquals("https://chatgpt.com/auth/login", definitions[1].loginStartUrl)
        assertEquals("https://gemini.google.com/usage", definitions[2].loginStartUrl)
        assertEquals("https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota", definitions[2].preferredUsageEndpoint)
        assertEquals(ProviderAuthStoreKind.NATIVE_TOKEN, definitions[2].authStoreKind)
        assertEquals(ProviderCollectionKind.NATIVE_API, definitions[2].collectionKind)
        assertEquals("https://github.com/settings/copilot/features", definitions[3].loginStartUrl)
        assertEquals(ProviderAuthStoreKind.WEBVIEW_PROFILE, definitions[3].authStoreKind)
        assertEquals(ProviderCollectionKind.WEBVIEW_COLLECTOR, definitions[3].collectionKind)
        assertEquals("https://antigravity.google/", definitions[4].loginStartUrl)
        assertEquals("https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels", definitions[4].preferredUsageEndpoint)
        assertEquals(ProviderAuthStoreKind.NATIVE_TOKEN, definitions[4].authStoreKind)
        assertEquals(ProviderCollectionKind.NATIVE_API, definitions[4].collectionKind)
        assertEquals("https://cursor.com/dashboard", definitions[5].loginStartUrl)
    }

    @Test
    fun allowlistsKeepCollectionInsideProviderSessions() {
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CLAUDE, "https://claude.ai/api/organizations/me"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CLAUDE, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://chatgpt.com/api/auth/session"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://auth.openai.com/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://appleid.apple.com/auth/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GEMINI, "https://gemini.google.com/app"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GEMINI, "https://gemini.google.com/usage"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GEMINI, "https://myaccount.google.com/signinoptions"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.COPILOT, "https://github.com/github-copilot/chat/entitlement"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.COPILOT, "https://github.githubassets.com/assets/login.js"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.ANTIGRAVITY, "https://antigravity.google/docs/plans"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.ANTIGRAVITY, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://api2.cursor.sh/auth/poll"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://api.workos.com/sso/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://authenticate.cursor.sh/oauth/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://authenticator.cursor.sh/oauth/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://accounts.google.com/o/oauth2/v2/auth"))

        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CLAUDE, "https://firebase.googleapis.com/"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "http://localhost:1455/auth/callback?code=abc"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://cloudfunctions.net/relay"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://platform.openai.com/usage"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.ANTIGRAVITY, "https://firebase.googleapis.com/"))
    }

    @Test
    fun loginNavigationAllowlistKeepsProviderLoginChallengesButBlocksUnrelatedHosts() {
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CLAUDE, "https://claude.ai/login"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CLAUDE, "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CLAUDE, "https://www.recaptcha.net/recaptcha/enterprise/anchor"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CLAUDE, "https://accounts.youtube.com/accounts/SetSID?ssdc=1"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CLAUDE, "https://play.google.com/log?format=json"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CODEX, "https://accounts.youtube.com/accounts/SetSID?ssdc=1"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CODEX, "https://play.google.com/log?format=json"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.GEMINI, "https://www.recaptcha.net/recaptcha/enterprise/anchor"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.GEMINI, "https://accounts.youtube.com/accounts/SetSID?ssdc=1"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.GEMINI, "https://play.google.com/log?format=json"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CURSOR, "https://accounts.youtube.com/accounts/SetSID?ssdc=1"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CURSOR, "https://play.google.com/log?format=json"))

        assertFalse(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CLAUDE, "https://firebase.googleapis.com/relay"))
        assertFalse(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.GEMINI, "https://cloudfunctions.net/relay"))
        assertFalse(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CURSOR, "https://example.com/dashboard"))
    }

    @Test
    fun googleLoginNavigationAllowsRegionalAccountsHostsForAllGoogleLoginProviders() {
        listOf(
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.GEMINI,
            ProviderId.ANTIGRAVITY,
            ProviderId.CURSOR
        ).forEach { providerId ->
            assertTrue(
                "provider=${providerId.storageId}",
                ProviderDefinitionRegistry.isLoginNavigationAllowed(
                    providerId,
                    "https://accounts.google.co.kr/signin/v2/challenge/pwd"
                )
            )
        }

        assertFalse(
            ProviderDefinitionRegistry.isLoginNavigationAllowed(
                ProviderId.CLAUDE,
                "https://accounts.google.evil.example/signin/v2/challenge/pwd"
            )
        )
    }

    @Test
    fun collectorNavigationAllowlistKeepsHiddenRefreshOnProviderShellsOnly() {
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CLAUDE, "https://claude.ai/"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CODEX, "https://chatgpt.com/"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.COPILOT, "https://github.com/settings/copilot/features"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CURSOR, "https://cursor.com/dashboard"))

        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/pwd"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CODEX, "https://auth.openai.com/authorize"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.COPILOT, "https://github.githubassets.com/assets/login.js"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CURSOR, "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage"))
    }
}
