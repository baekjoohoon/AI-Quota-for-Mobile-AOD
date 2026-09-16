package com.aiquota.mobile.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.aiquota.mobile.R

class CodexAodWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        WidgetAutoRefreshStarter.requestBackgroundRefresh(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, CodexAodWidgetProvider::class.java)
            updateWidgets(appContext, appWidgetManager, appWidgetManager.getAppWidgetIds(component))
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return

            val payload = codexAodWidgetPayload(WidgetSnapshotCache(context).read())
            appWidgetIds.forEach { appWidgetId ->
                appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, payload))
            }
        }

        private fun buildViews(context: Context, payload: CodexAodWidgetPayload): RemoteViews {
            return RemoteViews(context.packageName, R.layout.ai_quota_widget_codex_aod).apply {
                setTextViewText(R.id.codex_aod_five_hour_percent, payload.fiveHour.percentText)
                setTextViewText(R.id.codex_aod_five_hour_reset, payload.fiveHour.resetText)
                setViewVisibility(
                    R.id.codex_aod_five_hour_reset,
                    if (payload.fiveHour.resetText.isBlank()) View.GONE else View.VISIBLE
                )
                setTextViewText(R.id.codex_aod_weekly_percent, payload.weekly.percentText)
                setTextViewText(R.id.codex_aod_weekly_reset, payload.weekly.resetText)
                setViewVisibility(
                    R.id.codex_aod_weekly_reset,
                    if (payload.weekly.resetText.isBlank()) View.GONE else View.VISIBLE
                )
            }
        }
    }
}
