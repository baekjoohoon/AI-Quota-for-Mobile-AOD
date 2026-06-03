package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRefreshSessionPolicyTest {
    @Test
    fun webViewBackedProvidersKeepCookiesAfterAutomaticAuthFailure() {
        assertFalse(ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(ProviderId.CLAUDE))
        assertFalse(ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(ProviderId.COPILOT))
        assertFalse(ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(ProviderId.CURSOR))
        assertFalse(ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(ProviderId.CODEX))
    }

    @Test
    fun nativeTokenProvidersMayClearInvalidStoredCredentialsAfterAuthFailure() {
        assertTrue(ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(ProviderId.GEMINI))
        assertTrue(ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(ProviderId.ANTIGRAVITY))
    }

    @Test
    fun automaticRefreshAuthFailureRoutesThroughPolicyBeforeResetter() {
        val service = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val collectionService = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt").readText()
        val appShell = java.io.File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()

        assertTrue(service.contains("ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(job.providerId)"))
        assertTrue(collectionService.contains("ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)"))
        assertFalse(appShell.contains("ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(queuedJob.job.providerId)"))
        assertFalse(appShell.contains("BackgroundProviderWebCollector("))
    }
}
