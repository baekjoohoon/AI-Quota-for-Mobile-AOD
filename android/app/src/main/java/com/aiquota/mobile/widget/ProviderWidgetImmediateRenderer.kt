package com.aiquota.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.aiquota.mobile.MainActivity
import com.aiquota.mobile.R
import com.aiquota.mobile.local.AppTheme
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ThemePreferencesRepository
import com.aiquota.mobile.ui.AppRoute
import com.aiquota.mobile.ui.provider.providerIconRes
import kotlin.math.roundToInt

object ProviderWidgetImmediateRenderer {
    fun render(context: Context, appWidgetId: Int, providerId: ProviderId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        AppWidgetManager.getInstance(context).updateAppWidget(
            appWidgetId,
            responsiveViews(context, appWidgetId, providerId)
        )
    }

    private fun responsiveViews(context: Context, appWidgetId: Int, providerId: ProviderId): RemoteViews {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = providerWidgetSizes(options)
            if (!sizes.isNullOrEmpty()) {
                val sizeMappedViews = sizes.associateWith { size ->
                    buildViews(
                        context = context,
                        appWidgetId = appWidgetId,
                        providerId = providerId,
                        widthDp = size.width.roundToInt(),
                        heightDp = size.height.roundToInt()
                    )
                }
                return RemoteViews(sizeMappedViews)
            }
        }
        return buildViews(
            context = context,
            appWidgetId = appWidgetId,
            providerId = providerId,
            widthDp = providerWidgetResolvedHostWidthDp(options),
            heightDp = providerWidgetResolvedHostHeightDp(
                minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
                maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun providerWidgetSizes(options: android.os.Bundle): ArrayList<SizeF>? {
        return options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    }

    private fun buildViews(
        context: Context,
        appWidgetId: Int,
        providerId: ProviderId,
        widthDp: Int,
        heightDp: Int
    ): RemoteViews {
        val payload = providerWidgetPayload(
            snapshotJson = WidgetSnapshotCache(context).read(),
            providerId = providerId.storageId
        )
        val cellWidth = providerWidgetCellWidthForDp(widthDp)
        val cellHeight = providerWidgetCellHeightForDp(heightDp)
        val spec = providerWidgetLayoutSpec(
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            widgetHeightDp = heightDp
        )
        val lineLimit = spec.maxLineCount
        val themeColors = widgetThemeColors(context)
        val views = RemoteViews(context.packageName, R.layout.ai_quota_widget_provider_immediate)
        val isRefreshing = WidgetRefreshFeedback.isRefreshInProgress(
            widgetRefreshActive = WidgetRefreshFeedback.isWidgetRefreshInProgress(context, appWidgetId)
        )

        views.setInt(
            R.id.provider_immediate_root,
            "setBackgroundResource",
            providerImmediateBackgroundRes(ThemePreferencesRepository(context).currentTheme())
        )
        views.setImageViewResource(R.id.provider_immediate_icon, providerIconRes(providerId))
        views.setTextViewText(R.id.provider_immediate_name, providerId.displayName)
        views.setTextViewText(R.id.provider_immediate_status, "")
        views.setViewVisibility(R.id.provider_immediate_status, View.GONE)
        views.setImageViewResource(
            R.id.provider_immediate_refresh,
            if (isRefreshing) R.drawable.widget_refresh_spinner_dots else R.drawable.ic_refresh
        )
        views.setOnClickPendingIntent(R.id.provider_immediate_root, providerPendingIntent(context, providerId))
        views.setOnClickPendingIntent(
            R.id.provider_immediate_refresh,
            PendingIntent.getBroadcast(
                context,
                50_000 + appWidgetId,
                WidgetRefreshActions.widgetRefreshIntent(context, appWidgetId, providerId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        applyHeaderStyle(views, themeColors, spec)
        applyRows(views, payload, spec, lineLimit, themeColors)

        return views
    }

    private fun applyHeaderStyle(
        views: RemoteViews,
        themeColors: WidgetThemeColors,
        spec: ProviderWidgetLayoutSpec
    ) {
        val textColor = themeColors.caption.toArgb()
        views.setTextColor(R.id.provider_immediate_name, textColor)
        views.setTextColor(R.id.provider_immediate_status, textColor)
        views.setTextViewTextSize(
            R.id.provider_immediate_name,
            TypedValue.COMPLEX_UNIT_SP,
            spec.displayNameTextSizeSp.toFloat()
        )
        views.setTextViewTextSize(
            R.id.provider_immediate_status,
            TypedValue.COMPLEX_UNIT_SP,
            spec.statusTextSizeSp.toFloat()
        )
    }

    private fun applyRows(
        views: RemoteViews,
        payload: ProviderWidgetPayload,
        spec: ProviderWidgetLayoutSpec,
        lineLimit: Int,
        themeColors: WidgetThemeColors
    ) {
        val rows = providerWidgetRowIds()
        val visibleLines = payload.lines.take(lineLimit)
        if (visibleLines.isEmpty()) {
            applyEmptyRow(views, rows.first(), payload.status, spec)
            rows.drop(1).forEach { views.setViewVisibility(it.containerId, View.GONE) }
            return
        }

        rows.forEachIndexed { index, ids ->
            val line = visibleLines.getOrNull(index)
            if (line == null) {
                views.setViewVisibility(ids.containerId, View.GONE)
            } else {
                views.setViewVisibility(ids.containerId, View.VISIBLE)
                applyLineRow(views, ids, line, spec, themeColors, index)
            }
        }
    }

    private fun applyEmptyRow(
        views: RemoteViews,
        ids: ProviderWidgetRowIds,
        status: String,
        spec: ProviderWidgetLayoutSpec
    ) {
        views.setViewVisibility(ids.containerId, View.VISIBLE)
        applyLineLayout(
            views = views,
            containerId = ids.containerId,
            rowHeightDp = spec.lineRowHeightDp,
            topMarginDp = PROVIDER_HEADER_SPACER_MARGIN_DP
        )
        views.setTextViewText(
            ids.labelId,
            if (status.equals(DISCONNECTED_STATUS, ignoreCase = true)) {
                providerWidgetStatusLabel(DISCONNECTED_STATUS)
            } else {
                "데이터 없음"
            }
        )
        views.setTextViewText(ids.remainingId, "")
        views.setTextViewText(ids.resetId, "")
        views.setViewVisibility(ids.progressId, View.GONE)
    }

    private fun applyLineRow(
        views: RemoteViews,
        ids: ProviderWidgetRowIds,
        line: ProviderWidgetLine,
        spec: ProviderWidgetLayoutSpec,
        themeColors: WidgetThemeColors,
        rowIndex: Int
    ) {
        val textColor = themeColors.caption.toArgb()
        val remainingText = line.remainingText.ifBlank { providerWidgetStatusLabel(line.severity) }
        val textStyleSpec = providerWidgetLineTextStyleSpec(
            spec = spec,
            label = line.label,
            remainingText = remainingText
        )
        applyLineLayout(
            views = views,
            containerId = ids.containerId,
            rowHeightDp = spec.lineRowHeightDp,
            topMarginDp = if (rowIndex == 0) PROVIDER_HEADER_SPACER_MARGIN_DP else spec.lineGapDp
        )
        views.setTextColor(ids.labelId, textColor)
        views.setTextColor(ids.remainingId, textColor)
        views.setTextColor(ids.resetId, textColor)
        views.setTextViewTextSize(ids.labelId, TypedValue.COMPLEX_UNIT_SP, textStyleSpec.labelTextSizeSp)
        views.setTextViewTextSize(ids.remainingId, TypedValue.COMPLEX_UNIT_SP, textStyleSpec.remainingTextSizeSp)
        views.setTextViewTextSize(ids.resetId, TypedValue.COMPLEX_UNIT_SP, spec.detailTextSizeSp.toFloat())
        views.setTextViewText(ids.labelId, line.label)
        views.setTextViewText(ids.remainingId, remainingText)
        views.setViewVisibility(ids.progressId, View.VISIBLE)
        applyProgressHeight(views, ids.progressId, spec.gaugeHeightDp)
        views.setProgressBar(
            ids.progressId,
            100,
            ((line.remainingPercent ?: 0f).coerceIn(0f, 1f) * 100).roundToInt(),
            false
        )
        val detail = line.resetText ?: line.detailText
        if (spec.showResetCaption) {
            if (!detail.isNullOrBlank()) {
                views.setTextViewText(ids.resetId, detail)
                views.setViewVisibility(ids.resetId, View.VISIBLE)
            } else {
                views.setTextViewText(ids.resetId, RESET_CAPTION_PLACEHOLDER)
                views.setViewVisibility(ids.resetId, View.INVISIBLE)
            }
        } else {
            views.setTextViewText(ids.resetId, "")
            views.setViewVisibility(ids.resetId, View.GONE)
        }
    }

    private fun applyLineLayout(
        views: RemoteViews,
        containerId: Int,
        rowHeightDp: Int,
        topMarginDp: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(containerId, rowHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutMargin(
                containerId,
                RemoteViews.MARGIN_TOP,
                topMarginDp.toFloat(),
                TypedValue.COMPLEX_UNIT_DIP
            )
        }
    }

    private fun applyProgressHeight(views: RemoteViews, progressId: Int, gaugeHeightDp: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutHeight(progressId, gaugeHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
    }

    private fun providerPendingIntent(context: Context, providerId: ProviderId): PendingIntent {
        val intent = MainActivity.createHomeIntent(context).apply {
            putExtra(AppRoute.EXTRA_ROUTE, AppRoute.ROUTE_PROVIDER)
            putExtra(AppRoute.EXTRA_PROVIDER_ID, providerId.storageId)
            putExtra(AppRoute.EXTRA_PROVIDER_ID_LEGACY, providerId.storageId)
        }
        return PendingIntent.getActivity(
            context,
            30_000 + providerId.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun providerImmediateBackgroundRes(theme: AppTheme): Int {
        return when (theme) {
            AppTheme.MACOS -> R.drawable.widget_background_macos_rounded
            AppTheme.WINDOWS -> R.drawable.widget_background_windows_rounded
        }
    }

    private fun providerWidgetResolvedHostWidthDp(options: android.os.Bundle): Int {
        return maxOf(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        ).coerceAtLeast(PROVIDER_DEFAULT_WIDTH_DP)
    }

    private fun providerWidgetCellWidthForDp(widthDp: Int): Int {
        return (widthDp / PROVIDER_WIDGET_CELL_DP.toFloat()).roundToInt().coerceIn(2, 3)
    }

    private data class ProviderWidgetRowIds(
        val containerId: Int,
        val labelId: Int,
        val remainingId: Int,
        val progressId: Int,
        val resetId: Int
    )

    private fun providerWidgetRowIds(): List<ProviderWidgetRowIds> {
        return listOf(
            ProviderWidgetRowIds(
                containerId = R.id.provider_immediate_line_1,
                labelId = R.id.provider_immediate_label,
                remainingId = R.id.provider_immediate_remaining,
                progressId = R.id.provider_immediate_progress,
                resetId = R.id.provider_immediate_reset
            ),
            ProviderWidgetRowIds(
                containerId = R.id.provider_immediate_line_2,
                labelId = R.id.provider_immediate_label_2,
                remainingId = R.id.provider_immediate_remaining_2,
                progressId = R.id.provider_immediate_progress_2,
                resetId = R.id.provider_immediate_reset_2
            ),
            ProviderWidgetRowIds(
                containerId = R.id.provider_immediate_line_3,
                labelId = R.id.provider_immediate_label_3,
                remainingId = R.id.provider_immediate_remaining_3,
                progressId = R.id.provider_immediate_progress_3,
                resetId = R.id.provider_immediate_reset_3
            ),
            ProviderWidgetRowIds(
                containerId = R.id.provider_immediate_line_4,
                labelId = R.id.provider_immediate_label_4,
                remainingId = R.id.provider_immediate_remaining_4,
                progressId = R.id.provider_immediate_progress_4,
                resetId = R.id.provider_immediate_reset_4
            ),
            ProviderWidgetRowIds(
                containerId = R.id.provider_immediate_line_5,
                labelId = R.id.provider_immediate_label_5,
                remainingId = R.id.provider_immediate_remaining_5,
                progressId = R.id.provider_immediate_progress_5,
                resetId = R.id.provider_immediate_reset_5
            )
        )
    }

    private const val PROVIDER_WIDGET_CELL_DP = 80
    private const val PROVIDER_DEFAULT_WIDTH_DP = 160
    private const val DISCONNECTED_STATUS = "DISCONNECTED"
    private const val RESET_CAPTION_PLACEHOLDER = " "
    private const val PROVIDER_HEADER_SPACER_MARGIN_DP = 4
}
