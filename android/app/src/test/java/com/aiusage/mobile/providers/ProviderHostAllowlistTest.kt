package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHostAllowlistTest {
    @Test
    fun allowsExactProviderHosts() {
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://claude.ai/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://www.claude.ai/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://accounts.google.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://myaccount.google.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://chatgpt.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://auth.openai.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://accounts.google.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://myaccount.google.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://appleid.apple.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://login.microsoftonline.com/"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://platform.openai.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.GEMINI, "https://gemini.google.com/app"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.GEMINI, "https://accounts.google.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.COPILOT, "https://github.com/settings/copilot"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.COPILOT, "https://githubcopilot.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://cursor.com/dashboard"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://www.cursor.com/dashboard"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://api.workos.com/user_management/authorize"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://api2.cursor.sh/auth/poll"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://authenticate.cursor.sh/user_management/authorize"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://authenticator.cursor.sh/"))
    }

    @Test
    fun allowsSubdomainsOfProviderHosts() {
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://console.claude.ai/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://login.auth.openai.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.GEMINI, "https://login.accounts.google.com/"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.COPILOT, "https://docs.github.com/copilot"))
        assertTrue(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://www.cursor.com/dashboard"))
    }

    @Test
    fun rejectsLookalikeHostsAndOtherProviderHosts() {
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://evilclaude.ai/"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "https://claude.ai.evil.com/"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CODEX, "https://openai.com/"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.GEMINI, "https://google.com/"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.COPILOT, "https://github.com.evil.com/"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://chatgpt.com/"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CURSOR, "https://cursor.sh.evil.com/"))
    }

    @Test
    fun rejectsMalformedUrls() {
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, ""))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "claude.ai"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "not a url"))
        assertFalse(ProviderHostAllowlist.isAllowed(ProviderId.CLAUDE, "javascript:alert(1)"))
    }
}
