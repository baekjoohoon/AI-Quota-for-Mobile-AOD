package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderRefreshTimeoutPolicyTest {
    @Test
    fun cursorAuthChallengeTimeoutRequiresInteractiveAuth() {
        val failure = ProviderRefreshTimeoutPolicy.failureFor(
            ProviderId.CURSOR,
            "https://authenticator.cursor.sh/?client_id=abc"
        )

        assertEquals(ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED, failure.kind)
        assertEquals("Background refresh reached a provider login page.", failure.message)
    }

    @Test
    fun opencodeAuthExchangeTimeoutRequiresInteractiveAuth() {
        val failure = ProviderRefreshTimeoutPolicy.failureFor(
            ProviderId.OPENCODE,
            "https://auth.opencode.ai/authorize"
        )

        assertEquals(ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED, failure.kind)
        assertEquals("Background refresh reached a provider login page.", failure.message)
    }

    @Test
    fun providerOwnedUsagePageTimeoutStaysTimeout() {
        val failure = ProviderRefreshTimeoutPolicy.failureFor(
            ProviderId.CURSOR,
            "https://cursor.com/dashboard"
        )

        assertEquals(ProviderRefreshFailureKind.TIMEOUT, failure.kind)
        assertEquals("Background refresh timed out.", failure.message)
    }
}
