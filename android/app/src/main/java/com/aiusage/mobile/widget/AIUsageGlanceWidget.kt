package com.aiusage.mobile.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.aiusage.mobile.R

open class AIUsageGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(width = 64.dp, height = 64.dp),
            DpSize(width = 180.dp, height = 110.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Widget renders only from the local cache written by the app refresh flow.
            AIUsageWidgetContent(parseWidgetProviderGauges(WidgetSnapshotCache(context).readState().snapshotJson))
        }
    }
}

@Composable
private fun AIUsageWidgetContent(gauges: List<WidgetProviderGauge>) {
    val currentSize = LocalSize.current
    val isCompact = currentSize.width < 140.dp || currentSize.height < 100.dp
    val visibleGauges = gauges.take(MAX_VISIBLE_GAUGES)
    val layoutSpec = if (isCompact) {
        compactGaugeLayoutSpec(visibleGauges.size)
    } else {
        expandedGaugeLayoutSpec(visibleGauges.size)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(if (isCompact) 20.dp else 24.dp)
            .background(widgetBackgroundColor())
            .padding(if (isCompact) 4.dp else 10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (visibleGauges.isEmpty()) {
            Text("No data")
            return@Column
        }

        visibleGauges.forEachIndexed { index, gauge ->
            if (isCompact) {
                CompactGauge(gauge, layoutSpec)
            } else {
                IconGauge(gauge, layoutSpec)
            }
            if (index < visibleGauges.lastIndex && layoutSpec.rowSpacerHeightDp > 0) {
                Spacer(modifier = GlanceModifier.height(layoutSpec.rowSpacerHeightDp.dp))
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

private const val MAX_VISIBLE_GAUGES = 4
private const val EXPANDED_CAPTION_REMAINING_WIDTH_DP = 66
private const val EXPANDED_CAPTION_SPACER_WIDTH_DP = 8

private val WidgetCaptionStyle = TextStyle(
    color = ColorProvider(R.color.widget_caption),
    textAlign = TextAlign.Start
)

private fun widgetBackgroundColor(): ColorProvider {
    return ColorProvider(R.color.widget_background)
}

class AIUsageGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIUsageGlanceWidget()
}

class AIUsageLargeGlanceWidget : AIUsageGlanceWidget()

class AIUsageLargeGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AIUsageLargeGlanceWidget()
}
