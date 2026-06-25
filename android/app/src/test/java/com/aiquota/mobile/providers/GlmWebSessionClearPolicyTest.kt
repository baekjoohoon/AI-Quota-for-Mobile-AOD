package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlmWebSessionClearPolicyTest {
    @Test
    fun glmLoginPreservesExistingWebSessionBeforeStarting() {
        assertFalse(
            ProviderWebSessionClearPolicy.shouldClearBeforeLogin(
                ProviderId.GLM,
                null
            )
        )
    }

    @Test
    fun glmDisconnectClearsZaiAuthSubdomains() {
        val cookieUrls = ProviderWebSessionClearPolicy.cookieUrls(ProviderId.GLM)

        assertTrue(cookieUrls.contains("https://z.ai"))
        assertTrue(cookieUrls.contains("https://www.z.ai"))
        assertTrue(cookieUrls.contains("https://chat.z.ai"))
        assertTrue(cookieUrls.contains("https://api.z.ai"))
        assertTrue(cookieUrls.contains("https://auth.z.ai"))
        assertTrue(cookieUrls.contains("https://login.z.ai"))
        assertTrue(cookieUrls.contains("https://accounts.z.ai"))
        assertTrue(cookieUrls.contains("https://account.z.ai"))
    }

    @Test
    fun glmDisconnectDoesNotClearGoogleSsoUrls() {
        val cookieUrls = ProviderWebSessionClearPolicy.cookieUrls(ProviderId.GLM)
        val storageOrigins = ProviderWebSessionClearPolicy.storageOrigins(ProviderId.GLM)
        val browserStorageUrls = ProviderWebSessionClearPolicy.browserStorageCleanupUrls(ProviderId.GLM)

        assertFalse(cookieUrls.any { it.contains("google.com") || it.contains("googleapis.com") })
        assertFalse(storageOrigins.any { it.contains("google.com") || it.contains("googleapis.com") })
        assertFalse(browserStorageUrls.any { it.contains("google.com") || it.contains("googleapis.com") })
    }

    @Test
    fun glmDisconnectClearsZaiManageApiKeyPathCookies() {
        val cookieUrls = ProviderWebSessionClearPolicy.cookieUrls(ProviderId.GLM)

        assertTrue(cookieUrls.contains(GlmProviderUrls.WEB_OAUTH_URL))
        assertTrue(cookieUrls.contains(GlmProviderUrls.WEB_USAGE_URL))
        assertTrue(cookieUrls.contains("https://z.ai/manage-apikey"))
        assertTrue(cookieUrls.contains("https://z.ai/manage-apikey/coding-plan"))
        assertTrue(cookieUrls.contains("https://z.ai/manage-apikey/coding-plan/personal"))
        assertTrue(cookieUrls.contains(GlmProviderUrls.API_QUOTA_URL))
        assertTrue(cookieUrls.contains("https://api.z.ai/api/biz/subscription/list"))
    }

    @Test
    fun glmDisconnectClearsZaiAuthStorageOrigins() {
        val origins = ProviderWebSessionClearPolicy.storageOrigins(ProviderId.GLM)

        assertTrue(origins.contains("https://auth.z.ai"))
        assertTrue(origins.contains("https://login.z.ai"))
        assertTrue(origins.contains("https://accounts.z.ai"))
        assertTrue(origins.contains("https://account.z.ai"))
    }

    @Test
    fun glmBrowserStorageCleanupIsScopedToZaiOrigins() {
        val origins = ProviderWebSessionClearPolicy.browserStorageCleanupUrls(ProviderId.GLM)

        assertTrue(origins.contains("https://z.ai"))
        assertTrue(origins.contains("https://www.z.ai"))
        assertTrue(origins.contains("https://chat.z.ai"))
        assertTrue(origins.contains("https://auth.z.ai"))
        assertTrue(origins.contains("https://login.z.ai"))
        assertTrue(origins.contains("https://accounts.z.ai"))
        assertTrue(origins.contains("https://account.z.ai"))
        assertFalse(origins.any { !it.endsWith(".z.ai") && it != "https://z.ai" })
        assertTrue(ProviderWebSessionClearPolicy.browserStorageCleanupUrls(ProviderId.GEMINI).isEmpty())
    }

    @Test
    fun glmBrowserStorageCleanupScriptClearsDurableBrowserStores() {
        val script = ProviderWebSessionCleaner.browserStorageCleanupScript()

        assertTrue(script.contains("localStorage.clear()"))
        assertTrue(script.contains("sessionStorage.clear()"))
        assertTrue(script.contains("indexedDB.deleteDatabase"))
        assertTrue(script.contains("caches.delete"))
        assertTrue(script.contains("navigator.serviceWorker.getRegistrations"))
    }

    @Test
    fun glmWebLoginRunsInIsolatedWebViewProcess() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val loginActivity = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val application = File("src/main/java/com/aiquota/mobile/AIQuotaApplication.kt").readText()

        assertTrue(manifest.contains("android:name=\".providers.GlmWebLoginActivity\""))
        assertTrue(manifest.contains("android:process=\":glm_webview\""))
        assertTrue(loginActivity.contains("ProviderId.GLM -> GlmWebLoginActivity::class.java"))
        assertTrue(application.contains("GlmIsolatedWebViewProfile.configureIfNeeded(this)"))
    }

    @Test
    fun glmIsolatedWebViewProcessSkipsMainProcessApplicationInitialization() {
        val application = File("src/main/java/com/aiquota/mobile/AIQuotaApplication.kt").readText()
        val profile = File("src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSession.kt").readText()

        assertTrue(profile.contains("fun configureIfNeeded(context: Context): Boolean"))
        assertTrue(application.contains("GlmIsolatedWebViewProfile.configureIfNeeded(this)"))
        assertTrue(application.contains("if (glmIsolatedProcess) return"))
        assertTrue(
            application.indexOf("if (glmIsolatedProcess) return") <
                application.indexOf("FirebaseGatewayBootstrap.install()")
        )
    }

    @Test
    fun glmWebOAuthBackgroundRefreshUsesIsolatedCollector() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val collector = File("src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt").readText()

        assertTrue(service.contains("if (job.providerId == ProviderId.GLM)"))
        assertTrue(service.contains("GlmIsolatedWebSession.collectUsage("))
        assertTrue(collector.contains("class GlmIsolatedWebSessionService : Service()"))
        assertTrue(collector.contains("ProviderWebCollectorScripts.build("))
    }

    @Test
    fun glmDisconnectClearsOnlyIsolatedWebViewProfile() {
        val cleaner = File("src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt").readText()
        val resetter = File("src/main/java/com/aiquota/mobile/providers/ProviderSessionResetter.kt").readText()
        val isolated = File("src/main/java/com/aiquota/mobile/providers/GlmIsolatedWebSessionService.kt").readText()

        assertTrue(cleaner.contains("if (providerId == ProviderId.GLM)"))
        assertTrue(cleaner.contains("GlmIsolatedWebSession.clearAndWait(context.applicationContext)"))
        assertTrue(resetter.contains("ProviderWebSessionCleaner.clearProviderWebSession(appContext, providerId)"))
        assertTrue(isolated.contains("cookieManager.removeAllCookies"))
        assertTrue(isolated.contains("WebStorage.getInstance().deleteAllData()"))
    }

    @Test
    fun disconnectAllUsesProviderScopedCleanupInsteadOfGlobalCookieWipe() {
        val resetter = File("src/main/java/com/aiquota/mobile/providers/ProviderSessionResetter.kt").readText()
        val cleaner = File("src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt").readText()
        val disconnectAll = resetter.substringAfter("suspend fun disconnectAllAndWait")
            .substringBefore("private fun notifyProviderSessionReset")

        assertTrue(disconnectAll.contains("disconnectAndWait(providerId)"))
        assertFalse(disconnectAll.contains("clearAllProviderWebSessionsAndWait"))
        assertFalse(cleaner.contains("removeAllCookies"))
        assertFalse(cleaner.contains("deleteAllData"))
    }

    @Test
    fun webSessionCleanupAndHiddenRefreshUseSameMaintenanceGate() {
        val cleaner = File("src/main/java/com/aiquota/mobile/providers/ProviderWebSessionCleaner.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val contextCleanup = cleaner.substringAfter("suspend fun clearProviderWebSessionAndWait(context: Context")
            .substringBefore("fun clearProviderWebSession(")
        val hiddenCollectorBranch = service.substringAfter("ProviderRefreshMode.HIDDEN_WEB_COLLECTOR ->")
            .substringBefore("}")

        assertTrue(cleaner.contains("internal object ProviderWebSessionMaintenanceGate"))
        assertTrue(contextCleanup.contains("ProviderWebSessionMaintenanceGate.withMaintenanceLock"))
        assertTrue(hiddenCollectorBranch.contains("ProviderWebSessionMaintenanceGate.withMaintenanceLock"))
    }
}
