package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRefreshHttpErrorPolicyTest {
    @Test
    fun cursorAuthExchangeForbiddenResponsesCanContinueForChallengeFlow() {
        assertTrue(
            ProviderRefreshHttpErrorPolicy.shouldIgnoreMainFrameHttpError(
                ProviderId.CURSOR,
                "https://authenticator.cursor.sh/?client_id=abc",
                403
            )
        )
        assertTrue(
            ProviderRefreshHttpErrorPolicy.shouldIgnoreMainFrameHttpError(
                ProviderId.CURSOR,
                "https://api.workos.com/user_management/authorize",
                403
            )
        )
    }

    @Test
    fun providerOwnedUsagePagesStillFailOnMainFrameHttpErrors() {
        assertFalse(
            ProviderRefreshHttpErrorPolicy.shouldIgnoreMainFrameHttpError(
                ProviderId.CURSOR,
                "https://cursor.com/dashboard",
                403
            )
        )
        assertFalse(
            ProviderRefreshHttpErrorPolicy.shouldIgnoreMainFrameHttpError(
                ProviderId.OPENCODE,
                "https://opencode.ai/workspace/wrk_123/go",
                403
            )
        )
        assertFalse(
            ProviderRefreshHttpErrorPolicy.shouldIgnoreMainFrameHttpError(
                ProviderId.CLAUDE,
                "https://claude.ai/",
                403
            )
        )
    }

    @Test
    fun codexMainFrameUnauthorizedOrForbiddenRequiresInteractiveAuth() {
        listOf(401, 403).forEach { status ->
            val failure = ProviderRefreshHttpErrorPolicy.failureForMainFrameHttpError(
                ProviderId.CODEX,
                "https://chatgpt.com/codex/cloud/settings/analytics",
                status
            )

            assertEquals(ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED, failure.kind)
        }
    }

    @Test
    fun nonCodexMainFrameForbiddenStaysTransientHttp() {
        val failure = ProviderRefreshHttpErrorPolicy.failureForMainFrameHttpError(
            ProviderId.CLAUDE,
            "https://claude.ai/",
            403
        )

        assertEquals(ProviderRefreshFailureKind.TRANSIENT_HTTP, failure.kind)
    }
}
