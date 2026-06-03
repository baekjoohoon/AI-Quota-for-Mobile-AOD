package com.aiquota.mobile.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.aiquota.mobile.BuildConfig
import com.aiquota.mobile.local.ProviderId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse

object GoogleAppAuthUsageAuthorizer {
    val serviceConfiguration = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    fun authorizationRequest(): AuthorizationRequest {
        require(isConfigured()) { "google_android_oauth_client_id_missing" }
        return AuthorizationRequest.Builder(
            serviceConfiguration,
            BuildConfig.GOOGLE_ANDROID_OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            REDIRECT_URI
        )
            .setScope(SCOPE)
            .setPrompt("consent")
            .setAdditionalParameters(
                mapOf(
                    "access_type" to "offline"
                )
            )
            .build()
    }

    fun isConfigured(): Boolean {
        return BuildConfig.GOOGLE_ANDROID_OAUTH_CLIENT_ID.endsWith(".apps.googleusercontent.com")
    }

    fun fetchUsagePayload(context: Context, providerId: ProviderId): GoogleIdentityUsageAuthorizer.UsageResult {
        if (!providerId.isGoogleProvider()) {
            return GoogleIdentityUsageAuthorizer.UsageResult(null, "appauth_unsupported_provider")
        }
        Log.i("GoogleOAuth", "provider=${providerId.name} first_usage_fetch_start")
        val appContext = context.applicationContext
        val storedToken = GoogleAppAuthTokenStore(appContext).get(providerId)
            ?: return GoogleIdentityUsageAuthorizer.UsageResult(null, "appauth_token_missing")
        val accessTokenResult = freshAccessToken(appContext, providerId, storedToken)
        val accessToken = accessTokenResult.accessToken
        if (accessToken.isNullOrBlank()) {
            Log.i("GoogleOAuth", "provider=${providerId.name} first_usage_fetch_result=error")
            return GoogleIdentityUsageAuthorizer.UsageResult(
                null,
                accessTokenResult.diagnostic ?: "appauth_refresh_failed"
            )
        }
        return GoogleIdentityUsageAuthorizer.usagePayloadFromAccessToken(
            context = appContext,
            providerId = providerId,
            accessToken = accessToken,
            accountName = storedToken.accountName,
            email = storedToken.email
        )
    }

    fun storeTokenResponse(
        context: Context,
        providerId: ProviderId,
        tokenResponse: TokenResponse
    ): StoreResult {
        val accessToken = tokenResponse.accessToken?.takeIf { it.isNotBlank() }
        val refreshToken = tokenResponse.refreshToken?.takeIf { it.isNotBlank() }
        Log.i(
            "GoogleOAuth",
            "provider=${providerId.name} access_token=${accessToken != null} refresh_token=${refreshToken != null}"
        )
        if (accessToken == null) return StoreResult(false, "appauth_access_token_missing")
        if (refreshToken == null) return StoreResult(false, "appauth_refresh_token_missing")
        GoogleAppAuthTokenStore(context.applicationContext).save(
            providerId = providerId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = tokenResponse.accessTokenExpirationTime ?: 0L,
            accountName = null,
            email = null
        )
        GoogleIdentityCredentialStore(context.applicationContext).clear(providerId)
        Log.i("GoogleOAuth", "provider=${providerId.name} token_stored")
        return StoreResult(true, null)
    }

    fun authorizationDiagnostic(error: AuthorizationException?): String {
        if (error == null) return "appauth_authorization_failed"
        return "appauth_authorization_${error.type}_${error.code}"
    }

    fun tokenDiagnostic(error: AuthorizationException?): String {
        if (error == null) return "appauth_token_exchange_failed"
        return "appauth_token_${error.type}_${error.code}"
    }

    private fun freshAccessToken(
        context: Context,
        providerId: ProviderId,
        storedToken: StoredGoogleAppAuthToken
    ): AccessTokenResult {
        val now = System.currentTimeMillis()
        if (storedToken.accessToken.isNotBlank() && storedToken.expiresAtMillis > now + TOKEN_EXPIRY_SKEW_MILLIS) {
            return AccessTokenResult(storedToken.accessToken, null)
        }
        if (!isConfigured()) {
            return AccessTokenResult(null, "appauth_client_id_missing")
        }
        Log.i("GoogleOAuth", "provider=${providerId.name} token_refresh_start")
        val request = TokenRequest.Builder(
            serviceConfiguration,
            BuildConfig.GOOGLE_ANDROID_OAUTH_CLIENT_ID
        )
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(storedToken.refreshToken)
            .build()
        val response = awaitTokenResponse(context, request)
        val tokenResponse = response.response
        val accessToken = tokenResponse?.accessToken?.takeIf { it.isNotBlank() }
        Log.i("GoogleOAuth", "provider=${providerId.name} token_refresh_access_token=${accessToken != null}")
        if (accessToken == null) {
            return AccessTokenResult(null, response.diagnostic ?: "appauth_refresh_failed")
        }
        GoogleAppAuthTokenStore(context.applicationContext).save(
            providerId = providerId,
            accessToken = accessToken,
            refreshToken = tokenResponse.refreshToken?.takeIf { it.isNotBlank() } ?: storedToken.refreshToken,
            expiresAtMillis = tokenResponse.accessTokenExpirationTime ?: 0L,
            accountName = storedToken.accountName,
            email = storedToken.email
        )
        return AccessTokenResult(accessToken, null)
    }

    private fun awaitTokenResponse(context: Context, request: TokenRequest): AwaitTokenResult {
        val authService = AuthorizationService(context.applicationContext)
        val latch = CountDownLatch(1)
        var tokenResponse: TokenResponse? = null
        var tokenException: AuthorizationException? = null
        authService.performTokenRequest(request) { response, exception ->
            tokenResponse = response
            tokenException = exception
            latch.countDown()
        }
        val completed = latch.await(TOKEN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        authService.dispose()
        if (!completed) return AwaitTokenResult(null, "appauth_token_timeout")
        if (tokenResponse == null) return AwaitTokenResult(null, tokenDiagnostic(tokenException))
        return AwaitTokenResult(tokenResponse, null)
    }

    private fun ProviderId.isGoogleProvider(): Boolean {
        return this == ProviderId.GEMINI || this == ProviderId.ANTIGRAVITY
    }

    data class StoreResult(
        val stored: Boolean,
        val diagnostic: String?
    )

    private data class AccessTokenResult(
        val accessToken: String?,
        val diagnostic: String?
    )

    private data class AwaitTokenResult(
        val response: TokenResponse?,
        val diagnostic: String?
    )

    private const val SCOPE =
        "https://www.googleapis.com/auth/cloud-platform " +
            "https://www.googleapis.com/auth/userinfo.email " +
            "https://www.googleapis.com/auth/userinfo.profile"
    private val REDIRECT_URI: Uri = Uri.parse("${BuildConfig.GOOGLE_ANDROID_OAUTH_REDIRECT_SCHEME}:/oauth2redirect")
    private const val TOKEN_TIMEOUT_MS = 20_000L
    private const val TOKEN_EXPIRY_SKEW_MILLIS = 5 * 60_000L
}
