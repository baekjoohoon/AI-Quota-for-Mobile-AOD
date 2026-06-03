package com.aiquota.mobile.providers

import android.content.Context
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class GeminiCliOAuthRepository(context: Context) {
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
                ?: return@runCatching unavailable("Gemini Google Identity token was unavailable.")
            val setup = setupCodeAssist(token)
                ?: return@runCatching unavailable("Gemini Code Assist setup was unavailable.")
            val response = postCodeAssist(
                method = "retrieveUserQuota",
                payload = retrieveUserQuotaPayload(setup.projectId),
                accessToken = token
            )
            val quotaJson = runCatching { JSONObject(response.body) }.getOrNull()
            logCollection(
                "retrieveUserQuota status=${response.statusCode} projectPresent=${setup.projectId.isNotBlank()} " +
                    "bucketCount=${quotaJson?.let(::trustedBucketCount) ?: 0}"
            )
            if (!response.isSuccess || quotaJson == null) {
                lastFailureDiagnostic = response.providerDiagnostic("Gemini", "retrieveUserQuota")
                logHttpFailure("retrieveUserQuota", response)
                return@runCatching null
            }
            if (trustedBucketCount(quotaJson) == 0) {
                return@runCatching unavailable("Gemini retrieveUserQuota returned no quota buckets.")
            }
            structuredPayloadFromCodeAssist(
                loadJson = setup.loadJson,
                quotaJson = quotaJson,
                email = email ?: secureStore.getString(KEY_EMAIL)
            )
        }.onFailure { error ->
            lastFailureDiagnostic = "Gemini Google Identity collection failed with ${error.javaClass.simpleName}."
            Log.w("AIQuotaGemini", "collection geminiIdentityUsage exception=${error.javaClass.simpleName}")
        }.getOrNull().also { payload ->
            if (payload == null && lastFailureDiagnostic == null) {
                lastFailureDiagnostic = "Gemini quota payload was not available."
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
            ?: return unavailable("Gemini CLI OAuth refresh token was unavailable.")
        return fetchUsagePayloadWithAccessToken(accessToken, email = secureStore.getString(KEY_EMAIL))
    }

    fun fetchUsagePayloadFromGatewayTokenResult(tokenResult: GeminiCliTokenExchangeResult): String? {
        lastFailureDiagnostic = null
        if (!tokenResult.ok) {
            return unavailable("Gemini Firebase token exchange did not complete.")
        }
        val accessToken = tokenResult.accessToken?.takeIf { it.isNotBlank() }
            ?: return unavailable("Gemini Firebase token response did not include an access token.")
        secureStore.putString(KEY_ACCESS_TOKEN, accessToken)
        secureStore.putString(KEY_REFRESH_TOKEN, tokenResult.refreshToken)
        secureStore.putString(KEY_ID_TOKEN, tokenResult.idToken)
        tokenResult.expiresInSeconds?.let { expiresInSeconds ->
            secureStore.putLong(
                KEY_ACCESS_EXPIRES_AT,
                System.currentTimeMillis() + expiresInSeconds * 1000L
            )
        }
        secureStore.remove(KEY_STATE, KEY_VERIFIER)
        Log.i(
            "AIQuotaGemini",
            "collection geminiCliOAuthUsage firebaseTokenExchange accessToken=true " +
                "refreshToken=${!tokenResult.refreshToken.isNullOrBlank()}"
        )
        return fetchUsagePayloadWithAccessToken(accessToken, email = null)
    }

    private fun unavailable(message: String): String? {
        if (lastFailureDiagnostic == null) {
            lastFailureDiagnostic = message
        }
        return null
    }

    private fun setupCodeAssist(accessToken: String): SetupResult? {
        val initialLoad = loadCodeAssist(
            projectId = null,
            accessToken = accessToken
        )
            ?: return null
        projectIdFromLoad(initialLoad)?.let { projectId ->
            return SetupResult(projectId = projectId, loadJson = initialLoad)
        }

        val onboardProjectId = onboardUser(initialLoad, accessToken) ?: return null
        val refreshedLoad = loadCodeAssist(
            projectId = onboardProjectId,
            accessToken = accessToken
        ) ?: initialLoad
        return SetupResult(
            projectId = projectIdFromLoad(refreshedLoad) ?: onboardProjectId,
            loadJson = refreshedLoad
        )
    }

    private fun loadCodeAssist(
        projectId: String?,
        accessToken: String
    ): JSONObject? {
        val response = postCodeAssist(
            method = "loadCodeAssist",
            payload = loadCodeAssistPayload(projectId),
            accessToken = accessToken
        )
        logCollection("loadCodeAssist status=${response.statusCode} projectPresent=${!projectId.isNullOrBlank()}")
        if (!response.isSuccess) {
            lastFailureDiagnostic = response.providerDiagnostic("Gemini", "loadCodeAssist")
            logHttpFailure("loadCodeAssist", response)
            return null
        }
        return runCatching { JSONObject(response.body) }.getOrNull()
    }

    private fun onboardUser(
        loadJson: JSONObject,
        accessToken: String
    ): String? {
        val tier = defaultAllowedTier(loadJson) ?: return null
        val tierId = tier.optNullableString("id") ?: return null
        val payload = onboardUserPayload(tierId = tierId, projectId = null)
        var response = postCodeAssist(
            method = "onboardUser",
            payload = payload,
            accessToken = accessToken
        )
        logCollection("onboardUser status=${response.statusCode} tierPresent=true")
        if (!response.isSuccess) {
            lastFailureDiagnostic = response.providerDiagnostic("Gemini", "onboardUser")
            logHttpFailure("onboardUser", response)
            return null
        }

        var operation = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        repeat(OPERATION_POLL_ATTEMPTS) {
            if (operation.optBoolean("done", false)) return projectIdFromOperation(operation)
            val name = operation.optNullableString("name") ?: return null
            Thread.sleep(OPERATION_POLL_DELAY_MS)
            response = getCodeAssistOperation(name, accessToken)
            if (!response.isSuccess) {
                lastFailureDiagnostic = response.providerDiagnostic("Gemini", "getOperation")
                logHttpFailure("getOperation", response)
                return null
            }
            operation = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        }
        return projectIdFromOperation(operation)
    }

    private fun getCodeAssistOperation(name: String, accessToken: String): HttpResponse {
        return getJson(
            url = "$CODE_ASSIST_BASE_URL/$name",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $accessToken",
                "User-Agent" to USER_AGENT
            )
        )
    }

    private fun defaultAllowedTier(loadJson: JSONObject): JSONObject? {
        val allowedTiers = loadJson.optJSONArray("allowedTiers") ?: return null
        var firstTier: JSONObject? = null
        for (index in 0 until allowedTiers.length()) {
            val tier = allowedTiers.optJSONObject(index) ?: continue
            if (firstTier == null) firstTier = tier
            if (tier.optBoolean("isDefault", false)) return tier
        }
        return firstTier
    }

    private fun projectIdFromLoad(loadJson: JSONObject): String? {
        return loadJson.optNullableString("cloudaicompanionProject")
            ?: loadJson.optJSONObject("cloudaicompanionProject")?.optNullableString("id")
    }

    private fun projectIdFromOperation(operation: JSONObject): String? {
        return operation.optJSONObject("response")
            ?.optJSONObject("cloudaicompanionProject")
            ?.optNullableString("id")
    }

    private fun postCodeAssist(method: String, payload: String, accessToken: String): HttpResponse {
        return postJson(
            url = "$CODE_ASSIST_BASE_URL:$method",
            body = payload,
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $accessToken",
                "User-Agent" to USER_AGENT
            )
        )
    }

    private fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse {
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
                GeminiCliFirebaseGateway(appContext).refreshAccessToken(refreshToken)
            }
        }.onFailure { error ->
            lastFailureDiagnostic = "Gemini Firebase token refresh failed with ${error.javaClass.simpleName}."
            Log.w("AIQuotaGemini", "collection geminiCliOAuthUsage firebaseTokenRefresh exception=${error.javaClass.simpleName}")
        }.getOrNull() ?: return null
        if (!tokenResult.ok) {
            lastFailureDiagnostic = "Gemini Firebase token refresh did not complete."
            return null
        }
        val accessToken = tokenResult.accessToken?.takeIf { it.isNotBlank() }
            ?: return unavailable("Gemini Firebase token refresh did not include an access token.")
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

    private fun getJson(url: String, headers: Map<String, String>): HttpResponse {
        val connection = openConnection(url)
        connection.requestMethod = "GET"
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return connection.response()
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

    private data class SetupResult(
        val projectId: String,
        val loadJson: JSONObject
    )

    private fun logHttpFailure(method: String, response: HttpResponse) {
        Log.w(
            "AIQuotaGemini",
            "collection geminiCliOAuthUsage $method failed status=${response.statusCode} " +
                "bodyLength=${response.body.length} error=${response.safeErrorSummary()}"
        )
    }

    private fun logCollection(message: String) {
        Log.i("AIQuotaGemini", "collection geminiCliOAuthUsage $message")
    }

    companion object {
        private const val CODE_ASSIST_BASE_URL = "https://cloudcode-pa.googleapis.com/v1internal"
        private const val LOOPBACK_REDIRECT_URI = "http://127.0.0.1:46417/oauth2callback"
        private const val PREFERENCES = "ai_quota_gemini_oauth"
        private const val KEY_STATE = "state"
        private const val KEY_VERIFIER = "code_verifier"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_EMAIL = "email"
        private const val USER_AGENT = "GeminiCLI/0.0.0 (Android; AI Quota Mobile)"
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val TOKEN_EXPIRY_SKEW_MILLIS = 5 * 60_000L
        private const val OPERATION_POLL_ATTEMPTS = 12
        private const val OPERATION_POLL_DELAY_MS = 5_000L
        fun isLoopbackOAuthCallback(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val host = uri.host.orEmpty().lowercase(Locale.US)
            return (host == "127.0.0.1" || host == "localhost") &&
                uri.port == 46417 &&
                uri.path == "/oauth2callback"
        }

        fun usagePayloadOrNull(fetch: () -> String?): String? {
            return runCatching(fetch).getOrNull()
        }

        internal fun loadCodeAssistPayload(projectId: String?): String {
            val metadata = JSONObject()
                .put("ideType", "IDE_UNSPECIFIED")
                .put("platform", "PLATFORM_UNSPECIFIED")
                .put("pluginType", "GEMINI")
            if (!projectId.isNullOrBlank()) metadata.put("duetProject", projectId)
            val payload = JSONObject().put("metadata", metadata)
            if (!projectId.isNullOrBlank()) payload.put("cloudaicompanionProject", projectId)
            return payload.toString()
        }

        internal fun onboardUserPayload(tierId: String, projectId: String?): String {
            val metadata = JSONObject()
                .put("ideType", "IDE_UNSPECIFIED")
                .put("platform", "PLATFORM_UNSPECIFIED")
                .put("pluginType", "GEMINI")
            val payload = JSONObject()
                .put("tierId", tierId)
                .put("metadata", metadata)
            if (!projectId.isNullOrBlank()) {
                metadata.put("duetProject", projectId)
                payload.put("cloudaicompanionProject", projectId)
            }
            return payload.toString()
        }

        internal fun retrieveUserQuotaPayload(projectId: String): String {
            return JSONObject()
                .put("project", projectId)
                .toString()
        }

        internal fun structuredPayloadFromCodeAssist(
            loadJson: JSONObject,
            quotaJson: JSONObject,
            email: String?
        ): String? {
            return normalizeQuotaPayload(
                quotaJson = quotaJson.toString(),
                account = email,
                plan = planLabel(loadJson)
            )
        }

        internal fun trustedBucketCount(quotaJson: JSONObject): Int {
            val buckets = quotaJson.optJSONArray("buckets") ?: return 0
            var count = 0
            for (index in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(index) ?: continue
                if (bucketLabel(bucket.optString("modelId")) == null) continue
                val remaining = bucket.optDouble("remainingFraction", Double.NaN)
                if (!remaining.isNaN()) count += 1
            }
            return count
        }

        fun normalizeQuotaPayload(quotaJson: String, account: String?, plan: String?): String? {
            val buckets = JSONObject(quotaJson).optJSONArray("buckets") ?: return null
            val lines = mutableListOf<Pair<String, JSONObject>>()
            val isFreePlan = normalizeGeminiTier(plan) == "GEMINI_FREE"
            for (index in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(index) ?: continue
                val modelId = bucket.optString("modelId")
                val title = bucketLabel(modelId) ?: continue
                val remaining = bucket.optDouble("remainingFraction", Double.NaN)
                if (remaining.isNaN()) continue
                if (isUnavailableFreePlanProBucket(isFreePlan, modelId, remaining, bucket.optString("resetTime"))) continue
                val remainingPercent = (remaining.coerceIn(0.0, 1.0) * 100.0).roundToInt().coerceIn(0, 100)
                val line = JSONObject()
                    .put("modelId", modelId)
                    .put("title", title)
                    .put("usedPercent", 100 - remainingPercent)
                    .put("remainingPercent", remainingPercent)
                parseResetMillis(bucket.optString("resetTime"))?.let { resetsAtMillis ->
                    line
                        .put("resetsAt", resetsAtMillis)
                        .put("resetText", resetText(resetsAtMillis))
                }
                lines += modelId to line
            }
            if (lines.isEmpty()) return null
            val limits = JSONArray()
            lines.sortedWith(compareBy({ bucketSortOrder(it.first) }, { it.second.optString("title") }))
                .forEach { (_, line) -> limits.put(line) }
            if (limits.length() == 0) return null
            return JSONObject()
                .put("provider", "gemini")
                .apply {
                    if (!plan.isNullOrBlank()) {
                        put("plan", plan)
                    }
                }
                .put("account", account ?: JSONObject.NULL)
                .put("limits", limits)
                .toString()
        }

        fun planFromLoadCodeAssist(responseJson: String): String? {
            val root = runCatching { JSONObject(responseJson) }.getOrNull() ?: return null
            return planLabel(root) ?: findPlanValue(root, 0)
        }

        private fun planLabel(loadJson: JSONObject): String? {
            val paidTier = loadJson.optJSONObject("paidTier")
            val currentTier = loadJson.optJSONObject("currentTier")
            return normalizeGeminiTier(paidTier?.optNullableString("name"))
                ?: normalizeGeminiTier(paidTier?.optNullableString("id"))
                ?: normalizeGeminiTier(currentTier?.optNullableString("name"))
                ?: normalizeGeminiTier(currentTier?.optNullableString("id"))
        }

        private fun findPlanValue(value: Any?, depth: Int): String? {
            if (value == null || depth > 8) return null
            if (value is JSONObject) {
                val directKeys = listOf("tier", "plan", "planType", "subscriptionTier", "userTier")
                directKeys.forEach { key ->
                    normalizeGeminiTier(value.optNullableString(key))?.let { return it }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val raw = value.opt(key)
                    if (key.contains("tier", ignoreCase = true) || key.contains("plan", ignoreCase = true)) {
                        normalizeGeminiTier(raw?.toString())?.let { return it }
                    }
                    findPlanValue(raw, depth + 1)?.let { return it }
                }
            } else if (value is JSONArray) {
                for (index in 0 until value.length()) {
                    findPlanValue(value.opt(index), depth + 1)?.let { return it }
                }
            }
            return null
        }

        private fun normalizeGeminiTier(value: String?): String? {
            val compact = value?.lowercase(Locale.US)?.replace(Regex("[^a-z0-9]+"), "").orEmpty()
            return when (compact) {
                "standardtier", "paid", "googleaipro", "geminipro", "g1protier" -> "GEMINI_PRO"
                "geminicodeassistgoogleoneaipro", "googleoneaipro" -> "GEMINI_PRO"
                "legacytier", "legacy", "geminilegacy" -> "GEMINI_LEGACY"
                "freetier", "free", "workspace", "googleaifree", "geminifree" -> "GEMINI_FREE"
                "googleaiplus", "geminiplus" -> "GEMINI_PLUS"
                "googleaiultra", "geminiultra", "g1ultratier" -> "GEMINI_ULTRA"
                "googleoneaipremium" -> "GOOGLE_ONE_AI_PREMIUM"
                "geminiadvanced" -> "GEMINI_ADVANCED"
                else -> null
            }
        }

        private val GEMINI_MODEL_LABELS = listOf(
            "gemini-3.1-pro-preview" to "3.1 pro-pre",
            "gemini-3.1-flash-lite-preview" to "3.1 flash-lite-pre",
            "gemini-3.1-flash-lite" to "3.1 flash-lite",
            "gemini-3-pro-preview" to "3 pro-pre",
            "gemini-3-flash-preview" to "3 flash-pre",
            "gemini-2.5-pro" to "2.5 pro",
            "gemini-2.5-pro-preview" to "2.5 pro-pre",
            "gemini-2.5-flash-lite" to "2.5 flash-lite",
            "gemini-2.5-flash" to "2.5 flash"
        )

        private fun bucketLabel(modelId: String): String? {
            val normalized = modelId.trim().lowercase(Locale.US)
            if (normalized.isBlank()) return null
            GEMINI_MODEL_LABELS.firstOrNull { it.first == normalized }?.let { return it.second }
            return when {
                normalized.startsWith("gemini-") -> normalized
                    .removePrefix("gemini-")
                    .replace("-preview", "-pre")
                    .replace('-', ' ')
                "deep" in normalized && "research" in normalized -> "Gemini Deep Research"
                else -> null
            }
        }

        private fun bucketSortOrder(modelId: String): Int {
            val normalized = modelId.trim().lowercase(Locale.US)
            val index = GEMINI_MODEL_LABELS.indexOfFirst { it.first == normalized }
            return if (index >= 0) index else GEMINI_MODEL_LABELS.size
        }

        private fun isUnavailableFreePlanProBucket(
            isFreePlan: Boolean,
            modelId: String,
            remainingFraction: Double,
            resetTime: String
        ): Boolean {
            if (!isFreePlan) return false
            if (!modelId.trim().lowercase(Locale.US).contains("-pro")) return false
            if (remainingFraction != 0.0) return false
            return isEpochReset(resetTime)
        }

        private fun isEpochReset(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.startsWith("1970-01-01")) return true
            return parseResetMillis(trimmed) == 0L
        }

        private fun parseResetMillis(value: String): Long? {
            if (value.isBlank()) return null
            return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        }

        private fun resetText(resetsAtMillis: Long): String {
            val duration = Duration.between(Instant.now(), Instant.ofEpochMilli(resetsAtMillis))
            if (duration.isNegative || duration.isZero) return "Resets soon"
            val hours = duration.toHours()
            val minutes = duration.minusHours(hours).toMinutes()
            return when {
                hours > 0 && minutes > 0 -> "Resets in ${hours}h ${minutes}m"
                hours > 0 -> "Resets in ${hours}h"
                minutes > 0 -> "Resets in ${minutes}m"
                else -> "Resets soon"
            }
        }

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).takeIf { it.isNotBlank() && it != "null" }
        }

    }
}
