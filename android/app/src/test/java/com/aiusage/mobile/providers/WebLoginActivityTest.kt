package com.aiusage.mobile.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.aiusage.mobile.local.ProviderId

class WebLoginActivityTest {
    @Test
    fun detectsClaudeAuthenticatedOrgCookie() {
        assertTrue(
            claudeCookieIndicatesAuthenticatedSession(
                "cf_clearance=keep; lastActiveOrg=6d7e7f53-6216-45b3-93bb-764f73f98c92; other=value"
            )
        )
    }

    @Test
    fun rejectsClaudeCookieWithoutActiveOrganization() {
        assertFalse(
            claudeCookieIndicatesAuthenticatedSession(
                "cf_clearance=keep; sessionKey=abc"
            )
        )
    }

    @Test
    fun acceptsClaudeAuthenticatedAppNavigation() {
        assertTrue(
            isClaudeAuthenticatedAppNavigation("https://claude.ai/new")
        )
    }

    @Test
    fun rejectsClaudeLoginNavigationForAuthenticatedShortcut() {
        assertFalse(
            isClaudeAuthenticatedAppNavigation("https://claude.ai/login")
        )
    }

    @Test
    fun extractsClaudeOrganizationIdFromVerifiedApiPayload() {
        assertEquals(
            "6d7e7f53-6216-45b3-93bb-764f73f98c92",
            claudeOrganizationIdFromVerificationPayload(
                """
                {
                  "ok": true,
                  "status": 200,
                  "body": {
                    "organizations": [
                      {"uuid": "6d7e7f53-6216-45b3-93bb-764f73f98c92"}
                    ]
                  }
                }
                """.trimIndent()
            )
        )
    }

    @Test
    fun rejectsFailedClaudeApiVerificationPayload() {
        assertFalse(
            claudeVerificationPayloadHasOrganization(
                """{"ok":false,"status":401,"body":{"error":"unauthorized"}}"""
            )
        )
    }

    @Test
    fun detectsCursorAuthenticatorRedirectPage() {
        assertTrue(
            isCursorAuthenticatorUrl(
                provider = ProviderId.CURSOR,
                url = "https://authenticator.cursor.sh/"
            )
        )
    }

    @Test
    fun rejectsCursorAuthenticatorForOtherProviders() {
        assertFalse(
            isCursorAuthenticatorUrl(
                provider = ProviderId.CLAUDE,
                url = "https://authenticator.cursor.sh/"
            )
        )
    }

    @Test
    fun treatsGeminiLoopbackCallbackAsRecoverableNavigation() {
        assertTrue(
            isProviderOAuthCallbackNavigation(
                providerId = ProviderId.GEMINI,
                url = "http://127.0.0.1:46417/oauth2callback?code=abc&state=xyz"
            )
        )
    }

    @Test
    fun treatsCodexLoopbackCallbackAsRecoverableNavigation() {
        assertTrue(
            isProviderOAuthCallbackNavigation(
                providerId = ProviderId.CODEX,
                url = "http://localhost:1455/auth/callback?code=abc&state=xyz"
            )
        )
    }

    @Test
    fun recoversGoogleAuthErrorsForProviderLogin() {
        assertTrue(
            shouldRecoverLoginNavigationError(
                providerId = ProviderId.GEMINI,
                url = "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc",
                attempt = 0
            )
        )
        assertTrue(
            shouldRecoverLoginNavigationError(
                providerId = ProviderId.CLAUDE,
                url = "https://accounts.google.com/signin/oauth/consent",
                attempt = 0
            )
        )
    }

    @Test
    fun rejectsLoginRecoveryAfterMaxAttemptsOrUnknownHost() {
        assertFalse(
            shouldRecoverLoginNavigationError(
                providerId = ProviderId.GEMINI,
                url = "https://accounts.google.com/o/oauth2/v2/auth?client_id=abc",
                attempt = 2
            )
        )
        assertFalse(
            shouldRecoverLoginNavigationError(
                providerId = ProviderId.GEMINI,
                url = "https://example.com/login",
                attempt = 0
            )
        )
    }

    @Test
    fun usesProviderAppUrlForLoginRecovery() {
        assertEquals("https://gemini.google.com/app", loginRecoveryUrlFor(ProviderId.GEMINI))
        assertEquals("https://claude.ai/", loginRecoveryUrlFor(ProviderId.CLAUDE))
        assertEquals("https://cursor.com/dashboard", loginRecoveryUrlFor(ProviderId.CURSOR))
    }
}
