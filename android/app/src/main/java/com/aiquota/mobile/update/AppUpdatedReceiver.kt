package com.aiquota.mobile.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiquota.mobile.sync.ForegroundRefreshController
import com.aiquota.mobile.sync.ForegroundRefreshHealthScheduler

class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppUpdatedRefreshCooldown.markPackageReplaced(context)
            AppUpdateCheckScheduler.schedule(context)
            val controller = ForegroundRefreshController(context.applicationContext)
            if (controller.liveMonitoringEnabled()) {
                ForegroundRefreshHealthScheduler.schedule(context.applicationContext)
            }
        }
    }
}

object AppUpdatedRefreshCooldown {
    private const val PREFS = "ai_quota_app_update_refresh_cooldown"
    private const val KEY_PACKAGE_REPLACED_AT = "package_replaced_at"
    private const val PACKAGE_REPLACED_REFRESH_DELAY_MILLIS = 120_000L

    fun markPackageReplaced(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PACKAGE_REPLACED_AT, nowMillis)
            .apply()
    }

    fun remainingDelayMillis(context: Context, nowMillis: Long = System.currentTimeMillis()): Long {
        val replacedAt = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PACKAGE_REPLACED_AT, 0L)
        if (replacedAt <= 0L) return 0L
        return (PACKAGE_REPLACED_REFRESH_DELAY_MILLIS - (nowMillis - replacedAt)).coerceAtLeast(0L)
    }
}
