package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

object ProviderRefreshTimeoutPolicy {
    fun failureFor(providerId: ProviderId, lastUrl: String?): ProviderRefreshFailure {
        return if (isAuthExchangeUrl(providerId, lastUrl.orEmpty())) {
            ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)
        } else {
            ProviderRefreshFailure(ProviderRefreshFailureKind.TIMEOUT, BACKGROUND_REFRESH_TIMEOUT_MESSAGE)
        }
    }

    private fun isAuthExchangeUrl(providerId: ProviderId, url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return when (providerId) {
            ProviderId.OPENCODE -> host == "auth.opencode.ai"
            ProviderId.CURSOR -> host in CURSOR_AUTH_EXCHANGE_HOSTS
            else -> false
        }
    }

    private val CURSOR_AUTH_EXCHANGE_HOSTS = setOf(
        "api.workos.com",
        "auth.workos.com",
        "workos.com",
        "authenticate.cursor.sh",
        "authenticator.cursor.sh"
    )

    private const val LOGIN_PAGE_REACHED_MESSAGE = "Background refresh reached a provider login page."
    private const val BACKGROUND_REFRESH_TIMEOUT_MESSAGE = "Background refresh timed out."
}
