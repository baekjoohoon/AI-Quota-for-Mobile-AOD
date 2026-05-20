package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
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
        assertEquals(
            "https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fgemini.google.com%2Fapp&hl=ko",
            definitions[2].loginStartUrl
        )
        assertEquals("https://github.com/settings/copilot", definitions[3].loginStartUrl)
        assertEquals("https://cursor.com/dashboard", definitions[4].loginStartUrl)
    }

    @Test
    fun allowlistsKeepCollectionInsideProviderSessions() {
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CLAUDE, "https://claude.ai/api/organizations/me"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CLAUDE, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://chatgpt.com/backend-api/wham/usage"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://auth.openai.com/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://accounts.google.com/o/oauth2/v2/auth"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://appleid.apple.com/auth/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GEMINI, "https://gemini.google.com/app"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.GEMINI, "https://myaccount.google.com/signinoptions"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.COPILOT, "https://github.com/github-copilot/chat/entitlement"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.COPILOT, "https://github.githubassets.com/assets/login.js"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://api2.cursor.sh/auth/poll"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://api.workos.com/sso/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://authenticate.cursor.sh/oauth/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://authenticator.cursor.sh/oauth/authorize"))
        assertTrue(ProviderDefinitionRegistry.isAllowed(ProviderId.CURSOR, "https://accounts.google.com/o/oauth2/v2/auth"))

        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CLAUDE, "https://firebase.googleapis.com/"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "http://localhost:1455/auth/callback?code=abc"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://cloudfunctions.net/relay"))
        assertFalse(ProviderDefinitionRegistry.isAllowed(ProviderId.CODEX, "https://platform.openai.com/usage"))
    }
}
