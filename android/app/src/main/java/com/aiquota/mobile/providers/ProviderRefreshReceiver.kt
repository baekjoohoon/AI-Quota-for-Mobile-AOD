package com.aiquota.mobile.providers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.widget.WidgetRefreshActions
import com.aiquota.mobile.widget.WidgetRefreshFeedback

class ProviderRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ProviderBackgroundRefreshService.ACTION_REFRESH) return
        val appContext = context.applicationContext
        val appWidgetId = intent.getIntExtra(
            WidgetRefreshActions.EXTRA_APP_WIDGET_ID,
            android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val serviceIntent = Intent(appContext, ProviderBackgroundRefreshService::class.java)
            .setAction(ProviderBackgroundRefreshService.ACTION_REFRESH)
            .putExtra(WidgetRefreshActions.EXTRA_APP_WIDGET_ID, appWidgetId)
            .apply {
                intent.getStringExtra(WidgetRefreshActions.EXTRA_PROVIDER_ID)
                    ?.let { putExtra(WidgetRefreshActions.EXTRA_PROVIDER_ID, it) }
            }
        runCatching {
            ContextCompat.startForegroundService(appContext, serviceIntent)
        }.onFailure {
            WidgetRefreshFeedback.clearWidgetRefresh(appContext, appWidgetId)
            UsageSurfaceRefresher.refresh(appContext, LocalUsageRepository(appContext))
        }
    }
}
