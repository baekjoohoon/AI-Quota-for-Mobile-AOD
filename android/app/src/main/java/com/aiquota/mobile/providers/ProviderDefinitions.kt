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
        ProviderDefinition(
            providerId = ProviderId.CODEX,
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
                "www.cursor.com"
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
        if ("accounts.google.com" in definition.loginAllowedHosts && isGoogleAccountHost(host)) return true
        return isHostAllowed(host, definition.loginAllowedHosts)
    }

    fun isCollectorNavigationAllowed(providerId: ProviderId, url: String): Boolean {
        val host = hostOf(url) ?: return false
        return isHostAllowed(host, definitionFor(providerId).collectorAllowedHosts)
    }

    private fun isGoogleAccountHost(host: String): Boolean {
        return GOOGLE_ACCOUNT_HOST.matches(host)
    }

    private fun isHostAllowed(host: String, allowedHosts: Set<String>): Boolean {
        return allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun hostOf(url: String): String? {
        return runCatching { URI(url).host }
            .getOrNull()
            ?.lowercase(Locale.US)
    }

    private val GOOGLE_ACCOUNT_HOST =
        Regex("""^accounts\.google\.(?:com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")
}
