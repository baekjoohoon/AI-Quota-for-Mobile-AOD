package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleIdentityUsageAuthorizerTest {
    @Test
    fun googleIdentityAuthorizerRequestsCloudPlatformScopesWithoutClientSecret() {
        val appAuth = File("src/main/java/com/aiquota/mobile/providers/GoogleAppAuthUsageAuthorizer.kt").readText()
        val activity = File("src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt").readText()
        val build = File("build.gradle.kts").readText()

        assertTrue(build.contains("com.google.android.gms:play-services-auth"))
        assertTrue(build.contains("net.openid:appauth"))
        assertTrue(build.contains("GOOGLE_ANDROID_OAUTH_CLIENT_ID"))
        assertTrue(build.contains("GOOGLE_ANDROID_OAUTH_REDIRECT_SCHEME"))
        assertTrue(build.contains("com.googleusercontent.apps."))
        assertTrue(build.contains("manifestPlaceholders[\"appAuthRedirectScheme\"] = googleAndroidOAuthRedirectScheme"))
        assertTrue(appAuth.contains("AuthorizationRequest.Builder"))
        assertTrue(appAuth.contains("ResponseTypeValues.CODE"))
        assertTrue(appAuth.contains("\"access_type\" to \"offline\""))
        assertTrue(appAuth.contains("setPrompt(\"consent\")"))
        assertTrue(appAuth.contains("BuildConfig.GOOGLE_ANDROID_OAUTH_REDIRECT_SCHEME"))
        assertTrue(appAuth.contains(":/oauth2redirect"))
        assertFalse(appAuth.contains("com.aiquota:/oauth2redirect"))
        assertTrue(appAuth.contains("https://www.googleapis.com/auth/cloud-platform"))
        assertTrue(appAuth.contains("https://www.googleapis.com/auth/userinfo.email"))
        assertTrue(appAuth.contains("https://www.googleapis.com/auth/userinfo.profile"))
        assertTrue(activity.contains("setContentView(FrameLayout(this))"))
        assertTrue(activity.contains("AuthorizationService(this)"))
        assertTrue(activity.contains("AuthorizationResponse.fromIntent(data)"))
        assertTrue(activity.contains("createTokenExchangeRequest()"))
        assertTrue(activity.contains("ProviderUsageCollectionService.start"))
        assertFalse(appAuth.contains("client_secret"))
        assertFalse(activity.contains("client_secret"))
        assertFalse(appAuth.contains("notasecret"))
        assertFalse(activity.contains("notasecret"))
    }

    @Test
    fun googleIdentityActivityKeepsQuotaFailuresRetryPending() {
        val activity = File("src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt").readText()
        val handleResult = activity.substringAfter("private fun exchangeAppAuthCodeAndStore")
            .substringBefore("private fun startGoogleUsageCollection")
        val collection = activity.substringAfter("private fun startGoogleUsageCollection")
            .substringBefore("private fun finishAuthorizationFailure")
        val noPayload = collection.substringAfter("if (!usageResult.payload.isNullOrBlank())")

        assertTrue(handleResult.contains("markGoogleUsagePending"))
        assertTrue(handleResult.contains("GoogleUsagePendingRetryPolicy.PENDING_MESSAGE"))
        assertTrue(noPayload.contains("ProviderUsageCollectionService.start"))
        assertFalse(noPayload.contains("failKeepingPrevious"))
    }

    @Test
    fun googleIdentityAuthorizationFailureDisconnectsForInteractiveSignInStatuses() {
        val activity = File("src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt").readText()
        val failure = activity.substringAfter("private fun finishAuthorizationFailure")
            .substringBefore("private fun startTokenBrokerFallback")

        assertTrue(failure.contains("diagnostic.requiresInteractiveAuth()"))
        assertTrue(failure.contains("ProviderSessionResetter(applicationContext).disconnect(providerId)"))
        assertTrue(failure.contains("markSessionExpired"))
        assertTrue(activity.contains("identity_status_8"))
        assertTrue(activity.contains("identity_status_16"))
        assertTrue(activity.contains("token_broker_recoverable_auth"))
        assertTrue(activity.contains("Google authorization requires sign-in."))
    }

    @Test
    fun googleIdentityStatusEightUsesTokenBrokerFallbackBeforeErrorState() {
        val source = File("src/main/java/com/aiquota/mobile/providers/GoogleIdentityUsageAuthorizer.kt").readText()
        val activity = File("src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt").readText()
        val bridge = File("src/main/java/com/aiquota/mobile/providers/GoogleOAuthUsageBridge.kt").readText()

        assertTrue(activity.contains("diagnostic.canUseTokenBrokerFallback() && startTokenBrokerFallback(diagnostic)"))
        assertTrue(activity.contains("AccountPicker.newChooseAccountIntent"))
        assertTrue(activity.contains("GoogleIdentityUsageAuthorizer.requestGoogleAuthToken"))
        assertTrue(source.contains("usagePayloadFromStoredGoogleAccount"))
        assertTrue(source.contains("originalDiagnostic.canUseTokenBrokerFallback()"))
        assertTrue(source.contains("GoogleAuthUtil.getToken"))
        assertTrue(source.contains("UserRecoverableAuthException"))
        assertTrue(bridge.contains("identity_status_8"))
    }

    @Test
    fun googleIdentityActivityFinishesBeforeNetworkUsageCollectionToAvoidAnr() {
        val activity = File("src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt").readText()
        val handleResult = activity.substringAfter("private fun exchangeAppAuthCodeAndStore")
            .substringBefore("private fun startGoogleUsageCollection")
        val collection = activity.substringAfter("private fun startGoogleUsageCollection")
            .substringBefore("private fun finishAuthorizationFailure")

        assertTrue(handleResult.indexOf("markGoogleUsagePending") < handleResult.indexOf("startGoogleUsageCollection(tokenResponse.accessToken.orEmpty())"))
        assertTrue(handleResult.indexOf("startGoogleUsageCollection(tokenResponse.accessToken.orEmpty())") < handleResult.indexOf("finish()"))
        assertFalse(handleResult.contains("usagePayloadFromAuthorizationResult"))
        assertTrue(collection.contains("GoogleIdentityUsageAuthorizer.usagePayloadFromAccessToken"))
        assertFalse(collection.contains("runOnUiThread"))
    }

    @Test
    fun googleIdentityActivityParsesAuthorizationIntentWithoutResultOkGate() {
        val activity = File("src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt").readText()
        val onActivityResult = activity.substringAfter("private fun handleAuthorizationActivityResult")
            .substringBefore("private fun handleAccountPickerResult")

        assertTrue(onActivityResult.contains("AuthorizationResponse.fromIntent(data)"))
        assertTrue(onActivityResult.indexOf("data == null") < onActivityResult.indexOf("AuthorizationResponse.fromIntent(data)"))
        assertFalse(onActivityResult.contains("resultCode != RESULT_OK"))
        assertTrue(onActivityResult.contains("authorization_activity_result"))
    }

    @Test
    fun storedGoogleIdentityCredentialIsTheOnlyGoogleProviderCredentialSignal() {
        val store = File("src/main/java/com/aiquota/mobile/providers/GoogleIdentityCredentialStore.kt").readText()
        val appAuthStore = File("src/main/java/com/aiquota/mobile/providers/GoogleAppAuthTokenStore.kt").readText()
        val bridge = File("src/main/java/com/aiquota/mobile/providers/GoogleOAuthUsageBridge.kt").readText()

        assertTrue(store.contains("fun hasStoredCredential(providerId: ProviderId): Boolean"))
        assertTrue(store.contains("accountName(providerId) != null || email(providerId) != null"))
        assertTrue(appAuthStore.contains("fun hasStoredCredential(providerId: ProviderId): Boolean"))
        assertTrue(appAuthStore.contains("KEY_REFRESH_TOKEN"))
        assertTrue(bridge.contains("GoogleAppAuthTokenStore(appContext).hasStoredCredential(providerId)"))
        assertTrue(bridge.contains("GoogleIdentityCredentialStore(appContext).hasStoredCredential(providerId)"))
        assertFalse(bridge.substringBefore("fun bridgeUsagePayload").contains("GeminiCliOAuthRepository"))
        assertFalse(bridge.substringBefore("fun bridgeUsagePayload").contains("AntigravityOAuthRepository"))
        assertFalse(bridge.substringBefore("fun bridgeUsagePayload").contains("GoogleWebSessionCodeAssistFetcher"))
        assertTrue(bridge.contains("requiresAuth"))
    }

    @Test
    fun googleIdentityFlowEmitsOauthDiagnostics() {
        val source = File("src/main/java/com/aiquota/mobile/providers/GoogleIdentityUsageAuthorizer.kt").readText()
        val appAuth = File("src/main/java/com/aiquota/mobile/providers/GoogleAppAuthUsageAuthorizer.kt").readText()
        val activity = File("src/main/java/com/aiquota/mobile/providers/GoogleAuthorizationActivity.kt").readText()

        assertTrue(activity.contains("start_authorization"))
        assertTrue(activity.contains("auth_response_received="))
        assertTrue(activity.contains("token_exchange_start"))
        assertTrue(appAuth.contains("access_token="))
        assertTrue(appAuth.contains("refresh_token="))
        assertTrue(source.contains("refresh_token=false serverless=true"))
        assertTrue(appAuth.contains("token_stored"))
        assertTrue(source.contains("first_usage_fetch_start"))
        assertTrue(source.contains("first_usage_fetch_result="))
    }
}
