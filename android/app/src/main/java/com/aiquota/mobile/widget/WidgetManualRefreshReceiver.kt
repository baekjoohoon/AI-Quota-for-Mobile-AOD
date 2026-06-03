package com.aiquota.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.providers.UsageSurfaceRefresher

class WidgetManualRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val internalAction = WidgetRefreshActions.internalActionForWidgetAction(intent?.action) ?: return
        val appWidgetId = intent?.getIntExtra(
            WidgetRefreshActions.EXTRA_APP_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        WidgetRefreshFeedback.markWidgetRefreshStarted(context, appWidgetId)
        UsageSurfaceRefresher.refresh(context, LocalUsageRepository(context.applicationContext))
        context.sendBroadcast(
            WidgetRefreshActions.internalRefreshIntent(context, intent)
                .setAction(internalAction)
        )
    }
}
