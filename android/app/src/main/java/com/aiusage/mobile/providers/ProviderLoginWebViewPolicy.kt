package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

data class ProviderLoginWebViewCapabilities(
    val databaseEnabled: Boolean = true,
    val javaScriptCanOpenWindowsAutomatically: Boolean = true,
    val supportMultipleWindows: Boolean = true,
    val acceptThirdPartyCookies: Boolean = true,
    val webContentsDebuggingEnabled: Boolean = true
)

object ProviderLoginWebViewPolicy {
    fun capabilities(): ProviderLoginWebViewCapabilities = ProviderLoginWebViewCapabilities()

    fun shouldOverrideNavigation(providerId: ProviderId, url: String): Boolean {
        if (providerId == ProviderId.CLAUDE || providerId == ProviderId.GEMINI) return false
        if (providerId == ProviderId.CURSOR) return shouldBlockCursorMainFrameProbe(url)
        return !ProviderDefinitionRegistry.isAllowed(providerId, url)
    }

    fun shouldInterceptRequest(providerId: ProviderId, url: String): Boolean = false

    private fun shouldBlockCursorMainFrameProbe(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        val isCursorHost = host == "cursor.com" || host == "www.cursor.com"

        return when {
            host == "api2.cursor.sh" -> true
            isCursorHost && isCursorUsageMainFrameProbe(path) -> true
            else -> false
        }
    }

    private fun isCursorUsageMainFrameProbe(path: String): Boolean =
        path == "/api/usage" ||
            path == "/api/auth/usage" ||
            path == "/api/auth/stripe" ||
            path == "/api/usage-summary" ||
            path == "/api/dashboard/get-credit-grants-balance"
}
