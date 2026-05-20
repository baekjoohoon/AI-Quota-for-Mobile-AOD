package com.aiusage.mobile.providers

import android.webkit.CookieManager
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

object CursorNativeUsageFetcher {
    fun fetchJson(url: String, body: String?): String {
        val endpoint = cursorEndpoint(url)
            ?: return JSONObject().put("ok", false).put("error", "blocked_cursor_endpoint").toString()
        return runCatching {
            val postBody = body.orEmpty().takeIf { it.isNotBlank() }
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                requestMethod = if (postBody == null) "GET" else "POST"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ProviderWebViewUserAgent.loginUserAgent())
                if (endpoint.startsWith("/aiserver.")) {
                    setRequestProperty("Connect-Protocol-Version", "1")
                }
                cursorCookieHeader()?.let { setRequestProperty("Cookie", it) }
                if (postBody != null) {
                    doOutput = true
                    OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { it.write(postBody) }
                }
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

    private fun cursorCookieHeader(): String? {
        val values = listOf(
            CookieManager.getInstance().getCookie("https://cursor.com"),
            CookieManager.getInstance().getCookie("https://www.cursor.com"),
            CookieManager.getInstance().getCookie("https://api2.cursor.sh")
        ).mapNotNull { it?.takeIf(String::isNotBlank) }
        return values.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private fun cursorEndpoint(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty()
        val allowed = when (host) {
            "cursor.com", "www.cursor.com" -> path in setOf(
                "/api/auth/stripe",
                "/api/usage",
                "/api/auth/usage",
                "/api/usage-summary",
                "/api/dashboard/get-credit-grants-balance"
            )
            "api2.cursor.sh" -> path in setOf(
                "/aiserver.v1.DashboardService/GetCurrentPeriodUsage",
                "/aiserver.v1.DashboardService/GetPlanInfo",
                "/aiserver.v1.DashboardService/GetCreditGrantsBalance",
                "/auth/usage"
            )
            else -> false
        }
        return if (allowed) path else null
    }

    private const val NETWORK_TIMEOUT_MS = 20_000
}
