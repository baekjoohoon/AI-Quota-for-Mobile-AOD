package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId

enum class ProviderRefreshFailureKind {
    TRANSIENT_PAGE_LOAD,
    TRANSIENT_HTTP,
    TIMEOUT,
    COLLECTOR_ERROR,
    NO_TRUSTED_PAYLOAD,
    INTERACTIVE_AUTH_REQUIRED
}

data class ProviderRefreshFailure(
    val kind: ProviderRefreshFailureKind,
    val message: String
) {
    companion object {
        fun interactiveAuthRequired(message: String = "Provider session requires sign-in."): ProviderRefreshFailure {
            return ProviderRefreshFailure(
                ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED,
                message
            )
        }
    }
}

object ProviderRefreshFailureClassifier {
    fun requiresInteractiveAuth(kind: ProviderRefreshFailureKind): Boolean {
        return kind == ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED
    }

    fun requiresInteractiveAuth(providerId: ProviderId, kind: ProviderRefreshFailureKind): Boolean {
        return requiresInteractiveAuth(kind)
    }
}
