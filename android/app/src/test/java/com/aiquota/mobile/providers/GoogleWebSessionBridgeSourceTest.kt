package com.aiquota.mobile.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoogleWebSessionBridgeSourceTest {
    @Test
    fun googleOauthBridgeIsNotExposedToWebViewCollectors() {
        val login = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val foreground = File("src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt").readText()
        val oauthBridge = File("src/main/java/com/aiquota/mobile/providers/GoogleOAuthUsageBridge.kt").readText()

        assertFalse(oauthBridge.contains("GoogleWebSessionCodeAssistFetcher.bridgeUsagePayload"))
        assertTrue(oauthBridge.contains("GoogleAppAuthUsageAuthorizer.fetchUsagePayload"))
        assertTrue(oauthBridge.contains("GoogleIdentityUsageAuthorizer.fetchUsagePayload"))
        listOf(login, service, foreground).forEach { source ->
            assertFalse(source.contains("fetchGeminiWebSessionUsagePayload"))
            assertFalse(source.contains("fetchAntigravityWebSessionUsagePayload"))
            assertFalse(source.contains("GoogleOAuthUsageBridge.bridgeUsagePayload"))
        }
    }

    @Test
    fun googleProviderWebSessionFetchDoesNotEmbedClientSecret() {
        val source = File("src/main/java/com/aiquota/mobile/providers/GoogleWebSessionCodeAssistFetcher.kt").readText()

        assertFalse(source.contains("client_secret"))
        assertFalse(source.contains("clientSecret"))
        assertFalse(source.contains("notasecret"))
    }

    @Test
    fun backgroundGoogleCollectorsCanReinjectAfterEarlySamePageInjection() {
        val source = File("src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt").readText()
        val inject = source.substringAfter("private fun injectCollectorIfReady")
            .substringBefore("private fun finishWithErrorOnce")

        assertTrue(inject.contains("val firstInjectionForPage = collectorInjectionKeys.add(injectionKey)"))
        assertTrue(inject.contains("routeKeyOf(url)"))
        assertTrue(inject.contains("providerId != ProviderId.GEMINI && providerId != ProviderId.ANTIGRAVITY"))
        assertTrue(inject.contains("\"reinject\""))
    }

    @Test
    fun foregroundGoogleBackgroundCollectorInjectsAtPageStart() {
        val source = File("src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt").readText()
        val onPageStarted = source.substringAfter("override fun onPageStarted")
            .substringBefore("override fun shouldOverrideUrlLoading")

        assertFalse(onPageStarted.contains("job.job.providerId != ProviderId.CODEX"))
        assertTrue(onPageStarted.contains("ProviderWebCollectorScripts.isRefreshLoginPage(job.job.providerId, url)"))
        assertTrue(onPageStarted.contains("injectCollectorIfReady(view, url, \"\")"))
    }

    @Test
    fun googleBackgroundCollectorsDoNotExposePrivateApiPayloadNormalizers() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val foreground = File("src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt").readText()

        listOf(service, foreground).forEach { source ->
            assertFalse(source.contains("normalizeGeminiCodeAssistPayload"))
            assertFalse(source.contains("normalizeAntigravityCodeAssistPayload"))
        }
    }
}
