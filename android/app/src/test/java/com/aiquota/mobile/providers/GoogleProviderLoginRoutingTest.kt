package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleProviderLoginRoutingTest {
    @Test
    fun googleProviderLoginRoutingKeepsSecretsOutOfAppShell() {
        val source = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val connectProvider = source
            .substringAfter("fun connectProvider(providerId: ProviderId)")
            .substringBefore("fun finishProviderRefreshSurfaces()")

        assertFalse(source.contains("import com.aiquota.mobile.providers.CodexOAuthRepository"))
        assertFalse(connectProvider.contains("CodexOAuthRepository"))
        assertFalse(connectProvider.contains("GoogleAuthorizationActivity.createIntent"))
        assertTrue(connectProvider.contains("WebLoginActivity.createIntent(launchContext, providerId, loginStartUrl)"))
        assertTrue(connectProvider.contains("GeminiCliLoopbackOAuthActivity.createIntent(launchContext)"))
        assertTrue(connectProvider.contains("AntigravityLoopbackOAuthActivity.createIntent(launchContext)"))
        assertTrue(connectProvider.contains("providerId == ProviderId.GEMINI || providerId == ProviderId.ANTIGRAVITY"))
        assertFalse(connectProvider.contains("GeminiCliOAuthRepository.isLoopbackOAuthConfigured()"))
        assertFalse(connectProvider.contains("GeminiCliOAuthRepository(appContext).beginAuthorizationUrl()"))
        assertFalse(connectProvider.contains("AntigravityOAuthRepository(appContext).beginAuthorizationUrl()"))
        assertFalse(connectProvider.contains("http://127.0.0.1"))
        assertFalse(connectProvider.contains("requestOfflineAccess"))
        assertFalse(connectProvider.contains("client_secret"))
    }

    @Test
    fun glmApiKeyLoginBypassesWebHostAllowlist() {
        val source = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val connectProvider = source
            .substringAfter("fun connectProvider(providerId: ProviderId)")
            .substringBefore("fun finishProviderRefreshSurfaces()")

        assertTrue(connectProvider.contains("providerId != ProviderId.GLM"))
        assertTrue(connectProvider.contains("GlmApiKeyActivity.createIntent(launchContext)"))
        assertTrue(connectProvider.indexOf("providerId != ProviderId.GLM") < connectProvider.indexOf("ProviderHostAllowlist.isAllowed"))
    }

    @Test
    fun codexLoginAndCollectionDoNotUseOauthRepositoryOrCapturedBearerToken() {
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val login = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val backgroundService = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val backgroundCollector = File("src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt").readText()
        val resetter = File("src/main/java/com/aiquota/mobile/providers/ProviderSessionResetter.kt").readText()

        listOf(appShell, login, backgroundService, backgroundCollector, resetter).forEach { source ->
            assertFalse(source.contains("CodexOAuthRepository"))
            assertFalse(source.contains("observedCodexAccessToken"))
            assertFalse(source.contains("captureCodexAccessToken"))
        }
    }

    @Test
    fun codexInteractiveLoginWaitsForUsagePayloadInsteadOfCollectorTimeout() {
        val login = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val collectorInjection = login.substringAfter("private fun injectCollectorIfReady")
            .substringBefore("private fun captureCodexAccountId")
        val collectorError = login.substringAfter("fun postCollectorError")
            .substringBefore("@JavascriptInterface\n        fun fetchCursorJson")

        assertTrue(collectorInjection.contains("awaitInteractiveLoginUsage = providerId == ProviderId.CODEX"))
        assertTrue(collectorError.contains("shouldKeepLoginOpenUntilUsagePayload(errorKind)"))
        assertTrue(collectorError.indexOf("shouldKeepLoginOpenUntilUsagePayload(errorKind)") < collectorError.indexOf("finishConnectedWithoutUsage"))
    }

    @Test
    fun geminiLoginWaitsForUsagePayloadInsteadOfReturningOnCollectorMiss() {
        val login = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val keepOpen = login.substringAfter("private fun shouldKeepLoginOpenUntilUsagePayload")
            .substringBefore("private fun captureCodexAccountId")

        assertTrue(keepOpen.contains("ProviderId.GEMINI"))
        assertTrue(keepOpen.contains("gemini_no_trusted_payload"))
        assertTrue(keepOpen.contains("gemini_collector_error"))
    }

    @Test
    fun opencodeLoginRedirectsWorkspaceShellToGoUsagePage() {
        val login = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val pageStarted = login.substringAfter("override fun onPageStarted")
            .substringBefore("override fun shouldOverrideUrlLoading")
        val redirect = login.substringAfter("private fun maybeRedirectOpenCodeToGo")
            .substringBefore("private fun maybeRedirectCopilotToSettings")

        assertTrue(pageStarted.contains("maybeRedirectOpenCodeToGo(view, url)"))
        assertTrue(redirect.contains("OpenCodeUsagePageRoutes.goUsageUrlFrom(url)"))
        assertTrue(redirect.contains("view.loadUrl(goUsageUrl)"))
    }

    @Test
    fun googleInteractiveLoginSessionSignalUsesProviderSpecificReturnPolicy() {
        val login = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val intercept = login.substringAfter("override fun shouldInterceptRequest")
            .substringBefore("override fun onLoadResource")

        assertTrue(intercept.contains("ProviderLoginStrategy.isInteractiveLoginSessionReached(providerId, url)"))
        assertTrue(intercept.contains("ProviderLoginStrategy.shouldFinishOnInteractiveLoginSessionReached(providerId)"))
        assertTrue(intercept.contains("finishGoogleUsagePending"))
        assertTrue(intercept.indexOf("isInteractiveLoginSessionReached") < intercept.indexOf("return if (ProviderLoginWebViewPolicy.shouldInterceptRequest"))
    }
}
