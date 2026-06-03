package com.aiquota.mobile.widget

import android.content.Context
import com.aiquota.mobile.local.WidgetCacheSanitizer

data class WidgetSnapshotState(
    val snapshotJson: String,
    val deviceName: String,
    val status: String,
    val updatedAt: String
)

class WidgetSnapshotCache(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun write(snapshotJson: String, status: String = "NotLinked", deviceName: String = "", updatedAt: String = "") {
        preferences.edit()
            .putString(KEY_LATEST_SNAPSHOT, snapshotJson)
            .putString(KEY_STATUS, status)
            .putString(KEY_DEVICE_NAME, deviceName)
            .putString(KEY_UPDATED_AT, updatedAt)
            .apply()
    }

    fun writeLocalDisplaySnapshot(snapshotJson: String, updatedAt: String = "") {
        val sanitizedJson = WidgetCacheSanitizer.sanitizeDisplayOnlyJson(snapshotJson)
        preferences.edit()
            .putString(KEY_LOCAL_DISPLAY_SNAPSHOT, sanitizedJson)
            .putString(KEY_LOCAL_DISPLAY_UPDATED_AT, updatedAt)
            .apply()
    }

    fun read(): String {
        return readLocalDisplaySnapshot().ifBlank {
            preferences.getString(KEY_LATEST_SNAPSHOT, "") ?: ""
        }
    }

    fun readLocalDisplaySnapshot(): String {
        return preferences.getString(KEY_LOCAL_DISPLAY_SNAPSHOT, "") ?: ""
    }

    fun readState(): WidgetSnapshotState {
        val localSnapshot = readLocalDisplaySnapshot()
        if (localSnapshot.isNotBlank()) {
            return WidgetSnapshotState(
                snapshotJson = localSnapshot,
                deviceName = LOCAL_DEVICE_NAME,
                status = LOCAL_STATUS,
                updatedAt = preferences.getString(KEY_LOCAL_DISPLAY_UPDATED_AT, "") ?: ""
            )
        }
        return WidgetSnapshotState(
            snapshotJson = read(),
            deviceName = preferences.getString(KEY_DEVICE_NAME, "") ?: "",
            status = preferences.getString(KEY_STATUS, DEFAULT_STATUS) ?: DEFAULT_STATUS,
            updatedAt = preferences.getString(KEY_UPDATED_AT, "") ?: ""
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "ai_quota_widget_cache"
        const val KEY_LATEST_SNAPSHOT = "latest_snapshot"
        const val KEY_STATUS = "status"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_UPDATED_AT = "updated_at"
        const val KEY_LOCAL_DISPLAY_SNAPSHOT = "local_display_snapshot"
        const val KEY_LOCAL_DISPLAY_UPDATED_AT = "local_display_updated_at"
        const val DEFAULT_STATUS = "NotLinked"
        const val LOCAL_STATUS = "LocalProviders"
        const val LOCAL_DEVICE_NAME = "This device"
    }
}
