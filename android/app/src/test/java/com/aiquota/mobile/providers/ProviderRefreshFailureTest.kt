package com.aiquota.mobile.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.aiquota.mobile.local.ProviderId

class ProviderRefreshFailureTest {
    @Test
    fun interactiveAuthRequiredFailureKeepsStructuredKind() {
        val failure = ProviderRefreshFailure.interactiveAuthRequired()

        assertEquals(ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED, failure.kind)
        assertEquals("Provider session requires sign-in.", failure.message)
    }

    @Test
    fun onlyExplicitAuthFailuresRequireInteractiveAuth() {
        assertTrue(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderRefreshFailureKind.TIMEOUT))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderRefreshFailureKind.COLLECTOR_ERROR))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderRefreshFailureKind.TRANSIENT_PAGE_LOAD))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderRefreshFailureKind.TRANSIENT_HTTP))
    }

    @Test
    fun codexUsageUnavailableFailuresStayRecoverableWithoutInteractiveReauth() {
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderId.CODEX, ProviderRefreshFailureKind.TIMEOUT))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderId.CODEX, ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderId.CURSOR, ProviderRefreshFailureKind.TIMEOUT))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderId.CURSOR, ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD))

        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderId.CLAUDE, ProviderRefreshFailureKind.TIMEOUT))
        assertFalse(ProviderRefreshFailureClassifier.requiresInteractiveAuth(ProviderId.COPILOT, ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD))
    }
}
