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

    private val CURSOR_AUTH_EXCHANGE_HOSTS = setOf(
        "api.workos.com",
        "auth.workos.com",
        "workos.com",
        "authenticate.cursor.sh",
        "authenticator.cursor.sh"
    )
}
