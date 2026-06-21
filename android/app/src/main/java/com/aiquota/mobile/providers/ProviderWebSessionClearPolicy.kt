package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import java.net.URI

object ProviderWebSessionClearPolicy {
    fun shouldClearBeforeLogin(providerId: ProviderId, connectionState: ProviderConnectionState?): Boolean {
        if (connectionState != ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED) return false
        return providerId == ProviderId.CURSOR
    }

    fun shouldClearOnDisconnect(providerId: ProviderId): Boolean {
        return ProviderId.entries.contains(providerId)
    }

    fun cookieUrls(providerId: ProviderId): List<String> {
        return when (providerId) {
            ProviderId.CLAUDE -> listOf(
                "https://claude.ai"
            )
            ProviderId.CODEX -> listOf(
                "https://chatgpt.com",
                "https://chatgpt.com/auth/login",
                "https://chatgpt.com/api/auth/session",
                "https://chatgpt.com/backend-api/me",
                "https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27",
                "https://chatgpt.com/codex/cloud/settings/analytics",
                "https://chatgpt.com/codex/settings/usage",
                "https://ws.chatgpt.com",
                "https://sentinel.openai.com",
                "https://oaistatic.com",
                "https://chat.openai.com",
                "https://auth.openai.com",
                "https://auth.openai.com/authorize",
                "https://auth.openai.com/u/login"
            )
            ProviderId.GEMINI -> listOf(
                "https://gemini.google.com",
                "https://oauth2.googleapis.com"
            )
            ProviderId.COPILOT -> listOf(
                "https://github.com"
            )
            ProviderId.ANTIGRAVITY -> listOf(
                "https://antigravity.google",
                "https://www.antigravity.google",
                "https://oauth2.googleapis.com"
            )
            ProviderId.CURSOR -> listOf(
                "https://cursor.com",
                "https://www.cursor.com",
                "https://api2.cursor.sh",
                "https://authenticate.cursor.sh",
                "https://authenticator.cursor.sh",
                "https://api.workos.com",
                "https://auth.workos.com",
                "https://workos.com"
            )
            ProviderId.GLM -> listOf(
                "https://z.ai",
                "https://www.z.ai",
                "https://chat.z.ai",
                "https://api.z.ai"
            )
            ProviderId.OPENCODE -> listOf(
                "https://opencode.ai",
                "https://opencode.ai/auth",
                "https://www.opencode.ai"
            )
        }
    }

    fun storageOrigins(providerId: ProviderId): List<String> {
        return cookieUrls(providerId)
    }

    fun expiringCookieHeaders(cookieHeader: String?, url: String? = null): List<String> {
        if (cookieHeader.isNullOrBlank()) return emptyList()
        val domainVariants = cookieDomainVariants(url)
        val pathVariants = cookiePathVariants(url)
        return cookieHeader
            .split(";")
            .mapNotNull { cookie ->
                val name = cookie.trim().substringBefore("=").trim()
                name.takeIf { it.isNotBlank() && !it.contains(" ") }
            }
            .distinct()
            .flatMap { name ->
                pathVariants.flatMap { path ->
                    val base = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=$path"
                    val hostOnly = listOf(base, "$base; Secure")
                    val domainScoped = domainVariants.flatMap { domain ->
                        listOf("$base; Domain=$domain", "$base; Domain=$domain; Secure")
                    }
                    hostOnly + domainScoped
                }
            }
    }

    private fun cookiePathVariants(url: String?): List<String> {
        val path = runCatching { URI(url.orEmpty()).path.orEmpty() }.getOrDefault("")
        val segments = path.split("/").filter { it.isNotBlank() }
        if (segments.isEmpty()) return listOf("/")

        val paths = mutableListOf("/")
        var current = ""
        segments.forEach { segment ->
            current += "/$segment"
            paths += current
        }
        return paths.distinct()
    }

    private fun cookieDomainVariants(url: String?): List<String> {
        val host = runCatching { URI(url.orEmpty()).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank() || host == "localhost" || host == "127.0.0.1") return emptyList()
        val domains = mutableListOf(host, ".$host")
        parentCookieDomain(host)?.takeUnless { isSharedGoogleIdentityParent(host, it) }?.let { parent ->
            domains += parent
            domains += ".$parent"
        }
        return domains.distinct()
    }

    private fun isSharedGoogleIdentityParent(host: String, parent: String): Boolean {
        return parent == "google.com" && host != parent
    }

    private fun parentCookieDomain(host: String): String? {
        val labels = host.split(".").filter { it.isNotBlank() }
        if (labels.size < 3) return null
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in SECOND_LEVEL_PUBLIC_SUFFIXES && labels.size >= 4) {
            labels.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    private val SECOND_LEVEL_PUBLIC_SUFFIXES = setOf(
        "co.kr",
        "co.uk",
        "com.au",
        "com.br",
        "com.cn",
        "com.tw",
        "co.jp"
    )
}
