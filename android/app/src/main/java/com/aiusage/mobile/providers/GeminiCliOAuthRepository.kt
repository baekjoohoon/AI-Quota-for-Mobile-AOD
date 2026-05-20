package com.aiusage.mobile.providers

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

class GeminiCliOAuthRepository(context: Context) {
    private val secureStore = SecureStringStore(context.applicationContext, PREFERENCES)

    fun beginAuthorizationUrl(): String {
        val state = randomToken(24)
        secureStore.putString(KEY_STATE, state)
        return buildAuthorizationUrl(state)
    }

    fun completeAuthorization(callbackUrl: String): Result<Unit> = runCatching {
        val query = queryParameters(callbackUrl)
        query["error"]?.let { throw IllegalStateException(it) }
        val code = query["code"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Authorization code is missing.")
        val state = query["state"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OAuth state is missing.")
        val expectedState = secureStore.getString(KEY_STATE)
        if (expectedState.isNullOrBlank() || state != expectedState) {
            throw IllegalStateException("OAuth state did not match.")
        }
        val response = postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET
            )
        )
        if (!response.isSuccess) {
            throw IllegalStateException("Token exchange failed with status ${response.statusCode}.")
        }
        persistTokens(JSONObject(response.body))
        secureStore.remove(KEY_STATE)
    }

    fun fetchUsagePayload(): String? {
        val token = freshAccessToken() ?: return null
        val setup = setupCodeAssist(token) ?: return null
        val response = postCodeAssist(
            method = "retrieveUserQuota",
            payload = retrieveUserQuotaPayload(setup.projectId),
            accessToken = freshAccessToken() ?: token
        ).retryUnauthorizedWithFreshToken { refreshedToken ->
            postCodeAssist(
                method = "retrieveUserQuota",
                payload = retrieveUserQuotaPayload(setup.projectId),
                accessToken = refreshedToken
            )
        }
        val quotaJson = runCatching { JSONObject(response.body) }.getOrNull()
        logCollection(
            "retrieveUserQuota status=${response.statusCode} projectPresent=${setup.projectId.isNotBlank()} " +
                "bucketCount=${quotaJson?.let(::trustedBucketCount) ?: 0}"
        )
        if (!response.isSuccess || quotaJson == null) {
            logHttpFailure("retrieveUserQuota", response)
            return null
        }
        if (trustedBucketCount(quotaJson) == 0) return null
        return structuredPayloadFromCodeAssist(
            loadJson = setup.loadJson,
            quotaJson = quotaJson,
            email = secureStore.getString(KEY_EMAIL)
        )
    }

    private fun freshAccessToken(): String? {
        val accessToken = secureStore.getString(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }
        val expiresAt = secureStore.getLong(KEY_ACCESS_EXPIRES_AT, 0L)
        if (accessToken != null && (expiresAt == 0L || expiresAt > Instant.now().epochSecond + 60L)) {
            return accessToken
        }
        return if (refreshAccessToken()) {
            secureStore.getString(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() }
        } else {
            accessToken
        }
    }

    private fun refreshAccessToken(): Boolean {
        val refreshToken = secureStore.getString(KEY_REFRESH_TOKEN)?.takeIf { it.isNotBlank() } ?: return false
        val response = postForm(
            TOKEN_URL,
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET,
                "refresh_token" to refreshToken
            )
        )
        if (!response.isSuccess) return false
        persistTokens(JSONObject(response.body), previousRefreshToken = refreshToken)
        return true
    }

    private fun setupCodeAssist(accessToken: String): SetupResult? {
        val initialLoad = loadCodeAssist(projectId = null, accessToken = freshAccessToken() ?: accessToken)
            ?: return null
        projectIdFromLoad(initialLoad)?.let { projectId ->
            return SetupResult(projectId = projectId, loadJson = initialLoad)
        }

        val onboardProjectId = onboardUser(initialLoad, accessToken) ?: return null
        val refreshedLoad = loadCodeAssist(
            projectId = onboardProjectId,
            accessToken = freshAccessToken() ?: accessToken
        ) ?: initialLoad
        return SetupResult(
            projectId = projectIdFromLoad(refreshedLoad) ?: onboardProjectId,
            loadJson = refreshedLoad
        )
    }

    private fun loadCodeAssist(projectId: String?, accessToken: String): JSONObject? {
        val response = postCodeAssist(
            method = "loadCodeAssist",
            payload = loadCodeAssistPayload(projectId),
            accessToken = accessToken
        ).retryUnauthorizedWithFreshToken { refreshedToken ->
            postCodeAssist("loadCodeAssist", loadCodeAssistPayload(projectId), refreshedToken)
        }
        logCollection("loadCodeAssist status=${response.statusCode} projectPresent=${!projectId.isNullOrBlank()}")
        if (!response.isSuccess) {
            logHttpFailure("loadCodeAssist", response)
            return null
        }
        return runCatching { JSONObject(response.body) }.getOrNull()
    }

    private fun onboardUser(loadJson: JSONObject, accessToken: String): String? {
        val tier = defaultAllowedTier(loadJson) ?: return null
        val tierId = tier.optNullableString("id") ?: return null
        val payload = onboardUserPayload(tierId = tierId, projectId = null)
        var response = postCodeAssist(
            method = "onboardUser",
            payload = payload,
            accessToken = accessToken
        ).retryUnauthorizedWithFreshToken { refreshedToken ->
            postCodeAssist("onboardUser", payload, refreshedToken)
        }
        logCollection("onboardUser status=${response.statusCode} tierPresent=true")
        if (!response.isSuccess) {
            logHttpFailure("onboardUser", response)
            return null
        }

        var operation = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        repeat(OPERATION_POLL_ATTEMPTS) {
            if (operation.optBoolean("done", false)) return projectIdFromOperation(operation)
            val name = operation.optNullableString("name") ?: return null
            Thread.sleep(OPERATION_POLL_DELAY_MS)
            response = getCodeAssistOperation(name, freshAccessToken() ?: accessToken)
            if (!response.isSuccess) {
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
                "Authorization" to "Bearer $accessToken"
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

    private fun persistTokens(json: JSONObject, previousRefreshToken: String? = null) {
        val idToken = json.optNullableString("id_token") ?: secureStore.getString(KEY_ID_TOKEN).orEmpty()
        val accessToken = json.optNullableString("access_token") ?: secureStore.getString(KEY_ACCESS_TOKEN).orEmpty()
        val refreshToken = json.optNullableString("refresh_token") ?: previousRefreshToken.orEmpty()
        val claims = parseJwtClaims(idToken)
        val expiresIn = json.optLong("expires_in", 0L).takeIf { it > 0L }
        val expiresAt = expiresIn?.let { Instant.now().epochSecond + it }
            ?: parseJwtExpiration(idToken)
            ?: 0L
        secureStore.putString(KEY_ID_TOKEN, idToken)
        secureStore.putString(KEY_ACCESS_TOKEN, accessToken)
        secureStore.putString(KEY_REFRESH_TOKEN, refreshToken)
        secureStore.putLong(KEY_ACCESS_EXPIRES_AT, expiresAt)
        secureStore.putString(KEY_EMAIL, claims?.optNullableString("email") ?: secureStore.getString(KEY_EMAIL))
    }

    private fun postCodeAssist(method: String, payload: String, accessToken: String): HttpResponse {
        return postJson(
            url = "$CODE_ASSIST_BASE_URL:$method",
            body = payload,
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $accessToken"
            )
        )
    }

    private fun postForm(url: String, body: Map<String, String>): HttpResponse {
        val connection = openConnection(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(body.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" })
        }
        return connection.response()
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

    private data class SetupResult(
        val projectId: String,
        val loadJson: JSONObject
    )

    private fun HttpResponse.retryUnauthorizedWithFreshToken(block: (String) -> HttpResponse): HttpResponse {
        if (statusCode != HTTP_UNAUTHORIZED || !refreshAccessToken()) return this
        val token = freshAccessToken() ?: return this
        return block(token)
    }

    private fun logHttpFailure(method: String, response: HttpResponse) {
        Log.w("AIUsageGemini", "collection geminiCliOAuthUsage $method failed status=${response.statusCode} bodyLength=${response.body.length}")
    }

    private fun logCollection(message: String) {
        Log.i("AIUsageGemini", "collection geminiCliOAuthUsage $message")
    }

    companion object {
        const val REDIRECT_URI = "http://127.0.0.1:46417/oauth2callback"
        private const val CLIENT_ID = "681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com"
        private const val CLIENT_SECRET = "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"
        private const val AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val CODE_ASSIST_BASE_URL = "https://cloudcode-pa.googleapis.com/v1internal"
        private const val SCOPE = "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/cloud-platform"
        private const val PREFERENCES = "ai_usage_gemini_oauth"
        private const val KEY_STATE = "state"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_EMAIL = "email"
        private const val USER_AGENT = "GeminiCLI/0.0.0 (Android; AI Usage Mobile)"
        private const val NETWORK_TIMEOUT_MS = 20_000
        private const val HTTP_UNAUTHORIZED = 401
        private const val OPERATION_POLL_ATTEMPTS = 12
        private const val OPERATION_POLL_DELAY_MS = 5_000L

        fun buildAuthorizationUrl(state: String): String {
            val params = linkedMapOf(
                "response_type" to "code",
                "client_id" to CLIENT_ID,
                "redirect_uri" to REDIRECT_URI,
                "scope" to SCOPE,
                "state" to state,
                "access_type" to "offline",
                "prompt" to "consent"
            )
            return AUTHORIZE_URL + "?" + params.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
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
                if (bucketTitle(bucket.optString("modelId")) == null) continue
                val remaining = bucket.optDouble("remainingFraction", Double.NaN)
                if (!remaining.isNaN()) count += 1
            }
            return count
        }

        fun normalizeQuotaPayload(quotaJson: String, account: String?, plan: String?): String? {
            val buckets = JSONObject(quotaJson).optJSONArray("buckets") ?: return null
            val selected = linkedMapOf<String, JSONObject>()
            for (index in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(index) ?: continue
                val title = bucketTitle(bucket.optString("modelId")) ?: continue
                val remaining = bucket.optDouble("remainingFraction", Double.NaN)
                if (remaining.isNaN()) continue
                val existing = selected[title]
                if (existing == null || remaining < existing.optDouble("remainingFraction", 1.0)) {
                    selected[title] = bucket
                }
            }
            if (selected.isEmpty()) return null
            val limits = JSONArray()
            listOf("Pro", "Flash", "Deep Research").forEach { title ->
                selected[title]?.let { bucket ->
                    val remaining = bucket.optDouble("remainingFraction").coerceIn(0.0, 1.0)
                    val remainingPercent = (remaining * 100.0).roundToInt().coerceIn(0, 100)
                    val line = JSONObject()
                        .put("title", title)
                        .put("usedPercent", 100 - remainingPercent)
                        .put("remainingPercent", remainingPercent)
                    parseResetMillis(bucket.optString("resetTime"))?.let { resetsAtMillis ->
                        line
                            .put("resetsAt", resetsAtMillis)
                            .put("resetText", resetText(resetsAtMillis))
                    }
                    limits.put(line)
                }
            }
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

        private fun bucketTitle(modelId: String): String? {
            val compact = modelId.lowercase(Locale.US)
            return when {
                "gemini" in compact && "pro" in compact -> "Pro"
                "gemini" in compact && "flash" in compact -> "Flash"
                "deep" in compact && "research" in compact -> "Deep Research"
                else -> null
            }
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

        private fun randomToken(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }

        private fun encode(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }

        private fun queryParameters(url: String): Map<String, String> {
            val query = runCatching { URL(url).query }.getOrNull().orEmpty()
            if (query.isBlank()) return emptyMap()
            return query.split("&")
                .mapNotNull { part ->
                    val pieces = part.split("=", limit = 2)
                    if (pieces.isEmpty()) return@mapNotNull null
                    val key = pieces[0].urlDecode()
                    val value = pieces.getOrNull(1).orEmpty().urlDecode()
                    key to value
                }
                .toMap()
        }

        private fun parseJwtClaims(token: String): JSONObject? {
            val payload = token.split(".").getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
            return runCatching {
                JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), StandardCharsets.UTF_8))
            }.getOrNull()
        }

        private fun parseJwtExpiration(token: String): Long? {
            return parseJwtClaims(token)?.optLong("exp", 0L)?.takeIf { it > 0L }
        }

        private fun String.urlDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).takeIf { it.isNotBlank() && it != "null" }
        }
    }
}
