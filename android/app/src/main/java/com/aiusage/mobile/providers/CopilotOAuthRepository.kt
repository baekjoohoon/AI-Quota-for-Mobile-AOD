package com.aiusage.mobile.providers

import android.content.Context
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.json.JSONObject

class CopilotOAuthRepository(context: Context) {
    private val secureStore = SecureStringStore(context.applicationContext, PREFERENCES)

    fun beginDeviceAuthorization(): Result<CopilotDeviceAuthorization> = runCatching {
        val response = postForm(
            DEVICE_CODE_URL,
            mapOf(
                "client_id" to CLIENT_ID,
                "scope" to SCOPE
            )
        )
        if (!response.isSuccess) {
            throw IllegalStateException("Device code request failed with status ${response.statusCode}.")
        }
        val json = JSONObject(response.body)
        val deviceCode = json.optNullableString("device_code")
            ?: throw IllegalStateException("Device code is missing.")
        val userCode = json.optNullableString("user_code")
            ?: throw IllegalStateException("User code is missing.")
        val verificationUri = json.optNullableString("verification_uri")
            ?: throw IllegalStateException("Verification URI is missing.")
        val intervalSeconds = json.optLong("interval", DEFAULT_POLL_INTERVAL_SECONDS).coerceAtLeast(1L)
        val expiresAt = Instant.now().epochSecond + json.optLong("expires_in", DEFAULT_DEVICE_EXPIRES_SECONDS)
        secureStore.putString(KEY_DEVICE_CODE, deviceCode)
        secureStore.putString(KEY_USER_CODE, userCode)
        secureStore.putString(KEY_VERIFICATION_URI, verificationUri)
        secureStore.putLong(KEY_POLL_INTERVAL_SECONDS, intervalSeconds)
        secureStore.putLong(KEY_DEVICE_EXPIRES_AT, expiresAt)
        CopilotDeviceAuthorization(
            verificationUri = verificationUri,
            userCode = userCode,
            intervalMillis = intervalSeconds * 1000L
        )
    }

    fun currentUserCode(): String? = secureStore.getString(KEY_USER_CODE)?.takeIf { it.isNotBlank() }

    fun pollDeviceAuthorization(): CopilotDevicePollResult {
        val deviceCode = secureStore.getString(KEY_DEVICE_CODE)?.takeIf { it.isNotBlank() }
            ?: return CopilotDevicePollResult.Failed("device_code_missing")
        val expiresAt = secureStore.getLong(KEY_DEVICE_EXPIRES_AT, 0L)
        if (expiresAt > 0L && expiresAt <= Instant.now().epochSecond) {
            return CopilotDevicePollResult.Failed("device_code_expired")
        }
        val response = postForm(
            ACCESS_TOKEN_URL,
            mapOf(
                "client_id" to CLIENT_ID,
                "device_code" to deviceCode,
                "grant_type" to DEVICE_GRANT_TYPE
            )
        )
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: JSONObject()
        val error = json.optNullableString("error")
        if (error == "authorization_pending") return CopilotDevicePollResult.Pending(nextPollDelayMillis())
        if (error == "slow_down") return CopilotDevicePollResult.Pending(nextPollDelayMillis() + 5_000L)
        if (!response.isSuccess || error != null) {
            return CopilotDevicePollResult.Failed(error ?: "token_poll_http_${response.statusCode}")
        }
        val accessToken = json.optNullableString("access_token")
            ?: return CopilotDevicePollResult.Failed("access_token_missing")
        persistAccessToken(accessToken)
        secureStore.remove(KEY_DEVICE_CODE, KEY_USER_CODE, KEY_VERIFICATION_URI, KEY_DEVICE_EXPIRES_AT)
        return CopilotDevicePollResult.Authorized
    }

    fun fetchUsagePayload(): String? {
        val accessToken = secureStore.getString(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        fetchCopilotUser(accessToken)?.let { return it }
        val sessionToken = secureStore.getString(KEY_SESSION_TOKEN)?.takeIf { it.isNotBlank() }
            ?: fetchCopilotSessionToken(accessToken)?.also { secureStore.putString(KEY_SESSION_TOKEN, it) }
        return sessionToken?.let(::fetchCopilotUser)
    }

    private fun fetchCopilotUser(token: String): String? {
        val response = getJson(
            url = USER_URL,
            token = token,
            extraHeaders = copilotHeaders()
        )
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        return response.body.takeIf {
            response.isSuccess &&
                json != null &&
                (json.has("quota_snapshots") || json.has("limited_user_quotas"))
        }
    }

    private fun fetchCopilotSessionToken(accessToken: String): String? {
        val response = getJson(
            url = SESSION_TOKEN_URL,
            token = accessToken,
            extraHeaders = copilotHeaders()
        )
        if (!response.isSuccess) return null
        return runCatching { JSONObject(response.body).optNullableString("token") }.getOrNull()
    }

    private fun persistAccessToken(accessToken: String) {
        secureStore.putString(KEY_ACCESS_TOKEN, accessToken)
        secureStore.remove(KEY_SESSION_TOKEN)
    }

    private fun nextPollDelayMillis(): Long {
        return secureStore.getLong(KEY_POLL_INTERVAL_SECONDS, DEFAULT_POLL_INTERVAL_SECONDS) * 1000L
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

    private fun getJson(url: String, token: String, extraHeaders: Map<String, String>): HttpResponse {
        val connection = openConnection(url)
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "token $token")
        extraHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
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

    data class CopilotDeviceAuthorization(
        val verificationUri: String,
        val userCode: String,
        val intervalMillis: Long
    )

    sealed class CopilotDevicePollResult {
        data object Authorized : CopilotDevicePollResult()
        data class Pending(val delayMillis: Long) : CopilotDevicePollResult()
        data class Failed(val reason: String) : CopilotDevicePollResult()
    }

    private data class HttpResponse(val statusCode: Int, val body: String) {
        val isSuccess: Boolean = statusCode in 200..299
    }

    companion object {
        const val DEFAULT_LOGIN_START_URL = "https://github.com/login/device"
        private const val CLIENT_ID = "Iv1.b507a08c87ecfe98"
        private const val SCOPE = "read:user"
        private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
        private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val USER_URL = "https://api.github.com/copilot_internal/user"
        private const val SESSION_TOKEN_URL = "https://api.github.com/copilot_internal/v2/token"
        private const val USER_AGENT = "GitHubCopilotChat/0.35.0"
        private const val PREFERENCES = "ai_usage_copilot_oauth"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_USER_CODE = "user_code"
        private const val KEY_VERIFICATION_URI = "verification_uri"
        private const val KEY_DEVICE_EXPIRES_AT = "device_expires_at"
        private const val KEY_POLL_INTERVAL_SECONDS = "poll_interval_seconds"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val DEFAULT_POLL_INTERVAL_SECONDS = 5L
        private const val DEFAULT_DEVICE_EXPIRES_SECONDS = 900L
        private const val NETWORK_TIMEOUT_MS = 20_000

        private fun copilotHeaders(): Map<String, String> = mapOf(
            "Editor-Version" to "vscode/1.107.0",
            "Editor-Plugin-Version" to "copilot-chat/0.35.0",
            "Copilot-Integration-Id" to "vscode-chat",
            "X-GitHub-Api-Version" to "2025-04-01",
            "User-Agent" to USER_AGENT
        )

        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun String.urlDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}
