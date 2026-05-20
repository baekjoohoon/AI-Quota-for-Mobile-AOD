package com.aiusage.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.aiusage.mobile.MainActivity
import com.aiusage.mobile.R
import com.aiusage.mobile.local.AppTheme
import com.aiusage.mobile.local.ThemePreferencesRepository

class AIUsageCircularWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val MAX_CIRCULAR_GAUGES = 4
        private const val CANVAS_SIZE = 180
        private const val ICON_SIZE = 66
        private const val RING_STROKE_WIDTH = 15f
        private const val RING_PADDING = 13f

        private val gaugeViewIds = intArrayOf(
            R.id.circular_gauge_0,
            R.id.circular_gauge_1,
            R.id.circular_gauge_2,
            R.id.circular_gauge_3
        )

        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, AIUsageCircularWidgetProvider::class.java)
            updateWidgets(context, appWidgetManager, appWidgetManager.getAppWidgetIds(component))
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return
            val gauges = parseWidgetProviderGauges(
                WidgetSnapshotCache(context).readState().snapshotJson
            ).take(MAX_CIRCULAR_GAUGES)
            val hasProviderData = gauges.isNotEmpty()
            val theme = ThemePreferencesRepository(context).currentTheme()
            val themeColors = widgetThemeColors(theme)

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(context.packageName, R.layout.ai_usage_widget_circular)
                views.setInt(R.id.circular_widget_root, "setBackgroundResource", widgetBackgroundRes(theme))
                views.setTextColor(R.id.circular_login_message, themeColors.caption.toArgb())
                views.setOnClickPendingIntent(
                    R.id.circular_widget_root,
                    mainActivityPendingIntent(context)
                )
                views.setViewVisibility(
                    R.id.circular_login_message,
                    if (hasProviderData) View.GONE else View.VISIBLE
                )
                gaugeViewIds.forEachIndexed { index, viewId ->
                    val gauge = gauges.getOrNull(index)
                    if (gauge == null) {
                        views.setViewVisibility(viewId, View.INVISIBLE)
                    } else {
                        views.setViewVisibility(viewId, View.VISIBLE)
                        views.setImageViewBitmap(viewId, circularGaugeBitmap(context, gauge, themeColors))
                    }
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun mainActivityPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun circularGaugeBitmap(
            context: Context,
            gauge: WidgetProviderGauge,
            themeColors: WidgetThemeColors
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val rect = RectF(
                RING_PADDING,
                RING_PADDING,
                CANVAS_SIZE - RING_PADDING,
                CANVAS_SIZE - RING_PADDING
            )
            val ratio = gauge.remainingRatio.coerceIn(0f, 1f)
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = themeColors.gaugeTrack.toArgb()
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = RING_STROKE_WIDTH
            }
            val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = themeColors.gaugeColor(ratio).toArgb()
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = RING_STROKE_WIDTH
            }

            val consumedSweep = 360f * (1f - ratio)
            val remainingStartAngle = -90f + consumedSweep

            canvas.drawArc(rect, -90f, 360f, false, trackPaint)
            canvas.drawArc(rect, remainingStartAngle, 360f * ratio, false, activePaint)
            drawProviderIcon(context, canvas, gauge.providerId)
            return bitmap
        }

        private fun drawProviderIcon(context: Context, canvas: Canvas, providerId: String) {
            val drawable = ContextCompat.getDrawable(context, providerIconRes(providerId))?.mutate() ?: return
            val left = (CANVAS_SIZE - ICON_SIZE) / 2
            val top = (CANVAS_SIZE - ICON_SIZE) / 2
            drawable.setBounds(left, top, left + ICON_SIZE, top + ICON_SIZE)
            drawable.draw(canvas)
        }

        private fun providerIconRes(providerId: String): Int {
            return when (providerId.lowercase()) {
                "claude" -> R.drawable.ic_provider_claude
                "codex", "openai" -> R.drawable.ic_provider_openai
                "gemini" -> R.drawable.ic_provider_gemini
                "copilot", "github-copilot", "github_copilot" -> R.drawable.ic_provider_copilot
                "antigravity" -> R.drawable.ic_provider_antigravity
                else -> R.drawable.ic_provider_unknown
            }
        }

        private fun widgetBackgroundRes(theme: AppTheme): Int {
            return when (theme) {
                AppTheme.MACOS -> R.drawable.widget_background_macos_rounded
                AppTheme.WINDOWS -> R.drawable.widget_background_windows_rounded
            }
        }
    }
}
