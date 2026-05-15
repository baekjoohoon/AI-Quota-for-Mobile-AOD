package com.aiusage.mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
        val homeAction = actionStartActivity(
            MainActivity::class.java,
            actionParametersOf(RouteActionKey.to(AppRoute.ROUTE_HOME))
        )

        provideContent {
            // Widget renders only from the local cache written by the app refresh flow.
            if (payload.providers.isNotEmpty()) {
                AIUsageWidgetContent(payload, emptyMessage, homeAction)
            } else {
                EmptyDashboardWidgetContent(emptyMessage, homeAction)
            }
        }
    }
}

@Composable
private fun EmptyDashboardWidgetContent(message: String, homeAction: Action) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(onClick = homeAction)
            .cornerRadius(24.dp)
            .background(widgetBackgroundColor())
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = TextStyle(
                color = ColorProvider(R.color.widget_caption),
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun AIUsageWidgetContent(payload: UnifiedWidgetPayload, emptyMessage: String, homeAction: Action) {
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
            .background(widgetBackgroundColor())
            .padding(
                horizontal = layoutSpec.horizontalPaddingDp.dp,
                vertical = layoutSpec.verticalPaddingDp.dp
            ),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "AI Usage",
            style = widgetTextStyle(layoutSpec.titleTextSizeSp)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (providers.isEmpty()) {
            Text(
                text = emptyMessage,
                style = widgetTextStyle(layoutSpec.providerTextSizeSp, TextAlign.Center)
            )
            return@Column
        }

        providers.forEach { provider ->
            UnifiedProviderRow(provider, layoutSpec)
        }
    }
}

@Composable
private fun UnifiedProviderRow(provider: ProviderWidgetPayload, layoutSpec: UnifiedWidgetLayoutSpec) {
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
                style = widgetTextStyle(layoutSpec.providerTextSizeSp)
            )
            Text(
                text = remainingText,
                modifier = GlanceModifier.width(remainingTextWidthDp.dp),
                style = widgetTextStyle(layoutSpec.detailTextSizeSp, TextAlign.End)
            )
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        GaugeBar(
            ratio = ratio,
            width = layoutSpec.gaugeWidthDp.dp,
            height = layoutSpec.gaugeHeightDp.dp,
            radius = layoutSpec.gaugeRadiusDp.dp
        )
        if (layoutSpec.cellHeight > 2) {
            val detail = line?.detailText ?: line?.resetText
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = detail,
                    style = widgetTextStyle(layoutSpec.detailTextSizeSp)
                )
            }
        }
    }
}

@Composable
private fun CompactGauge(gauge: WidgetProviderGauge, layoutSpec: WidgetGaugeLayoutSpec) {
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
            radius = layoutSpec.gaugeRadiusDp.dp
        )
    }
}

@Composable
private fun IconGauge(gauge: WidgetProviderGauge, layoutSpec: WidgetGaugeLayoutSpec) {
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
                radius = layoutSpec.gaugeRadiusDp.dp
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Row(
                modifier = GlanceModifier.width(layoutSpec.gaugeWidthDp.dp)
            ) {
                Text(
                    text = gauge.remainingText,
                    modifier = GlanceModifier.width(EXPANDED_CAPTION_REMAINING_WIDTH_DP.dp),
                    style = WidgetCaptionStyle
                )
                Spacer(modifier = GlanceModifier.width(EXPANDED_CAPTION_SPACER_WIDTH_DP.dp))
                Text(
                    text = gauge.resetText.orEmpty(),
                    modifier = GlanceModifier.width(resetTextWidthDp.dp),
                    style = WidgetCaptionStyle.copy(textAlign = TextAlign.End)
                )
            }
        }
    }
}

@Composable
private fun GaugeBar(ratio: Float, width: Dp, height: Dp, radius: Dp) {
    val activeWidth = width * ratio.coerceIn(0f, 1f)
    val activeColor = gaugeColor(ratio)
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .cornerRadius(radius)
            .background(Color(0xFF334155))
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

private fun gaugeColor(ratio: Float): Color {
    return when {
        ratio < 0.15f -> Color(0xFFEF4444)
        ratio < 0.35f -> Color(0xFFF59E0B)
        else -> Color(0xFF22C55E)
    }
}

private fun Dp.toWidgetCells(min: Int, max: Int): Int {
    return (value / WIDGET_CELL_DP).roundToInt().coerceIn(min, max)
}

private fun widgetTextStyle(textSizeSp: Int, textAlign: TextAlign = TextAlign.Start): TextStyle {
    return TextStyle(
        color = ColorProvider(R.color.widget_caption),
        fontSize = textSizeSp.sp,
        textAlign = textAlign
    )
}

private const val MAX_VISIBLE_GAUGES = 4
private const val EXPANDED_CAPTION_REMAINING_WIDTH_DP = 66
private const val EXPANDED_CAPTION_SPACER_WIDTH_DP = 8
private const val WIDGET_CELL_DP = 80f

private val WidgetCaptionStyle = TextStyle(
    color = ColorProvider(R.color.widget_caption),
    textAlign = TextAlign.Start
)

private val RouteActionKey = ActionParameters.Key<String>(AppRoute.EXTRA_ROUTE)

private fun widgetBackgroundColor(): ColorProvider {
    return ColorProvider(R.color.widget_background)
}

class AIUsageUnifiedGlanceWidget : AIUsageGlanceWidget()

class AIUsageUnifiedGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIUsageUnifiedGlanceWidget()
}

class AIUsageLargeGlanceWidget : AIUsageGlanceWidget()

class AIUsageLargeGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIUsageLargeGlanceWidget()
}
