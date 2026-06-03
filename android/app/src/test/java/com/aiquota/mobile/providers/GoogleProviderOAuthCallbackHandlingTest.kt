package com.aiquota.mobile.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleProviderOAuthCallbackHandlingTest {
    @Test
    fun geminiAndAntigravityDoNotUseWebViewLoopbackOauthCallbacks() {
        val login = java.io.File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val strategy = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderLoginStrategy.kt").readText()

        assertFalse(login.contains("completeGoogleProviderAuthorization"))
        assertFalse(login.contains("recoverGoogleProviderFromOAuthCallback"))
        assertFalse(login.contains("GeminiCliOAuthRepository(applicationContext)"))
        assertFalse(login.contains("AntigravityOAuthRepository(applicationContext)"))
        assertFalse(login.contains("googleTokenExchange"))
        assertFalse(login.contains("google_token_exchange_failed"))
        assertFalse(strategy.contains("isGeminiOAuthCallback"))
        assertFalse(strategy.contains("isAntigravityOAuthCallback"))
        assertFalse(strategy.contains("GEMINI_CALLBACK_RECOVERY_URL"))
        assertFalse(strategy.contains("ANTIGRAVITY_CALLBACK_RECOVERY_URL"))
    }

    @Test
    fun googleProviderFlowUsesWebViewCollectorWithoutClientSecretOrLoopback() {
        val appShell = java.io.File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val build = java.io.File("build.gradle.kts").readText()
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        val appAuth = java.io.File("src/main/java/com/aiquota/mobile/providers/GoogleAppAuthUsageAuthorizer.kt").readText()
        val activity = java.io.File("src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt").readText()

        assertFalse(appShell.contains("GoogleAuthorizationActivity.createIntent(launchContext, providerId)"))
        assertTrue(appShell.contains("WebLoginActivity.createIntent(launchContext, providerId, loginStartUrl)"))
        assertTrue(build.contains("net.openid:appauth"))
        assertTrue(build.contains("GOOGLE_ANDROID_OAUTH_CLIENT_ID"))
        assertTrue(build.contains("GOOGLE_ANDROID_OAUTH_REDIRECT_SCHEME"))
        assertTrue(build.contains("com.googleusercontent.apps."))
        assertTrue(build.contains("manifestPlaceholders[\"appAuthRedirectScheme\"] = googleAndroidOAuthRedirectScheme"))
        assertTrue(appAuth.contains("AuthorizationRequest.Builder"))
        assertTrue(appAuth.contains("ResponseTypeValues.CODE"))
        assertTrue(appAuth.contains("BuildConfig.GOOGLE_ANDROID_OAUTH_REDIRECT_SCHEME"))
        assertTrue(appAuth.contains(":/oauth2redirect"))
        assertFalse(appAuth.contains("com.aiquota:/oauth2redirect"))
        assertTrue(manifest.contains("net.openid.appauth.RedirectUriReceiverActivity"))
        assertTrue(manifest.contains("Theme.AppCompat.Translucent.NoTitleBar"))
        assertTrue(activity.contains("AuthorizationService(this)"))
        assertTrue(activity.contains("AuthorizationResponse.fromIntent(data)"))
        assertFalse(build.contains("GEMINI_OAUTH_SECRET"))
        assertFalse(build.contains("ANTIGRAVITY_OAUTH_SECRET"))
        assertFalse(build.contains("aiquota.geminiOAuthSecret"))
        assertFalse(build.contains("aiquota.antigravityOAuthSecret"))
        assertFalse(appShell.contains("http://127.0.0.1"))
        assertFalse(appAuth.contains("client_secret"))
        assertFalse(activity.contains("client_secret"))
    }

    @Test
    fun googleUsageBridgePrefersAppAuthAuthorizationOverLegacyOauthAndWebSession() {
        val bridge = java.io.File("src/main/java/com/aiquota/mobile/providers/GoogleOAuthUsageBridge.kt").readText()
        val bridgePayloadMethod = bridge.substringAfter("fun bridgeUsagePayload")

        assertTrue(bridgePayloadMethod.contains("GoogleAppAuthUsageAuthorizer.fetchUsagePayload"))
        assertTrue(bridgePayloadMethod.contains("GoogleIdentityUsageAuthorizer.fetchUsagePayload"))
        assertFalse(bridgePayloadMethod.contains("GeminiCliOAuthRepository(context.applicationContext).let"))
        assertFalse(bridgePayloadMethod.contains("AntigravityOAuthRepository(context.applicationContext).let"))
        assertFalse(bridgePayloadMethod.contains("GoogleWebSessionCodeAssistFetcher.bridgeUsagePayload"))
        assertFalse(bridge.substringBefore("fun bridgeUsagePayload").contains("GeminiCliOAuthRepository"))
        assertFalse(bridge.substringBefore("fun bridgeUsagePayload").contains("AntigravityOAuthRepository"))
        assertTrue(bridge.contains("requiresAuth"))
    }

    @Test
    fun googleProviderNoTrustedPayloadStaysPendingButAuthorizationRequiredDisconnects() {
        val collectionService = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderUsageCollectionService.kt").readText()
        val backgroundService = java.io.File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(collectionService.contains("requiresAuth"))
        assertTrue(collectionService.contains("markSessionExpired"))
        assertTrue(collectionService.contains("markGoogleUsagePending"))
        assertTrue(backgroundService.contains("ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD"))
    }
}
