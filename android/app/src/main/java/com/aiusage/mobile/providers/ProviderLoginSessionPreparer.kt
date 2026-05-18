package com.aiusage.mobile.providers

import android.util.Log
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebStorage
import com.aiusage.mobile.local.ProviderId

object ProviderLoginSessionPreparer {
    fun prepare(providerId: ProviderId, onReady: () -> Unit) {
        if (providerId != ProviderId.CLAUDE) {
            onReady()
            return
        }

        deleteProviderLoginStorage()
        clearProviderLoginCookies(onReady)
    }

    internal fun claudeCookieDeletionRequestsForTest(existingCookiesByUrl: Map<String, String>): List<CookieDeletionRequest> {
        val names = CLAUDE_COOKIE_URLS
            .flatMap { url -> cookieNames(existingCookiesByUrl[url].orEmpty()) }
            .filterNot { it in PRESERVED_CLAUDE_COOKIE_NAMES }
            .toSet() + CLAUDE_COOKIE_NAMES
        return CLAUDE_COOKIE_URLS.flatMap { url ->
            names.flatMap { name ->
                deletionRequests(url, name, CLAUDE_COOKIE_DOMAINS)
            }
        }
    }

    private fun clearProviderLoginCookies(onReady: () -> Unit) {
        val cookieManager = CookieManager.getInstance()
        val currentClaudeCookies = CLAUDE_COOKIE_URLS.associateWith { url ->
            cookieManager.getCookie(url).orEmpty()
        }
        val requests = claudeCookieDeletionRequestsForTest(currentClaudeCookies)
        var remaining = requests.size
        if (remaining == 0) {
            onReady()
            return
        }
        val callback = ValueCallback<Boolean> {
            remaining -= 1
            if (remaining == 0) {
                cookieManager.flush()
                Log.d(
                    ProviderCollectionDiagnostics.TAG,
                    "login clearedClaudeLoginSession provider=${ProviderId.CLAUDE.storageId} cookies=${requests.size}"
                )
                onReady()
            }
        }
        requests.forEach { request ->
            cookieManager.setCookie(request.url, request.header, callback)
        }
    }

    private fun deleteProviderLoginStorage() {
        CLAUDE_STORAGE_ORIGINS.forEach { origin ->
            WebStorage.getInstance().deleteOrigin(origin)
        }
    }

    private fun deletionRequests(
        url: String,
        name: String,
        domains: List<String?>
    ): List<CookieDeletionRequest> {
        return domains.map { domain ->
            CookieDeletionRequest(
                url = url,
                header = expiredCookieHeader(name = name, domain = domain)
            )
        }
    }

    private fun expiredCookieHeader(name: String, domain: String?): String {
        return buildString {
            append(name)
            append("=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/")
            if (!domain.isNullOrBlank()) {
                append("; Domain=")
                append(domain)
            }
            append("; Secure")
        }
    }

    private fun cookieNames(cookieHeader: String): List<String> {
        return cookieHeader
            .split(";")
            .mapNotNull { part ->
                part.substringBefore("=", missingDelimiterValue = "")
                    .trim()
                    .takeIf { it.isNotBlank() }
            }
    }

    data class CookieDeletionRequest(
        val url: String,
        val header: String
    )

    private val CLAUDE_COOKIE_URLS = listOf(
        "https://claude.ai/",
        "https://www.claude.ai/"
    )

    private val CLAUDE_STORAGE_ORIGINS = listOf(
        "https://claude.ai",
        "https://www.claude.ai"
    )

    private val CLAUDE_COOKIE_DOMAINS = listOf(
        null,
        ".claude.ai",
        "claude.ai",
        "www.claude.ai"
    )

    private val CLAUDE_COOKIE_NAMES = setOf(
        "activitySessionId",
        "lastActiveOrg",
        "sessionKey"
    )

    private val PRESERVED_CLAUDE_COOKIE_NAMES = setOf(
        "cf_clearance",
        "__cf_bm"
    )
}
