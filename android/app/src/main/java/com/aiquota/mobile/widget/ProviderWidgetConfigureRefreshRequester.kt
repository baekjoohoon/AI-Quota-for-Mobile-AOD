package com.aiquota.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.providers.ProviderBackgroundRefreshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ProviderWidgetConfigureRefreshRequester {
    private const val PROVIDER_WIDGET_CONFIGURE_REFRESH_DELAY_MS = 5_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun schedule(context: Context, appWidgetId: Int, providerId: ProviderId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val appContext = context.applicationContext
        scope.launch {
            delay(PROVIDER_WIDGET_CONFIGURE_REFRESH_DELAY_MS)
            WidgetRefreshFeedback.markWidgetRefreshStarted(appContext, appWidgetId)
            ProviderWidgetImmediateRenderer.render(appContext, appWidgetId, providerId)
            runCatching {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, ProviderBackgroundRefreshService::class.java)
                        .setAction(ProviderBackgroundRefreshService.ACTION_REFRESH)
                        .putExtra(WidgetRefreshActions.EXTRA_APP_WIDGET_ID, appWidgetId)
                        .putExtra(WidgetRefreshActions.EXTRA_PROVIDER_ID, providerId.storageId)
                )
            }.onFailure {
                WidgetRefreshFeedback.clearWidgetRefresh(appContext, appWidgetId)
                ProviderWidgetImmediateRenderer.render(appContext, appWidgetId, providerId)
            }
        }
    }
}
