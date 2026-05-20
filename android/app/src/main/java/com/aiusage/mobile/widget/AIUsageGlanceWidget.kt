package com.aiusage.mobile.widget

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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aiusage.mobile.MainActivity
import com.aiusage.mobile.R
import com.aiusage.mobile.ui.AppRoute
import kotlin.math.roundToInt

open class AIUsageGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(width = 160.dp, height = 160.dp),
            DpSize(width = 240.dp, height = 160.dp),
            DpSize(width = 160.dp, height = 240.dp),
            DpSize(width = 240.dp, height = 240.dp),
            DpSize(width = 160.dp, height = 320.dp),
            DpSize(width = 240.dp, height = 320.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val payload = parseUnifiedWidgetPayload(WidgetSnapshotCache(context).read())
        val emptyMessage = context.getString(R.string.widget_no_data)
        val themeColors = widgetThemeColors(context)
        val homeAction = actionStartActivity(
            MainActivity::class.java,
            actionParametersOf(RouteActionKey.to(AppRoute.ROUTE_HOME))
        )

        provideContent {
            // Widget renders only from the local cache written by the app refresh flow.
            if (payload.providers.isNotEmpty()) {
                AIUsageWidgetContent(payload, emptyMessage, homeAction, themeColors)
            } else {
                EmptyDashboardWidgetContent(emptyMessage, homeAction, themeColors)
            }
        }
    }
}

@Composable
private fun EmptyDashboardWidgetContent(message: String, homeAction: Action, themeColors: WidgetThemeColors) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(onClick = homeAction)
            .cornerRadius(24.dp)
            .background(themeColors.background)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
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
private fun AIUsageWidgetContent(
    payload: UnifiedWidgetPayload,
    emptyMessage: String,
    homeAction: Action,
    themeColors: WidgetThemeColors
) {
    val widgetSize = LocalSize.current
    val layoutSpec = unifiedWidgetLayoutSpec(
        cellWidth = widgetSize.width.toWidgetCells(min = 2, max = 3),
        cellHeight = widgetSize.height.toWidgetCells(min = 2, max = 4)
    )
    val providers = payload.providers.take(layoutSpec.maxProviderCount)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(onClick = homeAction)
            .cornerRadius(24.dp)
            .background(themeColors.background)
            .padding(
                horizontal = layoutSpec.horizontalPaddingDp.dp,
                vertical = layoutSpec.verticalPaddingDp.dp
            ),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "AI Usage",
            style = widgetTextStyle(layoutSpec.titleTextSizeSp, themeColors = themeColors)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (providers.isEmpty()) {
            Text(
                text = emptyMessage,
                style = widgetTextStyle(layoutSpec.providerTextSizeSp, TextAlign.Center, themeColors)
            )
            return@Column
        }

        providers.forEach { provider ->
            UnifiedProviderRow(provider, layoutSpec, themeColors)
        }
    }
}

@Composable
private fun UnifiedProviderRow(
    provider: ProviderWidgetPayload,
    layoutSpec: UnifiedWidgetLayoutSpec,
    themeColors: WidgetThemeColors
) {
    val line = provider.lines.firstOrNull { it.remainingPercent != null }
        ?: provider.lines.firstOrNull()
    val ratio = line?.remainingPercent ?: 0f
    val providerTextWidthDp = (layoutSpec.gaugeWidthDp * 0.52f).roundToInt()
    val remainingTextWidthDp = (layoutSpec.gaugeWidthDp - providerTextWidthDp).coerceAtLeast(1)
    val remainingText = line?.remainingText
        ?.ifBlank { provider.status }
        ?: provider.status

    Column(modifier = GlanceModifier.height(layoutSpec.rowHeightDp.dp)) {
        Row(
            modifier = GlanceModifier.width(layoutSpec.gaugeWidthDp.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = ImageProvider(providerIconRes(provider.providerId)),
                contentDescription = provider.displayName,
                modifier = GlanceModifier.size(if (layoutSpec.cellHeight == 2) 16.dp else 18.dp)
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = provider.displayName,
                modifier = GlanceModifier.width((providerTextWidthDp - 22).coerceAtLeast(1).dp),
                style = widgetTextStyle(layoutSpec.providerTextSizeSp, themeColors = themeColors)
            )
            Text(
                text = remainingText,
                modifier = GlanceModifier.width(remainingTextWidthDp.dp),
                style = widgetTextStyle(layoutSpec.detailTextSizeSp, TextAlign.End, themeColors)
            )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        GaugeBar(
            ratio = ratio,
            width = layoutSpec.gaugeWidthDp.dp,
            height = layoutSpec.gaugeHeightDp.dp,
            radius = layoutSpec.gaugeRadiusDp.dp,
            themeColors = themeColors
        )
        val detail = line?.resetText ?: line?.detailText
        if (!detail.isNullOrBlank()) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = detail,
                style = widgetTextStyle(layoutSpec.detailTextSizeSp, themeColors = themeColors)
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
            themeColors = themeColors
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
                themeColors = themeColors
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
private fun GaugeBar(ratio: Float, width: Dp, height: Dp, radius: Dp, themeColors: WidgetThemeColors) {
    val activeWidth = width * ratio.coerceIn(0f, 1f)
    val activeColor = themeColors.gaugeColor(ratio)
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .cornerRadius(radius)
            .background(themeColors.gaugeTrack)
    ) {
        if (activeWidth > 0.dp) {
            Box(
                modifier = GlanceModifier
                    .width(activeWidth)
                    .height(height)
                    .cornerRadius(radius)
                    .background(activeColor)
            ) {}
        }
    }
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

class AIUsageUnifiedGlanceWidget : AIUsageGlanceWidget()

class AIUsageUnifiedGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIUsageUnifiedGlanceWidget()
}

class AIUsageLargeGlanceWidget : AIUsageGlanceWidget()

class AIUsageLargeGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIUsageLargeGlanceWidget()
}
