package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

object ProviderLoginStrategy {
    fun isLoginComplete(
        providerId: ProviderId,
        url: String,
        cookies: Map<String, String>,
        pageText: String
    ): Boolean {
        return when (providerId) {
            ProviderId.CLAUDE ->
                false
            ProviderId.CODEX ->
                false
            ProviderId.GEMINI ->
                isGeminiOAuthCallback(url)
            ProviderId.COPILOT ->
                false
            ProviderId.CURSOR ->
                false
        }
    }

    private fun isGeminiOAuthCallback(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty()
        return (host == "127.0.0.1" || host == "localhost") &&
            (uri.port == 46417 || uri.port == 14641) &&
            path == "/oauth2callback" &&
            (uri.query.orEmpty().contains("code=") || uri.query.orEmpty().contains("error="))
    }

    fun isTransientNavigationError(url: String, errorCode: Int): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        if (errorCode == 0) return false
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host.endsWith("accounts.google.com") ||
            host.endsWith("google.com") ||
            host.endsWith("gstatic.com") ||
            host.endsWith("github.com") ||
            host.endsWith("githubassets.com") ||
            host.endsWith("auth.openai.com") ||
            host.endsWith("chatgpt.com") ||
            host.endsWith("claude.ai") ||
            host.endsWith("cursor.com") ||
            host.endsWith("cursor.sh") ||
            host.endsWith("workos.com")
    }

    fun isBlockingHttpError(url: String, statusCode: Int): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        if (statusCode < 400) return false
        if (host.endsWith("claude.ai")) return false
        return host.isNotBlank()
    }
}
