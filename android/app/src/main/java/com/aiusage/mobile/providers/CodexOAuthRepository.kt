package com.aiusage.mobile.providers

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import com.aiusage.mobile.local.ProviderId
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import org.json.JSONObject

class CodexOAuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val secureStore = SecureStringStore(appContext, PREFERENCES)

    fun beginAuthorizationUrl(): String {
        return defaultLoginStartUrl()
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
        val verifier = secureStore.getString(KEY_VERIFIER)
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("PKCE verifier is missing.")
        val response = postForm(
            url = TOKEN_URL,
            body = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to (configuredClientId() ?: DEFAULT_CLIENT_ID),
                "code_verifier" to verifier
            )
        )
        if (!response.isSuccess) {
            throw IllegalStateException("Token exchange failed with status ${response.statusCode}.")
        }
        persistTokens(JSONObject(response.body))
        secureStore.remove(KEY_VERIFIER, KEY_STATE)
    }

    fun fetchUsagePayload(): String? {
        val token = freshAccessToken() ?: return null
        val response = fetchUsageWithToken(token)
        val accepted = if (response.statusCode == HTTP_UNAUTHORIZED && refreshAccessToken()) {
            freshAccessToken()?.let(::fetchUsageWithToken) ?: response
        } else {
            response
        }
        return accepted.body.takeIf { accepted.isSuccess && it.isNotBlank() }
    }

    private fun configuredClientId(): String? {
        return runCatching {
            val appInfo = appContext.packageManager.getApplicationInfo(appContext.packageName, PackageManager.GET_META_DATA)
            appInfo.metaData?.getString(META_DATA_CLIENT_ID)
        }.getOrNull()
    }

    companion object {
        const val REDIRECT_URI = "http://localhost:1455/auth/callback"
        private const val DEFAULT_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        private const val ISSUER = "https://auth.openai.com"
        private const val TOKEN_URL = "$ISSUER/oauth/token"
        private const val AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize"
        private const val SCOPE = "openid profile email offline_access api.connectors.read api.connectors.invoke"
        private const val ORIGINATOR = "codex_cli_rs"
        private const val META_DATA_CLIENT_ID = "com.aiusage.mobile.CODEX_OAUTH_CLIENT_ID"
        private const val PREFERENCES = "ai_usage_codex_oauth"
        private const val KEY_VERIFIER = "code_verifier"
        private const val KEY_STATE = "state"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_PLAN_TYPE = "plan_type"
        private const val KEY_EMAIL = "email"
        private const val USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
        private const val USER_AGENT = "codex_cli_rs/0.0.0 (Android; AI Usage Mobile)"
        private const val NETWORK_TIMEOUT_MS = 20_000
        private const val HTTP_UNAUTHORIZED = 401

        fun defaultLoginStartUrl(): String {
            return ProviderDefinitionRegistry.definitionFor(ProviderId.CODEX).loginStartUrl
        }

        fun buildAuthorizationUrl(clientId: String?, state: String, codeChallenge: String): String {
            if (clientId.isNullOrBlank()) return defaultLoginStartUrl()
            val params = linkedMapOf(
                "response_type" to "code",
                "client_id" to clientId,
                "redirect_uri" to REDIRECT_URI,
                "scope" to SCOPE,
                "state" to state,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
                "originator" to ORIGINATOR
            )
            return AUTHORIZE_URL + "?" + params.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        }

        private fun codeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }

        private fun randomToken(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            SecureRandom().nextBytes(bytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }

        private fun encode(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
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
            url = TOKEN_URL,
            body = mapOf(
                "grant_type" to "refresh_token",
                "client_id" to (configuredClientId() ?: DEFAULT_CLIENT_ID),
                "refresh_token" to refreshToken
            )
        )
        if (!response.isSuccess) return false
        persistTokens(JSONObject(response.body), previousRefreshToken = refreshToken)
        return true
    }

    private fun fetchUsageWithToken(accessToken: String): HttpResponse {
        val connection = openConnection(USAGE_URL)
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("originator", ORIGINATOR)
        connection.setRequestProperty("User-Agent", USER_AGENT)
        secureStore.getString(KEY_ACCOUNT_ID)?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("ChatGPT-Account-Id", it)
        }
        return connection.response()
    }

    private fun persistTokens(json: JSONObject, previousRefreshToken: String? = null) {
        val idToken = json.optNullableString("id_token").orEmpty()
        val accessToken = json.optNullableString("access_token").orEmpty()
        val refreshToken = json.optNullableString("refresh_token") ?: previousRefreshToken.orEmpty()
        val claims = parseJwtClaims(idToken).orElse(parseJwtClaims(accessToken))
        val authClaims = claims?.optJSONObject("https://api.openai.com/auth")
        val profileClaims = claims?.optJSONObject("https://api.openai.com/profile")
        val expiresIn = json.optLong("expires_in", 0L).takeIf { it > 0L }
        val expiresAt = expiresIn?.let { Instant.now().epochSecond + it }
            ?: parseJwtExpiration(accessToken)
            ?: parseJwtExpiration(idToken)
            ?: 0L
        secureStore.putString(KEY_ID_TOKEN, idToken)
        secureStore.putString(KEY_ACCESS_TOKEN, accessToken)
        secureStore.putString(KEY_REFRESH_TOKEN, refreshToken)
        secureStore.putLong(KEY_ACCESS_EXPIRES_AT, expiresAt)
        secureStore.putString(KEY_ACCOUNT_ID, authClaims?.optNullableString("chatgpt_account_id"))
        secureStore.putString(KEY_PLAN_TYPE, authClaims?.optNullableString("chatgpt_plan_type"))
        secureStore.putString(KEY_EMAIL, json.optNullableString("email") ?: claims?.optNullableString("email") ?: profileClaims?.optNullableString("email"))
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

    private fun JSONObject?.orElse(fallback: JSONObject?): JSONObject? = this ?: fallback

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun String.urlDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())

    private data class HttpResponse(val statusCode: Int, val body: String) {
        val isSuccess: Boolean = statusCode in 200..299
    }
}
