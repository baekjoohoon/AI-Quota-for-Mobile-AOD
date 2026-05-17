package com.aiusage.mobile.providers

import android.content.Context
import android.content.SharedPreferences
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageSnapshot
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class CodexOAuthRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun beginAuthorizationUrl(): String {
        val verifier = randomUrlSafeValue(48)
        val state = randomUrlSafeValue(32)
        val challenge = codeChallenge(verifier)
        preferences.edit()
            .putString(KEY_PENDING_VERIFIER, verifier)
            .putString(KEY_PENDING_STATE, state)
            .apply()
        return buildAuthorizationUrl(state, challenge)
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
        val verifier = preferences.getString(KEY_PENDING_VERIFIER, null)
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("PKCE verifier is missing.")

        val response = postForm(
            url = TOKEN_URL,
            body = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to CLIENT_ID,
                "code_verifier" to verifier
            )
        )
        if (!response.isSuccess) {
            throw IllegalStateException("Token exchange failed with status ${response.statusCode}.")
        }
        val json = JSONObject(response.body)
        persistTokens(
            idToken = json.requiredString("id_token"),
            accessToken = json.requiredString("access_token"),
            refreshToken = json.requiredString("refresh_token"),
            expiresInSeconds = json.optLongOrNull("expires_in")
        )
        preferences.edit()
            .remove(KEY_PENDING_VERIFIER)
            .remove(KEY_PENDING_STATE)
            .apply()
    }

    fun fetchUsageSnapshot(): ProviderUsageSnapshot? {
        val token = freshAccessToken() ?: return null
        val response = fetchUsageWithToken(token)
        val retryResponse = if (response.statusCode == HTTP_UNAUTHORIZED && refreshAccessToken()) {
            freshAccessToken()?.let(::fetchUsageWithToken)
        } else {
            null
        }
        val acceptedResponse = retryResponse ?: response
        val payload = structuredPayloadFromUsageBodies(
            planLabel = storedPlanLabel(),
            bodies = listOf(acceptedResponse.body)
        )
        val snapshot = TextUsageExtractor.extract(ProviderId.CODEX, payload)
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

    private fun fetchUsageWithToken(accessToken: String): HttpResponse {
        val accountId = preferences.getString(KEY_ACCOUNT_ID, null).orEmpty()
        return getJson(
            url = USAGE_URL,
            headers = buildMap {
                put("Accept", "application/json")
                put("Authorization", "Bearer $accessToken")
                put("originator", ORIGINATOR)
                if (accountId.isNotBlank()) put("ChatGPT-Account-Id", accountId)
            }
        )
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
        val body = JSONObject()
            .put("client_id", CLIENT_ID)
            .put("grant_type", "refresh_token")
            .put("refresh_token", refreshToken)
            .toString()
        val response = postJson(TOKEN_URL, body)
        if (!response.isSuccess) return false
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return false
        persistTokens(
            idToken = json.optNullableString("id_token")
                ?: preferences.getString(KEY_ID_TOKEN, null).orEmpty(),
            accessToken = json.optNullableString("access_token")
                ?: preferences.getString(KEY_ACCESS_TOKEN, null).orEmpty(),
            refreshToken = json.optNullableString("refresh_token")
                ?: refreshToken,
            expiresInSeconds = json.optLongOrNull("expires_in")
        )
        return true
    }

    private fun persistTokens(
        idToken: String,
        accessToken: String,
        refreshToken: String,
        expiresInSeconds: Long?
    ) {
        val claims = parseJwtClaims(idToken) ?: parseJwtClaims(accessToken)
        val authClaims = claims?.optJSONObject("https://api.openai.com/auth")
        val profileClaims = claims?.optJSONObject("https://api.openai.com/profile")
        val accountId = authClaims?.optNullableString("chatgpt_account_id")
        val planType = authClaims?.optNullableString("chatgpt_plan_type")
        val email = claims?.optNullableString("email") ?: profileClaims?.optNullableString("email")
        val expiresAt = expiresInSeconds
            ?.let { Instant.now().epochSecond + it }
            ?: parseJwtExpiration(accessToken)
            ?: parseJwtExpiration(idToken)
            ?: 0L

        preferences.edit()
            .putString(KEY_ID_TOKEN, idToken)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, expiresAt)
            .putString(KEY_ACCOUNT_ID, accountId)
            .putString(KEY_PLAN_TYPE, planType)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    private fun storedPlanLabel(): String? {
        return preferences.getString(KEY_PLAN_TYPE, null)
            ?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
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

    private fun postJson(url: String, body: String): HttpResponse {
        return postBody(url = url, contentType = "application/json", body = body)
    }

    private fun postBody(url: String, contentType: String, body: String): HttpResponse {
        val connection = openConnection(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", contentType)
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("originator", ORIGINATOR)
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
        private const val PREFERENCES_NAME = "ai_usage_codex_oauth"
        private const val KEY_PENDING_VERIFIER = "pending_verifier"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_PLAN_TYPE = "plan_type"
        private const val KEY_EMAIL = "email"

        private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        private const val ISSUER = "https://auth.openai.com"
        private const val TOKEN_URL = "$ISSUER/oauth/token"
        private const val AUTHORIZE_URL = "$ISSUER/oauth/authorize"
        private const val REDIRECT_URI = "http://localhost:1455/auth/callback"
        private const val ORIGINATOR = "codex_cli_rs"
        private const val SCOPE = "openid profile email offline_access api.connectors.read api.connectors.invoke"
        private const val USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
        private const val USER_AGENT = "codex_cli_rs/0.0.0 (Android; AI Usage Mobile)"
        private const val NETWORK_TIMEOUT_MS = 20_000
        private const val HTTP_UNAUTHORIZED = 401

        fun isCallbackUrl(url: String?): Boolean {
            val uri = runCatching { URI(url?.trim().orEmpty()) }.getOrNull() ?: return false
            return uri.scheme.equals("http", ignoreCase = true) &&
                uri.host.equals("localhost", ignoreCase = true) &&
                uri.port == 1455 &&
                uri.path == "/auth/callback"
        }

        internal fun buildAuthorizationUrl(state: String, codeChallenge: String): String {
            return AUTHORIZE_URL + "?" + listOf(
                "response_type" to "code",
                "client_id" to CLIENT_ID,
                "redirect_uri" to REDIRECT_URI,
                "scope" to SCOPE,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
                "id_token_add_organizations" to "true",
                "codex_cli_simplified_flow" to "true",
                "state" to state,
                "originator" to ORIGINATOR
            ).joinToString("&") { (key, value) ->
                "${key.urlEncode()}=${value.urlEncode()}"
            }
        }

        internal fun structuredPayloadFromUsageBodies(
            planLabel: String?,
            bodies: List<String>
        ): String {
            val limits = JSONArray()
            var plan = planLabel
            bodies.forEach { body ->
                val parsed = runCatching { JSONObject(body) }.getOrNull() ?: return@forEach
                val usage = parsed.optJSONObject("usage") ?: parsed
                plan = plan ?: usage.optNullableString("plan_type")
                    ?: usage.optNullableString("plan")
                appendWhamWindowLimits(limits, usage.optJSONObject("rate_limit"), "Codex")
                appendWhamWindowLimits(limits, usage.optJSONObject("code_review_rate_limit"), "Code review")
                appendCreditLimit(limits, usage.optJSONObject("credits"))
            }
            return JSONObject()
                .put("s", if (limits.length() > 0 || !plan.isNullOrBlank()) "s" else "e")
                .put("provider", ProviderId.CODEX.storageId)
                .put(
                    "d",
                    JSONObject()
                        .put("p", plan)
                        .put("x", limits)
                        .put("l", System.currentTimeMillis())
                )
                .put("m", if (limits.length() > 0) JSONObject.NULL else "No usage limits found in Codex OAuth response.")
                .toString()
        }

        internal fun structuredPayloadFromWhamBodyForTest(planLabel: String?, body: String): String {
            return structuredPayloadFromUsageBodies(planLabel, listOf(body))
        }

        private fun appendWhamWindowLimits(limits: JSONArray, rateLimit: JSONObject?, prefix: String) {
            if (rateLimit == null) return
            listOf(
                "primary_window" to "5-hour limit",
                "secondary_window" to "Weekly limit"
            ).forEach { (key, fallbackLabel) ->
                val window = rateLimit.optJSONObject(key) ?: return@forEach
                val usedPercent = window.optNumber(
                    "used_percent",
                    "usedPercentage",
                    "used_percentage",
                    "percent_used"
                ) ?: window.optNumber("percent_left")?.let { 100.0 - it }
                    ?: return@forEach
                val windowSeconds = window.optLongOrNull("limit_window_seconds")
                    ?: window.optLongOrNull("window_seconds")
                val label = when (windowSeconds) {
                    18_000L -> "$prefix 5-hour limit"
                    604_800L -> "$prefix weekly limit"
                    else -> "$prefix $fallbackLabel"
                }
                limits.put(
                    JSONObject()
                        .put("l", label)
                        .put("u", usedPercent.coerceIn(0.0, 100.0))
                        .put("t", resetText(window))
                        .put("unit", "percent")
                        .put("category", if (prefix == "Code review") "code_review" else "usage_window")
                        .put("window", windowText(windowSeconds))
                        .put("source", "Codex OAuth")
                        .put("confidence", 0.98)
                )
            }
        }

        private fun appendCreditLimit(limits: JSONArray, credits: JSONObject?) {
            if (credits == null || credits.optBoolean("unlimited", false)) return
            val balance = credits.optNumber("balance") ?: return
            limits.put(
                JSONObject()
                    .put("l", "Credits")
                    .put("remaining", balance)
                    .put("unit", "credits")
                    .put("category", "credits")
                    .put("source", "Codex OAuth")
                    .put("confidence", 0.9)
            )
        }

        private fun resetText(window: JSONObject): String? {
            val resetAtSeconds = window.optLongOrNull("reset_at")
            if (resetAtSeconds != null && resetAtSeconds > 0L) {
                return Instant.ofEpochSecond(resetAtSeconds).toString()
            }
            val resetAfterSeconds = window.optLongOrNull("reset_after_seconds")
            if (resetAfterSeconds != null && resetAfterSeconds >= 0L) {
                return Instant.now().plusSeconds(resetAfterSeconds).toString()
            }
            return null
        }

        private fun windowText(windowSeconds: Long?): String? {
            return when (windowSeconds) {
                18_000L -> "5 hours"
                604_800L -> "7 days"
                null -> null
                else -> "${windowSeconds / 60L} minutes"
            }
        }

        private fun randomUrlSafeValue(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        private fun codeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        private fun parseJwtClaims(jwt: String): JSONObject? {
            val payload = jwt.split(".").getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
            val decoded = runCatching { Base64.getUrlDecoder().decode(payload) }.getOrNull() ?: return null
            return runCatching { JSONObject(String(decoded, StandardCharsets.UTF_8)) }.getOrNull()
        }

        private fun parseJwtExpiration(jwt: String): Long? {
            return parseJwtClaims(jwt)?.optLongOrNull("exp")
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

        private fun JSONObject.optNumber(vararg keys: String): Double? {
            keys.forEach { key ->
                if (!has(key) || isNull(key)) return@forEach
                val number = when (val value = opt(key)) {
                    is Number -> value.toDouble()
                    is String -> value.trim().toDoubleOrNull()
                    else -> null
                }
                if (number != null) return number
            }
            return null
        }

        private fun String.urlEncode(): String {
            return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
        }

        private fun String.urlDecode(): String {
            return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
        }
    }
}
