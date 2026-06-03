package com.aiquota.mobile.providers

import android.content.Context
import android.webkit.WebSettings
import com.aiquota.mobile.local.ProviderId

object ProviderWebViewUserAgent {
    fun loginUserAgent(context: Context): String {
        return sanitizeWebViewUserAgent(WebSettings.getDefaultUserAgent(context))
    }

    fun loginUserAgent(): String {
        return FALLBACK_WEBVIEW_USER_AGENT
    }

    fun hiddenCollectorUserAgent(context: Context, providerId: ProviderId): String {
        return when (providerId) {
            ProviderId.GEMINI -> DESKTOP_CHROME_USER_AGENT
            ProviderId.ANTIGRAVITY -> "antigravity"
            else -> loginUserAgent(context)
        }
    }

    fun hiddenCollectorUserAgent(providerId: ProviderId): String {
        return when (providerId) {
            ProviderId.GEMINI -> DESKTOP_CHROME_USER_AGENT
            ProviderId.ANTIGRAVITY -> "antigravity"
            else -> loginUserAgent()
        }
    }

    private fun sanitizeWebViewUserAgent(userAgent: String): String {
        return userAgent
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
    }

    private const val FALLBACK_WEBVIEW_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"

    private const val DESKTOP_CHROME_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"
}
