package com.aiquota.mobile.providers

import android.content.Context
import com.aiquota.mobile.local.ProviderId
import org.json.JSONObject

object GoogleOAuthUsageBridge {
    fun hasStoredCredential(context: Context, providerId: ProviderId): Boolean {
        val appContext = context.applicationContext
        if (!providerId.isGoogleProvider()) return false
        return hasStoredPublicClientCredential(appContext, providerId) ||
            GoogleAppAuthTokenStore(appContext).hasStoredCredential(providerId) ||
            GoogleIdentityCredentialStore(appContext).hasStoredCredential(providerId)
    }

    fun bridgeUsagePayload(context: Context, providerId: ProviderId): String {
        val appContext = context.applicationContext
        if (!providerId.isGoogleProvider()) {
            return JSONObject()
                .put("ok", false)
                .put("provider", providerId.storageId)
                .put("diagnostic", "identity_unsupported_provider")
                .put("requiresAuth", false)
                .toString()
        }
        val publicClientResult = publicClientUsagePayload(appContext, providerId)
        if (!publicClientResult.payload.isNullOrBlank()) {
            val result = runCatching {
                JSONObject()
                    .put("ok", true)
                    .put("source", "google_public_client_oauth")
                    .put("payload", JSONObject(publicClientResult.payload))
                    .put("requiresAuth", false)
                    .toString()
            }.getOrNull()
            if (result != null) return result
        }
        if (publicClientResult.diagnostic != "public_client_token_missing") {
            return JSONObject()
                .put("ok", false)
                .put("provider", providerId.storageId)
                .put("diagnostic", publicClientResult.diagnostic ?: "public_client_payload_unavailable")
                .put("identityDiagnostic", publicClientResult.diagnostic ?: "public_client_payload_unavailable")
                .put("requiresAuth", requiresInteractiveGoogleAuth(publicClientResult.diagnostic))
                .toString()
        }
        val appAuthResult = GoogleAppAuthUsageAuthorizer.fetchUsagePayload(
            appContext,
            providerId
        )
        if (!appAuthResult.payload.isNullOrBlank()) {
            val result = runCatching {
                JSONObject()
                    .put("ok", true)
                    .put("source", "google_appauth")
                    .put("payload", JSONObject(appAuthResult.payload))
                    .put("requiresAuth", false)
                    .toString()
            }.getOrNull()
            if (result != null) return result
        }
        if (appAuthResult.diagnostic != "appauth_token_missing") {
            return JSONObject()
                .put("ok", false)
                .put("provider", providerId.storageId)
                .put("diagnostic", appAuthResult.diagnostic ?: "appauth_payload_unavailable")
                .put("identityDiagnostic", appAuthResult.diagnostic ?: "appauth_payload_unavailable")
                .put("requiresAuth", requiresInteractiveGoogleAuth(appAuthResult.diagnostic))
                .toString()
        }

        val identityResult = GoogleIdentityUsageAuthorizer.fetchUsagePayload(
            appContext,
            providerId
        )
        val identityPayload = identityResult.payload
        val identityDiagnostic = identityResult.diagnostic
        if (!identityPayload.isNullOrBlank()) {
            val result = runCatching {
                JSONObject()
                    .put("ok", true)
                    .put("source", "google_identity")
                    .put("payload", JSONObject(identityPayload))
                    .put("requiresAuth", false)
                    .toString()
            }.getOrNull()
            if (result != null) return result
        }

        return JSONObject()
            .put("ok", false)
            .put("provider", providerId.storageId)
            .put("diagnostic", identityDiagnostic ?: "identity_payload_unavailable")
            .put("identityDiagnostic", identityDiagnostic ?: "identity_payload_unavailable")
            .put("requiresAuth", requiresInteractiveGoogleAuth(identityDiagnostic))
            .toString()
    }

    private fun requiresInteractiveGoogleAuth(diagnostic: String?): Boolean {
        val value = diagnostic.orEmpty()
        return value.startsWith("appauth_start_") ||
            value.startsWith("appauth_authorization_") ||
            value.startsWith("appauth_token_") ||
            value == "identity_resolution_required" ||
            value == "identity_authorization_unavailable" ||
            value == "identity_access_token_unavailable" ||
            value == "identity_status_4" ||
            value == "identity_status_6" ||
            value == "identity_status_8" ||
            value == "identity_status_16" ||
            value == "token_broker_recoverable_auth" ||
            value == "token_broker_access_token_missing" ||
            value == "token_broker_GoogleAuthException" ||
            value == "appauth_client_id_missing" ||
            value == "appauth_refresh_token_missing" ||
            value == "appauth_refresh_failed" ||
            value == "public_client_token_missing"
    }

    private fun hasStoredPublicClientCredential(context: Context, providerId: ProviderId): Boolean {
        return when (providerId) {
            ProviderId.GEMINI -> GeminiCliOAuthRepository(context).hasStoredCredential()
            ProviderId.ANTIGRAVITY -> AntigravityOAuthRepository(context).hasStoredCredential()
            else -> false
        }
    }

    private fun publicClientUsagePayload(
        context: Context,
        providerId: ProviderId
    ): GoogleIdentityUsageAuthorizer.UsageResult {
        return when (providerId) {
            ProviderId.GEMINI -> GeminiCliOAuthRepository(context).let { repository ->
                if (!repository.hasStoredCredential()) {
                    GoogleIdentityUsageAuthorizer.UsageResult(null, "public_client_token_missing")
                } else {
                    val payload = repository.fetchUsagePayloadFromStoredCredential()
                    GoogleIdentityUsageAuthorizer.UsageResult(
                        payload,
                        if (payload == null) repository.lastFailureDiagnostic() ?: "public_client_payload_unavailable" else null
                    )
                }
            }
            ProviderId.ANTIGRAVITY -> AntigravityOAuthRepository(context).let { repository ->
                if (!repository.hasStoredCredential()) {
                    GoogleIdentityUsageAuthorizer.UsageResult(null, "public_client_token_missing")
                } else {
                    val payload = repository.fetchUsagePayloadFromStoredCredential()
                    GoogleIdentityUsageAuthorizer.UsageResult(
                        payload,
                        if (payload == null) repository.lastFailureDiagnostic() ?: "public_client_payload_unavailable" else null
                    )
                }
            }
            else -> GoogleIdentityUsageAuthorizer.UsageResult(null, "public_client_token_missing")
        }
    }

    private fun ProviderId.isGoogleProvider(): Boolean {
        return this == ProviderId.GEMINI || this == ProviderId.ANTIGRAVITY
    }
}
