package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.json.JSONObject
import java.net.URI
import java.util.Locale

object ProviderCollectionDiagnostics {
    const val TAG = "AIUsageProvider"

    fun safeUrl(url: String?): String {
        val value = url?.trim().orEmpty()
        if (value.isBlank()) return "blank"
        val uri = runCatching { URI(value) }.getOrNull() ?: return "invalid"
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        if (scheme.isBlank() || host.isBlank()) return "invalid"
        val path = uri.rawPath.orEmpty().ifBlank { "/" }
        return "$scheme://$host$path"
    }

    fun webError(errorCode: Int, description: CharSequence?): String {
        return "code=$errorCode description=${safeDescription(description)}"
    }

    fun payloadSummary(providerId: ProviderId, payload: String): String {
        val trimmed = payload.trim()
        val root = runCatching { JSONObject(trimmed) }.getOrNull()
            ?: return "provider=${providerId.storageId} payload=text length=${trimmed.length}"
        val completion = root.optJSONObject("c")
        val data = root.optJSONObject("d") ?: root.optJSONObject("data")
        val usage = root.optJSONObject("usage")
        val account = root.optJSONObject("account")
        val limits = data?.optJSONArray("x")
            ?: data?.optJSONArray("limits")
            ?: usage?.optJSONArray("x")
            ?: usage?.optJSONArray("limits")
        val planPresent = data?.nonBlankValue("p") == true ||
            data?.nonBlankValue("plan") == true ||
            data?.nonBlankValue("planLabel") == true ||
            account?.nonBlankValue("p") == true ||
            account?.nonBlankValue("plan") == true ||
            account?.nonBlankValue("planLabel") == true
        val debug = root.optJSONArray("q")
            ?.let { summaries ->
                buildList {
                    for (index in 0 until summaries.length().coerceAtMost(8)) {
                        val summary = summaries.optJSONObject(index) ?: continue
                        val endpoint = summary.optString("e").ifBlank { "unknown" }
                        val statusCode = summary.optInt("s", -1)
                        val limitKeys = summary.optInt("lk", 0)
                        val remainingKeys = summary.optInt("rk", 0)
                        val usageKeys = summary.optInt("uk", 0)
                        val planKeys = summary.optInt("pk", 0)
                        val keyPaths = summary.optJSONArray("k")
                            ?.let { keys ->
                                buildList {
                                    for (keyIndex in 0 until keys.length().coerceAtMost(3)) {
                                        val keyPath = keys.optString(keyIndex).trim()
                                        if (keyPath.isNotBlank()) add(keyPath.take(80))
                                    }
                                }.joinToString(",")
                            }
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "[$it]" }
                            .orEmpty()
                        val metricValues = summary.optJSONArray("v")
                            ?.let { values ->
                                buildList {
                                    for (valueIndex in 0 until values.length().coerceAtMost(4)) {
                                        val metric = values.optString(valueIndex).trim()
                                        if (metric.isNotBlank()) add(metric.take(80))
                                    }
                                }.joinToString(",")
                            }
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "{$it}" }
                            .orEmpty()
                        add("$endpoint:$statusCode/l$limitKeys/r$remainingKeys/u$usageKeys/p$planKeys$keyPaths$metricValues")
                    }
                }.joinToString("|")
            }
            ?.takeIf { it.isNotBlank() }
        val status = root.optString("s")
            .ifBlank { root.optString("status") }
            .ifBlank { "unknown" }
        val flags = listOfNotNull(
            completion?.optBoolean("login")?.let { "login=$it" },
            completion?.optBoolean("providerPage")?.let { "providerPage=$it" },
            completion?.optBoolean("authenticatedApp")?.let { "authenticatedApp=$it" },
            completion?.optInt("textLength")?.let { "textLength=$it" }
        ).joinToString(",")

        return "provider=${providerId.storageId} payload=json status=$status " +
            "lines=${limits?.length() ?: 0} plan=$planPresent flags=[$flags] " +
            "debug=${debug ?: "none"} length=${trimmed.length}"
    }

    private fun safeDescription(description: CharSequence?): String {
        return description
            ?.toString()
            ?.replace(Regex("\\s+"), " ")
            ?.take(96)
            ?.ifBlank { "blank" }
            ?: "none"
    }

    private fun JSONObject.nonBlankValue(name: String): Boolean {
        if (!has(name) || isNull(name)) return false
        return optString(name).trim().isNotBlank()
    }
}
