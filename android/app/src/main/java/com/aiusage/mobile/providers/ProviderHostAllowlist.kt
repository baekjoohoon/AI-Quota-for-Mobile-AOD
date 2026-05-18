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
        ProviderId.CLAUDE to listOf(
            "claude.ai",
            "www.claude.ai",
            "accounts.google.com",
            "accounts.youtube.com",
            "myaccount.google.com",
            "appleid.apple.com"
        ),
        ProviderId.CODEX to listOf(
            "chatgpt.com",
            "chat.openai.com",
            "auth.openai.com",
            "accounts.openai.com",
            "login.openai.com",
            "accounts.google.com",
            "myaccount.google.com",
            "appleid.apple.com",
            "login.microsoftonline.com"
        ),
        ProviderId.GEMINI to listOf(
            "gemini.google.com",
            "gemini.google",
            "one.google.com",
            "accounts.google.com",
            "myaccount.google.com"
        ),
        ProviderId.COPILOT to listOf("github.com", "githubcopilot.com"),
        ProviderId.CURSOR to listOf(
            "cursor.com",
            "www.cursor.com",
            "api.workos.com",
            "api2.cursor.sh",
            "authenticate.cursor.sh",
            "authenticator.cursor.sh",
            "accounts.google.com",
            "github.com",
            "appleid.apple.com"
        )
    )
}
