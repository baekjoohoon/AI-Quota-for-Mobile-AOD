package com.aiquota.mobile.providers

import java.net.URI
import java.util.Locale

object GeminiUsagePageRoutes {
    const val USAGE_URL = "https://gemini.google.com/usage"

    fun usageUrlFrom(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = normalizedPath(uri)
        if (host == "myaccount.google.com") return USAGE_URL
        if (host != "gemini.google.com") return null
        if (isUsagePath(path)) return null
        return if (isLandingPath(path)) USAGE_URL else null
    }

    fun isUsageUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return host == "gemini.google.com" && isUsagePath(normalizedPath(uri))
    }

    fun isLoginLandingUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        return host == "gemini.google.com" && isLandingPath(normalizedPath(uri))
    }

    private fun normalizedPath(uri: URI): String {
        return uri.path.orEmpty().lowercase(Locale.US).trimEnd('/')
    }

    private fun isUsagePath(path: String): Boolean {
        return path == "/usage"
    }

    private fun isLandingPath(path: String): Boolean {
        return path.isBlank() ||
            path == "/" ||
            path == "/app" ||
            path.startsWith("/app/") ||
            path.startsWith("/u/")
    }
}
