package com.aiquota.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.aiquota.mobile.local.ProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ProviderWidgetPostConfigureUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun schedule(context: Context, appWidgetId: Int, providerId: ProviderId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val appContext = context.applicationContext
        scope.launch {
            providerWidgetPostConfigureRetryDelaysMs().forEach { delayMs ->
                if (delayMs > 0L) delay(delayMs)
                runCatching { ProviderWidgetImmediateRenderer.render(appContext, appWidgetId, providerId) }
            }
        }
    }
}

internal fun providerWidgetPostConfigureRetryDelaysMs(): LongArray {
    return longArrayOf(0L, 250L, 750L, 1_500L, 3_000L, 4_500L)
}
