package com.aiusage.mobile.local

import android.content.Context
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
            displayName = optString(KEY_DISPLAY_NAME).ifBlank { providerId.displayName },
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
            message = optionalString(KEY_MESSAGE),
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
            )
        )
    }

    private fun ProviderUsageSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put(KEY_PROVIDER_ID, providerId.storageId)
            .put(KEY_DISPLAY_NAME, displayName.ifBlank { providerId.displayName })
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
    }

    private fun List<ProviderUsageSnapshot>.normalizedSnapshots(): List<ProviderUsageSnapshot> {
        val byProvider = LinkedHashMap<ProviderId, ProviderUsageSnapshot>()
        forEach { snapshot ->
            byProvider[snapshot.providerId] = snapshot.normalized()
        }
        return ProviderId.defaultOrder().mapNotNull { byProvider[it] }
    }

    private fun ProviderUsageSnapshot.normalized(): ProviderUsageSnapshot {
        return copy(
            displayName = displayName.ifBlank { providerId.displayName },
            lines = lines.map { line ->
                line.copy(
                    label = line.label.ifBlank { "Usage" },
                    remainingPercent = line.remainingPercent?.coerceIn(0f, 1f)
                )
            }
        )
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optionalFloat(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toFloatOrNull()?.coerceIn(0f, 1f)
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
    }
}
