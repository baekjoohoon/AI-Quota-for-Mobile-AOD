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
        assertFalse(ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(ProviderId.GEMINI))
    }

    @Test
    fun nativeTokenProvidersMayClearInvalidStoredCredentialsAfterAuthFailure() {
        assertTrue(ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(ProviderId.ANTIGRAVITY))
    }

    @Test
    fun automaticRefreshAuthFailureDoesNotClearCredentials() {
        val service = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val collectionService = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt").readText()
        val appShell = java.io.File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val authFailureHandler = service.substringAfter("private fun handleRefreshAuthFailure")
            .substringBefore("private suspend fun collectNativeProviderUsage")
        val automaticBranch = authFailureHandler.substringAfter("if (automaticRefresh)")
            .substringBefore("if (!ProviderRefreshSessionPolicy")

        assertFalse(automaticBranch.contains("ProviderSessionResetter(applicationContext).disconnect"))
        assertTrue(authFailureHandler.contains("ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)"))
        assertTrue(collectionService.contains("ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)"))
        assertFalse(appShell.contains("ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(queuedJob.job.providerId)"))
        assertFalse(appShell.contains("BackgroundProviderWebCollector("))
    }
}
