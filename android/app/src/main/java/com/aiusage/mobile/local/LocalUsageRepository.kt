package com.aiusage.mobile.local

import android.content.Context
import android.content.SharedPreferences
import com.aiusage.mobile.providers.ProviderSnapshotCodec
import java.time.Duration
import java.time.Instant

class LocalUsageRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readSnapshots(): List<ProviderUsageSnapshot> {
        return ProviderSnapshotCodec.decode(preferences.getString(KEY_SNAPSHOTS, "").orEmpty())
            .map(::clearStaleRefreshing)
    }

    private fun clearStaleRefreshing(snapshot: ProviderUsageSnapshot): ProviderUsageSnapshot {
        if (snapshot.refreshState != ProviderRefreshState.REFRESHING) return snapshot
        val updatedAt = runCatching { Instant.parse(snapshot.updatedAt) }.getOrNull() ?: return snapshot
        if (Duration.between(updatedAt, Instant.now()) < STALE_REFRESH_TIMEOUT) return snapshot
        val nextConnectionState = when (snapshot.connectionState) {
            ProviderConnectionState.CONNECTING,
            ProviderConnectionState.COLLECTING -> if (snapshot.lines.isEmpty()) {
                ProviderConnectionState.DISCONNECTED
            } else {
                ProviderConnectionState.STALE
            }
            else -> snapshot.connectionState
        }
        return snapshot.copy(
            connectionState = nextConnectionState,
            refreshState = ProviderRefreshState.IDLE,
            message = "Previous collection did not finish."
        )
    }

    fun saveSnapshot(snapshot: ProviderUsageSnapshot) {
        val next = readSnapshots().filterNot { it.providerId == snapshot.providerId } + snapshot
        saveSnapshots(next)
    }

    fun saveSnapshots(snapshots: List<ProviderUsageSnapshot>) {
        val ordered = ProviderId.defaultOrder().mapNotNull { provider ->
            snapshots.lastOrNull { it.providerId == provider }
        }
        preferences.edit()
            .putString(KEY_SNAPSHOTS, ProviderSnapshotCodec.encode(ordered))
            .apply()
    }

    fun markConnecting(providerId: ProviderId) {
        saveSnapshot(ProviderUsageSnapshot.connecting(providerId))
    }

    fun markCollecting(providerId: ProviderId) {
        val current = readSnapshots().firstOrNull { it.providerId == providerId }
            ?: ProviderUsageSnapshot.notConnected(providerId)
        saveSnapshot(ProviderUsageSnapshot.collecting(current))
    }

    fun markConnectedWithoutUsage(providerId: ProviderId, message: String) {
        saveSnapshot(
            ProviderUsageSnapshot.connectedWithoutUsage(
                providerId = providerId,
                previous = readSnapshots().firstOrNull { it.providerId == providerId },
                message = message
            )
        )
    }

    fun failKeepingPrevious(providerId: ProviderId, message: String) {
        saveSnapshot(
            ProviderUsageSnapshot.failedKeepingPrevious(
                providerId = providerId,
                previous = readSnapshots().firstOrNull { it.providerId == providerId },
                message = message
            )
        )
    }

    fun exportDisplayJson(): String = ProviderSnapshotCodec.encode(readSnapshots())

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

    fun removeProviderSnapshot(providerId: ProviderId) {
        saveSnapshots(readSnapshots().filterNot { it.providerId == providerId })
    }

    fun registerSnapshotListener(onChanged: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SNAPSHOTS) onChanged()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterSnapshotListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val PREFERENCES_NAME = "ai_usage_local_usage"
        const val KEY_SNAPSHOTS = "provider_snapshots"
        val STALE_REFRESH_TIMEOUT: Duration = Duration.ofSeconds(45)
    }
}
