package com.aiquota.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context

object WidgetRefreshFeedback {
    fun isRefreshInProgress(
        widgetRefreshActive: Boolean = false
    ): Boolean {
        return widgetRefreshActive
    }

    fun markWidgetRefreshStarted(context: Context, appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        preferences(context)
            .edit()
            .putLong(keyFor(appWidgetId), System.currentTimeMillis())
            .apply()
    }

    fun clearWidgetRefresh(context: Context, appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        preferences(context)
            .edit()
            .remove(keyFor(appWidgetId))
            .apply()
    }

    fun isWidgetRefreshInProgress(
        context: Context,
        appWidgetId: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val prefs = preferences(context)
        val startedAt = prefs.getLong(keyFor(appWidgetId), 0L)
        if (startedAt <= 0L) return false
        val active = nowMillis - startedAt <= WIDGET_REFRESH_FEEDBACK_TTL_MS
        if (!active) {
            prefs.edit().remove(keyFor(appWidgetId)).apply()
        }
        return active
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private fun keyFor(appWidgetId: Int): String = "$KEY_PREFIX$appWidgetId"

    private const val PREFS_NAME = "ai_quota_widget_refresh_feedback"
    private const val KEY_PREFIX = "widget_"
    private const val WIDGET_REFRESH_FEEDBACK_TTL_MS = 60_000L
}
