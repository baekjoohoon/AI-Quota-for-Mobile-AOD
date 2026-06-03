package com.aiquota.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.aiquota.mobile.R
import com.aiquota.mobile.local.ProviderPreferencesRepository

class ProviderUsageWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val repository = ProviderPreferencesRepository(context.applicationContext)
        appWidgetIds.forEach { appWidgetId ->
            repository.clearProviderWidgetSelection(appWidgetId)
            WidgetRefreshFeedback.clearWidgetRefresh(context, appWidgetId)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            updateWidgets(
                appContext,
                appWidgetManager,
                appWidgetManager.getAppWidgetIds(ComponentName(appContext, ProviderUsageWidgetProvider::class.java))
            )
        }

        fun updateWidget(context: Context, appWidgetId: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            updateWidgets(context.applicationContext, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val repository = ProviderPreferencesRepository(context.applicationContext)
            appWidgetIds.forEach { appWidgetId ->
                val providerId = repository.providerWidgetSelection(appWidgetId)
                if (providerId == null) {
                    appWidgetManager.updateAppWidget(appWidgetId, notConfiguredViews(context, appWidgetId))
                } else {
                    ProviderWidgetImmediateRenderer.render(context, appWidgetId, providerId)
                }
            }
        }

        private fun notConfiguredViews(context: Context, appWidgetId: Int): RemoteViews {
            return RemoteViews(context.packageName, R.layout.ai_quota_widget_provider_not_configured).apply {
                setOnClickPendingIntent(
                    R.id.provider_widget_not_configured_root,
                    PendingIntent.getActivity(
                        context,
                        40_000 + appWidgetId,
                        Intent(context, ProviderWidgetConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
    }
}
