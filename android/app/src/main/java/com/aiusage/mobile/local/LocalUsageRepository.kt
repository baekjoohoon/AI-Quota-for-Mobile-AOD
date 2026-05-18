package com.aiusage.mobile.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class LocalUsageRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readSnapshots(): List<ProviderUsageSnapshot> {
        val raw = preferences.getString(KEY_SNAPSHOTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val providers = runCatching {
            JSONObject(raw).optJSONArray(KEY_PROVIDERS) ?: JSONArray()
        }.getOrDefault(JSONArray())

        return buildList {
            for (index in 0 until providers.length()) {
                val provider = providers.optJSONObject(index) ?: continue
                val providerId = ProviderId.fromStorageId(provider.optString(KEY_PROVIDER_ID)) ?: continue
                add(provider.toSnapshot(providerId))
            }
        }.normalizedSnapshots()
    }

    fun saveSnapshot(snapshot: ProviderUsageSnapshot) {
        saveSnapshots(readSnapshots().filterNot { it.providerId == snapshot.providerId } + snapshot)
    }

    fun registerSnapshotListener(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SNAPSHOTS) {
                onChanged()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterSnapshotListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun saveSnapshots(snapshots: List<ProviderUsageSnapshot>) {
        val providers = JSONArray()
        snapshots.normalizedSnapshots().forEach { snapshot ->
            providers.put(snapshot.toJson())
        }
        val root = JSONObject().put(KEY_PROVIDERS, providers)
        preferences.edit()
            .putString(KEY_SNAPSHOTS, root.toString())
            .apply()
    }

    fun removeProviderSnapshot(providerId: ProviderId) {
        saveSnapshots(readSnapshots().filterNot { it.providerId == providerId })
    }

    fun exportDisplayOnlyCache(
        order: List<ProviderId>,
        hidden: Set<ProviderId>,
        updatedAt: String
    ): String {
        return WidgetCacheSanitizer.toDisplayOnlyJson(
            snapshots = readSnapshots(),
            order = order,
            hidden = hidden,
            updatedAt = updatedAt
        )
    }

    private fun JSONObject.toSnapshot(providerId: ProviderId): ProviderUsageSnapshot {
        val linesJson = optJSONArray(KEY_LINES) ?: JSONArray()
        return ProviderUsageSnapshot(
            providerId = providerId,
            displayName = providerId.normalizedDisplayName(optString(KEY_DISPLAY_NAME)),
            connectionState = enumValueOfOrDefault(
                value = optString(KEY_CONNECTION_STATE),
                default = ProviderConnectionState.DISCONNECTED
            ),
            refreshState = enumValueOfOrDefault(
                value = optString(KEY_REFRESH_STATE),
                default = ProviderRefreshState.IDLE
            ),
            planLabel = optionalString(KEY_PLAN_LABEL),
            updatedAt = optString(KEY_UPDATED_AT),
            message = optionalString(KEY_MESSAGE).withoutLegacyCapturePrompt(),
            lines = buildList {
                for (index in 0 until linesJson.length()) {
                    val line = linesJson.optJSONObject(index) ?: continue
                    add(line.toUsageLine())
                }
            }
        )
    }

    private fun JSONObject.toUsageLine(): ProviderUsageLine {
        return ProviderUsageLine(
            label = optString(KEY_LABEL).ifBlank { "Usage" },
            remainingPercent = optionalFloat(KEY_REMAINING_PERCENT),
            remainingText = optString(KEY_REMAINING_TEXT),
            resetText = optionalString(KEY_RESET_TEXT),
            detailText = optionalString(KEY_DETAIL_TEXT),
            severity = enumValueOfOrDefault(
                value = optString(KEY_SEVERITY),
                default = UsageSeverity.UNKNOWN
            ),
            usedAmount = optionalDouble(KEY_USED),
            limitAmount = optionalDouble(KEY_LIMIT),
            remainingAmount = optionalDouble(KEY_REMAINING),
            unit = optionalString(KEY_UNIT),
            category = optionalString(KEY_CATEGORY),
            windowText = optionalString(KEY_WINDOW_TEXT),
            startsAt = optionalString(KEY_STARTS_AT),
            resetsAt = optionalString(KEY_RESETS_AT),
            sourceLabel = optionalString(KEY_SOURCE_LABEL),
            confidence = optionalFloat(KEY_CONFIDENCE)
        )
    }

    private fun ProviderUsageSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put(KEY_PROVIDER_ID, providerId.storageId)
            .put(KEY_DISPLAY_NAME, providerId.normalizedDisplayName(displayName))
            .put(KEY_CONNECTION_STATE, connectionState.name)
            .put(KEY_REFRESH_STATE, refreshState.name)
            .putNullable(KEY_PLAN_LABEL, planLabel)
            .put(KEY_UPDATED_AT, updatedAt)
            .putNullable(KEY_MESSAGE, message)
            .put(KEY_LINES, JSONArray().also { array ->
                lines.forEach { line ->
                    array.put(line.toJson())
                }
            })
    }

    private fun ProviderUsageLine.toJson(): JSONObject {
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
            .putNullable(KEY_SOURCE_LABEL, sourceLabel)
            .putNullable(KEY_CONFIDENCE, confidence?.toDouble())
    }

    private fun List<ProviderUsageSnapshot>.normalizedSnapshots(): List<ProviderUsageSnapshot> {
        val byProvider = LinkedHashMap<ProviderId, ProviderUsageSnapshot>()
        forEach { snapshot ->
            byProvider[snapshot.providerId] = snapshot.normalized()
        }
        return ProviderId.defaultOrder().mapNotNull { byProvider[it] }
    }

    private fun ProviderUsageSnapshot.normalized(): ProviderUsageSnapshot {
        val normalizedLines = lines.map { line ->
            line.copy(
                label = providerId.normalizedUsageLineLabelForDisplay(line.label.ifBlank { "Usage" }),
                remainingPercent = line.remainingPercent?.coerceIn(0f, 1f),
                confidence = line.confidence?.coerceIn(0f, 1f)
            )
        }
        return copy(
            displayName = providerId.normalizedDisplayName(displayName),
            planLabel = providerId.normalizedPlanLabelForDisplay(planLabel),
            lines = normalizedLines.deduplicated(providerId)
        ).withRecoveredStaleProgress()
    }

    private fun List<ProviderUsageLine>.deduplicated(providerId: ProviderId): List<ProviderUsageLine> {
        val deduped = LinkedHashMap<String, ProviderUsageLine>()
        forEach { line ->
            val key = if (providerId == ProviderId.GEMINI) {
                providerId.normalizedUsageLineLabelForDisplay(line.label).lowercase(Locale.US)
            } else {
                listOf(
                    line.label,
                    line.remainingText,
                    line.resetText.orEmpty(),
                    line.resetsAt.orEmpty(),
                    line.sourceLabel.orEmpty()
                ).joinToString("|").lowercase(Locale.US)
            }
            deduped.putIfAbsent(key, line)
        }
        return deduped.values.toList()
    }

    private fun ProviderId.normalizedDisplayName(value: String): String {
        val normalized = value.trim()
        if (this == ProviderId.COPILOT && normalized.equals("GitHub Copilot", ignoreCase = true)) {
            return displayName
        }
        return normalized.ifBlank { displayName }
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optionalFloat(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toFloatOrNull()?.coerceIn(0f, 1f)
    }

    private fun JSONObject.optionalDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toDoubleOrNull()
    }

    private fun String?.withoutLegacyCapturePrompt(): String? {
        val value = this ?: return null
        return when {
            value.contains("캡처", ignoreCase = true) -> null
            value.contains("capture visible usage", ignoreCase = true) -> null
            value.contains("local web login capture", ignoreCase = true) -> null
            else -> value
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private inline fun <reified T : Enum<T>> enumValueOfOrDefault(value: String, default: T): T {
        val normalized = value.trim().uppercase(Locale.US)
        return enumValues<T>().firstOrNull { it.name == normalized } ?: default
    }

    private companion object {
        const val PREFERENCES_NAME = "ai_usage_local_usage"
        const val KEY_SNAPSHOTS = "provider_snapshots"
        const val KEY_PROVIDERS = "providers"
        const val KEY_PROVIDER_ID = "providerId"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_CONNECTION_STATE = "connectionState"
        const val KEY_REFRESH_STATE = "refreshState"
        const val KEY_PLAN_LABEL = "planLabel"
        const val KEY_UPDATED_AT = "updatedAt"
        const val KEY_MESSAGE = "message"
        const val KEY_LINES = "lines"
        const val KEY_LABEL = "label"
        const val KEY_REMAINING_PERCENT = "remainingPercent"
        const val KEY_REMAINING_TEXT = "remainingText"
        const val KEY_RESET_TEXT = "resetText"
        const val KEY_DETAIL_TEXT = "detailText"
        const val KEY_SEVERITY = "severity"
        const val KEY_USED = "used"
        const val KEY_LIMIT = "limit"
        const val KEY_REMAINING = "remaining"
        const val KEY_UNIT = "unit"
        const val KEY_CATEGORY = "category"
        const val KEY_WINDOW_TEXT = "windowText"
        const val KEY_STARTS_AT = "startsAt"
        const val KEY_RESETS_AT = "resetsAt"
        const val KEY_SOURCE_LABEL = "sourceLabel"
        const val KEY_CONFIDENCE = "confidence"
    }
}
