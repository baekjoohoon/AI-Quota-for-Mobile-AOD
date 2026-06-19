package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
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
}
