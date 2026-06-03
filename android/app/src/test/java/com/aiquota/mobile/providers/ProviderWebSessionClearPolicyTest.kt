package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWebSessionClearPolicyTest {
    @Test
    fun codexAndCursorClearWebSessionBeforeInteractiveReauth() {
        assertTrue(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.CODEX,
                ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED
            )
        )
        assertTrue(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.CURSOR,
                ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED
            )
        )
    }

    @Test
    fun codexClearsWebSessionBeforeFreshLoginAttempts() {
        assertTrue(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.CODEX,
                null
            )
        )
        assertTrue(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.CODEX,
                ProviderConnectionState.DISCONNECTED
            )
        )
        assertTrue(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.CODEX,
                ProviderConnectionState.NOT_CONNECTED
            )
        )
        assertTrue(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.CODEX,
                ProviderConnectionState.ERROR
            )
        )
    }

    @Test
    fun connectedOrOtherProviderLoginDoesNotClearWebSession() {
        assertFalse(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.CODEX,
                ProviderConnectionState.CONNECTED
            )
        )
        assertFalse(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.CLAUDE,
                ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED
            )
        )
    }

    @Test
    fun allProvidersClearWebSessionOnExplicitDisconnect() {
        ProviderId.entries.forEach { providerId ->
            assertTrue(
                "${providerId.storageId} should clear WebView session on explicit disconnect",
                ProviderWebSessionClearPolicy.shouldClearOnDisconnect(providerId)
            )
        }
    }

    @Test
    fun explicitDisconnectUsesProviderScopedCookieUrls() {
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CLAUDE).contains("https://claude.ai"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CLAUDE).contains("https://accounts.google.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://chatgpt.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://chatgpt.com/api/auth/session"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://chatgpt.com/backend-api/me"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://chatgpt.com/codex/cloud/settings/analytics"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://auth.openai.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://auth.openai.com/authorize"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://ws.chatgpt.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://sentinel.openai.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://oaistatic.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.GEMINI).contains("https://gemini.google.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.GEMINI).contains("https://accounts.google.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.COPILOT).contains("https://github.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.ANTIGRAVITY).contains("https://antigravity.google"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.ANTIGRAVITY).contains("https://accounts.google.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CURSOR).contains("https://cursor.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CURSOR).contains("https://auth.workos.com"))
        assertTrue(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CURSOR).contains("https://github.com"))

        assertFalse(ProviderWebSessionClearPolicy.cookieUrls(ProviderId.CODEX).contains("https://github.com"))
    }

    @Test
    fun explicitDisconnectAlsoClearsProviderStorageOrigins() {
        ProviderId.entries.forEach { providerId ->
            assertTrue(
                "${providerId.storageId} should clear WebView origin storage on explicit disconnect",
                ProviderWebSessionClearPolicy.storageOrigins(providerId).isNotEmpty()
            )
        }
        assertTrue(ProviderWebSessionClearPolicy.storageOrigins(ProviderId.CODEX).contains("https://chatgpt.com"))
        assertTrue(ProviderWebSessionClearPolicy.storageOrigins(ProviderId.CURSOR).contains("https://cursor.com"))
    }

    @Test
    fun expiringCookieHeadersDoNotIncludeCookieValues() {
        val headers = ProviderWebSessionClearPolicy.expiringCookieHeaders("a=secret; b=private")

        assertTrue(headers.contains("a=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"))
        assertTrue(headers.contains("b=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"))
        assertFalse(headers.joinToString("\n").contains("secret"))
        assertFalse(headers.joinToString("\n").contains("private"))
    }

    @Test
    fun expiringCookieHeadersIncludeDomainVariantsWithoutValues() {
        val headers = ProviderWebSessionClearPolicy.expiringCookieHeaders(
            cookieHeader = "session=secret",
            url = "https://chatgpt.com"
        )

        assertTrue(headers.contains("session=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"))
        assertTrue(headers.contains("session=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=chatgpt.com"))
        assertTrue(headers.contains("session=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.chatgpt.com"))
        assertFalse(headers.joinToString("\n").contains("secret"))
    }

    @Test
    fun expiringCookieHeadersIncludeSecureVariantsForPrefixedSessionCookies() {
        val headers = ProviderWebSessionClearPolicy.expiringCookieHeaders(
            cookieHeader = "__Secure-next-auth.session-token.0=secret; __Host-next-auth.csrf-token=private",
            url = "https://chatgpt.com"
        )

        assertTrue(headers.any { it.startsWith("__Secure-next-auth.session-token.0=;") && it.contains("; Secure") })
        assertTrue(headers.any { it.startsWith("__Host-next-auth.csrf-token=;") && it.contains("; Secure") && !it.contains("; Domain=") })
        assertFalse(headers.joinToString("\n").contains("secret"))
        assertFalse(headers.joinToString("\n").contains("private"))
    }

    @Test
    fun expiringCookieHeadersIncludePathVariantsForPathScopedCookies() {
        val headers = ProviderWebSessionClearPolicy.expiringCookieHeaders(
            cookieHeader = "session=secret",
            url = "https://chatgpt.com/backend-api/me"
        )

        assertTrue(headers.contains("session=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/backend-api"))
        assertTrue(headers.contains("session=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/backend-api; Domain=chatgpt.com"))
        assertTrue(headers.contains("session=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/backend-api; Domain=.chatgpt.com"))
        assertFalse(headers.joinToString("\n").contains("secret"))
    }

    @Test
    fun expiringCookieHeadersIncludeParentDomainVariantsForOauthSubdomains() {
        val headers = ProviderWebSessionClearPolicy.expiringCookieHeaders(
            cookieHeader = "sso=secret",
            url = "https://accounts.google.com"
        )

        assertTrue(headers.contains("sso=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=google.com"))
        assertTrue(headers.contains("sso=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.google.com"))
        assertFalse(headers.joinToString("\n").contains("secret"))
    }

    @Test
    fun disconnectFlowClearsProviderWebSessionCookiesBeforeRemovingSnapshot() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val method = source.substringAfter("fun disconnectProvider(providerId: ProviderId)")
            .substringBefore("fun refreshNotificationState")

        assertFalse(method.contains("queuedRefreshJobs"))
        assertTrue(method.contains("providerSessionResetter.disconnectAndWait(providerId)"))
        assertTrue(method.indexOf("providerSessionResetter.disconnectAndWait(providerId)") < method.indexOf("localUsageRepository.removeProviderSnapshot(providerId)"))
    }
}
