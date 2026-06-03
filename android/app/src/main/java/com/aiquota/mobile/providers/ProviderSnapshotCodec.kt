package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import com.aiquota.mobile.local.UsageSeverity
import org.json.JSONArray
import org.json.JSONObject

object ProviderSnapshotCodec {
    fun encode(snapshots: List<ProviderUsageSnapshot>): String {
        return JSONObject()
            .put("providers", JSONArray().also { providers ->
                snapshots.forEach { snapshot ->
                    providers.put(snapshot.toJson())
                }
            })
            .toString()
    }

    fun decode(raw: String): List<ProviderUsageSnapshot> {
        if (raw.isBlank()) return emptyList()
        val providers = runCatching {
            JSONObject(raw).optJSONArray("providers") ?: JSONArray()
        }.getOrDefault(JSONArray())
        return buildList {
            for (index in 0 until providers.length()) {
                val item = providers.optJSONObject(index) ?: continue
                val providerId = ProviderId.fromStorageId(item.optString("providerId")) ?: continue
                add(item.toSnapshot(providerId).requireUsageForConnectedState())
            }
        }.sortedBy { ProviderId.defaultOrder().indexOf(it.providerId) }
    }

    private fun ProviderUsageSnapshot.toJson(): JSONObject {
        return JSONObject()
            .put("providerId", providerId.storageId)
            .put("displayName", displayName)
            .put("connectionState", connectionState.name)
            .put("refreshState", refreshState.name)
            .putNullable("planLabel", planLabel)
            .putNullable("account", account)
            .put("updatedAt", updatedAt)
            .putNullable("message", message)
            .put("lines", JSONArray().also { array ->
                lines.forEach { line -> array.put(line.toJson()) }
            })
    }

    private fun ProviderUsageLine.toJson(): JSONObject {
        return JSONObject()
            .put("key", key)
            .put("label", label)
            .putNullable("remainingPercent", remainingPercent)
            .putNullable("usedPercent", usedPercent)
            .putNullable("resetsAt", resetsAt)
            .putNullable("resetText", resetText)
            .put("remainingText", remainingText)
            .putNullable("detailText", detailText)
            .put("severity", severity.name)
            .put("unit", unit)
            .putNullable("category", category)
            .putNullable("windowText", windowText)
            .putNullable("startsAt", startsAt)
            .putNullable("source", sourceLabel)
            .put("confidence", confidence)
    }

    private fun JSONObject.toSnapshot(providerId: ProviderId): ProviderUsageSnapshot {
        val linesJson = optJSONArray("lines") ?: JSONArray()
        return ProviderUsageSnapshot(
            providerId = providerId,
            displayName = optionalString("displayName") ?: providerId.displayName,
            connectionState = enumValue(optString("connectionState"), ProviderConnectionState.DISCONNECTED),
            refreshState = enumValue(optString("refreshState"), ProviderRefreshState.IDLE),
            planLabel = optionalString("planLabel") ?: optionalString("plan"),
            account = optionalString("account"),
            updatedAt = optionalString("updatedAt") ?: optionalString("fetchedAt").orEmpty(),
            message = optionalString("message"),
            lines = buildList {
                for (index in 0 until linesJson.length()) {
                    val line = linesJson.optJSONObject(index) ?: continue
                    add(line.toUsageLine(providerId))
                }
            }
        )
    }

    private fun JSONObject.toUsageLine(providerId: ProviderId): ProviderUsageLine {
        return ProviderUsageLine(
            key = optString("key").ifBlank { "${providerId.storageId}:usage" },
            label = optString("label").ifBlank { "Usage" },
            remainingPercent = optionalFloatRatio("remainingPercent"),
            remainingText = optString("remainingText").ifBlank {
                optionalFloatRatio("remainingPercent")?.let { "${(it * 100f).toInt()}% left" }.orEmpty()
            },
            resetsAt = optionalString("resetsAt"),
            resetText = optionalString("resetText"),
            detailText = optionalString("detailText"),
            severity = enumValue(optString("severity"), UsageSeverity.UNKNOWN),
            unit = optString("unit").ifBlank { "percent" },
            category = optionalString("category"),
            windowText = optionalString("windowText"),
            startsAt = optionalString("startsAt"),
            sourceLabel = optString("source").ifBlank { "provider-api" },
            confidence = optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f)
        )
    }

    private fun ProviderUsageSnapshot.requireUsageForConnectedState(): ProviderUsageSnapshot {
        if (lines.isNotEmpty()) return this
        if (connectionState != ProviderConnectionState.CONNECTED) return this
        return copy(
            connectionState = ProviderConnectionState.UNAVAILABLE,
            refreshState = ProviderRefreshState.IDLE,
            message = message ?: "Usage quota is not available yet."
        )
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optionalFloatRatio(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)?.toString()?.toDoubleOrNull() ?: return null
        return (if (value > 1.0) value / 100.0 else value).toFloat().coerceIn(0f, 1f)
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, default: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}
