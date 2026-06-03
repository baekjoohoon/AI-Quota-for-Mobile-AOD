package com.aiquota.mobile.local

import org.json.JSONArray
import org.json.JSONObject

object WidgetCacheSanitizer {
    fun toDisplayOnlyJson(
        snapshots: List<ProviderUsageSnapshot>,
        order: List<ProviderId>,
        hidden: Set<ProviderId>,
        updatedAt: String
    ): String {
        val byProvider = snapshots.associateBy { it.providerId }
        val providers = JSONArray()
        ProviderPreferencesCodec.visibleProviders(order, hidden).forEach { providerId ->
            val snapshot = byProvider[providerId] ?: ProviderUsageSnapshot.disconnected(providerId)
            providers.put(snapshot.toDisplayOnlyJson())
        }
        val root = JSONObject()
            .put(KEY_SCHEMA, SCHEMA)
            .put(KEY_UPDATED_AT, updatedAt)
            .put(KEY_PROVIDERS, providers)
        return sanitizeDisplayOnlyJson(root.toString())
    }

    fun sanitizeDisplayOnlyJson(snapshotJson: String): String {
        val root = JSONObject(snapshotJson)
        val sanitized = JSONObject()
            .put(KEY_SCHEMA, SCHEMA)
            .put(KEY_UPDATED_AT, root.optionalString(KEY_UPDATED_AT).orEmpty())
            .put(KEY_PROVIDERS, root.optJSONArray(KEY_PROVIDERS).sanitizeProviders())
        requireNoSensitiveKeys(sanitized)
        return sanitized.toString()
    }

    private fun ProviderUsageSnapshot.toDisplayOnlyJson(): JSONObject {
        return JSONObject()
            .put(KEY_PROVIDER_ID, providerId.storageId)
            .put(KEY_DISPLAY_NAME, providerId.normalizedDisplayName(displayName))
            .put(KEY_CONNECTION_STATE, connectionState.name)
            .put(KEY_REFRESH_STATE, refreshState.name)
            .putNullable(KEY_PLAN_LABEL, planLabel)
            .put(KEY_UPDATED_AT, updatedAt)
            .putNullable(KEY_MESSAGE, message)
            .put(KEY_VISIBLE, true)
            .put(KEY_LINES, JSONArray().also { array ->
                lines.forEach { line ->
                    array.put(line.toDisplayOnlyJson())
                }
            })
    }

    private fun ProviderUsageLine.toDisplayOnlyJson(): JSONObject {
        return JSONObject()
            .put(KEY_LABEL, label)
            .putNullable(KEY_REMAINING_PERCENT, remainingPercent?.toDouble())
            .put(KEY_REMAINING_TEXT, remainingText)
            .putNullable(KEY_RESET_TEXT, resetText)
            .putNullable(KEY_DETAIL_TEXT, detailText)
            .put(KEY_SEVERITY, severity.name)
            .putNullable(KEY_USED, usedAmount)
            .putNullable(KEY_LIMIT, limitAmount)
            .putNullable(KEY_REMAINING, remainingAmount)
            .putNullable(KEY_UNIT, unit)
            .putNullable(KEY_CATEGORY, category)
            .putNullable(KEY_WINDOW_TEXT, windowText)
            .putNullable(KEY_STARTS_AT, startsAt)
            .putNullable(KEY_RESETS_AT, resetsAt)
            .putNullable(KEY_CONFIDENCE, confidence?.toDouble())
    }

    private fun JSONArray?.sanitizeProviders(): JSONArray {
        val providers = JSONArray()
        if (this == null) return providers
        for (index in 0 until length()) {
            val provider = optJSONObject(index) ?: continue
            providers.put(provider.sanitizeProvider())
        }
        return providers
    }

    private fun JSONObject.sanitizeProvider(): JSONObject {
        val providerId = optionalString(KEY_PROVIDER_ID).orEmpty().ifBlank { "unknown" }
        return JSONObject()
            .put(KEY_PROVIDER_ID, providerId)
            .put(KEY_DISPLAY_NAME, normalizedDisplayName(providerId, optionalString(KEY_DISPLAY_NAME)))
            .put(
                KEY_CONNECTION_STATE,
                optionalString(KEY_CONNECTION_STATE).orEmpty().ifBlank {
                    ProviderConnectionState.DISCONNECTED.name
                }
            )
            .put(
                KEY_REFRESH_STATE,
                optionalString(KEY_REFRESH_STATE).orEmpty().ifBlank { ProviderRefreshState.IDLE.name }
            )
            .putNullable(KEY_PLAN_LABEL, optionalString(KEY_PLAN_LABEL))
            .put(KEY_UPDATED_AT, optionalString(KEY_UPDATED_AT).orEmpty())
            .putNullable(KEY_MESSAGE, optionalString(KEY_MESSAGE))
            .put(KEY_VISIBLE, if (has(KEY_VISIBLE)) optBoolean(KEY_VISIBLE, true) else true)
            .put(KEY_LINES, optJSONArray(KEY_LINES).sanitizeLines())
    }

    private fun JSONArray?.sanitizeLines(): JSONArray {
        val lines = JSONArray()
        if (this == null) return lines
        for (index in 0 until length()) {
            val line = optJSONObject(index) ?: continue
            lines.put(line.sanitizeLine())
        }
        return lines
    }

    private fun JSONObject.sanitizeLine(): JSONObject {
        return JSONObject()
            .put(KEY_LABEL, optionalString(KEY_LABEL).orEmpty())
            .putNullable(KEY_REMAINING_PERCENT, optionalDouble(KEY_REMAINING_PERCENT))
            .put(KEY_REMAINING_TEXT, optionalString(KEY_REMAINING_TEXT).orEmpty())
            .putNullable(KEY_RESET_TEXT, optionalString(KEY_RESET_TEXT))
            .putNullable(KEY_DETAIL_TEXT, optionalString(KEY_DETAIL_TEXT))
            .put(KEY_SEVERITY, optionalString(KEY_SEVERITY).orEmpty().ifBlank { UsageSeverity.UNKNOWN.name })
            .putNullable(KEY_USED, optionalDouble(KEY_USED))
            .putNullable(KEY_LIMIT, optionalDouble(KEY_LIMIT))
            .putNullable(KEY_REMAINING, optionalDouble(KEY_REMAINING))
            .putNullable(KEY_UNIT, optionalString(KEY_UNIT))
            .putNullable(KEY_CATEGORY, optionalString(KEY_CATEGORY))
            .putNullable(KEY_WINDOW_TEXT, optionalString(KEY_WINDOW_TEXT))
            .putNullable(KEY_STARTS_AT, optionalString(KEY_STARTS_AT))
            .putNullable(KEY_RESETS_AT, optionalString(KEY_RESETS_AT))
            .putNullable(KEY_CONFIDENCE, optionalDouble(KEY_CONFIDENCE))
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is String -> value
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optionalDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toDoubleOrNull()
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private fun ProviderId.normalizedDisplayName(value: String): String {
        val normalized = value.trim()
        if (this == ProviderId.COPILOT && normalized.equals("GitHub Copilot", ignoreCase = true)) {
            return displayName
        }
        return normalized.ifBlank { displayName }
    }

    private fun normalizedDisplayName(providerId: String, displayName: String?): String {
        val normalizedProviderId = providerId.trim()
        val normalizedDisplayName = displayName?.trim().orEmpty()
        val knownProvider = ProviderId.fromStorageId(normalizedProviderId)
        if (
            knownProvider == ProviderId.COPILOT &&
            normalizedDisplayName.equals("GitHub Copilot", ignoreCase = true)
        ) {
            return ProviderId.COPILOT.displayName
        }
        return normalizedDisplayName.ifBlank {
            knownProvider?.displayName ?: normalizedProviderId.ifBlank { "unknown" }
        }
    }

    private fun requireNoSensitiveKeys(value: Any?) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    require(!key.isSensitiveKey()) { "Display-only cache contains sensitive key: $key" }
                    requireNoSensitiveKeys(value.opt(key))
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    requireNoSensitiveKeys(value.opt(index))
                }
            }
        }
    }

    private fun String.isSensitiveKey(): Boolean {
        val lower = lowercase()
        return SENSITIVE_KEY_PARTS.any { lower.contains(it) }
    }

    const val SCHEMA = "local-provider-display-v1"

    private const val KEY_SCHEMA = "schema"
    private const val KEY_PROVIDERS = "providers"
    private const val KEY_PROVIDER_ID = "providerId"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_CONNECTION_STATE = "connectionState"
    private const val KEY_REFRESH_STATE = "refreshState"
    private const val KEY_PLAN_LABEL = "planLabel"
    private const val KEY_UPDATED_AT = "updatedAt"
    private const val KEY_MESSAGE = "message"
    private const val KEY_VISIBLE = "visible"
    private const val KEY_LINES = "lines"
    private const val KEY_LABEL = "label"
    private const val KEY_REMAINING_PERCENT = "remainingPercent"
    private const val KEY_REMAINING_TEXT = "remainingText"
    private const val KEY_RESET_TEXT = "resetText"
    private const val KEY_DETAIL_TEXT = "detailText"
    private const val KEY_SEVERITY = "severity"
    private const val KEY_USED = "used"
    private const val KEY_LIMIT = "limit"
    private const val KEY_REMAINING = "remaining"
    private const val KEY_UNIT = "unit"
    private const val KEY_CATEGORY = "category"
    private const val KEY_WINDOW_TEXT = "windowText"
    private const val KEY_STARTS_AT = "startsAt"
    private const val KEY_RESETS_AT = "resetsAt"
    private const val KEY_CONFIDENCE = "confidence"

    private val SENSITIVE_KEY_PARTS = listOf(
        "accesstoken",
        "refreshtoken",
        "apikey",
        "token",
        "secret",
        "cookie",
        "session",
        "credential",
        "raw",
        "url",
        "page",
        "source"
    )
}
