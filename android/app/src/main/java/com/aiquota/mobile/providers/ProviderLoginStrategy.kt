package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

object ProviderLoginStrategy {
    const val CODEX_CALLBACK_RECOVERY_URL = "https://chatgpt.com/codex/cloud/settings/analytics#usage"

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
            ProviderId.GLM ->
                false
            ProviderId.OPENCODE ->
                false
            ProviderId.GEMINI ->
                false
            ProviderId.COPILOT ->
                false
            ProviderId.ANTIGRAVITY ->
                false
            ProviderId.CURSOR ->
                false
        }
    }

    fun shouldRecoverCodexLocalAuthCallback(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty()
        if (host != "localhost" && host != "127.0.0.1") return false
        if (uri.port != 1455 || path != "/auth/callback") return false
        val query = uri.query.orEmpty()
        return query.contains("code=") || query.contains("error=")
    }

    fun isInteractiveLoginSessionReached(providerId: ProviderId, url: String): Boolean {
        if (providerId != ProviderId.GEMINI && providerId != ProviderId.ANTIGRAVITY) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val rawQuery = uri.rawQuery.orEmpty()
        if (rawQuery.contains("up.signed_in=yes")) return true
        if (host == "play.google.com" &&
            uri.path.orEmpty() == "/log" &&
            rawQuery.contains("auth=SAPISID")
        ) {
            return true
        }
        return false
    }

    fun shouldFinishOnInteractiveLoginSessionReached(providerId: ProviderId): Boolean {
        return providerId == ProviderId.ANTIGRAVITY
    }

    fun shouldRedirectCopilotToSettings(url: String, pageText: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        if (host != "github.com" && host != "www.github.com") return false
        val path = uri.path.orEmpty().lowercase(Locale.US)
        if (path.startsWith("/login") ||
            path.startsWith("/sessions") ||
            path.startsWith("/session") ||
            path.contains("two-factor") ||
            path.startsWith("/settings/copilot") ||
            path.startsWith("/github-copilot")
        ) {
            return false
        }
        if (path == "/" || path == "/dashboard") return true
        val text = pageText.lowercase(Locale.US)
        val signedInHome = text.contains("dashboard") &&
            (text.contains("top repositories") || text.contains("pull requests") || text.contains("new repository"))
        return signedInHome && !text.contains("sign in to github")
    }

    fun isTransientNavigationError(url: String, errorCode: Int): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        if (errorCode == 0) return false
        return host == "localhost" ||
            host == "127.0.0.1" ||
            isGoogleAccountHost(host) ||
            host.endsWith("accounts.youtube.com") ||
            host.endsWith("google.com") ||
            host.endsWith("gstatic.com") ||
            host.endsWith("github.com") ||
            host.endsWith("githubassets.com") ||
            host.endsWith("auth.openai.com") ||
            host.endsWith("chatgpt.com") ||
            host.endsWith("z.ai") ||
            host.endsWith("api.z.ai") ||
            host.endsWith("opencode.ai") ||
            host.endsWith("claude.ai") ||
            host.endsWith("antigravity.google") ||
            host.endsWith("cursor.com") ||
            host.endsWith("cursor.sh") ||
            host.endsWith("workos.com")
    }

    private fun isGoogleAccountHost(host: String): Boolean {
        return GOOGLE_ACCOUNT_HOST.matches(host)
    }

    fun isBlockingHttpError(url: String, statusCode: Int): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        if (statusCode < 400) return false
        if (host.endsWith("claude.ai")) return false
        if (host == "authenticator.cursor.sh" && (statusCode == 401 || statusCode == 403)) return false
        return host.isNotBlank()
    }

    private val GOOGLE_ACCOUNT_HOST =
        Regex("""^accounts\.google\.(?:com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")
}
