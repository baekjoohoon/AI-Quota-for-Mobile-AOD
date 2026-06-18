package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AntigravityFirebaseGatewayTest {
    @Test
    fun antigravityFirebaseGatewayExchangesOauthCodeAndRefreshesToken() {
        val gateway = File("src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt").readText()
        val repository = File("src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt").readText()
        val activity = File("src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt").readText()
        val build = File("build.gradle.kts").readText()

        assertTrue(build.contains("1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"))
        assertTrue(gateway.contains("callWithAppCheckRetry(appCheck, \"startAntigravityOAuth\""))
        assertTrue(gateway.contains("callWithAppCheckRetry(appCheck, \"completeAntigravityOAuth\""))
        assertTrue(gateway.contains("callWithAppCheckRetry("))
        assertTrue(gateway.contains("\"refreshAntigravityAccessToken\""))
        assertTrue(activity.contains("WebView"))
        assertTrue(activity.contains("AntigravityFirebaseGateway(applicationContext).startOAuth()"))
        assertTrue(activity.contains("AntigravityFirebaseGateway(applicationContext).completeOAuth(url)"))
        assertTrue(activity.contains("repository.fetchUsagePayloadFromGatewayTokenResult(tokenResult)"))
        assertTrue(repository.contains("fetchUsagePayloadFromGatewayTokenResult"))
        assertTrue(repository.contains("fetchUsagePayloadWithAccessToken(accessToken, email = null)"))
        assertTrue(repository.contains("AntigravityFirebaseGateway(appContext).refreshAccessToken(refreshToken)"))
        assertFalse("${repository}\n${gateway}\n${activity}\n${build}".contains("ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET"))
        assertFalse("${repository}\n${gateway}\n${activity}\n${build}".contains("client_secret"))
    }

    @Test
    fun antigravityRoutesThroughNativeTokenPrivateApiInsteadOfBackendGatewayConnector() {
        val definitions = File("src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt").readText()
        val refreshPlan = File("src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt").readText()
        val registry = File("src/main/java/com/aiquota/mobile/providers/ProviderConnectorRegistry.kt").readText()
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val backgroundService =
            File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        val antigravityDefinition = definitions.substringAfter("providerId = ProviderId.ANTIGRAVITY,")
            .substringBefore("ProviderDefinition(")

        assertTrue(antigravityDefinition.contains("authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN"))
        assertTrue(antigravityDefinition.contains("collectionKind = ProviderCollectionKind.NATIVE_API"))
        assertTrue(refreshPlan.contains("ProviderCollectionKind.NATIVE_API -> ProviderRefreshMode.NATIVE_API"))
        assertFalse(registry.contains("AntigravityFirebaseConnector("))
        assertTrue(appShell.contains("AntigravityLoopbackOAuthActivity.createIntent"))
        assertFalse(appShell.contains("AntigravityOAuthRepository(appContext).fetchUsagePayloadFromStoredCredential()"))
        assertTrue(backgroundService.contains("AntigravityOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()"))
        assertFalse(appShell.contains("beginAuthorizationUrl()"))
        assertFalse(appShell.contains("Antigravity gateway collection failed with"))
        assertFalse(backgroundService.contains("Antigravity gateway collection failed with"))
    }

    @Test
    fun androidGatewayBootstrapsFirebaseAuthAndAppCheck() {
        val gateway = File("src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt").readText()
        val geminiGateway = File("src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt").readText()
        val appCheckCallable = File("src/main/java/com/aiquota/mobile/providers/FirebaseAppCheckCallable.kt")
            .readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val application = File("src/main/java/com/aiquota/mobile/AIQuotaApplication.kt").readText()
        val debugBootstrap = File("src/debug/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt").readText()
        val releaseBootstrap = File("src/release/java/com/aiquota/mobile/FirebaseGatewayBootstrap.kt").readText()

        assertTrue(gateway.contains("FirebaseAuth"))
        assertTrue(gateway.contains("FirebaseAppCheck"))
        assertTrue(geminiGateway.contains("FirebaseAppCheck"))
        assertTrue(gateway.contains("ensureSignedIn()"))
        assertTrue(gateway.contains("signInAnonymously().await()"))
        assertTrue(gateway.contains("callWithAppCheckRetry(appCheck"))
        assertTrue(geminiGateway.contains("callWithAppCheckRetry(appCheck"))
        assertTrue(appCheckCallable.contains("getAppCheckToken(false).await()"))
        assertTrue(appCheckCallable.contains("getAppCheckToken(true).await()"))
        assertTrue(appCheckCallable.contains("FirebaseFunctionsException.Code.UNAUTHENTICATED"))
        assertTrue(manifest.contains("""android:name=".AIQuotaApplication""""))
        assertTrue(application.contains("class AIQuotaApplication"))
        assertTrue(application.contains("FirebaseGatewayBootstrap.install()"))
        assertTrue(debugBootstrap.contains("DebugAppCheckProviderFactory"))
        assertTrue(releaseBootstrap.contains("PlayIntegrityAppCheckProviderFactory"))
    }
}
