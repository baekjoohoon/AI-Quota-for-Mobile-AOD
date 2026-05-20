package com.aiusage.mobile.providers

import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

object CopilotNativeUsageFetcher {
    fun fetchJson(url: String): String {
        val endpoint = copilotEndpoint(url)
            ?: return JSONObject().put("ok", false).put("error", "blocked_copilot_endpoint").toString()
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json, application/json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ProviderWebViewUserAgent.loginUserAgent())
                setRequestProperty("Referer", "https://github.com/settings/copilot")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                copilotCookieHeader()?.let { setRequestProperty("Cookie", it) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            val parsed = runCatching { JSONObject(text) }.getOrNull()
            JSONObject()
                .put("ok", status in 200..299)
                .put("status", status)
                .put("endpoint", endpoint)
                .put("json", parsed ?: JSONObject().put("rawText", text.take(1000)))
                .toString()
        }.getOrElse { error ->
            JSONObject()
                .put("ok", false)
                .put("endpoint", endpoint)
                .put("error", error.javaClass.simpleName)
                .toString()
        }
    }

    private fun copilotCookieHeader(): String? {
        val values = listOf(
            CookieManager.getInstance().getCookie("https://github.com"),
            CookieManager.getInstance().getCookie("https://www.github.com"),
            CookieManager.getInstance().getCookie("https://api.github.com")
        ).mapNotNull { it?.takeIf(String::isNotBlank) }
        return values.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private fun copilotEndpoint(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty()
        val allowed = when (host) {
            "github.com", "www.github.com" -> path == "/github-copilot/chat/entitlement"
            "api.github.com" -> path == "/copilot_internal/user"
            else -> false
        }
        return if (allowed) path else null
    }

    private const val NETWORK_TIMEOUT_MS = 20_000
}
