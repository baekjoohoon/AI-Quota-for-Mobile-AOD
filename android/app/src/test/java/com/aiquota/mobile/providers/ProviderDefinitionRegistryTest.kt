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
        val definitionsByProvider = definitions.associateBy { it.providerId }

        assertEquals(ProviderId.defaultOrder(), definitions.map { it.providerId })
        assertEquals("https://claude.ai/login", definitionsByProvider[ProviderId.CLAUDE]?.loginStartUrl)
        assertEquals("https://chatgpt.com/auth/login", definitionsByProvider[ProviderId.CODEX]?.loginStartUrl)
        assertEquals("aiquota://provider/glm-api-key", definitionsByProvider[ProviderId.GLM]?.loginStartUrl)
        assertEquals(
            "https://api.z.ai/api/monitor/usage/quota/limit",
            definitionsByProvider[ProviderId.GLM]?.preferredUsageEndpoint
        )
        assertEquals(ProviderAuthStoreKind.NATIVE_TOKEN, definitionsByProvider[ProviderId.GLM]?.authStoreKind)
        assertEquals(ProviderCollectionKind.NATIVE_API, definitionsByProvider[ProviderId.GLM]?.collectionKind)
        assertEquals("https://opencode.ai/auth", definitionsByProvider[ProviderId.OPENCODE]?.loginStartUrl)
        assertEquals("https://opencode.ai/auth", definitionsByProvider[ProviderId.OPENCODE]?.preferredUsageEndpoint)
        assertEquals(ProviderAuthStoreKind.WEBVIEW_PROFILE, definitionsByProvider[ProviderId.OPENCODE]?.authStoreKind)
        assertEquals(ProviderCollectionKind.WEBVIEW_COLLECTOR, definitionsByProvider[ProviderId.OPENCODE]?.collectionKind)
        assertEquals("https://gemini.google.com/usage", definitionsByProvider[ProviderId.GEMINI]?.loginStartUrl)
        assertEquals(
            "https://gemini.google.com/usage",
            definitionsByProvider[ProviderId.GEMINI]?.preferredUsageEndpoint
        )
        assertEquals(ProviderAuthStoreKind.WEBVIEW_PROFILE, definitionsByProvider[ProviderId.GEMINI]?.authStoreKind)
        assertEquals(ProviderCollectionKind.WEBVIEW_COLLECTOR, definitionsByProvider[ProviderId.GEMINI]?.collectionKind)
        assertEquals("https://github.com/settings/copilot/features", definitionsByProvider[ProviderId.COPILOT]?.loginStartUrl)
        assertEquals(ProviderAuthStoreKind.WEBVIEW_PROFILE, definitionsByProvider[ProviderId.COPILOT]?.authStoreKind)
        assertEquals(ProviderCollectionKind.WEBVIEW_COLLECTOR, definitionsByProvider[ProviderId.COPILOT]?.collectionKind)
        assertEquals("https://antigravity.google/", definitionsByProvider[ProviderId.ANTIGRAVITY]?.loginStartUrl)
        assertEquals(
            "https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels",
            definitionsByProvider[ProviderId.ANTIGRAVITY]?.preferredUsageEndpoint
        )
        assertEquals(ProviderAuthStoreKind.NATIVE_TOKEN, definitionsByProvider[ProviderId.ANTIGRAVITY]?.authStoreKind)
        assertEquals(ProviderCollectionKind.NATIVE_API, definitionsByProvider[ProviderId.ANTIGRAVITY]?.collectionKind)
        assertEquals("https://cursor.com/dashboard", definitionsByProvider[ProviderId.CURSOR]?.loginStartUrl)
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
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GLM, "https://api.z.ai/api/monitor/usage/quota/limit"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GLM, "https://z.ai/manage-apikey/coding-plan/personal/my-plan"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GLM, "https://chat.z.ai/"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GLM, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.OPENCODE, "https://opencode.ai/auth"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.OPENCODE, "https://auth.opencode.ai/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.OPENCODE, "https://opencode.ai/zen/go/usage"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.OPENCODE, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.OPENCODE, "https://github.com/login/oauth/authorize"))
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
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://admin.openai.com/analytics/codex"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.GLM, "https://bigmodel.cn/api/monitor/usage/quota/limit"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.OPENCODE, "https://opencode.ai/docs/go/"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.OPENCODE, "https://example.com/auth"))
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
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.OPENCODE, "https://opencode.ai/auth"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.OPENCODE, "https://auth.opencode.ai/authorize"))
        assertTrue(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.OPENCODE, "https://github.com/login/oauth/authorize"))

        assertFalse(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CLAUDE, "https://firebase.googleapis.com/relay"))
        assertFalse(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.GEMINI, "https://cloudfunctions.net/relay"))
        assertFalse(ProviderDefinitionRegistry.isLoginNavigationAllowed(ProviderId.CURSOR, "https://example.com/dashboard"))
    }

    @Test
    fun googleLoginNavigationAllowsRegionalAccountsHostsForAllGoogleLoginProviders() {
        listOf(
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.OPENCODE,
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
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.GLM, "https://api.z.ai/api/monitor/usage/quota/limit"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.GLM, "https://z.ai/manage-apikey/coding-plan/personal/my-plan"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.OPENCODE, "https://opencode.ai/auth"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.OPENCODE, "https://auth.opencode.ai/authorize"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.OPENCODE, "https://opencode.ai/zen/go/usage"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.COPILOT, "https://github.com/settings/copilot/features"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CURSOR, "https://cursor.com/dashboard"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CURSOR, "https://api.workos.com/user_management/authorize"))
        assertTrue(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CURSOR, "https://authenticate.cursor.sh/user_management/authorize"))

        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CLAUDE, "https://accounts.google.com/signin/v2/challenge/pwd"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CODEX, "https://auth.openai.com/authorize"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.GLM, "https://example.com/"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.OPENCODE, "https://github.com/login/oauth/authorize"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.OPENCODE, "https://opencode.ai/docs/go/"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.COPILOT, "https://github.githubassets.com/assets/login.js"))
        assertFalse(ProviderDefinitionRegistry.isCollectorNavigationAllowed(ProviderId.CURSOR, "https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage"))
    }
}
