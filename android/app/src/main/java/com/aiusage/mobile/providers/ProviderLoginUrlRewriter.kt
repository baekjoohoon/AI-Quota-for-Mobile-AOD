package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object ProviderLoginUrlRewriter {
    fun rewriteMainFrameUrl(providerId: ProviderId, url: String): String? {
        if (providerId != ProviderId.CLAUDE) return null
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.host.orEmpty().isAccountsGoogleHost()) return null
        if (uri.rawPath.orEmpty().contains("AccountChooser", ignoreCase = true)) return null
        if (!uri.looksLikeGoogleOAuthStart()) return null
        if (
            uri.promptTokens().any { it.equals("select_account", ignoreCase = true) } &&
            !uri.hasAccountPinningParameter()
        ) {
            return null
        }

        return uri.withPromptSelectAccount()
    }

    private fun URI.looksLikeGoogleOAuthStart(): Boolean {
        val path = rawPath.orEmpty().lowercase(Locale.US)
        val query = rawQuery.orEmpty().lowercase(Locale.US)
        return (
            path == "/o/oauth2/v2/auth" ||
                path == "/o/oauth2/auth"
            ) &&
            "client_id=" in query &&
            "redirect_uri=" in query
    }

    private fun URI.withPromptSelectAccount(): String {
        val promptTokens = promptTokens()
            .filterNot { it.equals("none", ignoreCase = true) }
            .filterNot { it.equals("select_account", ignoreCase = true) }
        val nextPrompt = listOf("select_account") + promptTokens
        val queryParts = rawQuery.orEmpty()
            .split("&")
            .filter { it.isNotBlank() }
            .filterNot { part ->
                val key = part.substringBefore("=")
                key.equals("prompt", ignoreCase = true) || key.isAccountPinningParameter()
            } + "prompt=${nextPrompt.joinToString(" ").urlEncode()}"

        return buildString {
            append(scheme)
            append("://")
            append(rawAuthority)
            append(rawPath.orEmpty().ifBlank { "/" })
            if (queryParts.isNotEmpty()) {
                append("?")
                append(queryParts.joinToString("&"))
            }
            rawFragment?.let { fragment ->
                append("#")
                append(fragment)
            }
        }
    }

    private fun URI.promptTokens(): List<String> {
        return rawQuery.orEmpty()
            .split("&")
            .asSequence()
            .mapNotNull { part ->
                val separator = part.indexOf("=")
                if (separator < 0) return@mapNotNull null
                val key = part.substring(0, separator)
                if (!key.equals("prompt", ignoreCase = true)) return@mapNotNull null
                part.substring(separator + 1).urlDecode()
            }
            .flatMap { prompt -> prompt.split(Regex("""\s+""")).asSequence() }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun URI.hasAccountPinningParameter(): Boolean {
        return rawQuery.orEmpty()
            .split("&")
            .any { part ->
                part.substringBefore("=").isAccountPinningParameter()
            }
    }

    private fun String.isAccountPinningParameter(): Boolean {
        return equals("login_hint", ignoreCase = true) ||
            equals("authuser", ignoreCase = true) ||
            equals("hd", ignoreCase = true)
    }

    private fun String.isAccountsGoogleHost(): Boolean {
        val normalized = lowercase(Locale.US)
        return normalized == "accounts.google.com" ||
            normalized.endsWith(".accounts.google.com")
    }

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }

    private fun String.urlDecode(): String {
        return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    }
}
