package com.aiquota.mobile.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiquota.mobile.sync.ForegroundRefreshController

class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppUpdateCheckScheduler.schedule(context)
            val controller = ForegroundRefreshController(context.applicationContext)
            if (controller.liveMonitoringEnabled()) {
                runCatching { controller.startPreciseRefresh() }
            }
        }
    }
}
