package com.aiquota.mobile.update

import android.content.Context

object AppUpdateStateStore {
    private const val PREFS = "ai_quota_app_update"
    private const val KEY_UPDATE_AVAILABLE = "update_available"

    fun setUpdateAvailable(context: Context, available: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UPDATE_AVAILABLE, available)
            .apply()
    }

    fun isUpdateAvailable(context: Context): Boolean {
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_UPDATE_AVAILABLE, false)
    }
}
