package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

data class ProviderDefinition(
    val providerId: ProviderId,
    val loginStartUrl: String,
    val allowedHosts: Set<String>,
    val preferredUsageEndpoint: String
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
                "apis.google.com",
                "ssl.gstatic.com",
                "www.gstatic.com"
            ),
            preferredUsageEndpoint = "https://claude.ai/api/organizations/{organizationId}/usage"
        ),
        ProviderDefinition(
            providerId = ProviderId.CODEX,
            loginStartUrl = "https://chatgpt.com/auth/login",
            allowedHosts = setOf(
                "chatgpt.com",
                "chat.openai.com",
                "auth.openai.com",
                "accounts.google.com",
                "appleid.apple.com",
                "challenges.cloudflare.com"
            ),
            preferredUsageEndpoint = "https://chatgpt.com/backend-api/wham/usage"
        ),
        ProviderDefinition(
            providerId = ProviderId.GEMINI,
            loginStartUrl = "https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fgemini.google.com%2Fapp&hl=ko",
            allowedHosts = setOf(
                "accounts.google.com",
                "myaccount.google.com",
                "google.com",
                "www.google.com",
                "oauth2.googleapis.com",
                "gemini.google.com",
                "www.gstatic.com",
                "ssl.gstatic.com"
            ),
            preferredUsageEndpoint = "assets/gemini_collector.js"
        ),
        ProviderDefinition(
            providerId = ProviderId.COPILOT,
            loginStartUrl = "https://github.com/settings/copilot",
            allowedHosts = setOf(
                "github.com",
                "api.github.com",
                "github.githubassets.com",
                "githubassets.com",
                "githubusercontent.com",
                "objects.githubusercontent.com",
                "avatars.githubusercontent.com"
            ),
            preferredUsageEndpoint = "https://github.com/github-copilot/chat/entitlement"
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
                "ssl.gstatic.com",
                "www.gstatic.com",
                "github.com",
                "github.githubassets.com",
                "githubassets.com",
                "githubusercontent.com"
            ),
            preferredUsageEndpoint = "https://api2.cursor.sh/auth/poll"
        )
    )

    fun all(): List<ProviderDefinition> = definitions

    fun definitionFor(providerId: ProviderId): ProviderDefinition {
        return definitions.first { it.providerId == providerId }
    }

    fun isAllowed(providerId: ProviderId, url: String): Boolean {
        val host = hostOf(url) ?: return false
        return definitionFor(providerId).allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    private fun hostOf(url: String): String? {
        return runCatching { URI(url).host }
            .getOrNull()
            ?.lowercase(Locale.US)
    }
}
