package com.aiusage.mobile.providers

import android.content.Context
import android.util.Log
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.local.normalizedPlanLabelForDisplay
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class GeminiCliOAuthRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun beginAuthorizationUrl(): String {
        val state = randomState()
        preferences.edit()
            .putString(KEY_PENDING_STATE, state)
            .apply()
        return buildAuthorizationUrl(state)
    }

    fun hasStoredTokens(): Boolean {
        return !preferences.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank() ||
            !preferences.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()
    }

    fun completeAuthorization(callbackUrl: String): Result<Unit> = runCatching {
        val query = queryParameters(callbackUrl)
        query["error"]?.let { error ->
            throw IllegalStateException(error)
        }
        val code = query["code"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Authorization code is missing.")
        val state = query["state"]?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OAuth state is missing.")
        val expectedState = preferences.getString(KEY_PENDING_STATE, null)
        if (expectedState.isNullOrBlank() || state != expectedState) {
            throw IllegalStateException("OAuth state did not match.")
        }

        val response = postForm(
            url = TOKEN_URL,
            body = mapOf(
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
        val json = JSONObject(response.body)
        persistTokens(
            accessToken = json.requiredString("access_token"),
            refreshToken = json.optNullableString("refresh_token")
                ?: preferences.getString(KEY_REFRESH_TOKEN, null).orEmpty(),
            idToken = json.optNullableString("id_token"),
            expiresInSeconds = json.optLongOrNull("expires_in")
        )
        preferences.edit()
            .remove(KEY_PENDING_STATE)
            .apply()
    }

    fun fetchUsageSnapshot(): ProviderUsageSnapshot? {
        val token = freshAccessToken() ?: return null
        val setup = setupCodeAssist(token) ?: return null
        val quotaResponse = postCodeAssist(
            method = "retrieveUserQuota",
            payload = JSONObject()
                .put("project", setup.projectId)
                .toString(),
            accessToken = freshAccessToken() ?: token
        ).retryUnauthorizedWithFreshToken { freshToken ->
            postCodeAssist(
                method = "retrieveUserQuota",
                payload = JSONObject()
                    .put("project", setup.projectId)
                    .toString(),
                accessToken = freshToken
            )
        }
        if (!quotaResponse.isSuccess) {
            logHttpFailure("retrieveUserQuota", quotaResponse)
            return null
        }
        val quotaJson = runCatching { JSONObject(quotaResponse.body) }.getOrNull() ?: return null
        val payload = structuredPayloadFromCodeAssist(
            loadJson = setup.loadJson,
            quotaJson = quotaJson,
            email = storedEmail()
        )
        val snapshot = TextUsageExtractor.extract(ProviderId.GEMINI, payload)
        return if (
            snapshot.connectionState == ProviderConnectionState.CONNECTED &&
            snapshot.lines.isNotEmpty()
        ) {
            snapshot.copy(
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString()
            )
        } else {
            null
        }
    }

    private fun setupCodeAssist(accessToken: String): SetupResult? {
        val initialLoad = loadCodeAssist(projectId = null, accessToken = accessToken) ?: return null
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
        ).retryUnauthorizedWithFreshToken { freshToken ->
            postCodeAssist("loadCodeAssist", loadCodeAssistPayload(projectId), freshToken)
        }
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
        ).retryUnauthorizedWithFreshToken { freshToken ->
            postCodeAssist("onboardUser", payload, freshToken)
        }
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
            headers = mapOf("Authorization" to "Bearer $accessToken")
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

    private fun logHttpFailure(method: String, response: HttpResponse) {
        Log.w(
            ProviderCollectionDiagnostics.TAG,
            "geminiCli $method failed status=${response.statusCode} bodyLength=${response.body.length}"
        )
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

    private fun freshAccessToken(): String? {
        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
        val expiresAt = preferences.getLong(KEY_ACCESS_EXPIRES_AT, 0L)
        if (accessToken != null && (expiresAt == 0L || expiresAt > Instant.now().epochSecond + 60L)) {
            return accessToken
        }
        return if (refreshAccessToken()) {
            preferences.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
        } else {
            accessToken
        }
    }

    private fun refreshAccessToken(): Boolean {
        val refreshToken = preferences.getString(KEY_REFRESH_TOKEN, null)
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val response = postForm(
            url = TOKEN_URL,
            body = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to CLIENT_ID,
                "client_secret" to CLIENT_SECRET
            )
        )
        if (!response.isSuccess) return false
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return false
        persistTokens(
            accessToken = json.optNullableString("access_token")
                ?: preferences.getString(KEY_ACCESS_TOKEN, null).orEmpty(),
            refreshToken = json.optNullableString("refresh_token") ?: refreshToken,
            idToken = json.optNullableString("id_token"),
            expiresInSeconds = json.optLongOrNull("expires_in")
        )
        return true
    }

    private fun persistTokens(
        accessToken: String,
        refreshToken: String,
        idToken: String?,
        expiresInSeconds: Long?
    ) {
        val expiresAt = expiresInSeconds
            ?.let { Instant.now().epochSecond + it }
            ?: 0L
        val email = idToken
            ?.let(::parseJwtClaims)
            ?.optNullableString("email")
            ?: preferences.getString(KEY_EMAIL, null)
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ID_TOKEN, idToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, expiresAt)
            .putString(KEY_EMAIL, email)
            .apply()
        fetchUserInfo(accessToken)?.let { userEmail ->
            preferences.edit().putString(KEY_EMAIL, userEmail).apply()
        }
    }

    private fun fetchUserInfo(accessToken: String): String? {
        val response = getJson(
            url = USERINFO_URL,
            headers = mapOf("Authorization" to "Bearer $accessToken")
        )
        if (!response.isSuccess) return null
        return runCatching { JSONObject(response.body).optNullableString("email") }.getOrNull()
    }

    private fun storedEmail(): String? {
        return preferences.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }
    }

    private fun postCodeAssist(method: String, payload: String, accessToken: String): HttpResponse {
        return postBody(
            url = "$CODE_ASSIST_BASE_URL:$method",
            contentType = "application/json",
            body = payload,
            headers = mapOf(
                "Authorization" to "Bearer $accessToken",
                "Accept" to "application/json"
            )
        )
    }

    private fun postForm(url: String, body: Map<String, String>): HttpResponse {
        return postBody(
            url = url,
            contentType = "application/x-www-form-urlencoded",
            body = body.entries.joinToString("&") { (key, value) ->
                "${key.urlEncode()}=${value.urlEncode()}"
            }
        )
    }

    private fun postBody(
        url: String,
        contentType: String,
        body: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse {
        val connection = openConnection(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", contentType)
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(body)
        }
        return connection.readResponse()
    }

    private fun getJson(url: String, headers: Map<String, String>): HttpResponse {
        val connection = openConnection(url)
        connection.requestMethod = "GET"
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return connection.readResponse()
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
        }
    }

    private fun HttpURLConnection.readResponse(): HttpResponse {
        val status = responseCode
        val stream = if (status in 200..299) inputStream else errorStream
        val body = stream?.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
        }.orEmpty()
        disconnect()
        return HttpResponse(status, body)
    }

    private data class HttpResponse(
        val statusCode: Int,
        val body: String
    ) {
        val isSuccess: Boolean = statusCode in 200..299
    }

    companion object {
        private const val PREFERENCES_NAME = "ai_usage_gemini_cli_oauth"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_EMAIL = "email"

        private const val CLIENT_ID =
            "681255809395-oo8ft2oprdrnp9e3aqf6av3hmdib135j.apps.googleusercontent.com"
        private const val CLIENT_SECRET = "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"
        private const val REDIRECT_URI = "http://127.0.0.1:46417/oauth2callback"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
        private const val CODE_ASSIST_BASE_URL = "https://cloudcode-pa.googleapis.com/v1internal"
        private const val SCOPE =
            "https://www.googleapis.com/auth/cloud-platform " +
                "https://www.googleapis.com/auth/userinfo.email " +
                "https://www.googleapis.com/auth/userinfo.profile"
        private const val USER_AGENT = "gemini-cli/0.0.0 (Android; AI Usage Mobile)"
        private const val NETWORK_TIMEOUT_MS = 20_000
        private const val HTTP_UNAUTHORIZED = 401
        private const val OPERATION_POLL_ATTEMPTS = 12
        private const val OPERATION_POLL_DELAY_MS = 5_000L

        fun isCallbackUrl(url: String?): Boolean {
            val uri = runCatching { URI(url?.trim().orEmpty()) }.getOrNull() ?: return false
            return uri.scheme.equals("http", ignoreCase = true) &&
                uri.host == "127.0.0.1" &&
                uri.port == 46417 &&
                uri.path == "/oauth2callback"
        }

        internal fun buildAuthorizationUrl(state: String): String {
            return "https://accounts.google.com/o/oauth2/v2/auth?" + listOf(
                "client_id" to CLIENT_ID,
                "redirect_uri" to REDIRECT_URI,
                "response_type" to "code",
                "scope" to SCOPE,
                "state" to state,
                "access_type" to "offline",
                "prompt" to "consent"
            ).joinToString("&") { (key, value) ->
                "${key.urlEncode()}=${value.urlEncode()}"
            }
        }

        internal fun structuredPayloadFromCodeAssist(
            loadJson: JSONObject,
            quotaJson: JSONObject,
            email: String?
        ): String {
            val limits = JSONArray()
            val buckets = quotaJson.optJSONArray("buckets") ?: JSONArray()
            for (index in 0 until buckets.length()) {
                val bucket = buckets.optJSONObject(index) ?: continue
                val label = geminiBucketLabel(bucket.optNullableString("modelId")) ?: continue
                val remainingFraction = bucket.optNumber("remainingFraction")
                    ?.coerceIn(0.0, 1.0)
                    ?: continue
                limits.put(
                    JSONObject()
                        .put("l", label)
                        .put("u", ((1.0 - remainingFraction) * 100.0).coerceIn(0.0, 100.0))
                        .put("remainingText", "${Math.round(remainingFraction * 100.0)}% left")
                        .put("r", bucket.optNullableString("resetTime"))
                        .put("unit", "percent")
                        .put("category", "usage_window")
                        .put("source", "Gemini Code Assist retrieveUserQuota")
                        .put("confidence", 0.99)
                )
            }
            val plan = planLabel(loadJson)
            val account = JSONObject().put("p", plan)
            if (!email.isNullOrBlank()) account.put("e", email)
            return JSONObject()
                .put("s", if (limits.length() > 0 || !plan.isNullOrBlank()) "s" else "e")
                .put("provider", ProviderId.GEMINI.storageId)
                .put("account", account)
                .put(
                    "usage",
                    JSONObject()
                        .put("x", limits)
                        .put("l", System.currentTimeMillis())
                )
                .put("m", if (limits.length() > 0) JSONObject.NULL else "No Gemini Code Assist quota buckets found.")
                .toString()
        }

        private fun loadCodeAssistPayload(projectId: String?): String {
            val metadata = JSONObject()
                .put("ideType", "IDE_UNSPECIFIED")
                .put("platform", "PLATFORM_UNSPECIFIED")
                .put("pluginType", "GEMINI")
            if (!projectId.isNullOrBlank()) metadata.put("duetProject", projectId)
            val payload = JSONObject()
                .put("metadata", metadata)
            if (!projectId.isNullOrBlank()) payload.put("cloudaicompanionProject", projectId)
            return payload.toString()
        }

        private fun onboardUserPayload(tierId: String, projectId: String?): String {
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

        private fun planLabel(loadJson: JSONObject): String? {
            val paidTier = loadJson.optJSONObject("paidTier")
            val currentTier = loadJson.optJSONObject("currentTier")
            val raw = paidTier?.optNullableString("name")
                ?: paidTier?.optNullableString("id")
                ?: currentTier?.optNullableString("name")
                ?: currentTier?.optNullableString("id")
            return ProviderId.GEMINI.normalizedPlanLabelForDisplay(raw)
        }

        private fun geminiBucketLabel(modelId: String?): String? {
            val compact = modelId.orEmpty().lowercase(Locale.US)
            return when {
                "pro" in compact -> "Gemini Pro"
                "flash" in compact -> "Gemini Flash"
                else -> null
            }
        }

        private fun randomState(): String {
            return java.util.UUID.randomUUID().toString().replace("-", "")
        }

        private fun queryParameters(url: String): Map<String, String> {
            val query = URI(url).rawQuery.orEmpty()
            if (query.isBlank()) return emptyMap()
            return query.split("&").mapNotNull { part ->
                val separator = part.indexOf("=")
                if (separator < 0) return@mapNotNull null
                part.substring(0, separator).urlDecode() to part.substring(separator + 1).urlDecode()
            }.toMap()
        }

        private fun parseJwtClaims(jwt: String): JSONObject? {
            val payload = jwt.split(".").getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
            val decoded = runCatching { java.util.Base64.getUrlDecoder().decode(payload) }.getOrNull()
                ?: return null
            return runCatching { JSONObject(String(decoded, StandardCharsets.UTF_8)) }.getOrNull()
        }

        private fun JSONObject.requiredString(key: String): String {
            return optNullableString(key) ?: throw IllegalStateException("$key is missing.")
        }

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).trim().takeIf { it.isNotBlank() }
        }

        private fun JSONObject.optLongOrNull(key: String): Long? {
            if (!has(key) || isNull(key)) return null
            return opt(key)?.toString()?.toLongOrNull()
        }

        private fun JSONObject.optNumber(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            return when (val value = opt(key)) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> null
            }
        }

        private fun String.urlEncode(): String {
            return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        }

        private fun String.urlDecode(): String {
            return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
        }
    }
}
