package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.json.JSONObject

object ProviderCollectorErrorPolicy {
    fun failureFor(providerId: ProviderId, rawError: String): ProviderRefreshFailure {
        val error = parse(rawError)
        return when {
            error.errorKind in EXPLICIT_AUTH_ERRORS -> ProviderRefreshFailure.interactiveAuthRequired(
                error.message ?: "Provider session requires sign-in."
            )
            providerId == ProviderId.CODEX && error.errorKind == CODEX_USAGE_UNAVAILABLE -> {
                ProviderRefreshFailure(
                    ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD,
                    error.message ?: "Codex session reached, but trusted usage payload was not available."
                )
            }
            else -> ProviderRefreshFailure(
                ProviderRefreshFailureKind.COLLECTOR_ERROR,
                "Background collector failed: ${error.errorKind}"
            )
        }
    }

    fun errorKind(rawError: String): String {
        return parse(rawError).errorKind
    }

    private fun parse(rawError: String): CollectorError {
        return runCatching {
            val json = JSONObject(rawError)
            CollectorError(
                errorKind = json.optString("errorKind", "collector_error").ifBlank { "collector_error" },
                message = json.optString("message").takeIf { it.isNotBlank() }
            )
        }.getOrElse {
            CollectorError(errorKind = "collector_error", message = null)
        }
    }

    private data class CollectorError(
        val errorKind: String,
        val message: String?
    )

    private val EXPLICIT_AUTH_ERRORS = setOf(
        "auth_required",
        "login_required",
        "session_expired",
        "codex_auth_required",
        "oauth_failed",
        "token_refresh_failed"
    )

    private const val CODEX_USAGE_UNAVAILABLE = "codex_usage_unavailable"
}

object CodexCollectorRetryPolicy {
    private const val CODEX_USAGE_UNAVAILABLE = "codex_usage_unavailable"
    private const val MAX_RETRIES = 2

    fun shouldRetry(providerId: ProviderId, errorKind: String, retryCount: Int): Boolean {
        return providerId == ProviderId.CODEX &&
            errorKind == CODEX_USAGE_UNAVAILABLE &&
            retryCount < MAX_RETRIES
    }
}
