package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import java.net.URI
import java.util.Locale

internal const val MAX_LOGIN_NAVIGATION_RECOVERY_ATTEMPTS = 2

internal fun isProviderOAuthCallbackNavigation(providerId: ProviderId, url: String?): Boolean {
    return when (providerId) {
        ProviderId.CODEX -> CodexOAuthRepository.isCallbackUrl(url)
        ProviderId.GEMINI -> GeminiCliOAuthRepository.isCallbackUrl(url)
        else -> false
    }
}

internal fun shouldRecoverLoginNavigationError(
    providerId: ProviderId,
    url: String?,
    attempt: Int
): Boolean {
    if (attempt >= MAX_LOGIN_NAVIGATION_RECOVERY_ATTEMPTS) return false
    if (isProviderOAuthCallbackNavigation(providerId, url)) return true

    val host = normalizedHttpsHost(url) ?: return false
    return when (providerId) {
        ProviderId.CLAUDE -> host.isGoogleAuthHost()
        ProviderId.CODEX -> host.isOpenAiAuthHost() || host.isGoogleAuthHost() || host.isMicrosoftLoginHost()
        ProviderId.GEMINI -> host.isGoogleAuthHost()
        ProviderId.COPILOT -> host == "github.com" || host.endsWith(".github.com")
        ProviderId.CURSOR -> host.isGoogleAuthHost() || host.isCursorAuthHost() || host.isWorkosHost()
    }
}

internal fun loginRecoveryUrlFor(providerId: ProviderId): String {
    return when (providerId) {
        ProviderId.CLAUDE -> "https://claude.ai/"
        ProviderId.CODEX -> "https://chatgpt.com/"
        ProviderId.GEMINI -> "https://gemini.google.com/app"
        ProviderId.COPILOT -> "https://github.com/settings/copilot"
        ProviderId.CURSOR -> "https://cursor.com/dashboard"
    }
}

private fun normalizedHttpsHost(url: String?): String? {
    val uri = runCatching { URI(url.orEmpty().trim()) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    return uri.host
        ?.trimEnd('.')
        ?.lowercase(Locale.US)
        ?.takeIf { it.isNotBlank() }
}

private fun String.isGoogleAuthHost(): Boolean {
    return this == "accounts.google.com" ||
        endsWith(".accounts.google.com") ||
        this == "myaccount.google.com" ||
        endsWith(".myaccount.google.com") ||
        this == "accounts.youtube.com" ||
        endsWith(".accounts.youtube.com")
}

private fun String.isOpenAiAuthHost(): Boolean {
    return this == "auth.openai.com" ||
        this == "accounts.openai.com" ||
        this == "login.openai.com"
}

private fun String.isMicrosoftLoginHost(): Boolean {
    return this == "login.microsoftonline.com" || endsWith(".login.microsoftonline.com")
}

private fun String.isCursorAuthHost(): Boolean {
    return this == "authenticate.cursor.sh" ||
        this == "authenticator.cursor.sh"
}

private fun String.isWorkosHost(): Boolean {
    return this == "api.workos.com" || endsWith(".api.workos.com")
}
