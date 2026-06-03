package com.aiquota.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.providers.ProviderBackgroundRefreshService

object WidgetRefreshActions {
    const val ACTION_WIDGET_REFRESH = "u.sage.widget.action.REFRESH"
    const val EXTRA_APP_WIDGET_ID = "com.aiquota.mobile.widget.extra.APP_WIDGET_ID"
    const val EXTRA_PROVIDER_ID = "com.aiquota.mobile.widget.extra.PROVIDER_ID"

    fun internalActionForWidgetAction(action: String?): String? {
        return if (action == ACTION_WIDGET_REFRESH) {
            ProviderBackgroundRefreshService.ACTION_REFRESH
        } else {
            null
        }
    }

    fun widgetRefreshIntent(
        context: Context,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
        providerId: ProviderId? = null
    ): Intent {
        return Intent(context, WidgetManualRefreshReceiver::class.java)
            .setAction(ACTION_WIDGET_REFRESH)
            .putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
            .apply {
                providerId?.let { putExtra(EXTRA_PROVIDER_ID, it.storageId) }
            }
    }

    fun internalRefreshIntent(context: Context, sourceIntent: Intent? = null): Intent {
        return Intent(context, com.aiquota.mobile.providers.ProviderRefreshReceiver::class.java)
            .setAction(ProviderBackgroundRefreshService.ACTION_REFRESH)
            .putExtra(
                EXTRA_APP_WIDGET_ID,
                sourceIntent?.getIntExtra(EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    ?: AppWidgetManager.INVALID_APPWIDGET_ID
            )
            .apply {
                sourceIntent?.getStringExtra(EXTRA_PROVIDER_ID)?.let { putExtra(EXTRA_PROVIDER_ID, it) }
            }
    }
}
