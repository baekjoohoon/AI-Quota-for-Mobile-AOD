package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

data class ProviderDefinition(
    val providerId: ProviderId,
    val loginStartUrl: String,
    val allowedHosts: Set<String>,
    val loginAllowedHosts: Set<String> = allowedHosts,
    val collectorAllowedHosts: Set<String> = allowedHosts,
    val preferredUsageEndpoint: String,
    val authStoreKind: ProviderAuthStoreKind,
    val collectionKind: ProviderCollectionKind,
    val sessionProbeUrl: String
)

object ProviderDefinitionRegistry {
    private val definitions = listOf(
        ProviderDefinition(
            providerId = ProviderId.CLAUDE,
            loginStartUrl = "https://claude.ai/login",
            allowedHosts = setOf(
                "claude.ai",
                "www.claude.ai",
                "accounts.google.com",
                "myaccount.google.com",
                "google.com",
                "www.google.com",
                "oauth2.googleapis.com",
                "cloudcode-pa.googleapis.com",
                "apis.google.com",
                "accounts.youtube.com",
                "play.google.com",
                "challenges.cloudflare.com",
                "www.recaptcha.net",
                "recaptcha.net",
                "ssl.gstatic.com",
                "www.gstatic.com"
            ),
            collectorAllowedHosts = setOf(
                "claude.ai",
                "www.claude.ai"
            ),
            preferredUsageEndpoint = "https://claude.ai/api/organizations/{organizationId}/usage",
            authStoreKind = ProviderAuthStoreKind.WEBVIEW_PROFILE,
            collectionKind = ProviderCollectionKind.WEBVIEW_COLLECTOR,
            sessionProbeUrl = "https://claude.ai/"
        ),
        codexDefinition(ProviderId.CODEX),
        ProviderDefinition(
            providerId = ProviderId.GLM,
            loginStartUrl = "aiquota://provider/glm-api-key",
            allowedHosts = setOf(
                "z.ai",
                "www.z.ai",
                "chat.z.ai",
                "api.z.ai",
                "accounts.google.com",
                "myaccount.google.com",
                "google.com",
                "www.google.com",
                "oauth2.googleapis.com",
                "accounts.youtube.com",
                "play.google.com",
                "challenges.cloudflare.com",
                "www.recaptcha.net",
                "recaptcha.net",
                "ssl.gstatic.com",
                "www.gstatic.com"
            ),
            collectorAllowedHosts = setOf(
                "z.ai",
                "www.z.ai",
                "chat.z.ai",
                "api.z.ai"
            ),
            preferredUsageEndpoint = GlmProviderUrls.API_QUOTA_URL,
            authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN,
            collectionKind = ProviderCollectionKind.NATIVE_API,
            sessionProbeUrl = GlmProviderUrls.WEB_OAUTH_URL
        ),
        ProviderDefinition(
            providerId = ProviderId.OPENCODE,
            loginStartUrl = "https://opencode.ai/auth",
            allowedHosts = setOf(
                "opencode.ai",
                "www.opencode.ai",
                "auth.opencode.ai",
                "accounts.google.com",
                "myaccount.google.com",
                "google.com",
                "www.google.com",
                "oauth2.googleapis.com",
                "accounts.youtube.com",
                "play.google.com",
                "github.com",
                "github.githubassets.com",
                "githubassets.com",
                "githubusercontent.com",
                "challenges.cloudflare.com",
                "www.recaptcha.net",
                "recaptcha.net",
                "ssl.gstatic.com",
                "www.gstatic.com"
            ),
            collectorAllowedHosts = setOf(
                "opencode.ai",
                "www.opencode.ai",
                "auth.opencode.ai"
            ),
            preferredUsageEndpoint = "https://opencode.ai/auth",
            authStoreKind = ProviderAuthStoreKind.WEBVIEW_PROFILE,
            collectionKind = ProviderCollectionKind.WEBVIEW_COLLECTOR,
            sessionProbeUrl = "https://opencode.ai/auth"
        ),
        ProviderDefinition(
            providerId = ProviderId.GEMINI,
            loginStartUrl = "https://gemini.google.com/usage",
            allowedHosts = setOf(
                "accounts.google.com",
                "myaccount.google.com",
                "google.com",
                "www.google.com",
                "oauth2.googleapis.com",
                "gemini.google.com",
                "accounts.youtube.com",
                "play.google.com",
                "challenges.cloudflare.com",
                "www.recaptcha.net",
                "recaptcha.net",
                "www.gstatic.com",
                "ssl.gstatic.com"
            ),
            collectorAllowedHosts = setOf(
                "gemini.google.com"
            ),
            preferredUsageEndpoint = "https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuota",
            authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN,
            collectionKind = ProviderCollectionKind.NATIVE_API,
            sessionProbeUrl = "https://gemini.google.com/app"
        ),
        ProviderDefinition(
            providerId = ProviderId.COPILOT,
            loginStartUrl = "https://github.com/settings/copilot/features",
            allowedHosts = setOf(
                "github.com",
                "api.github.com",
                "github.githubassets.com",
                "githubassets.com",
                "githubusercontent.com",
                "objects.githubusercontent.com",
                "avatars.githubusercontent.com"
            ),
            collectorAllowedHosts = setOf(
                "github.com"
            ),
            preferredUsageEndpoint = "https://github.com/github-copilot/chat/entitlement",
            authStoreKind = ProviderAuthStoreKind.WEBVIEW_PROFILE,
            collectionKind = ProviderCollectionKind.WEBVIEW_COLLECTOR,
            sessionProbeUrl = "https://github.com/settings/copilot/features"
        ),
        ProviderDefinition(
            providerId = ProviderId.ANTIGRAVITY,
            loginStartUrl = "https://antigravity.google/",
            allowedHosts = setOf(
                "antigravity.google",
                "www.antigravity.google",
                "accounts.google.com",
                "myaccount.google.com",
                "google.com",
                "www.google.com",
                "oauth2.googleapis.com",
                "apis.google.com",
                "accounts.youtube.com",
                "play.google.com",
                "challenges.cloudflare.com",
                "www.recaptcha.net",
                "recaptcha.net",
                "ssl.gstatic.com",
                "www.gstatic.com"
            ),
            collectorAllowedHosts = setOf(
                "antigravity.google",
                "www.antigravity.google"
            ),
            preferredUsageEndpoint = "https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels",
            authStoreKind = ProviderAuthStoreKind.NATIVE_TOKEN,
            collectionKind = ProviderCollectionKind.NATIVE_API,
            sessionProbeUrl = "https://antigravity.google/"
        ),
        ProviderDefinition(
            providerId = ProviderId.CURSOR,
            loginStartUrl = "https://cursor.com/dashboard",
            allowedHosts = setOf(
                "cursor.com",
                "www.cursor.com",
                "cursor.sh",
                "api2.cursor.sh",
                "authenticate.cursor.sh",
                "authenticator.cursor.sh",
                "api.workos.com",
                "auth.workos.com",
                "workos.com",
                "accounts.google.com",
                "myaccount.google.com",
                "google.com",
                "www.google.com",
                "oauth2.googleapis.com",
                "accounts.youtube.com",
                "play.google.com",
                "challenges.cloudflare.com",
                "www.recaptcha.net",
                "recaptcha.net",
                "ssl.gstatic.com",
                "www.gstatic.com",
                "github.com",
                "github.githubassets.com",
                "githubassets.com",
                "githubusercontent.com"
            ),
            collectorAllowedHosts = setOf(
                "cursor.com",
                "www.cursor.com",
                "api.workos.com",
                "auth.workos.com",
                "workos.com",
                "authenticate.cursor.sh",
                "authenticator.cursor.sh"
            ),
            preferredUsageEndpoint = "https://api2.cursor.sh/auth/poll",
            authStoreKind = ProviderAuthStoreKind.TOKEN_OR_WEBVIEW_FALLBACK,
            collectionKind = ProviderCollectionKind.NATIVE_API_WITH_WEBVIEW_FALLBACK,
            sessionProbeUrl = "https://cursor.com/dashboard"
        )
    )

    fun all(): List<ProviderDefinition> = definitions

    fun definitionFor(providerId: ProviderId): ProviderDefinition {
        return definitions.first { it.providerId == providerId }
    }

    fun isAllowed(providerId: ProviderId, url: String): Boolean {
        return isLoginNavigationAllowed(providerId, url) ||
            isCollectorNavigationAllowed(providerId, url)
    }

    fun isLoginNavigationAllowed(providerId: ProviderId, url: String): Boolean {
        val host = hostOf(url) ?: return false
        val definition = definitionFor(providerId)
        if (providerId == ProviderId.OPENCODE && isOpenCodeDocsUrl(url)) return false
        if ("accounts.google.com" in definition.loginAllowedHosts && isGoogleAccountHost(host)) return true
        return isHostAllowed(host, definition.loginAllowedHosts)
    }

    fun isCollectorNavigationAllowed(providerId: ProviderId, url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (providerId == ProviderId.OPENCODE && isOpenCodeDocsUrl(url)) return false
        return isHostAllowed(host, definitionFor(providerId).collectorAllowedHosts)
    }

    private fun isOpenCodeDocsUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        val path = uri.path.orEmpty().lowercase(Locale.US)
        return (host == "opencode.ai" || host == "www.opencode.ai") &&
            (path.startsWith("/docs") || path.startsWith("/brand"))
    }

    private fun isGoogleAccountHost(host: String): Boolean {
        return GOOGLE_ACCOUNT_HOST.matches(host)
    }

    private fun isHostAllowed(host: String, allowedHosts: Set<String>): Boolean {
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun codexDefinition(providerId: ProviderId): ProviderDefinition {
        return ProviderDefinition(
            providerId = providerId,
            loginStartUrl = "https://chatgpt.com/auth/login",
            allowedHosts = setOf(
                "chatgpt.com",
                "chat.openai.com",
                "auth.openai.com",
                "accounts.google.com",
                "accounts.youtube.com",
                "play.google.com",
                "appleid.apple.com",
                "challenges.cloudflare.com"
            ),
            collectorAllowedHosts = setOf(
                "chatgpt.com",
                "chat.openai.com"
            ),
            preferredUsageEndpoint = "https://chatgpt.com/",
            authStoreKind = ProviderAuthStoreKind.WEBVIEW_PROFILE,
            collectionKind = ProviderCollectionKind.WEBVIEW_COLLECTOR,
            sessionProbeUrl = "https://chatgpt.com/api/auth/session"
        )
    }

    private fun hostOf(url: String): String? {
        return runCatching { URI(url).host }
            .getOrNull()
            ?.lowercase(Locale.US)
    }

    private val GOOGLE_ACCOUNT_HOST =
        Regex("""^accounts\.google\.(?:com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")
}
