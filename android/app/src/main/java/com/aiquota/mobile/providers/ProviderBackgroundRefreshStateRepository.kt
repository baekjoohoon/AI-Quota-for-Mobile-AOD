package com.aiquota.mobile.providers

import android.content.Context

class ProviderBackgroundRefreshStateRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordStarted(source: String, nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_RUNNING, true)
            .putString(KEY_SOURCE, source)
            .putLong(KEY_STARTED_AT, nowMillis)
            .putLong(KEY_HEARTBEAT_AT, nowMillis)
            .remove(KEY_LAST_FAILURE)
            .apply()
    }

    fun recordHeartbeat(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_RUNNING, true)
            .putLong(KEY_HEARTBEAT_AT, nowMillis)
            .apply()
    }

    fun recordFailure(kind: String, nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString(KEY_LAST_FAILURE, kind)
            .putLong(KEY_HEARTBEAT_AT, nowMillis)
            .apply()
    }

    fun recordStopped(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putBoolean(KEY_RUNNING, false)
            .putLong(KEY_STOPPED_AT, nowMillis)
            .apply()
    }

    fun isHeartbeatStale(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val heartbeat = preferences.getLong(KEY_HEARTBEAT_AT, 0L)
        if (heartbeat <= 0L) return true
        return nowMillis - heartbeat > STALE_HEARTBEAT_MILLIS
    }

    fun heartbeatAtMillis(): Long {
        return preferences.getLong(KEY_HEARTBEAT_AT, 0L)
    }

    companion object {
        private const val PREFS = "ai_quota_background_refresh_state"
        private const val KEY_RUNNING = "running"
        private const val KEY_SOURCE = "source"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_HEARTBEAT_AT = "heartbeat_at"
        private const val KEY_STOPPED_AT = "stopped_at"
        private const val KEY_LAST_FAILURE = "last_failure"
        const val STALE_HEARTBEAT_MILLIS = 180_000L
    }
}
