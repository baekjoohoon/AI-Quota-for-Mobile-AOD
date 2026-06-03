package com.aiquota.mobile.providers

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aiquota.mobile.local.ProviderId
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object GoogleIdentityUsageAuthorizer {
    fun authorizationRequest(accountName: String? = null): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(REQUESTED_SCOPES)
        if (!accountName.isNullOrBlank()) {
            builder.setAccount(Account(accountName, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
        }
        return builder.build()
    }

    fun fetchUsagePayload(context: Context, providerId: ProviderId): UsageResult {
        if (!providerId.isGoogleProvider()) return UsageResult(null, "identity_unsupported_provider")
        Log.i("GoogleOAuth", "provider=${providerId.name} first_usage_fetch_start")
        val appContext = context.applicationContext
        val store = GoogleIdentityCredentialStore(appContext)
        val authorization = awaitAuthorization(
            Identity.getAuthorizationClient(appContext).authorize(
                authorizationRequest(store.accountName(providerId))
            )
        )
        val result = authorization.result
            ?: return usagePayloadFromStoredGoogleAccount(
                context = appContext,
                providerId = providerId,
                originalDiagnostic = authorization.diagnostic ?: "identity_authorization_unavailable"
            )
        return usagePayloadFromAuthorizationResult(appContext, providerId, result)
    }

    fun usagePayloadFromAuthorizationResult(
        context: Context,
        providerId: ProviderId,
        result: AuthorizationResult
    ): UsageResult {
        Log.i("GoogleOAuth", "provider=${providerId.name} auth_response_received=true")
        if (result.hasResolution()) {
            Log.i("GoogleOAuth", "provider=${providerId.name} first_usage_fetch_result=error")
            return UsageResult(null, "identity_resolution_required")
        }
        val accessToken = result.accessToken?.takeIf { it.isNotBlank() }
        Log.i(
            "GoogleOAuth",
            "provider=${providerId.name} access_token=${accessToken != null} refresh_token=false serverless=true"
        )
        if (accessToken == null) {
            Log.i("GoogleOAuth", "provider=${providerId.name} first_usage_fetch_result=error")
            return UsageResult(null, "identity_access_token_unavailable")
        }
        val account = runCatching { result.toGoogleSignInAccount() }.getOrNull()
        val email = account?.email
        val accountName = account?.account?.name ?: email
        return usagePayloadFromAccessToken(context, providerId, accessToken, accountName, email)
    }

    fun usagePayloadFromAccessToken(
        context: Context,
        providerId: ProviderId,
        accessToken: String,
        accountName: String?,
        email: String?
    ): UsageResult {
        Log.i(
            "GoogleOAuth",
            "provider=${providerId.name} access_token=${accessToken.isNotBlank()} refresh_token=false serverless=true"
        )
        GoogleIdentityCredentialStore(context.applicationContext).save(providerId, accountName, email)
        Log.i("GoogleOAuth", "provider=${providerId.name} token_stored")
        Log.i("GoogleOAuth", "provider=${providerId.name} first_usage_fetch_start")

        var usageDiagnostic: String? = null
        val payload = when (providerId) {
            ProviderId.GEMINI -> GeminiCliOAuthRepository(context.applicationContext).let { repository ->
                repository.fetchUsagePayloadWithAccessToken(accessToken, email).also { payload ->
                    if (payload == null) usageDiagnostic = repository.lastFailureDiagnostic()
                }
            }
            ProviderId.ANTIGRAVITY -> AntigravityOAuthRepository(context.applicationContext).let { repository ->
                repository.fetchUsagePayloadWithAccessToken(accessToken, email).also { payload ->
                    if (payload == null) usageDiagnostic = repository.lastFailureDiagnostic()
                }
            }
            else -> null
        }
        Log.i(
            "GoogleOAuth",
            "provider=${providerId.name} first_usage_fetch_result=${if (payload == null) "untrusted" else "trusted"}"
        )
        return UsageResult(payload, if (payload == null) usageDiagnostic ?: "identity_payload_unavailable" else null)
    }

    fun requestGoogleAuthToken(context: Context, accountName: String): TokenBrokerResult {
        val account = Account(accountName, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE)
        val scope = "oauth2:" + REQUESTED_SCOPES.joinToString(" ") { it.scopeUri }
        return try {
            val token = GoogleAuthUtil.getToken(context.applicationContext, account, scope)
            TokenBrokerResult(
                accessToken = token?.takeIf { it.isNotBlank() },
                recoveryIntent = null,
                diagnostic = if (token.isNullOrBlank()) "token_broker_access_token_missing" else null
            )
        } catch (error: UserRecoverableAuthException) {
            TokenBrokerResult(
                accessToken = null,
                recoveryIntent = error.intent,
                diagnostic = "token_broker_recoverable_auth"
            )
        } catch (error: GoogleAuthException) {
            TokenBrokerResult(null, null, "token_broker_${error.javaClass.simpleName}")
        } catch (error: java.io.IOException) {
            TokenBrokerResult(null, null, "token_broker_${error.javaClass.simpleName}")
        } catch (error: RuntimeException) {
            TokenBrokerResult(null, null, "token_broker_${error.javaClass.simpleName}")
        }
    }

    private fun usagePayloadFromStoredGoogleAccount(
        context: Context,
        providerId: ProviderId,
        originalDiagnostic: String
    ): UsageResult {
        if (!originalDiagnostic.canUseTokenBrokerFallback()) {
            return UsageResult(null, originalDiagnostic)
        }
        val store = GoogleIdentityCredentialStore(context.applicationContext)
        val accountName = store.accountName(providerId) ?: store.email(providerId)
            ?: return UsageResult(null, originalDiagnostic)
        Log.i(
            "GoogleOAuth",
            "provider=${providerId.name} token_broker_fallback_start diagnostic=$originalDiagnostic"
        )
        val tokenResult = requestGoogleAuthToken(context, accountName)
        val accessToken = tokenResult.accessToken
        if (!accessToken.isNullOrBlank()) {
            return usagePayloadFromAccessToken(context, providerId, accessToken, accountName, store.email(providerId) ?: accountName)
        }
        return UsageResult(null, tokenResult.diagnostic ?: originalDiagnostic)
    }

    private fun awaitAuthorization(task: Task<AuthorizationResult>): AwaitAuthorizationResult {
        val latch = CountDownLatch(1)
        var result: AuthorizationResult? = null
        var failure: Exception? = null
        task
            .addOnSuccessListener {
                result = it
                latch.countDown()
            }
            .addOnFailureListener {
                failure = it
                latch.countDown()
            }
        if (!latch.await(AUTHORIZATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            return AwaitAuthorizationResult(null, "identity_authorization_timeout")
        }
        if (failure != null) {
            val diagnostic = identityFailureDiagnostic(failure)
            Log.w(TAG, "googleIdentityAuthorize success=false diagnostic=$diagnostic")
            return AwaitAuthorizationResult(null, diagnostic)
        }
        return AwaitAuthorizationResult(result, null)
    }

    private fun identityFailureDiagnostic(error: Exception?): String {
        return when (error) {
            is ApiException -> "identity_status_${error.statusCode}"
            null -> "identity_unknown_failure"
            else -> "identity_${error.javaClass.simpleName}"
        }
    }

    private fun ProviderId.isGoogleProvider(): Boolean {
        return this == ProviderId.GEMINI || this == ProviderId.ANTIGRAVITY
    }

    data class UsageResult(
        val payload: String?,
        val diagnostic: String?
    )

    data class TokenBrokerResult(
        val accessToken: String?,
        val recoveryIntent: Intent?,
        val diagnostic: String?
    )

    private data class AwaitAuthorizationResult(
        val result: AuthorizationResult?,
        val diagnostic: String?
    )

    private const val TAG = "AIQuotaGoogleIdentity"
    private const val AUTHORIZATION_TIMEOUT_MS = 15_000L
    private val REQUESTED_SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/cloud-platform"),
        Scope("https://www.googleapis.com/auth/userinfo.email"),
        Scope("https://www.googleapis.com/auth/userinfo.profile")
    )

}

private fun String.canUseTokenBrokerFallback(): Boolean {
    return this == "identity_status_8"
}
