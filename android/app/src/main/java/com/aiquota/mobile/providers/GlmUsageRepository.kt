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

class GlmUsageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = GlmApiKeyStore(appContext)
    private val modeStore = GlmConnectionModeStore(appContext)

    fun saveApiKey(apiKey: String) {
        store.save(apiKey)
        modeStore.save(GlmConnectionMode.API_KEY)
    }

    fun useWebOAuth() {
        modeStore.save(GlmConnectionMode.WEB_OAUTH)
    }

    fun connectionMode(): GlmConnectionMode {
        return modeStore.mode()
    }

    fun clear() {
        store.clear()
        modeStore.clear()
    }

    fun fetchUsagePayloadFromStoredCredential(): GlmUsageResult {
        val apiKey = store.apiKey()
            ?: return GlmUsageResult(null, requiresAuth = true, diagnostic = "glm_api_key_missing")
        return GlmUsageFetcher.fetchUsagePayload(apiKey)
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
        return runCatching {
            val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", authorizationHeader)
                setRequestProperty("User-Agent", "AIQuotaMobile/1.0")
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
                    .put("account", maskApiKey(apiKey))
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
}
