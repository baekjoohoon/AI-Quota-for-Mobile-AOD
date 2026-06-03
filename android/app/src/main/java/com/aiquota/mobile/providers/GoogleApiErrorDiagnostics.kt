package com.aiquota.mobile.providers

import org.json.JSONObject

internal object GoogleApiErrorDiagnostics {
    fun safeSummary(statusCode: Int, body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val rootError = json?.opt("error")
        if (rootError is JSONObject) {
            return listOfNotNull(
                "status=$statusCode",
                rootError.optInt("code", -1).takeIf { it >= 0 }?.let { "apiCode=$it" },
                rootError.optNullableString("status")?.let { "apiStatus=$it" },
                errorInfoReason(rootError)?.let { "reason=$it" }
            ).joinToString(" ")
        }
        val error = when (rootError) {
            is String -> rootError
            else -> json?.optNullableString("error")
        }
        val description = json?.optNullableString("error_description")
        return listOfNotNull(
            "status=$statusCode",
            error?.let { "error=$it" },
            description?.let { "description=$it" }
        ).joinToString(" ")
    }

    fun providerDiagnostic(
        providerName: String,
        method: String,
        statusCode: Int,
        body: String
    ): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val rootError = json?.optJSONObject("error")
        return when (errorInfoReason(rootError)) {
            "SERVICE_DISABLED" -> "$providerName Cloud Code Private API is disabled for this OAuth project."
            "ACCESS_TOKEN_SCOPE_INSUFFICIENT" -> "$providerName Google token is missing the required Cloud Platform scope."
            "CONSUMER_INVALID" -> "$providerName Google OAuth project is not accepted by Cloud Code Private API."
            else -> "$providerName $method returned HTTP $statusCode."
        }
    }

    private fun errorInfoReason(rootError: JSONObject?): String? {
        val details = rootError?.optJSONArray("details") ?: return null
        for (index in 0 until details.length()) {
            val detail = details.optJSONObject(index) ?: continue
            val type = detail.optNullableString("@type")
            if (type != "type.googleapis.com/google.rpc.ErrorInfo") continue
            return detail.optNullableString("reason")
        }
        return null
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
