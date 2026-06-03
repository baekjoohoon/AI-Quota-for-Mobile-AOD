package com.aiquota.mobile.providers

import android.util.Log
import android.webkit.CookieManager
import com.aiquota.mobile.local.ProviderId
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object GoogleWebSessionCodeAssistFetcher {
    fun hasSessionCookie(providerId: ProviderId): Boolean {
        return sapisidFromCookieHeader(googleCookieHeader(providerId)) != null
    }

    fun bridgeUsagePayload(providerId: ProviderId): String {
        val result = fetchUsagePayload(providerId)
        return JSONObject()
            .put("ok", result.payload != null)
            .put("provider", providerId.storageId)
            .put("diagnostic", result.diagnostic)
            .put("statuses", JSONArray().apply { result.statuses.forEach(::put) })
            .put("payload", result.payload?.let { JSONObject(it) } ?: JSONObject.NULL)
            .toString()
    }

    fun fetchUsagePayload(providerId: ProviderId): UsageResult {
        return when (providerId) {
            ProviderId.GEMINI -> fetchGeminiUsagePayload()
            ProviderId.ANTIGRAVITY -> fetchAntigravityUsagePayload()
            else -> UsageResult(null, "unsupported_provider", emptyList())
        }
    }

    private fun fetchGeminiUsagePayload(): UsageResult {
        val statuses = mutableListOf<String>()
        fun postJson(method: String, body: String): JSONObject? {
            val response = postCodeAssist(ProviderId.GEMINI, method, body)
            statuses += "$method:${response.statusCode}"
            if (!response.isSuccess) return null
            return runCatching { JSONObject(response.body) }.getOrNull()
        }

        val initialLoad = postJson("loadCodeAssist", GeminiCliOAuthRepository.loadCodeAssistPayload(projectId = null))
            ?: return UsageResult(null, "gemini_load_code_assist_failed", statuses)
        var loadJson = initialLoad
        var projectId = projectIdFromLoad(initialLoad)
        if (projectId.isNullOrBlank()) {
            projectId = onboardGeminiUser(initialLoad, statuses)
            if (!projectId.isNullOrBlank()) {
                loadJson = postJson("loadCodeAssist", GeminiCliOAuthRepository.loadCodeAssistPayload(projectId))
                    ?: initialLoad
            }
        }
        if (projectId.isNullOrBlank()) {
            return UsageResult(null, "gemini_project_unavailable", statuses)
        }

        val quotaJson = postJson("retrieveUserQuota", GeminiCliOAuthRepository.retrieveUserQuotaPayload(projectId))
            ?: return UsageResult(null, "gemini_retrieve_quota_failed", statuses)
        if (GeminiCliOAuthRepository.trustedBucketCount(quotaJson) == 0) {
            return UsageResult(null, "gemini_no_trusted_quota", statuses)
        }
        val payload = GeminiCliOAuthRepository.structuredPayloadFromCodeAssist(
            loadJson = loadJson,
            quotaJson = quotaJson,
            email = null
        )
        return UsageResult(payload, if (payload == null) "gemini_payload_normalizer_rejected" else null, statuses)
    }

    private fun onboardGeminiUser(loadJson: JSONObject, statuses: MutableList<String>): String? {
        val tier = defaultAllowedTier(loadJson) ?: return null
        val tierId = tier.optNullableString("id") ?: return null
        val onboard = postCodeAssist(
            ProviderId.GEMINI,
            "onboardUser",
            GeminiCliOAuthRepository.onboardUserPayload(tierId = tierId, projectId = null)
        )
        statuses += "onboardUser:${onboard.statusCode}"
        var operation = runCatching { JSONObject(onboard.body) }.getOrNull() ?: return null
        repeat(OPERATION_POLL_ATTEMPTS) {
            if (operation.optBoolean("done", false)) return projectIdFromOperation(operation)
            val name = operation.optNullableString("name") ?: return null
            Thread.sleep(OPERATION_POLL_DELAY_MS)
            val response = getCodeAssistOperation(ProviderId.GEMINI, name)
            statuses += "getOperation:${response.statusCode}"
            if (!response.isSuccess) return null
            operation = runCatching { JSONObject(response.body) }.getOrNull() ?: return null
        }
        return projectIdFromOperation(operation)
    }

    private fun fetchAntigravityUsagePayload(): UsageResult {
        val statuses = mutableListOf<String>()
        fun postJson(method: String, body: String): JSONObject? {
            val response = postCodeAssist(ProviderId.ANTIGRAVITY, method, body)
            statuses += "$method:${response.statusCode}"
            if (!response.isSuccess) return null
            return runCatching { JSONObject(response.body) }.getOrNull()
        }

        val loadJson = postJson("loadCodeAssist", AntigravityOAuthRepository.loadCodeAssistPayload())
        val projectId = loadJson?.let(::projectIdFromLoad)
        val modelsJson = postJson("fetchAvailableModels", AntigravityOAuthRepository.fetchAvailableModelsPayload(projectId))
            ?: return UsageResult(null, "antigravity_fetch_models_failed", statuses)
        if (AntigravityOAuthRepository.trustedModelQuotaCount(modelsJson) == 0) {
            return UsageResult(null, "antigravity_no_trusted_quota", statuses)
        }
        val payload = AntigravityOAuthRepository.structuredPayloadFromAvailableModels(
            modelsJson = modelsJson.toString(),
            account = null,
            plan = loadJson?.let(::planLabel)
        )
        return UsageResult(payload, if (payload == null) "antigravity_payload_normalizer_rejected" else null, statuses)
    }

    private fun postCodeAssist(providerId: ProviderId, method: String, body: String): HttpResponse {
        if (!isAllowedMethod(providerId, method)) {
            return HttpResponse(0, "", "blocked_method")
        }
        return requestCodeAssist(
            providerId = providerId,
            url = "$CODE_ASSIST_BASE_URL/v1internal:$method",
            method = "POST",
            body = body
        )
    }

    private fun getCodeAssistOperation(providerId: ProviderId, name: String): HttpResponse {
        val sanitizedName = name.trim().trimStart('/')
        if (sanitizedName.isBlank() || sanitizedName.contains("..")) {
            return HttpResponse(0, "", "blocked_operation")
        }
        return requestCodeAssist(
            providerId = providerId,
            url = "$CODE_ASSIST_BASE_URL/$sanitizedName",
            method = "GET",
            body = null
        )
    }

    private fun requestCodeAssist(
        providerId: ProviderId,
        url: String,
        method: String,
        body: String?
    ): HttpResponse {
        val profiles = requestProfilesFor(providerId)
        if (profiles.isEmpty()) return HttpResponse(0, "", "unsupported_origin")
        val cookieHeader = googleCookieHeader(providerId)
        val credentials = sapisidCredentialsFromCookieHeader(cookieHeader)
        if (credentials.isEmpty()) {
            return HttpResponse(0, "", "missing_google_web_session_cookie").also {
                Log.w(TAG, "provider=${providerId.storageId} webSessionAuth missingCookie cookieCount=${parseCookieHeader(cookieHeader).size}")
            }
        }

        var lastResponse: HttpResponse? = null
        for (profile in profiles) {
            for (credential in credentials) {
                val response = executeCodeAssistRequest(
                    providerId = providerId,
                    url = url,
                    method = method,
                    body = body,
                    cookieHeader = cookieHeader,
                    sapisid = credential,
                    profile = profile
                )
                lastResponse = response
                if (response.isSuccess || !response.shouldRetryWithNextProfile()) return response
            }
        }
        return lastResponse ?: HttpResponse(0, "", "no_auth_profile")
    }

    private fun googleCookieHeader(providerId: ProviderId): String {
        return mergeCookieHeaders(cookieUrlsFor(providerId).map { urlForCookies ->
            runCatching { CookieManager.getInstance().getCookie(urlForCookies) }.getOrNull()
        })
    }

    private fun executeCodeAssistRequest(
        providerId: ProviderId,
        url: String,
        method: String,
        body: String?,
        cookieHeader: String,
        sapisid: SapisidCredential,
        profile: WebSessionRequestProfile
    ): HttpResponse {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Authorization", sapisidAuthorizationHeader(Instant.now().epochSecond, sapisid, profile.authOrigin))
                setRequestProperty("Origin", profile.originHeader)
                setRequestProperty("Referer", profile.refererHeader)
                setRequestProperty("X-Origin", profile.xOriginHeader)
                setRequestProperty("X-Goog-AuthUser", "0")
                if (cookieHeader.isNotBlank()) {
                    setRequestProperty("Cookie", cookieHeader)
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }
            connection.response().also { response ->
                Log.i(
                    TAG,
                    "provider=${providerId.storageId} webSessionCodeAssist method=$method status=${response.statusCode} " +
                        "profile=${profile.label} " +
                        "credential=${sapisid.label} " +
                        "authCookie=true cookieCount=${parseCookieHeader(cookieHeader).size} bodyLength=${response.body.length} " +
                        "error=${response.safeErrorSummary()}"
                )
            }
        }.getOrElse { error ->
            Log.w(
                TAG,
                "provider=${providerId.storageId} webSessionCodeAssist method=$method profile=${profile.label} credential=${sapisid.label} exception=${error.javaClass.simpleName}"
            )
            HttpResponse(0, "", error.javaClass.simpleName)
        }
    }

    private fun HttpURLConnection.response(): HttpResponse {
        val status = responseCode
        val stream = if (status in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        disconnect()
        return HttpResponse(status, body, null)
    }

    fun isAllowedMethod(providerId: ProviderId, method: String): Boolean {
        return when (providerId) {
            ProviderId.GEMINI -> method in setOf("loadCodeAssist", "onboardUser", "retrieveUserQuota")
            ProviderId.ANTIGRAVITY -> method in setOf("loadCodeAssist", "fetchAvailableModels")
            else -> false
        }
    }

    fun mergeCookieHeaders(headers: List<String?>): String {
        val cookies = linkedMapOf<String, String>()
        headers.forEach { header ->
            parseCookieHeader(header.orEmpty()).forEach { (name, value) ->
                cookies.putIfAbsent(name, value)
            }
        }
        return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    }

    fun sapisidFromCookieHeader(cookieHeader: String): String? {
        return sapisidCredentialsFromCookieHeader(cookieHeader).firstOrNull()?.value
    }

    internal fun sapisidCredentialsFromCookieHeader(cookieHeader: String): List<SapisidCredential> {
        val cookies = parseCookieHeader(cookieHeader)
        return listOf("__Secure-3PAPISID", "__Secure-1PAPISID", "SAPISID", "APISID")
            .mapNotNull { name ->
                cookies[name]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { SapisidCredential(name, it) }
            }
    }

    fun sapisidAuthorizationHeader(timestampSeconds: Long, sapisid: String, origin: String): String {
        val digestInput = "$timestampSeconds $sapisid $origin"
        return "SAPISIDHASH ${timestampSeconds}_${sha1Hex(digestInput)}"
    }

    private fun sapisidAuthorizationHeader(timestampSeconds: Long, credential: SapisidCredential, origin: String): String {
        val digestInput = "$timestampSeconds ${credential.value} $origin"
        return "${credential.authScheme} ${timestampSeconds}_${sha1Hex(digestInput)}"
    }

    internal fun parseCookieHeader(cookieHeader: String): Map<String, String> {
        return cookieHeader.split(";")
            .mapNotNull { part ->
                val index = part.indexOf("=")
                if (index <= 0) return@mapNotNull null
                val name = part.substring(0, index).trim()
                val value = part.substring(index + 1).trim()
                if (name.isBlank() || value.isBlank()) null else name to value
            }
            .toMap(linkedMapOf())
    }

    private fun sha1Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    }

    internal fun requestProfilesFor(providerId: ProviderId): List<WebSessionRequestProfile> {
        val providerOrigin = providerPageOriginFor(providerId) ?: return emptyList()
        val apiOrigin = CODE_ASSIST_BASE_URL
        return listOf(
            WebSessionRequestProfile(
                label = "browser_api_x_origin",
                authOrigin = apiOrigin,
                originHeader = providerOrigin,
                refererHeader = "$providerOrigin/",
                xOriginHeader = apiOrigin
            ),
            WebSessionRequestProfile(
                label = "browser_provider_auth",
                authOrigin = providerOrigin,
                originHeader = providerOrigin,
                refererHeader = "$providerOrigin/",
                xOriginHeader = apiOrigin
            ),
            WebSessionRequestProfile(
                label = "api_origin_provider_auth",
                authOrigin = providerOrigin,
                originHeader = apiOrigin,
                refererHeader = "$providerOrigin/",
                xOriginHeader = apiOrigin
            ),
            WebSessionRequestProfile(
                label = "provider_page",
                authOrigin = providerOrigin,
                originHeader = providerOrigin,
                refererHeader = "$providerOrigin/",
                xOriginHeader = providerOrigin
            ),
            WebSessionRequestProfile(
                label = "api_origin",
                authOrigin = apiOrigin,
                originHeader = apiOrigin,
                refererHeader = "$apiOrigin/",
                xOriginHeader = apiOrigin
            ),
            WebSessionRequestProfile(
                label = "legacy_mixed",
                authOrigin = providerOrigin,
                originHeader = apiOrigin,
                refererHeader = "$providerOrigin/",
                xOriginHeader = providerOrigin
            ),
            WebSessionRequestProfile(
                label = "api_auth_provider_context",
                authOrigin = apiOrigin,
                originHeader = providerOrigin,
                refererHeader = "$providerOrigin/",
                xOriginHeader = providerOrigin
            )
        )
    }

    private fun providerPageOriginFor(providerId: ProviderId): String? {
        return when (providerId) {
            ProviderId.GEMINI -> "https://gemini.google.com"
            ProviderId.ANTIGRAVITY -> "https://antigravity.google"
            else -> null
        }
    }

    private fun cookieUrlsFor(providerId: ProviderId): List<String> {
        return buildList {
            add("https://accounts.google.com")
            add("https://google.com")
            add("https://www.google.com")
            add("https://cloudcode-pa.googleapis.com")
            when (providerId) {
                ProviderId.GEMINI -> add("https://gemini.google.com")
                ProviderId.ANTIGRAVITY -> add("https://antigravity.google")
                else -> Unit
            }
        }
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
            ?: loadJson.optNullableString("project")
            ?: loadJson.optJSONObject("project")?.optNullableString("id")
    }

    private fun projectIdFromOperation(operation: JSONObject): String? {
        return operation.optJSONObject("response")
            ?.optJSONObject("cloudaicompanionProject")
            ?.optNullableString("id")
    }

    private fun planLabel(loadJson: JSONObject): String? {
        return loadJson.optJSONObject("paidTier")?.optNullableString("name")
            ?: loadJson.optJSONObject("currentTier")?.optNullableString("name")
            ?: loadJson.optJSONObject("tier")?.optNullableString("name")
            ?: loadJson.optJSONObject("plan")?.optNullableString("name")
            ?: loadJson.optNullableString("plan")
            ?: loadJson.optNullableString("tier")
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    data class UsageResult(
        val payload: String?,
        val diagnostic: String?,
        val statuses: List<String>
    )

    private data class HttpResponse(
        val statusCode: Int,
        val body: String,
        val error: String?
    ) {
        val isSuccess: Boolean = statusCode in 200..299

        fun safeErrorSummary(): String {
            if (isSuccess) return "none"
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return error ?: "none"
            val rootError = json.optJSONObject("error")
            return listOfNotNull(
                rootError?.optString("status")?.takeIf { it.isNotBlank() },
                rootError?.optString("reason")?.takeIf { it.isNotBlank() },
                rootError?.optString("message")?.takeIf { it.isNotBlank() }?.take(120)
            ).joinToString("|").ifBlank { error ?: "none" }
        }

        fun shouldRetryWithNextProfile(): Boolean {
            return statusCode in setOf(400, 401, 403)
        }
    }

    internal data class WebSessionRequestProfile(
        val label: String,
        val authOrigin: String,
        val originHeader: String,
        val refererHeader: String,
        val xOriginHeader: String
    )

    internal data class SapisidCredential(
        val label: String,
        val value: String
    ) {
        val authScheme: String = when (label) {
            "__Secure-3PAPISID" -> "SAPISID3PHASH"
            "__Secure-1PAPISID" -> "SAPISID1PHASH"
            else -> "SAPISIDHASH"
        }
    }

    private const val TAG = "AIQuotaGoogleWeb"
    private const val CODE_ASSIST_BASE_URL = "https://cloudcode-pa.googleapis.com"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
    private const val NETWORK_TIMEOUT_MS = 10_000
    private const val OPERATION_POLL_ATTEMPTS = 8
    private const val OPERATION_POLL_DELAY_MS = 2_000L
}
