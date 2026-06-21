package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

object ProviderRefreshHttpErrorPolicy {
    fun shouldIgnoreMainFrameHttpError(providerId: ProviderId, url: String, statusCode: Int): Boolean {
        if (statusCode != 403) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return providerId == ProviderId.CURSOR && host in CURSOR_AUTH_EXCHANGE_HOSTS
    }

    fun failureForMainFrameHttpError(providerId: ProviderId, url: String, statusCode: Int): ProviderRefreshFailure {
        return if (isAuthBlockingMainFrameHttpError(providerId, url, statusCode)) {
            ProviderRefreshFailure.interactiveAuthRequired()
        } else {
            ProviderRefreshFailure(
                ProviderRefreshFailureKind.TRANSIENT_HTTP,
                "Background refresh returned HTTP $statusCode."
            )
        }
    }

    private fun isAuthBlockingMainFrameHttpError(providerId: ProviderId, url: String, statusCode: Int): Boolean {
        if (providerId != ProviderId.CODEX) return false
        if (statusCode != 401 && statusCode != 403) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return host == "chatgpt.com" || host.endsWith(".chatgpt.com") || host == "auth.openai.com"
    }

    private val CURSOR_AUTH_EXCHANGE_HOSTS = setOf(
        "api.workos.com",
        "auth.workos.com",
        "workos.com",
        "authenticate.cursor.sh",
        "authenticator.cursor.sh"
    )
}
