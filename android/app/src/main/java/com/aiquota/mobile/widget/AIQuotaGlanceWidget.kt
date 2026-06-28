package com.aiquota.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aiquota.mobile.MainActivity
import com.aiquota.mobile.R
import com.aiquota.mobile.local.ProviderGaugeColor
import com.aiquota.mobile.local.ProviderPreferencesRepository
import com.aiquota.mobile.ui.AppRoute
import com.aiquota.mobile.ui.provider.providerIconRes as sharedProviderIconRes
import kotlin.math.roundToInt

open class AIQuotaGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val preferences = ProviderPreferencesRepository(context.applicationContext)
        val payload = dashboardWidgetPayload(
            snapshotJson = WidgetSnapshotCache(context).read(),
            order = preferences.dashboardWidgetProviderOrder(appWidgetId),
            hidden = preferences.dashboardWidgetHiddenProviders(appWidgetId)
        )
        val emptyMessage = context.getString(R.string.widget_no_data)
        val refreshDescription = context.getString(R.string.widget_refresh)
        val isRefreshing = WidgetRefreshFeedback.isRefreshInProgress(
            widgetRefreshActive = WidgetRefreshFeedback.isWidgetRefreshInProgress(context, appWidgetId)
        )
        val themeColors = widgetThemeColors(context)
        val homeAction = actionStartActivity(
            MainActivity::class.java,
            actionParametersOf(RouteActionKey.to(AppRoute.ROUTE_HOME))
        )
        val refreshAction = actionSendBroadcast(WidgetRefreshActions.widgetRefreshIntent(context, appWidgetId))

        provideContent {
            // Widget renders only from the local cache written by the app refresh flow.
            if (payload.providers.isNotEmpty()) {
                AIQuotaWidgetContent(
                    payload,
                    emptyMessage,
                    homeAction,
                    refreshAction,
                    refreshDescription,
                    isRefreshing,
                    themeColors
                )
            } else {
                EmptyDashboardWidgetContent(
                    emptyMessage,
                    homeAction,
                    refreshAction,
                    refreshDescription,
                    isRefreshing,
                    themeColors
                )
            }
        }
    }
}

@Composable
private fun EmptyDashboardWidgetContent(
    message: String,
    homeAction: Action,
    refreshAction: Action,
    refreshDescription: String,
    isRefreshing: Boolean,
    themeColors: WidgetThemeColors
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(onClick = homeAction)
            .cornerRadius(24.dp)
            .background(themeColors.background)
            .padding(14.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        WidgetRefreshHeader("", refreshAction, refreshDescription, isRefreshing, themeColors)
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = message,
            style = TextStyle(
                color = ColorProvider(themeColors.caption),
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun AIQuotaWidgetContent(
    payload: UnifiedWidgetPayload,
    emptyMessage: String,
    homeAction: Action,
    refreshAction: Action,
    refreshDescription: String,
    isRefreshing: Boolean,
    themeColors: WidgetThemeColors
) {
    val widgetSize = LocalSize.current
    val widgetHeightDp = widgetSize.height.value.roundToInt()
    val capacitySpec = unifiedWidgetLayoutSpec(
        cellWidth = widgetSize.width.toWidgetCells(min = 2, max = 3),
        cellHeight = unifiedWidgetCellHeightForDp(widgetHeightDp),
        widgetHeightDp = widgetHeightDp
    )
    val providers = payload.providers.take(capacitySpec.maxProviderCount)
    val layoutSpec = unifiedWidgetLayoutSpec(
        cellWidth = widgetSize.width.toWidgetCells(min = 2, max = 3),
        cellHeight = unifiedWidgetCellHeightForDp(widgetHeightDp),
        widgetHeightDp = widgetHeightDp,
        visibleProviderCount = providers.size
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(onClick = homeAction)
            .cornerRadius(24.dp)
            .background(themeColors.background)
            .padding(
                horizontal = layoutSpec.horizontalPaddingDp.dp,
                vertical = layoutSpec.verticalPaddingDp.dp
            )
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = if (unifiedWidgetCentersProviderBlock(layoutSpec)) {
                Alignment.Vertical.CenterVertically
            } else {
                Alignment.Vertical.Top
            }
        ) {
            if (providers.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = widgetTextStyle(layoutSpec.providerTextSizeSp, TextAlign.Center, themeColors)
                )
                return@Column
            }

            providers.forEachIndexed { index, provider ->
                UnifiedProviderRow(
                    provider,
                    layoutSpec,
                    themeColors,
                    reserveTopEndSpace = index == 0 && unifiedWidgetReservesFirstRowTopEndSpace(layoutSpec)
                )
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.TopEnd
        ) {
            WidgetRefreshIcon(refreshAction, refreshDescription, isRefreshing)
        }
    }
}

@Composable
private fun WidgetRefreshHeader(
    title: String,
    refreshAction: Action,
    refreshDescription: String,
    isRefreshing: Boolean,
    themeColors: WidgetThemeColors,
    titleTextSizeSp: Int = 12
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                modifier = GlanceModifier.defaultWeight(),
                style = widgetTextStyle(titleTextSizeSp, themeColors = themeColors)
            )
        } else {
            Spacer(modifier = GlanceModifier.defaultWeight())
        }
        if (isRefreshing) {
            Image(
                provider = ImageProvider(R.drawable.widget_refresh_spinner_dots),
                contentDescription = refreshDescription,
                modifier = GlanceModifier.size(20.dp)
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = refreshDescription,
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(onClick = refreshAction)
            )
        }
    }
}

@Composable
private fun WidgetRefreshIcon(
    refreshAction: Action,
    refreshDescription: String,
    isRefreshing: Boolean
) {
    if (isRefreshing) {
        Image(
            provider = ImageProvider(R.drawable.widget_refresh_spinner_dots),
            contentDescription = refreshDescription,
            modifier = GlanceModifier.size(20.dp)
        )
    } else {
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = refreshDescription,
            modifier = GlanceModifier
                .size(20.dp)
                .clickable(onClick = refreshAction)
        )
    }
}

@Composable
private fun UnifiedProviderRow(
    provider: ProviderWidgetPayload,
    layoutSpec: UnifiedWidgetLayoutSpec,
    themeColors: WidgetThemeColors,
    reserveTopEndSpace: Boolean = false
) {
    val line = provider.lines.firstOrNull { it.remainingPercent != null }
        ?: provider.lines.firstOrNull()
    val ratio = line?.remainingPercent ?: 0f
    val remainingText = line?.remainingText
        ?.ifBlank { providerWidgetStatusLabel(provider.status) }
        ?: providerWidgetStatusLabel(provider.status)

    Column(modifier = GlanceModifier.height(layoutSpec.rowHeightDp.dp)) {
        if (layoutSpec.rowContentVerticalInsetDp > 0) {
            Spacer(modifier = GlanceModifier.height(layoutSpec.rowContentVerticalInsetDp.dp))
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(providerIconRes(provider.providerId)),
                contentDescription = provider.displayName,
                modifier = GlanceModifier.size(layoutSpec.providerIconSizeDp.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = provider.displayName,
                modifier = GlanceModifier.defaultWeight(),
                style = widgetTextStyle(layoutSpec.providerTextSizeSp, themeColors = themeColors)
            )
            Text(
                text = remainingText,
                style = widgetTextStyle(layoutSpec.detailTextSizeSp, TextAlign.End, themeColors)
            )
            if (reserveTopEndSpace) {
                Spacer(modifier = GlanceModifier.width(24.dp))
            }
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        GaugeBar(
            ratio = ratio,
            width = layoutSpec.gaugeWidthDp.dp,
            height = layoutSpec.gaugeHeightDp.dp,
            radius = layoutSpec.gaugeRadiusDp.dp,
            themeColors = themeColors,
            gaugeColorHex = provider.gaugeColorHex
        )
        if (layoutSpec.rowContentVerticalInsetDp > 0) {
            Spacer(modifier = GlanceModifier.height(layoutSpec.rowContentVerticalInsetDp.dp))
        }
        val detail = line?.resetText ?: line?.detailText
        if (unifiedWidgetShowsResetCaption(layoutSpec) && !detail.isNullOrBlank()) {
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = detail,
                modifier = GlanceModifier.fillMaxWidth(),
                style = widgetTextStyle(
                    layoutSpec.detailTextSizeSp,
                    widgetResetCaptionTextAlign(),
                    themeColors
                )
            )
        }
    }
}

@Composable
private fun CompactGauge(gauge: WidgetProviderGauge, layoutSpec: WidgetGaugeLayoutSpec, themeColors: WidgetThemeColors) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Image(
            provider = ImageProvider(providerIconRes(gauge.providerId)),
            contentDescription = gauge.providerId,
            modifier = GlanceModifier.size(layoutSpec.iconSizeDp.dp)
        )
        Spacer(modifier = GlanceModifier.width(3.dp))
        GaugeBar(
            ratio = gauge.remainingRatio,
            width = layoutSpec.gaugeWidthDp.dp,
            height = layoutSpec.gaugeHeightDp.dp,
            radius = layoutSpec.gaugeRadiusDp.dp,
            themeColors = themeColors,
            gaugeColorHex = gauge.gaugeColorHex
        )
    }
}

@Composable
private fun IconGauge(gauge: WidgetProviderGauge, layoutSpec: WidgetGaugeLayoutSpec, themeColors: WidgetThemeColors) {
    val resetTextWidthDp = (
        layoutSpec.gaugeWidthDp - EXPANDED_CAPTION_REMAINING_WIDTH_DP - EXPANDED_CAPTION_SPACER_WIDTH_DP
    ).coerceAtLeast(0)

    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Image(
            provider = ImageProvider(providerIconRes(gauge.providerId)),
            contentDescription = gauge.providerId,
            modifier = GlanceModifier.size(layoutSpec.iconSizeDp.dp)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column {
            GaugeBar(
                ratio = gauge.remainingRatio,
                width = layoutSpec.gaugeWidthDp.dp,
                height = layoutSpec.gaugeHeightDp.dp,
                radius = layoutSpec.gaugeRadiusDp.dp,
                themeColors = themeColors,
                gaugeColorHex = gauge.gaugeColorHex
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Row(
                modifier = GlanceModifier.width(layoutSpec.gaugeWidthDp.dp)
            ) {
                Text(
                    text = gauge.remainingText,
                    modifier = GlanceModifier.width(EXPANDED_CAPTION_REMAINING_WIDTH_DP.dp),
                    style = widgetTextStyle(themeColors = themeColors)
                )
                Spacer(modifier = GlanceModifier.width(EXPANDED_CAPTION_SPACER_WIDTH_DP.dp))
                Text(
                    text = gauge.resetText.orEmpty(),
                    modifier = GlanceModifier.width(resetTextWidthDp.dp),
                    style = widgetTextStyle(textAlign = TextAlign.End, themeColors = themeColors)
                )
            }
        }
    }
}

@Composable
private fun GaugeBar(
    ratio: Float,
    width: Dp,
    height: Dp,
    radius: Dp,
    themeColors: WidgetThemeColors,
    gaugeColorHex: String? = null
) {
    val activeColor = ProviderGaugeColor.toArgbOrNull(gaugeColorHex)?.let(::Color) ?: themeColors.gaugeColor(ratio)
    LinearProgressIndicator(
        progress = ratio.coerceIn(0f, 1f),
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height),
        color = ColorProvider(activeColor),
        backgroundColor = ColorProvider(themeColors.gaugeTrack)
    )
}

private fun providerIconRes(providerId: String): Int {
    return sharedProviderIconRes(providerId)
}

private fun Dp.toWidgetCells(min: Int, max: Int): Int {
    return (value / WIDGET_CELL_DP).roundToInt().coerceIn(min, max)
}

private fun widgetTextStyle(
    textSizeSp: Int = 11,
    textAlign: TextAlign = TextAlign.Start,
    themeColors: WidgetThemeColors
): TextStyle {
    return TextStyle(
        color = ColorProvider(themeColors.caption),
        fontSize = textSizeSp.sp,
        textAlign = textAlign
    )
}

private const val MAX_VISIBLE_GAUGES = 4
private const val EXPANDED_CAPTION_REMAINING_WIDTH_DP = 66
private const val EXPANDED_CAPTION_SPACER_WIDTH_DP = 8
private const val WIDGET_CELL_DP = 80f

private val RouteActionKey = ActionParameters.Key<String>(AppRoute.EXTRA_ROUTE)

class AIQuotaUnifiedGlanceWidget : AIQuotaGlanceWidget()

class AIQuotaUnifiedGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIQuotaUnifiedGlanceWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetAutoRefreshStarter.requestBackgroundRefresh(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

class AIQuotaLargeGlanceWidget : AIQuotaGlanceWidget()

class AIQuotaLargeGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIQuotaLargeGlanceWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetAutoRefreshStarter.requestBackgroundRefresh(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}
