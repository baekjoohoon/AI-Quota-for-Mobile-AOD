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
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesRepository
import com.aiusage.mobile.ui.AppRoute
import java.util.Locale
import kotlin.math.roundToInt

class ProviderUsageGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(width = 160.dp, height = 80.dp),
            DpSize(width = 240.dp, height = 80.dp),
            DpSize(width = 160.dp, height = 160.dp),
            DpSize(width = 240.dp, height = 160.dp),
            DpSize(width = 160.dp, height = 240.dp),
            DpSize(width = 240.dp, height = 240.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val providerId = selectedProvider(context, appWidgetId)
        val payload = providerId?.let {
            providerWidgetPayload(
                snapshotJson = WidgetSnapshotCache(context).read(),
                providerId = it.storageId
            )
        } ?: noVisibleProviderPayload()
        val providerAction = actionStartActivity(
            MainActivity::class.java,
            actionParametersOf(
                RouteActionKey.to(AppRoute.ROUTE_PROVIDER),
                ProviderActionKey.to(payload.providerId),
                LegacyProviderActionKey.to(payload.providerId)
            )
        )

        provideContent {
            ProviderWidgetContent(payload, providerAction)
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
    return savedSelection?.takeIf { it in providers }
        ?: providers.firstOrNull()
}

@Composable
private fun ProviderWidgetContent(payload: ProviderWidgetPayload, providerAction: Action) {
    val size = LocalSize.current
    val spec = providerWidgetLayoutSpec(
        cellWidth = size.width.toWidgetCells(min = 2, max = 3),
        cellHeight = size.height.toWidgetCells(min = 1, max = 3)
    )
    val visibleLines = payload.lines.take(spec.maxLineCount)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(onClick = providerAction)
            .cornerRadius(if (spec.cellHeight == 1) 18.dp else 24.dp)
            .background(ColorProvider(R.color.widget_background))
            .padding(horizontal = spec.horizontalPaddingDp.dp, vertical = spec.verticalPaddingDp.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        ProviderHeader(payload, spec)
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (visibleLines.isEmpty()) {
            ProviderEmptyState(payload, spec)
        } else {
            visibleLines.forEach { line ->
                ProviderLine(line, spec)
            }
        }
    }
}

@Composable
private fun ProviderHeader(payload: ProviderWidgetPayload, spec: ProviderWidgetLayoutSpec) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Image(
            provider = ImageProvider(providerIconRes(payload.providerId)),
            contentDescription = payload.displayName,
            modifier = GlanceModifier.size(if (spec.cellHeight == 1) 18.dp else 22.dp)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column {
            Text(
                text = payload.displayName,
                style = widgetTextStyle(spec.displayNameTextSizeSp)
            )
            if (spec.cellHeight > 1) {
                Text(
                    text = statusLabel(payload.status),
                    style = widgetTextStyle(spec.statusTextSizeSp)
                )
            }
        }
    }
}

@Composable
private fun ProviderEmptyState(payload: ProviderWidgetPayload, spec: ProviderWidgetLayoutSpec) {
    Text(
        text = if (payload.status.equals(DISCONNECTED_STATUS, ignoreCase = true)) {
            "Disconnected"
        } else {
            "No data"
        },
        style = widgetTextStyle(spec.lineTextSizeSp, TextAlign.Center)
    )
}

@Composable
private fun ProviderLine(line: ProviderWidgetLine, spec: ProviderWidgetLayoutSpec) {
    val labelWidthDp = (spec.gaugeWidthDp * 0.46f).roundToInt()
    val valueWidthDp = (spec.gaugeWidthDp - labelWidthDp).coerceAtLeast(1)
    Column(modifier = GlanceModifier.height(spec.lineRowHeightDp.dp)) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = line.label,
                modifier = GlanceModifier.width(labelWidthDp.dp),
                style = widgetTextStyle(spec.lineTextSizeSp)
            )
            Text(
                text = line.remainingText.ifBlank { statusLabel(line.severity) },
                modifier = GlanceModifier.width(valueWidthDp.dp),
                style = widgetTextStyle(spec.lineTextSizeSp, TextAlign.End)
            )
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
        GaugeBar(
            ratio = line.remainingPercent ?: 0f,
            width = spec.gaugeWidthDp.dp,
            height = spec.gaugeHeightDp.dp,
            radius = spec.gaugeRadiusDp.dp
        )
        if (spec.cellHeight > 1) {
            val detail = line.resetText ?: line.detailText
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = detail,
                    style = widgetTextStyle(spec.detailTextSizeSp)
                )
            }
        }
    }
}

@Composable
private fun GaugeBar(ratio: Float, width: Dp, height: Dp, radius: Dp) {
    val boundedRatio = ratio.coerceIn(0f, 1f)
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(height)
            .cornerRadius(radius)
            .background(Color(0xFF334155))
    ) {
        if (boundedRatio > 0f) {
            Box(
                modifier = GlanceModifier
                    .width(width * boundedRatio)
                    .height(height)
                    .cornerRadius(radius)
                    .background(gaugeColor(boundedRatio))
            ) {}
        }
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

private fun statusLabel(status: String): String {
    return when (status.trim().uppercase(Locale.US)) {
        "CONNECTED" -> "Connected"
        "CONNECTING" -> "Connecting"
        "DISCONNECTED" -> "Disconnected"
        "UNAVAILABLE" -> "Unavailable"
        "ERROR", "DANGER" -> "Error"
        "WARNING" -> "Warning"
        "NORMAL" -> "Normal"
        else -> status.ifBlank { "Unknown" }
    }
}

private fun providerIconRes(providerId: String): Int {
    return when (providerId.lowercase(Locale.US)) {
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

private fun noVisibleProviderPayload(): ProviderWidgetPayload {
    return ProviderWidgetPayload(
        providerId = UNKNOWN_PROVIDER_ID,
        displayName = "AI Usage",
        status = DISCONNECTED_STATUS,
        visible = false,
        lines = emptyList()
    )
}

class ProviderUsageGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProviderUsageGlanceWidget()
}

private val RouteActionKey = ActionParameters.Key<String>(AppRoute.EXTRA_ROUTE)
private val ProviderActionKey = ActionParameters.Key<String>(AppRoute.EXTRA_PROVIDER_ID)
private val LegacyProviderActionKey = ActionParameters.Key<String>(AppRoute.EXTRA_PROVIDER_ID_LEGACY)

private const val WIDGET_CELL_DP = 80f
private const val DISCONNECTED_STATUS = "DISCONNECTED"
private const val UNKNOWN_PROVIDER_ID = "unknown"
