package com.aiquota.mobile.providers

import android.content.Context
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageSnapshot
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

data class GlmUsageResult(
    val payload: String?,
    val requiresAuth: Boolean,
    val diagnostic: String
)

object GlmProviderUrls {
    const val WEB_OAUTH_URL = "https://z.ai/manage-apikey/coding-plan/personal/my-plan"
    const val WEB_USAGE_URL = "https://z.ai/manage-apikey/coding-plan/personal/usage"
    const val API_QUOTA_URL = "https://api.z.ai/api/monitor/usage/quota/limit"
    val WEB_COOKIE_URLS = listOf(
        "https://z.ai",
        "https://www.z.ai",
        "https://chat.z.ai",
        WEB_OAUTH_URL,
        WEB_USAGE_URL,
        API_QUOTA_URL
    )
}

object GlmNoSubscriptionPolicy {
    const val ERROR_KIND = "glm_no_subscription"
    const val MESSAGE = "You don't have any subscription"
    const val PLAN_LABEL = "Plan 없음"

    fun isNoSubscriptionMessage(value: String?): Boolean {
        return value.orEmpty().contains(MESSAGE, ignoreCase = true)
    }

    fun isNoSubscriptionSnapshot(snapshot: ProviderUsageSnapshot): Boolean {
        return snapshot.providerId == ProviderId.GLM &&
            snapshot.lines.isEmpty() &&
            (snapshot.planLabel == PLAN_LABEL || isNoSubscriptionMessage(snapshot.message))
    }
}

enum class GlmConnectionMode(val storageValue: String) {
    API_KEY("api_key"),
    WEB_OAUTH("web_oauth");

    companion object {
        fun fromStorageValue(value: String?): GlmConnectionMode {
            return entries.firstOrNull { it.storageValue == value } ?: API_KEY
        }
    }
}

class GlmConnectionModeStore(context: Context) {
    private val secureStore = SecureStringStore(context.applicationContext, STORE_NAME)

    fun mode(): GlmConnectionMode {
        return GlmConnectionMode.fromStorageValue(secureStore.getString(KEY_MODE))
    }

    fun save(mode: GlmConnectionMode) {
        secureStore.putString(KEY_MODE, mode.storageValue)
    }

    fun clear() {
        secureStore.remove(KEY_MODE)
    }

    private companion object {
        const val STORE_NAME = "ai_quota_glm_connection"
        const val KEY_MODE = "mode"
    }
}

class GlmApiKeyStore(context: Context) {
    private val secureStore = SecureStringStore(context.applicationContext, STORE_NAME)

    fun apiKey(): String? {
        return secureStore.getString(KEY_API_KEY)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun save(apiKey: String) {
        secureStore.putString(KEY_API_KEY, apiKey.trim())
    }

    fun clear() {
        secureStore.remove(KEY_API_KEY)
    }

    private companion object {
        const val STORE_NAME = "ai_quota_glm_api_key"
        const val KEY_API_KEY = "api_key"
    }
}

class GlmWebSessionCookieStore(context: Context) {
    private val secureStore = SecureStringStore(context.applicationContext, STORE_NAME)

    fun cookieHeader(): String? {
        return secureStore.getString(KEY_COOKIE_HEADER)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun save(cookieHeader: String) {
        secureStore.putString(KEY_COOKIE_HEADER, cookieHeader.trim())
    }

    fun clear() {
        secureStore.remove(KEY_COOKIE_HEADER)
    }

    private companion object {
        const val STORE_NAME = "ai_quota_glm_web_session"
        const val KEY_COOKIE_HEADER = "cookie_header"
    }
}

class GlmWebSessionFallbackGate(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun canRunFallback(
        automaticRefresh: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!automaticRefresh) return true
        val lastAttemptAt = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L)
        return lastAttemptAt <= 0L ||
            nowMillis - lastAttemptAt >= AUTO_FALLBACK_MIN_INTERVAL_MS
    }

    fun remainingDelayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val lastAttemptAt = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0L)
        if (lastAttemptAt <= 0L) return 0L
        return (AUTO_FALLBACK_MIN_INTERVAL_MS - (nowMillis - lastAttemptAt)).coerceAtLeast(0L)
    }

    fun recordFallbackAttempt(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_ATTEMPT_AT, nowMillis).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val AUTO_FALLBACK_MIN_INTERVAL_MS = 10 * 60 * 1_000L
        private const val PREFS = "ai_quota_glm_web_session_fallback"
        private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"
    }
}

class GlmUsageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = GlmApiKeyStore(appContext)
    private val modeStore = GlmConnectionModeStore(appContext)
    private val webSessionCookieStore = GlmWebSessionCookieStore(appContext)
    private val fallbackGate = GlmWebSessionFallbackGate(appContext)

    fun saveApiKey(apiKey: String) {
        store.save(apiKey)
        modeStore.save(GlmConnectionMode.API_KEY)
    }

    fun useWebOAuth() {
        modeStore.save(GlmConnectionMode.WEB_OAUTH)
    }

    fun saveWebSessionCookieHeader(cookieHeader: String?) {
        val trimmed = cookieHeader?.trim()?.takeIf { it.isNotBlank() } ?: return
        webSessionCookieStore.save(trimmed)
        modeStore.save(GlmConnectionMode.WEB_OAUTH)
    }

    fun connectionMode(): GlmConnectionMode {
        return modeStore.mode()
    }

    fun clear() {
        store.clear()
        webSessionCookieStore.clear()
        modeStore.clear()
        fallbackGate.clear()
    }

    fun fetchUsagePayloadFromStoredCredential(): GlmUsageResult {
        val apiKey = store.apiKey()
            ?: return GlmUsageResult(null, requiresAuth = true, diagnostic = "glm_api_key_missing")
        return GlmUsageFetcher.fetchUsagePayload(apiKey)
    }

    fun fetchUsagePayloadFromWebSession(): GlmUsageResult {
        val cookieHeader = webSessionCookieStore.cookieHeader()
            ?: return GlmUsageResult(null, requiresAuth = false, diagnostic = "glm_web_cookie_missing")
        return GlmUsageFetcher.fetchUsagePayloadWithCookie(cookieHeader)
    }
}

object GlmUsageFetcher {
    fun fetchUsagePayload(apiKey: String): GlmUsageResult {
        return fetchUsagePayload(apiKey, GlmProviderUrls.API_QUOTA_URL)
    }

    internal fun fetchUsagePayload(apiKey: String, endpointUrl: String): GlmUsageResult {
        val bearerResult = executeFetch(apiKey, endpointUrl, authorizationHeader = "Bearer ${apiKey.trim()}")
        if (bearerResult.shouldRetryWithoutBearer() || bearerResult.isMcpOnlyQuotaPayload()) {
            val rawResult = executeFetch(apiKey, endpointUrl, authorizationHeader = apiKey)
            if (rawResult.hasTokenQuotaPayload() || bearerResult.payload == null) return rawResult
        }
        return bearerResult
    }

    private fun executeFetch(apiKey: String, endpointUrl: String, authorizationHeader: String): GlmUsageResult {
        return executeFetch(endpointUrl, accountLabel = maskApiKey(apiKey)) {
            setRequestProperty("Authorization", authorizationHeader)
        }
    }

    fun fetchUsagePayloadWithCookie(cookieHeader: String): GlmUsageResult {
        return fetchUsagePayloadWithCookie(cookieHeader, GlmProviderUrls.API_QUOTA_URL)
    }

    internal fun fetchUsagePayloadWithCookie(cookieHeader: String, endpointUrl: String): GlmUsageResult {
        val trimmedCookieHeader = cookieHeader.trim()
        if (trimmedCookieHeader.isBlank()) {
            return GlmUsageResult(null, requiresAuth = false, diagnostic = "glm_web_cookie_missing")
        }
        return executeFetch(endpointUrl, accountLabel = "z.ai web session") {
            setRequestProperty("Cookie", trimmedCookieHeader)
            setRequestProperty("Origin", "https://z.ai")
            setRequestProperty("Referer", GlmProviderUrls.WEB_USAGE_URL)
            setRequestProperty("User-Agent", WEB_SESSION_USER_AGENT)
        }
    }

    private fun executeFetch(
        endpointUrl: String,
        accountLabel: String,
        configureConnection: HttpURLConnection.() -> Unit
    ): GlmUsageResult {
        return runCatching {
            val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "AIQuotaMobile/1.0")
                configureConnection()
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (status == 401 || status == 403) {
                return GlmUsageResult(null, requiresAuth = true, diagnostic = "glm_auth_required")
            }
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: return GlmUsageResult(null, requiresAuth = false, diagnostic = "glm_invalid_json")
            if (json.looksLikeAuthFailure()) {
                return GlmUsageResult(null, requiresAuth = true, diagnostic = "glm_auth_required")
            }
            if (status !in 200..299) {
                return GlmUsageResult(null, requiresAuth = false, diagnostic = "glm_http_$status")
            }
            if (!json.hasGlmQuotaLimits()) {
                return GlmUsageResult(null, requiresAuth = false, diagnostic = json.glmQuotaDiagnostic())
            }
            GlmUsageResult(
                json
                    .put("provider", "glm")
                    .put("account", accountLabel)
                    .toString(),
                requiresAuth = false,
                diagnostic = "ok"
            )
        }.getOrElse { error ->
            GlmUsageResult(null, requiresAuth = false, diagnostic = "glm_${error.javaClass.simpleName}")
        }
    }

    private fun GlmUsageResult.shouldRetryWithoutBearer(): Boolean {
        return requiresAuth || diagnostic == "glm_http_400" || diagnostic == "glm_http_404"
    }

    private fun GlmUsageResult.hasTokenQuotaPayload(): Boolean {
        val json = payload?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        return json.hasGlmQuotaLimitType("TOKENS_LIMIT")
    }

    private fun GlmUsageResult.isMcpOnlyQuotaPayload(): Boolean {
        val json = payload?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        return json.hasGlmQuotaLimitType("TIME_LIMIT") && !json.hasGlmQuotaLimitType("TOKENS_LIMIT")
    }

    private fun JSONObject.looksLikeAuthFailure(): Boolean {
        val code = opt("code")?.toString().orEmpty().lowercase()
        val message = optString("msg").ifBlank { optString("message") }.lowercase()
        return code in setOf("401", "403", "1001", "1002") ||
            message.contains("auth") ||
            message.contains("unauthorized") ||
            message.contains("invalid")
    }

    private fun JSONObject.hasGlmQuotaLimits(): Boolean {
        return (optJSONObject("data") ?: this).optJSONArray("limits")?.length()?.let { it > 0 } == true
    }

    private fun JSONObject.hasGlmQuotaLimitType(type: String): Boolean {
        val limits = (optJSONObject("data") ?: this).optJSONArray("limits") ?: return false
        for (index in 0 until limits.length()) {
            if (limits.optJSONObject(index)?.optString("type") == type) return true
        }
        return false
    }

    private fun JSONObject.glmQuotaDiagnostic(): String {
        val code = opt("code")?.toString().orEmpty()
        val message = optString("msg").ifBlank { optString("message") }.lowercase()
        return when {
            code == "1309" || message.contains("coding plan") && message.contains("expired") ->
                "glm_coding_plan_unavailable"
            code == "1310" || message.contains("limit exhausted") ->
                "glm_quota_exhausted"
            message.contains("subscription") || message.contains("plan") ->
                "glm_no_coding_plan_quota"
            else -> "glm_missing_quota_limits"
        }
    }

    private fun maskApiKey(apiKey: String): String {
        val trimmed = apiKey.trim()
        if (trimmed.length <= 8) return "****"
        return trimmed.take(4) + "****" + trimmed.takeLast(4)
    }

    private const val NETWORK_TIMEOUT_MS = 10_000
    private const val WEB_SESSION_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
}
