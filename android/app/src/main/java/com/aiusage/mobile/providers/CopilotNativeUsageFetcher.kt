package com.aiusage.mobile.providers

import android.webkit.CookieManager
import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

object CopilotNativeUsageFetcher {
    fun fetchUsagePayload(githubAccessToken: String? = null): String? {
        val githubAuthorizationHeader = githubAccessToken?.let(::githubTokenAuthorizationHeader)
        val entitlement = fetchWrappedJson(COPILOT_ENTITLEMENT_URL)
        val token = fetchWrappedJson(COPILOT_CHAT_TOKEN_URL)
        val internalUser = fetchWrappedJson(
            COPILOT_INTERNAL_USER_URL,
            authorizationHeader = githubAuthorizationHeader
                ?: copilotAuthorizationHeader(token.optJSONObject("json"))
                ?.takeIf(::isGitHubApiAuthorizationHeader)
        )
        val settingsPage = fetchWrappedJson(COPILOT_SETTINGS_URL)
        val billingPage = fetchWrappedJson(COPILOT_PREMIUM_USAGE_URL)
        val billingInput = if (billingPage.optBoolean("ok")) {
            extractPremiumBillingInput(billingPage.optJSONObject("json")?.optString("rawText").orEmpty())
        } else {
            null
        }
        val premiumCard = billingInput?.let { input ->
            fetchWrappedJson(premiumBillingCardUrl(input))
                .takeIf { it.optBoolean("ok") }
                ?.optJSONObject("json")
                ?.let(::premiumBillingObject)
        }
        Log.d(
            "AIUsageCopilot",
                "nativeFetch entitlement=${entitlement.optInt("status", -1)} " +
                "token=${token.optInt("status", -1)} " +
                "internal=${internalUser.optInt("status", -1)} " +
                "settings=${settingsPage.optInt("status", -1)} " +
                "billing=${billingPage.optInt("status", -1)} " +
                "billingInput=${billingInput != null} premiumCard=${premiumCard != null}"
        )
        val payload = buildUsagePayload(
            entitlement = entitlement.optJSONObject("json"),
            internalUser = internalUser.takeIf { it.optBoolean("ok") }?.optJSONObject("json"),
            settingsPage = copilotSettingsUsage(settingsPage.takeIf { it.optBoolean("ok") }?.optJSONObject("json")),
            premiumBilling = premiumCard,
            billingInput = billingInput
        )
        return payload?.takeIf(::hasCopilotUsage)?.toString()
    }

    fun fetchJson(url: String): String {
        return fetchWrappedJson(url).toString()
    }

    fun fetchJsonWithAuthorization(url: String, authorizationHeader: String): String {
        return fetchWrappedJson(url, authorizationHeader = authorizationHeader).toString()
    }

    internal fun extractPremiumBillingInput(rawText: String): CopilotPremiumBillingInput? {
        if (rawText.isBlank()) return null
        val markerIndex = rawText.indexOf("react-app.embeddedData")
        val scriptText = if (markerIndex >= 0) {
            val openEnd = rawText.indexOf(">", markerIndex)
            val closeStart = if (openEnd >= 0) rawText.indexOf("</script>", openEnd) else -1
            if (openEnd >= 0 && closeStart > openEnd) rawText.substring(openEnd + 1, closeStart) else null
        } else {
            null
        } ?: EMBEDDED_DATA_REGEX.find(rawText)?.groups?.get(1)?.value ?: return null
        val embedded = runCatching { JSONObject(htmlDecode(scriptText.trim())) }.getOrNull() ?: return null
        val payload = embedded.optJSONObject("payload") ?: embedded
        val customer = payload.optJSONObject("customer") ?: JSONObject()
        val firstCustomer = payload.optJSONArray("customer_selections")?.firstObject()
            ?: payload.optJSONArray("customerSelections")?.firstObject()
            ?: JSONObject()
        val customerId = customer.optionalString("customerId")
            ?: customer.optionalString("id")
            ?: payload.optionalString("customer_id")
            ?: firstCustomer.optionalString("customerId")
            ?: firstCustomer.optionalString("id")
            ?: return null
        val period = selectedPeriod(payload.optJSONArray("period_selections"))
            ?: selectedPeriod(payload.optJSONArray("periodSelections"))
            ?: "3"
        val account = customer.optionalString("displayId")
            ?: customer.optionalString("slug")
            ?: customer.optionalString("name")
            ?: firstCustomer.optionalString("displayId")
            ?: firstCustomer.optionalString("name")
        return CopilotPremiumBillingInput(customerId = customerId, period = period, account = account)
    }

    internal fun buildUsagePayload(
        entitlement: JSONObject?,
        internalUser: JSONObject?,
        settingsPage: JSONObject? = null,
        premiumBilling: JSONObject?,
        billingInput: CopilotPremiumBillingInput?
    ): JSONObject? {
        val raw = entitlement ?: JSONObject()
        val quotas = raw.optJSONObject("quotas")
            ?: raw.optJSONObject("quota")
            ?: raw.optJSONObject("usage")
            ?: JSONObject()
        quotaSnapshots(internalUser ?: raw)?.let { snapshots ->
            if (!quotas.has("quota_snapshots")) quotas.put("quota_snapshots", snapshots)
        }
        copyObjectIfPresent(internalUser, quotas, "limited_user_quotas", "limitedUserQuotas")
        copyObjectIfPresent(internalUser, quotas, "monthly_quotas", "monthlyQuotas")
        copyObjectIfPresent(settingsPage, quotas, "limited_user_quotas", "limitedUserQuotas")
        copyObjectIfPresent(settingsPage, quotas, "monthly_quotas", "monthlyQuotas")
        premiumBilling?.let { quotas.put("premium_billing", it) }
        val resetDate = internalUser.optionalString("quota_reset_date")
            ?: internalUser.optionalString("resetDate")
            ?: settingsPage.optionalString("quota_reset_date")
            ?: settingsPage.optionalString("resetDate")
            ?: settingsPage.optionalString("limited_user_reset_date")
            ?: raw.optionalString("quota_reset_date")
            ?: raw.optionalString("resetDate")
            ?: quotas.optionalString("quota_reset_date")
            ?: quotas.optionalString("resetDate")
        resetDate?.let {
            if (!quotas.has("quota_reset_date")) quotas.put("quota_reset_date", it)
        }
        val account = raw.optionalString("account")
            ?: raw.optionalString("login")
            ?: raw.optionalString("user_login")
            ?: raw.optJSONObject("user")?.optionalString("login")
            ?: raw.optJSONObject("user")?.optionalString("name")
            ?: internalUser.optionalString("account")
            ?: internalUser.optionalString("login")
            ?: internalUser.optionalString("user_login")
            ?: settingsPage.optionalString("account")
            ?: settingsPage.optionalString("login")
            ?: settingsPage.optionalString("user_login")
            ?: billingInput?.account
        return JSONObject()
            .put("provider", "copilot")
            .put("plan", raw.optionalString("plan") ?: raw.optionalString("sku") ?: raw.optionalString("licenseType"))
            .put("account", account)
            .put("quotas", quotas)
    }

    private fun fetchWrappedJson(url: String, authorizationHeader: String? = null): JSONObject {
        val endpoint = copilotEndpoint(url)
            ?: return JSONObject().put("ok", false).put("error", "blocked_copilot_endpoint")
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT_MS
                readTimeout = NETWORK_TIMEOUT_MS
                requestMethod = if (endpoint == "/github-copilot/chat/token") "POST" else "GET"
                setRequestProperty("Accept", "application/vnd.github+json, application/json, text/html")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ProviderWebViewUserAgent.loginUserAgent())
                setRequestProperty("Referer", "https://github.com/settings/copilot")
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                if (endpoint == "/copilot_internal/user") {
                    authorizationHeader?.takeIf(::isAllowedCopilotAuthorizationHeader)
                        ?.let { setRequestProperty("Authorization", it) }
                }
                copilotCookieHeader()?.let { setRequestProperty("Cookie", it) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            val parsed = runCatching { JSONObject(text) }.getOrNull()
            JSONObject()
                .put("ok", status in 200..299)
                .put("status", status)
                .put("endpoint", endpoint)
                .put("json", parsed ?: JSONObject().put("rawText", text.take(rawTextLimit(endpoint))))
        }.getOrElse { error ->
            JSONObject()
                .put("ok", false)
                .put("endpoint", endpoint)
                .put("error", error.javaClass.simpleName)
        }
    }

    private fun premiumBillingCardUrl(input: CopilotPremiumBillingInput): String {
        return "https://github.com/settings/billing/copilot_usage_card" +
            "?customer_id=${input.customerId.urlEncode()}&period=${input.period.urlEncode()}&query="
    }

    private fun premiumBillingObject(json: JSONObject): JSONObject {
        return json.optJSONObject("payload")
            ?: json.optJSONObject("data")
            ?: json
    }

    private fun quotaSnapshots(json: JSONObject?): JSONObject? {
        if (json == null) return null
        return json.optJSONObject("quota_snapshots")
            ?: json.optJSONObject("quotaSnapshots")
            ?: json.optJSONObject("quotas")?.optJSONObject("quota_snapshots")
            ?: json.optJSONObject("quotas")?.optJSONObject("quotaSnapshots")
    }

    private fun copilotSettingsUsage(json: JSONObject?): JSONObject? {
        if (json == null) return null
        findCopilotQuotaEnvelope(json)?.let { return it }
        val rawText = json.optionalString("rawText") ?: return null
        val candidates = buildList {
            EMBEDDED_DATA_REGEX.findAll(rawText).forEach { match ->
                runCatching { JSONObject(htmlDecode(match.groups[1]?.value.orEmpty().trim())) }
                    .getOrNull()
                    ?.let(::add)
            }
            JSON_SCRIPT_REGEX.findAll(rawText).forEach { match ->
                runCatching { JSONObject(htmlDecode(match.groups[1]?.value.orEmpty().trim())) }
                    .getOrNull()
                    ?.let(::add)
            }
        }
        candidates.forEach { candidate ->
            findCopilotQuotaEnvelope(candidate)?.let { return it }
        }
        return null
    }

    internal fun copilotAuthorizationHeader(json: JSONObject?): String? {
        if (json == null) return null
        val direct = json.optionalString("authorizationHeaderValue")
            ?: json.optionalString("authorization_header_value")
            ?: json.optionalString("authorizationHeader")
        if (direct != null && isAllowedCopilotAuthorizationHeader(direct)) return direct
        val token = json.optionalString("token")
            ?: json.optionalString("value")
            ?: json.optionalString("accessToken")
            ?: json.optionalString("access_token")
            ?: return null
        return "GitHub-Bearer $token".takeIf(::isAllowedCopilotAuthorizationHeader)
    }

    private fun isAllowedCopilotAuthorizationHeader(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.any { it == '\r' || it == '\n' }) return false
        return trimmed.startsWith("GitHub-Bearer ") || trimmed.startsWith("Bearer ") || trimmed.startsWith("token ")
    }

    private fun isGitHubApiAuthorizationHeader(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("Bearer ") || trimmed.startsWith("token ")
    }

    private fun githubTokenAuthorizationHeader(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.any { it == '\r' || it == '\n' }) return null
        return if (trimmed.startsWith("Bearer ") || trimmed.startsWith("token ")) {
            trimmed
        } else {
            "token $trimmed"
        }
    }

    private fun findCopilotQuotaEnvelope(value: Any?, depth: Int = 0): JSONObject? {
        if (value == null || depth > 8) return null
        when (value) {
            is JSONObject -> {
                if (value.has("limited_user_quotas") || value.has("limitedUserQuotas") ||
                    value.has("monthly_quotas") || value.has("monthlyQuotas") ||
                    value.has("quota_snapshots") || value.has("quotaSnapshots")
                ) {
                    return value
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    findCopilotQuotaEnvelope(value.opt(keys.next()), depth + 1)?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    findCopilotQuotaEnvelope(value.opt(index), depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    private fun hasCopilotUsage(payload: JSONObject): Boolean {
        val quotas = payload.optJSONObject("quotas") ?: return false
        if (quotas.has("quota_snapshots") || quotas.has("quotaSnapshots")) return true
        if (quotas.has("premium_billing") || quotas.has("premiumBilling")) return true
        if (quotas.has("premium_requests") || quotas.has("premiumRequests")) return true
        if (quotas.has("chat") || quotas.has("completions")) return true
        val remaining = quotas.optJSONObject("remaining") ?: return false
        return remaining.has("chat") || remaining.has("completions") || remaining.has("premiumInteractions")
    }

    private fun selectedPeriod(periods: JSONArray?): String? {
        if (periods == null) return null
        for (index in 0 until periods.length()) {
            val period = periods.optJSONObject(index) ?: continue
            if (period.optBoolean("selected") || period.optBoolean("default") || period.optInt("type", -1) == 3) {
                return period.optionalString("type")
                    ?: period.optionalString("id")
                    ?: period.optionalString("value")
            }
        }
        return null
    }

    private fun copilotCookieHeader(): String? {
        val values = listOf(
            CookieManager.getInstance().getCookie("https://github.com"),
            CookieManager.getInstance().getCookie("https://www.github.com"),
            CookieManager.getInstance().getCookie("https://api.github.com")
        ).mapNotNull { it?.takeIf(String::isNotBlank) }
        return values.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private fun copilotEndpoint(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty()
        val allowed = when (host) {
            "github.com", "www.github.com" -> path in setOf(
                "/github-copilot/chat/entitlement",
                "/github-copilot/chat/token",
                "/github-copilot/chat",
                "/github-copilot/chat/deferred_payload.json",
                "/settings/copilot",
                "/settings/billing/premium_requests_usage",
                "/settings/billing/copilot_usage_card"
            )
            "api.github.com" -> path == "/copilot_internal/user"
            else -> false
        }
        return if (allowed) path else null
    }

    private fun rawTextLimit(endpoint: String): Int {
        return if (endpoint == "/settings/billing/premium_requests_usage" || endpoint == "/settings/copilot") 1_000_000 else 4_000
    }

    private fun copyObjectIfPresent(source: JSONObject?, target: JSONObject, snakeKey: String, camelKey: String) {
        val value = source?.optJSONObject(snakeKey)
            ?: source?.optJSONObject(camelKey)
            ?: source?.optJSONObject("quotas")?.optJSONObject(snakeKey)
            ?: source?.optJSONObject("quotas")?.optJSONObject(camelKey)
            ?: return
        if (!target.has(snakeKey) && !target.has(camelKey)) {
            target.put(snakeKey, value)
        }
    }

    private fun JSONArray.firstObject(): JSONObject? {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { return it }
        }
        return null
    }

    private fun JSONObject?.optionalString(key: String): String? {
        if (this == null || !has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun htmlDecode(value: String): String {
        return value
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun String.urlEncode(): String {
        return java.net.URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    }

    private val EMBEDDED_DATA_REGEX =
        Regex("""<script[^>]+data-target=["']react-app\.embeddedData["'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
    private val JSON_SCRIPT_REGEX =
        Regex("""<script[^>]+type=["']application/json["'][^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)

    private const val COPILOT_ENTITLEMENT_URL = "https://github.com/github-copilot/chat/entitlement"
    private const val COPILOT_CHAT_TOKEN_URL = "https://github.com/github-copilot/chat/token"
    private const val COPILOT_INTERNAL_USER_URL = "https://api.github.com/copilot_internal/user"
    private const val COPILOT_SETTINGS_URL = "https://github.com/settings/copilot"
    private const val COPILOT_PREMIUM_USAGE_URL = "https://github.com/settings/billing/premium_requests_usage"
    private const val NETWORK_TIMEOUT_MS = 20_000
}

internal data class CopilotPremiumBillingInput(
    val customerId: String,
    val period: String,
    val account: String?
)
