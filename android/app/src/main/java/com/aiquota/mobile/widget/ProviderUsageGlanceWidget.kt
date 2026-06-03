package com.aiquota.mobile.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesRepository
import com.aiquota.mobile.ui.AppRoute
import com.aiquota.mobile.ui.provider.providerIconRes as sharedProviderIconRes
import kotlin.math.roundToInt

class ProviderUsageGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(width = 160.dp, height = 80.dp),
            DpSize(width = 240.dp, height = 80.dp),
            DpSize(width = 160.dp, height = 144.dp),
            DpSize(width = 240.dp, height = 144.dp),
            DpSize(width = 160.dp, height = 160.dp),
            DpSize(width = 240.dp, height = 160.dp),
            DpSize(width = 160.dp, height = 240.dp),
            DpSize(width = 240.dp, height = 240.dp),
            DpSize(width = 160.dp, height = 320.dp),
            DpSize(width = 240.dp, height = 320.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val hostOptionHeightDp = providerWidgetHostOptionHeightDp(context, appWidgetId)
        val providerId = selectedProvider(context, appWidgetId)
        val payload = providerId?.let {
            providerWidgetPayload(
                snapshotJson = WidgetSnapshotCache(context).read(),
                providerId = it.storageId
            )
        } ?: noVisibleProviderPayload()
        val themeColors = widgetThemeColors(context)
        val refreshAction = actionSendBroadcast(WidgetRefreshActions.widgetRefreshIntent(context, appWidgetId, providerId))
        val refreshDescription = context.getString(R.string.widget_refresh)
        val isRefreshing = WidgetRefreshFeedback.isRefreshInProgress(
            widgetRefreshActive = WidgetRefreshFeedback.isWidgetRefreshInProgress(context, appWidgetId)
        )
        val providerAction = actionStartActivity(
            MainActivity::class.java,
            actionParametersOf(
                RouteActionKey.to(AppRoute.ROUTE_PROVIDER),
                ProviderActionKey.to(payload.providerId),
                LegacyProviderActionKey.to(payload.providerId)
            )
        )

        provideContent {
            ProviderWidgetContent(
                payload,
                providerAction,
                refreshAction,
                refreshDescription,
                isRefreshing,
                themeColors,
                hostOptionHeightDp
            )
        }
    }

    private fun selectedProvider(context: Context, appWidgetId: Int): ProviderId? {
        val repository = ProviderPreferencesRepository(context)
        return providerWidgetSelection(
            savedSelection = repository.providerWidgetSelection(appWidgetId),
            providers = repository.providerOrder()
        )
    }
}

internal fun providerWidgetSelection(
    savedSelection: ProviderId?,
    providers: List<ProviderId>
): ProviderId? {
    val availableProviders = providers.ifEmpty { ProviderId.defaultOrder() }
    return savedSelection?.takeIf { it in availableProviders }
}

@Composable
private fun ProviderWidgetContent(
    payload: ProviderWidgetPayload,
    providerAction: Action,
    refreshAction: Action,
    refreshDescription: String,
    isRefreshing: Boolean,
    themeColors: WidgetThemeColors,
    hostOptionHeightDp: Int
) {
    val size = LocalSize.current
    val localHeightDp = size.height.value.roundToInt()
    val measuredHeightDp = providerWidgetMeasuredHeightDp(
        localHeightDp = localHeightDp,
        hostOptionHeightDp = hostOptionHeightDp
    )
    val cellWidth = size.width.toWidgetCells(min = 2, max = 3)
    val measuredCellHeight = providerWidgetCellHeightForDp(measuredHeightDp)
    val spec = providerWidgetLayoutSpec(
        cellWidth = cellWidth,
        cellHeight = providerWidgetEffectiveCellHeight(
            cellWidth = cellWidth,
            localHeightDp = localHeightDp,
            measuredCellHeight = measuredCellHeight
        ),
        widgetHeightDp = measuredHeightDp
    )
    val visibleLines = payload.lines.take(spec.maxLineCount)
    val centerContent = providerWidgetUsesCenteredContent(measuredHeightDp)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(onClick = providerAction)
            .cornerRadius(if (spec.cellHeight == 1) 18.dp else 24.dp)
            .background(themeColors.background)
            .padding(horizontal = spec.horizontalPaddingDp.dp, vertical = spec.verticalPaddingDp.dp),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        if (centerContent) {
            Spacer(modifier = GlanceModifier.defaultWeight())
        }
        ProviderHeader(payload, spec, refreshAction, refreshDescription, isRefreshing, themeColors)
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (visibleLines.isEmpty()) {
            ProviderEmptyState(payload, spec, themeColors)
        } else {
            visibleLines.forEachIndexed { index, line ->
                ProviderLine(line, spec, themeColors)
                if (index < visibleLines.lastIndex && spec.lineGapDp > 0) {
                    Spacer(modifier = GlanceModifier.height(spec.lineGapDp.dp))
                }
            }
        }
        if (centerContent) {
            Spacer(modifier = GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun ProviderHeader(
    payload: ProviderWidgetPayload,
    spec: ProviderWidgetLayoutSpec,
    refreshAction: Action,
    refreshDescription: String,
    isRefreshing: Boolean,
    themeColors: WidgetThemeColors
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(providerIconRes(payload.providerId)),
            contentDescription = payload.displayName,
            modifier = GlanceModifier.size(if (spec.cellHeight == 1) 18.dp else 22.dp)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = payload.displayName,
                style = widgetTextStyle(spec.displayNameTextSizeSp, themeColors = themeColors),
                maxLines = 1
            )
            if (spec.cellHeight > 1) {
                Text(
                    text = providerWidgetStatusLabel(payload.status),
                    style = widgetTextStyle(spec.statusTextSizeSp, themeColors = themeColors)
                )
            }
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
private fun ProviderEmptyState(
    payload: ProviderWidgetPayload,
    spec: ProviderWidgetLayoutSpec,
    themeColors: WidgetThemeColors
) {
    Text(
        text = if (payload.status.equals(DISCONNECTED_STATUS, ignoreCase = true)) {
            providerWidgetStatusLabel(DISCONNECTED_STATUS)
        } else {
            "데이터 없음"
        },
        style = widgetTextStyle(spec.lineTextSizeSp, TextAlign.Center, themeColors)
    )
}

@Composable
private fun ProviderLine(
    line: ProviderWidgetLine,
    spec: ProviderWidgetLayoutSpec,
    themeColors: WidgetThemeColors
) {
    Column(modifier = GlanceModifier.height(spec.lineRowHeightDp.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = line.label,
                modifier = GlanceModifier.defaultWeight(),
                style = widgetTextStyle(spec.lineTextSizeSp, themeColors = themeColors),
                maxLines = 1
            )
            Text(
                text = line.remainingText.ifBlank { providerWidgetStatusLabel(line.severity) },
                style = widgetTextStyle(spec.lineTextSizeSp, TextAlign.End, themeColors),
                maxLines = 1
            )
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        GaugeBar(
            ratio = line.remainingPercent ?: 0f,
            width = spec.gaugeWidthDp.dp,
            height = spec.gaugeHeightDp.dp,
            radius = spec.gaugeRadiusDp.dp,
            themeColors = themeColors
        )
        if (spec.showResetCaption) {
            val detail = line.resetText ?: line.detailText
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = detail,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = widgetTextStyle(
                        spec.detailTextSizeSp,
                        widgetResetCaptionTextAlign(),
                        themeColors
                    )
                )
            }
        }
    }
}

@Composable
private fun GaugeBar(ratio: Float, width: Dp, height: Dp, radius: Dp, themeColors: WidgetThemeColors) {
    val boundedRatio = ratio.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = boundedRatio,
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height),
        color = ColorProvider(themeColors.gaugeColor(boundedRatio)),
        backgroundColor = ColorProvider(themeColors.gaugeTrack)
    )
}

private fun Dp.toWidgetCells(min: Int, max: Int): Int {
    return (value / WIDGET_CELL_DP).roundToInt().coerceIn(min, max)
}

private fun widgetTextStyle(
    textSizeSp: Int,
    textAlign: TextAlign = TextAlign.Start,
    themeColors: WidgetThemeColors
): TextStyle {
    return TextStyle(
        color = ColorProvider(themeColors.caption),
        fontSize = textSizeSp.sp,
        textAlign = textAlign
    )
}

private fun providerIconRes(providerId: String): Int {
    return sharedProviderIconRes(providerId)
}

private fun providerWidgetHostOptionHeightDp(context: Context, appWidgetId: Int): Int {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
    return providerWidgetResolvedHostHeightDp(minHeightDp = minHeight, maxHeightDp = maxHeight)
}

internal fun providerWidgetResolvedHostHeightDp(minHeightDp: Int, maxHeightDp: Int): Int {
    return when {
        minHeightDp > 0 -> minHeightDp
        else -> maxHeightDp.coerceAtLeast(0)
    }
}

private fun noVisibleProviderPayload(): ProviderWidgetPayload {
    return ProviderWidgetPayload(
        providerId = UNKNOWN_PROVIDER_ID,
        displayName = "AI Quota",
        status = DISCONNECTED_STATUS,
        visible = false,
        lines = emptyList()
    )
}

class ProviderUsageGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProviderUsageGlanceWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetAutoRefreshStarter.requestBackgroundRefresh(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

private val RouteActionKey = ActionParameters.Key<String>(AppRoute.EXTRA_ROUTE)
private val ProviderActionKey = ActionParameters.Key<String>(AppRoute.EXTRA_PROVIDER_ID)
private val LegacyProviderActionKey = ActionParameters.Key<String>(AppRoute.EXTRA_PROVIDER_ID_LEGACY)

private const val WIDGET_CELL_DP = 80f
private const val DISCONNECTED_STATUS = "DISCONNECTED"
private const val UNKNOWN_PROVIDER_ID = "unknown"
