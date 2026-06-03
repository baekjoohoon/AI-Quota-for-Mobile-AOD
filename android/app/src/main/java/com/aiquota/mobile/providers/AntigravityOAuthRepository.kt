package com.aiquota.mobile.providers

import android.content.Context
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class AntigravityOAuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val secureStore = SecureStringStore(appContext, PREFERENCES)
    private var lastFailureDiagnostic: String? = null

    fun disconnect() {
        secureStore.remove(
            KEY_STATE,
            KEY_VERIFIER,
            KEY_ID_TOKEN,
            KEY_ACCESS_TOKEN,
            KEY_REFRESH_TOKEN,
            KEY_ACCESS_EXPIRES_AT,
            KEY_ACCOUNT_NAME,
            KEY_EMAIL
        )
    }

    fun fetchUsagePayloadWithAccessToken(accessToken: String, email: String?): String? {
        lastFailureDiagnostic = null
        return runCatching {
            val token = accessToken.takeIf { it.isNotBlank() }
                ?: return@runCatching unavailable("Antigravity Google Identity token was unavailable.")
            val loadJson = loadCodeAssist(token)
            val projectId = loadJson?.let(::projectIdFromLoad)
            val response = postCodeAssist(
                method = "fetchAvailableModels",
                payload = fetchAvailableModelsPayload(projectId),
                accessToken = token
            )
            val modelsJson = runCatching { JSONObject(response.body) }.getOrNull()
            Log.i(
                TAG,
                "collection antigravityIdentityUsage fetchAvailableModels status=${response.statusCode} " +
                    "projectPresent=${!projectId.isNullOrBlank()} modelQuotaCount=${modelsJson?.let(::trustedModelQuotaCount) ?: 0}"
            )
            if (!response.isSuccess || modelsJson == null || trustedModelQuotaCount(modelsJson) == 0) {
                lastFailureDiagnostic = if (!response.isSuccess || modelsJson == null) {
                    response.providerDiagnostic("Antigravity", "fetchAvailableModels")
                } else {
                    "Antigravity fetchAvailableModels returned no trusted model quotas."
                }
                return@runCatching null
            }
            structuredPayloadFromAvailableModels(
                modelsJson = modelsJson.toString(),
                account = email ?: secureStore.getString(KEY_EMAIL),
                plan = loadJson?.let(::planLabel)
            )
        }.onFailure { error ->
            lastFailureDiagnostic = "Antigravity Google Identity collection failed with ${error.javaClass.simpleName}."
            Log.w(TAG, "collection antigravityIdentityUsage exception=${error.javaClass.simpleName}")
        }.getOrNull().also { payload ->
            if (payload == null && lastFailureDiagnostic == null) {
                lastFailureDiagnostic = "Antigravity quota payload was not available."
            }
        }
    }

    fun lastFailureDiagnostic(): String? = lastFailureDiagnostic

    fun hasStoredCredential(): Boolean {
        return !secureStore.getString(KEY_REFRESH_TOKEN).isNullOrBlank()
    }

    fun fetchUsagePayloadFromStoredCredential(): String? {
        lastFailureDiagnostic = null
        val accessToken = freshAccessToken()
            ?: return unavailable("Antigravity OAuth refresh token was unavailable.")
        return fetchUsagePayloadWithAccessToken(accessToken, email = secureStore.getString(KEY_EMAIL))
    }

    fun fetchUsagePayloadFromGatewayTokenResult(tokenResult: AntigravityTokenExchangeResult): String? {
        lastFailureDiagnostic = null
        if (!tokenResult.ok) {
            return unavailable("Antigravity Firebase token exchange did not complete.")
        }
        val accessToken = tokenResult.accessToken?.takeIf { it.isNotBlank() }
            ?: return unavailable("Antigravity Firebase token response did not include an access token.")
        saveTokens(
            tokenJson = tokenResult.toJsonObject(),
            accessToken = accessToken,
            refreshToken = tokenResult.refreshToken
        )
        secureStore.remove(KEY_STATE, KEY_VERIFIER)
        Log.i(
            TAG,
            "collection antigravityOAuthUsage firebaseTokenExchange accessToken=true " +
                "refreshToken=${!tokenResult.refreshToken.isNullOrBlank()}"
        )
        return fetchUsagePayloadWithAccessToken(accessToken, email = null)
    }

    private fun loadCodeAssist(accessToken: String): JSONObject? {
        val response = postCodeAssist(
            method = "loadCodeAssist",
            payload = loadCodeAssistPayload(),
            accessToken = accessToken
        )
        Log.i(TAG, "collection antigravityOAuthUsage loadCodeAssist status=${response.statusCode}")
        if (!response.isSuccess) {
            lastFailureDiagnostic = response.providerDiagnostic("Antigravity", "loadCodeAssist")
            Log.w(
                TAG,
                "collection antigravityOAuthUsage loadCodeAssist failed status=${response.statusCode} " +
                    "bodyLength=${response.body.length} error=${response.safeErrorSummary()}"
            )
            return null
        }
        return runCatching { JSONObject(response.body) }.getOrNull()
    }

    private fun unavailable(message: String): String? {
        if (lastFailureDiagnostic == null) {
            lastFailureDiagnostic = message
        }
        return null
    }

    private fun postCodeAssist(method: String, payload: String, accessToken: String): HttpResponse {
        return postJson(
            url = "$CODE_ASSIST_BASE_URL/v1internal:$method",
            body = payload,
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $accessToken",
                "User-Agent" to USER_AGENT
            )
        )
    }

    private fun postJson(url: String, body: String, headers: Map<String, String>): HttpResponse {
        val connection = openConnection(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(body)
        }
        return connection.response()
    }

    private fun freshAccessToken(): String? {
        val now = System.currentTimeMillis()
        val storedAccessToken = secureStore.getString(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }
        val expiresAt = secureStore.getLong(KEY_ACCESS_EXPIRES_AT, 0L)
        if (!storedAccessToken.isNullOrBlank() && expiresAt > now + TOKEN_EXPIRY_SKEW_MILLIS) {
            return storedAccessToken
        }
        val refreshToken = secureStore.getString(KEY_REFRESH_TOKEN)?.takeIf { it.isNotBlank() }
            ?: return null
        val tokenResult = runCatching {
            runBlocking {
                AntigravityFirebaseGateway(appContext).refreshAccessToken(refreshToken)
            }
        }.onFailure { error ->
            lastFailureDiagnostic = "Antigravity Firebase token refresh failed with ${error.javaClass.simpleName}."
            Log.w(TAG, "collection antigravityOAuthUsage firebaseTokenRefresh exception=${error.javaClass.simpleName}")
        }.getOrNull() ?: return null
        if (!tokenResult.ok) {
            lastFailureDiagnostic = "Antigravity Firebase token refresh did not complete."
            return null
        }
        val accessToken = tokenResult.accessToken?.takeIf { it.isNotBlank() }
            ?: return unavailable("Antigravity Firebase token refresh did not include an access token.")
        saveTokens(
            tokenJson = tokenResult.toJsonObject(),
            accessToken = accessToken,
            refreshToken = tokenResult.refreshToken ?: refreshToken
        )
        return accessToken
    }

    private fun saveTokens(tokenJson: JSONObject, accessToken: String, refreshToken: String?) {
        secureStore.putString(KEY_ACCESS_TOKEN, accessToken)
        secureStore.putString(KEY_REFRESH_TOKEN, refreshToken)
        tokenJson.optNullableString("id_token")?.let { secureStore.putString(KEY_ID_TOKEN, it) }
        secureStore.putLong(
            KEY_ACCESS_EXPIRES_AT,
            System.currentTimeMillis() + tokenJson.optLong("expires_in", 0L) * 1000L
        )
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            instanceFollowRedirects = false
        }
    }

    private fun HttpURLConnection.response(): HttpResponse {
        val status = responseCode
        val stream = if (status in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        disconnect()
        return HttpResponse(status, body)
    }

    private data class HttpResponse(val statusCode: Int, val body: String) {
        val isSuccess: Boolean = statusCode in 200..299
    }

    private fun HttpResponse.safeErrorSummary(): String {
        return GoogleApiErrorDiagnostics.safeSummary(statusCode, body)
    }

    private fun HttpResponse.providerDiagnostic(providerName: String, method: String): String {
        return GoogleApiErrorDiagnostics.providerDiagnostic(providerName, method, statusCode, body)
    }

    companion object {
        private const val CODE_ASSIST_BASE_URL = "https://daily-cloudcode-pa.googleapis.com"
        private const val LOOPBACK_REDIRECT_URI = "http://127.0.0.1:8080/callback"
        private const val PREFERENCES = "ai_quota_antigravity_oauth"
        private const val KEY_STATE = "state"
        private const val KEY_VERIFIER = "code_verifier"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_EMAIL = "email"
        private const val USER_AGENT = "antigravity/2.0.0 android/unknown"
        private const val CLIENT_VERSION = "2.0.0"
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val TOKEN_EXPIRY_SKEW_MILLIS = 5 * 60_000L
        private const val TAG = "AIQuotaAntigravity"
        fun isLoopbackOAuthCallback(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host.orEmpty().lowercase(Locale.US)
            return (host == "127.0.0.1" || host == "localhost") &&
                uri.port == 8080 &&
                uri.path == "/callback"
        }

        fun loadCodeAssistPayload(): String {
            return JSONObject()
                .put(
                    "metadata",
                    JSONObject()
                        .put("ideName", "antigravity")
                        .put("ideType", "ANTIGRAVITY")
                        .put("ideVersion", CLIENT_VERSION)
                        .put("pluginVersion", CLIENT_VERSION)
                        .put("platform", "PLATFORM_UNSPECIFIED")
                        .put("updateChannel", "stable")
                        .put("pluginType", "GEMINI")
                )
                .put("mode", "FULL_ELIGIBILITY_CHECK")
                .toString()
        }

        fun fetchAvailableModelsPayload(projectId: String?): String {
            return JSONObject().apply {
                if (!projectId.isNullOrBlank()) put("project", projectId)
            }.toString()
        }

        fun structuredPayloadFromAvailableModels(modelsJson: String, account: String?, plan: String?): String? {
            val root = runCatching { JSONObject(modelsJson) }.getOrNull() ?: return null
            if (trustedModelQuotaCount(root) == 0) return null
            return JSONObject()
                .put("provider", "antigravity")
                .put("models", root.optJSONObject("models") ?: root.optJSONArray("models") ?: JSONObject.NULL)
                .apply {
                    if (!account.isNullOrBlank()) put("account", account)
                    if (!plan.isNullOrBlank()) put("plan", plan)
                }
                .toString()
        }

        internal fun trustedModelQuotaCount(root: JSONObject): Int {
            val models = root.opt("models") ?: return 0
            var count = 0
            fun visit(modelId: String, model: JSONObject?) {
                if (model == null) return
                val label = model.optNullableString("displayName")
                    ?: model.optNullableString("display_name")
                    ?: model.optNullableString("name")
                    ?: model.optNullableString("model")
                    ?: modelId
                if (label.isBlank() || model.optBoolean("isInternal", false)) return
                val quota = model.optJSONObject("quotaInfo") ?: model.optJSONObject("quota") ?: model
                val remaining = quota.optDouble("remainingFraction", Double.NaN)
                if (!remaining.isNaN()) count += 1
            }
            when (models) {
                is JSONObject -> {
                    val keys = models.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        visit(key, models.optJSONObject(key))
                    }
                }
                is JSONArray -> {
                    for (index in 0 until models.length()) {
                        visit(index.toString(), models.optJSONObject(index))
                    }
                }
            }
            return count
        }

        private fun projectIdFromLoad(loadJson: JSONObject): String? {
            return loadJson.optNullableString("cloudaicompanionProject")
                ?: loadJson.optJSONObject("cloudaicompanionProject")?.optNullableString("id")
                ?: loadJson.optNullableString("project")
                ?: loadJson.optJSONObject("project")?.optNullableString("id")
        }

        private fun planLabel(loadJson: JSONObject): String? {
            return listOf(
                loadJson.optJSONObject("paidTier")?.let(::planFromObject),
                loadJson.optJSONObject("currentTier")?.let(::planFromObject),
                loadJson.optJSONObject("tier")?.let(::planFromObject),
                loadJson.optJSONObject("plan")?.let(::planFromObject),
                findPlanValue(loadJson, 0)
            ).firstOrNull { !it.isNullOrBlank() }
        }

        private fun planFromObject(json: JSONObject): String? {
            return json.optNullableString("displayName")
                ?: json.optNullableString("display_name")
                ?: json.optNullableString("name")
                ?: json.optNullableString("id")
        }

        private fun findPlanValue(value: Any?, depth: Int): String? {
            if (value == null || depth > 6) return null
            if (value is JSONObject) {
                listOf("plan", "planName", "planType", "subscriptionTier", "tier").forEach { key ->
                    value.optNullableString(key)?.takeIf(::looksLikePlan)?.let { return it }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.contains("token", ignoreCase = true)) continue
                    findPlanValue(value.opt(key), depth + 1)?.let { return it }
                }
            } else if (value is JSONArray) {
                for (index in 0 until value.length()) {
                    findPlanValue(value.opt(index), depth + 1)?.let { return it }
                }
            }
            return null
        }

        private fun looksLikePlan(value: String): Boolean {
            val compact = value.lowercase(Locale.US)
            return compact.contains("google ai") ||
                compact.contains("antigravity") ||
                compact in setOf("free", "plus", "pro", "ultra")
        }

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        }

        private fun URI.queryParam(name: String): String? {
            val query = rawQuery ?: return null
            return query.split("&")
                .firstOrNull { it.substringBefore("=") == name }
                ?.substringAfter("=", "")
                ?.takeIf { it.isNotBlank() }
                ?.let { java.net.URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        }

    }
}
