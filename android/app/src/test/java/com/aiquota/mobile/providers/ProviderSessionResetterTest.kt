package com.aiquota.mobile.providers

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderSessionResetterTest {
    @Test
    fun explicitDisconnectClearsOauthStoresBeforeWebSession() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderSessionResetter.kt").readText()
        val method = source.substringAfter("fun disconnect(providerId: ProviderId)")
            .substringBefore("private fun clearStoredProviderCredentials")

        assertTrue(method.contains("clearStoredProviderCredentials(providerId)"))
        assertTrue(method.contains("ProviderWebSessionCleaner.clearProviderWebSession(appContext, providerId)"))
        assertTrue(method.indexOf("clearStoredProviderCredentials(providerId)") < method.indexOf("ProviderWebSessionCleaner.clearProviderWebSession(appContext, providerId)"))
    }

    @Test
    fun interactiveDisconnectWaitsForDestructiveWebSessionCleanup() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderSessionResetter.kt").readText()
        val method = source.substringAfter("suspend fun disconnectAndWait(providerId: ProviderId)")
            .substringBefore("private fun clearStoredProviderCredentials")
        val appShell = java.io.File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val disconnectFlow = appShell.substringAfter("fun disconnectProvider(providerId: ProviderId)")
            .substringBefore("fun refreshNotificationState")

        assertTrue(method.contains("clearStoredProviderCredentials(providerId)"))
        assertTrue(method.contains("ProviderWebSessionCleaner.clearProviderWebSessionAndWait(appContext, providerId)"))
        assertTrue(method.indexOf("clearStoredProviderCredentials(providerId)") < method.indexOf("ProviderWebSessionCleaner.clearProviderWebSessionAndWait(appContext, providerId)"))
        assertTrue(disconnectFlow.contains("providerSessionResetter.disconnectAndWait(providerId)"))
    }

    @Test
    fun explicitDisconnectNotifiesRunningBackgroundCollectorsToDropRetainedWebViews() {
        val resetter = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderSessionResetter.kt").readText()
        val service = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val disconnectAndWait = resetter.substringAfter("suspend fun disconnectAndWait(providerId: ProviderId)")
            .substringBefore("private fun clearStoredProviderCredentials")

        assertTrue(disconnectAndWait.contains("notifyProviderSessionReset(providerId)"))
        assertTrue(resetter.contains("ProviderBackgroundRefreshService.createSessionResetIntent(appContext, providerId)"))
        assertTrue(service.contains("ACTION_PROVIDER_SESSION_RESET"))
        assertTrue(service.contains("registerReceiver(sessionResetReceiver"))
        assertTrue(service.contains("handleProviderSessionReset(providerId)"))
        assertTrue(service.contains("destroyProviderWebView(providerId)"))
        assertTrue(service.contains("repository.removeProviderSnapshot(providerId)"))
    }

    @Test
    fun awaitedWebSessionCleanupWaitsForCookieDeletionCallbacks() {
        val cleaner = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt").readText()
        val awaitedMethod = cleaner.substringAfter("suspend fun clearProviderWebSessionAndWait(")
            .substringBefore("fun clearProviderWebSessionCookies(providerId: ProviderId)")

        assertTrue(
            "Explicit user disconnect must wait for WebView cookie deletion before reconnect can start.",
            awaitedMethod.contains("withContext(Dispatchers.Main.immediate)") &&
                cleaner.contains("suspendCancellableCoroutine") &&
                cleaner.contains("ValueCallback<Boolean>") &&
                cleaner.contains("clearProviderWebSessionCookiesAndWait(cookieManager, providerId)")
        )
    }

    @Test
    fun oauthProvidersExposeDisconnectTokenClearingMethods() {
        val gemini = java.io.File("src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt").readText()
        val antigravity = java.io.File("src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt").readText()
        val resetter = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderSessionResetter.kt").readText()

        assertTrue(gemini.substringAfter("fun disconnect()").contains("KEY_REFRESH_TOKEN"))
        assertTrue(gemini.substringAfter("fun disconnect()").contains("KEY_ACCESS_TOKEN"))
        assertTrue(antigravity.substringAfter("fun disconnect()").contains("KEY_REFRESH_TOKEN"))
        assertTrue(antigravity.substringAfter("fun disconnect()").contains("KEY_ACCESS_TOKEN"))
        assertTrue(resetter.contains("GoogleAppAuthTokenStore(appContext).clear(providerId)"))
        assertTrue(resetter.contains("GoogleIdentityCredentialStore(appContext).clear(providerId)"))
        assertTrue(resetter.contains("GeminiCliOAuthRepository(appContext).disconnect()"))
        assertTrue(resetter.contains("AntigravityOAuthRepository(appContext).disconnect()"))
        assertFalse(resetter.contains("CodexOAuthRepository"))
    }
}
