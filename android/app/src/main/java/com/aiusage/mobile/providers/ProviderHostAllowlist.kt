package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import java.net.IDN
import java.net.URI
import java.util.Locale

object ProviderHostAllowlist {
    fun isAllowed(providerId: ProviderId, url: String): Boolean {
        val host = parseHost(url) ?: return false
        return allowedHosts.getValue(providerId).any { allowedHost ->
            host == allowedHost || host.endsWith(".$allowedHost")
        }
    }

    private fun parseHost(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host ?: return null
        return runCatching {
            IDN.toASCII(host)
                .trimEnd('.')
                .lowercase(Locale.US)
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private val allowedHosts = mapOf(
        ProviderId.CLAUDE to listOf("claude.ai", "www.claude.ai"),
        ProviderId.CODEX to listOf("chatgpt.com", "auth.openai.com", "platform.openai.com"),
        ProviderId.GEMINI to listOf("gemini.google.com", "accounts.google.com"),
        ProviderId.COPILOT to listOf("github.com", "githubcopilot.com"),
        ProviderId.CURSOR to listOf("cursor.com", "www.cursor.com")
    )
}
