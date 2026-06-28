package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleProviderWebViewFirstCollectionTest {
    @Test
    fun geminiKeepsWebViewProfileAndRefreshesThroughUsageDomCollector() {
        val definitions = File("src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt").readText()
        val gemini = definitions.substringAfter("providerId = ProviderId.GEMINI,")
            .substringBefore("ProviderDefinition(")
        val antigravity = definitions.substringAfter("providerId = ProviderId.ANTIGRAVITY,")
            .substringBefore("ProviderDefinition(")

        assertTrue(gemini.contains("authStoreKind = ProviderAuthStoreKind.WEBVIEW_PROFILE"))
        assertTrue(gemini.contains("collectionKind = ProviderCollectionKind.WEBVIEW_COLLECTOR"))
        assertTrue(antigravity.contains("collectionKind = ProviderCollectionKind.NATIVE_API"))
        assertTrue(antigravity.contains("authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN"))
    }

    @Test
    fun blankGoogleCollectionDoesNotFallBackToNativePrivateApiBridge() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt").readText()

        assertFalse(service.contains("rawPayload.isBlank() && providerId.isGoogleProvider()"))
        assertFalse(service.contains("googleBridgePayload(providerId)"))
        assertFalse(service.contains("GoogleOAuthUsageBridge.bridgeUsagePayload(applicationContext, providerId)"))
    }

    @Test
    fun foregroundServiceRefreshKeepsAntigravityNativeOAuthAndGeminiOutOfAppShellNativeCollection() {
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val nativeRefresh = service
            .substringAfter("fun collectNativeProviderUsage")
            .substringBefore("fun collectWebProviderUsage")

        assertFalse(nativeRefresh.contains("GoogleOAuthUsageBridge.bridgeUsagePayload"))
        assertTrue(nativeRefresh.contains("AntigravityOAuthRepository"))
        assertFalse(nativeRefresh.contains("AntigravityFirebaseConnector"))
        assertFalse(appShell.contains("fun collectNativeProviderUsage"))
        assertFalse(appShell.contains("GeminiCliLoopbackOAuthActivity.createIntent"))
        assertFalse(appShell.contains("BackgroundProviderWebCollector("))
    }
}
