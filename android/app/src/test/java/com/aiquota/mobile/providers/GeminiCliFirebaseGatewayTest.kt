package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiCliFirebaseGatewayTest {
    @Test
    fun androidGoogleOauthDoesNotContainOauthClientSecret() {
        val files = listOf(
            File("src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt"),
            File("src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt"),
            File("src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt"),
            File("src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt"),
            File("src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt"),
            File("src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt"),
            File("build.gradle.kts")
        ).joinToString("\n") { it.readText() }

        assertFalse(files.contains("GEMINI_CLI_GOOGLE_OAUTH_CLIENT_SECRET"))
        assertFalse(files.contains("ANTIGRAVITY_GOOGLE_OAUTH_CLIENT_SECRET"))
        assertFalse(files.contains("client_secret"))
        assertFalse(files.contains("clientSecret"))
    }

    @Test
    fun googleProvidersUseConfiguredClientIdsAndFirebaseWebViewExchange() {
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val geminiActivity = File("src/main/java/com/aiquota/mobile/providers/GeminiCliLoopbackOAuthActivity.kt").readText()
        val antigravityActivity =
            File("src/main/java/com/aiquota/mobile/providers/AntigravityLoopbackOAuthActivity.kt").readText()
        val geminiGateway = File("src/main/java/com/aiquota/mobile/providers/GeminiCliFirebaseGateway.kt").readText()
        val antigravityGateway = File("src/main/java/com/aiquota/mobile/providers/AntigravityFirebaseGateway.kt").readText()
        val build = File("build.gradle.kts").readText()

        assertTrue(build.contains("681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com"))
        assertTrue(build.contains("1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"))
        assertTrue(appShell.contains("GeminiCliLoopbackOAuthActivity.createIntent"))
        assertTrue(appShell.contains("AntigravityLoopbackOAuthActivity.createIntent"))
        assertTrue(geminiActivity.contains("WebView"))
        assertTrue(geminiActivity.contains("GeminiCliFirebaseGateway(applicationContext).startOAuth()"))
        assertTrue(geminiActivity.contains("GeminiCliFirebaseGateway(applicationContext).completeOAuth(url)"))
        assertTrue(geminiActivity.contains("repository.fetchUsagePayloadFromGatewayTokenResult(tokenResult)"))
        assertTrue(geminiActivity.contains("GeminiCliOAuthRepository.isLoopbackOAuthCallback(url)"))
        assertFalse(geminiActivity.contains("ServerSocket"))
        assertFalse(geminiActivity.contains("Intent.ACTION_VIEW"))
        assertFalse(geminiActivity.contains("fetchUsagePayloadFromLoopbackCallback"))
        assertTrue(antigravityActivity.contains("WebView"))
        assertTrue(antigravityActivity.contains("AntigravityFirebaseGateway(applicationContext).startOAuth()"))
        assertTrue(antigravityActivity.contains("AntigravityFirebaseGateway(applicationContext).completeOAuth(url)"))
        assertTrue(antigravityActivity.contains("repository.fetchUsagePayloadFromGatewayTokenResult(tokenResult)"))
        assertTrue(antigravityActivity.contains("AntigravityOAuthRepository.isLoopbackOAuthCallback(url)"))
        assertFalse(antigravityActivity.contains("ServerSocket"))
        assertFalse(antigravityActivity.contains("Intent.ACTION_VIEW"))
        assertFalse(antigravityActivity.contains("fetchUsagePayloadFromLoopbackCallback"))
        assertTrue(geminiGateway.contains("getHttpsCallable(\"startGeminiCliOAuth\")"))
        assertTrue(geminiGateway.contains("getHttpsCallable(\"completeGeminiCliOAuth\")"))
        assertTrue(geminiGateway.contains("getHttpsCallable(\"refreshGeminiCliAccessToken\")"))
        assertTrue(antigravityGateway.contains("getHttpsCallable(\"startAntigravityOAuth\")"))
        assertTrue(antigravityGateway.contains("getHttpsCallable(\"completeAntigravityOAuth\")"))
        assertTrue(antigravityGateway.contains("getHttpsCallable(\"refreshAntigravityAccessToken\")"))
    }

    @Test
    fun googleProvidersRefreshNativeApiUsingStoredTokensAndFirebaseRefresh() {
        val definitions = File("src/main/java/com/aiquota/mobile/providers/ProviderDefinitions.kt").readText()
        val refreshPlan = File("src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt").readText()
        val geminiRepository = File("src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt").readText()
        val antigravityRepository =
            File("src/main/java/com/aiquota/mobile/providers/AntigravityOAuthRepository.kt").readText()
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val backgroundService =
            File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        val geminiDefinition = definitions.substringAfter("providerId = ProviderId.GEMINI,")
            .substringBefore("ProviderDefinition(")
        val antigravityDefinition = definitions.substringAfter("providerId = ProviderId.ANTIGRAVITY,")
            .substringBefore("ProviderDefinition(")

        assertTrue(geminiDefinition.contains("authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN"))
        assertTrue(geminiDefinition.contains("collectionKind = ProviderCollectionKind.NATIVE_API"))
        assertTrue(antigravityDefinition.contains("authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN"))
        assertTrue(antigravityDefinition.contains("collectionKind = ProviderCollectionKind.NATIVE_API"))
        assertTrue(refreshPlan.contains("ProviderCollectionKind.NATIVE_API -> ProviderRefreshMode.NATIVE_API"))
        assertTrue(geminiRepository.contains("GeminiCliFirebaseGateway(appContext).refreshAccessToken(refreshToken)"))
        assertTrue(antigravityRepository.contains("AntigravityFirebaseGateway(appContext).refreshAccessToken(refreshToken)"))
        assertFalse(appShell.contains("GeminiCliOAuthRepository(appContext).fetchUsagePayloadFromStoredCredential()"))
        assertFalse(appShell.contains("AntigravityOAuthRepository(appContext).fetchUsagePayloadFromStoredCredential()"))
        assertTrue(backgroundService.contains("GeminiCliOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()"))
        assertTrue(backgroundService.contains("AntigravityOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()"))
    }
}
