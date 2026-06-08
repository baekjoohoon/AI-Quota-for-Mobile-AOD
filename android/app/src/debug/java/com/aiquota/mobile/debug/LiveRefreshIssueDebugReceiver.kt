package com.aiquota.mobile.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiquota.mobile.notification.UsageLimitNotificationController

class LiveRefreshIssueDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SHOW_LIVE_REFRESH_ISSUE) return
        UsageLimitNotificationController.notifyLiveRefreshIssue(context.applicationContext)
    }

    companion object {
        const val ACTION_SHOW_LIVE_REFRESH_ISSUE = "com.aiquota.mobile.debug.SHOW_LIVE_REFRESH_ISSUE"
    }
}
