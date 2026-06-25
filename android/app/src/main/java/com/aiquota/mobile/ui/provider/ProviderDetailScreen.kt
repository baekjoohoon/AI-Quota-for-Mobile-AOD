package com.aiquota.mobile.ui.provider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aiquota.mobile.R
import com.aiquota.mobile.local.AppTheme
import com.aiquota.mobile.local.ProviderGaugeColor
import com.aiquota.mobile.local.ProviderConnectionAction
import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import com.aiquota.mobile.local.displayRemainingText
import com.aiquota.mobile.local.displayResetTextForLocale
import com.aiquota.mobile.local.displaySnapshotUpdatedAtLine
import com.aiquota.mobile.local.displayUsageLabel
import com.aiquota.mobile.local.effectiveResetText
import com.aiquota.mobile.local.primaryConnectionAction
import com.aiquota.mobile.ui.AIQuotaColors
import com.aiquota.mobile.ui.AIQuotaTheme
import com.aiquota.mobile.ui.AppLayoutMetrics
import com.aiquota.mobile.ui.compactProviderLineBreakStyle
import com.aiquota.mobile.ui.rememberAppLayoutMetrics
import kotlin.math.roundToInt

private val ClassicWindowChrome = AIQuotaColors.WindowChrome
private val ClassicTitleBlue = AIQuotaColors.SurfaceStrong
private val ClassicTitleText = AIQuotaColors.SurfaceMuted
private val ClassicBorderDark = AIQuotaColors.BorderDefault
private val ClassicBorderLight = AIQuotaColors.SurfaceMuted
private val ClassicContent = AIQuotaColors.SurfaceMuted
private val ClassicText = AIQuotaColors.TextPrimary
private val ClassicMutedText = AIQuotaColors.TextSecondary
private val ClassicProgress = AIQuotaColors.SurfaceStrong
private val ClassicProgressTrack = AIQuotaColors.WindowChrome
private val ProviderGaugeHeight = 10.4.dp

@Composable
fun ProviderDetailScreen(
    snapshot: ProviderUsageSnapshot,
    isHidden: Boolean,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAddWidget: () -> Unit,
    gaugeColorHex: String?,
    onGaugeColorChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutMetrics = rememberAppLayoutMetrics()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = layoutMetrics.contentHorizontalPaddingDp.dp,
                vertical = layoutMetrics.contentVerticalPaddingDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacingDp.dp)
    ) {
        ClassicProviderWindow(
            snapshot = snapshot,
            isBusy = isBusy,
            layoutMetrics = layoutMetrics,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onAddWidget = onAddWidget,
            gaugeColorHex = gaugeColorHex,
            onGaugeColorChange = onGaugeColorChange
        )
    }
}

@Composable
private fun ClassicProviderWindow(
    snapshot: ProviderUsageSnapshot,
    isBusy: Boolean,
    layoutMetrics: AppLayoutMetrics,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAddWidget: () -> Unit,
    gaugeColorHex: String?,
    onGaugeColorChange: (String?) -> Unit
) {
    val colors = AIQuotaTheme.colors
    val windowTitle = providerDetailWindowTitle(snapshot)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (colors.theme == AppTheme.MACOS) 12.dp else 2.dp),
        color = colors.cardChrome,
        shadowElevation = 1.dp,
        border = BorderStroke(if (colors.theme == AppTheme.MACOS) 1.dp else 2.dp, colors.border)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (colors.theme == AppTheme.MACOS) 30.dp else 22.dp)
                    .background(colors.titleBar)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (colors.theme == AppTheme.MACOS) {
                    ClassicWindowButton(index = 0)
                    Spacer(modifier = Modifier.width(6.dp))
                    ClassicWindowButton(index = 1)
                    Spacer(modifier = Modifier.width(6.dp))
                    ClassicWindowButton(index = 2)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = windowTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.titleText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (colors.theme != AppTheme.MACOS) {
                    ClassicWindowButton(index = 0)
                    Spacer(modifier = Modifier.width(3.dp))
                    ClassicWindowButton(index = 1)
                    Spacer(modifier = Modifier.width(3.dp))
                    ClassicWindowButton(index = 2)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardChrome)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderIconImage(
                    providerId = snapshot.providerId,
                    modifier = Modifier.size(18.dp)
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(0.dp),
                    color = colors.contentAlt,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = if (colors.theme == AppTheme.MACOS) {
                            "~/AI Quota/$windowTitle"
                        } else {
                            "C:\\AI Quota\\$windowTitle"
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                shape = RoundedCornerShape(0.dp),
                color = colors.content,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(layoutMetrics.cardPaddingDp.dp),
                    verticalArrangement = Arrangement.spacedBy(layoutMetrics.cardSpacingDp.dp)
                ) {
                    ProviderSummaryBlock(
                        snapshot = snapshot,
                        isBusy = isBusy,
                        layoutMetrics = layoutMetrics,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                        onAddWidget = onAddWidget,
                        gaugeColorHex = gaugeColorHex,
                        onGaugeColorChange = onGaugeColorChange
                    )

                    ClassicSectionTitle(text = stringResource(R.string.provider_usage_title))
                    if (snapshot.lines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.provider_no_lines),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary
                        )
                    } else {
                        snapshot.lines.forEachIndexed { index, line ->
                            ProviderUsageLineRow(
                                line = line,
                                providerId = snapshot.providerId,
                                lineIndex = index,
                                layoutMetrics = layoutMetrics,
                                gaugeColorHex = gaugeColorHex
                            )
                        }
                    }

                    UsageAnalysisSection(snapshot = snapshot)
                }
            }
        }
    }
}

@Composable
private fun ProviderSummaryBlock(
    snapshot: ProviderUsageSnapshot,
    isBusy: Boolean,
    layoutMetrics: AppLayoutMetrics,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAddWidget: () -> Unit,
    gaugeColorHex: String?,
    onGaugeColorChange: (String?) -> Unit
) {
    val colors = AIQuotaTheme.colors
    val connectionAction = snapshot.primaryConnectionAction()
    var showColorDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProviderIconImage(
                providerId = snapshot.providerId,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = snapshot.providerId.displayName,
                style = compactProviderLineBreakStyle(snapshot.providerId, MaterialTheme.typography.labelMedium),
                color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(layoutMetrics.cardSpacingDp.dp)
        ) {
            ClassicInfoLine(
                text = stringResource(R.string.provider_detail_status, providerStatus(snapshot, isBusy)),
                bold = true
            )
            snapshot.planLabel?.let { planLabel ->
                ClassicInfoLine(text = stringResource(R.string.provider_detail_plan, planLabel))
            }
            snapshot.updatedAt.takeIf { it.isNotBlank() }?.let { updatedAt ->
                ClassicInfoLine(text = displaySnapshotUpdatedAtLine(updatedAt))
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (connectionAction) {
                ProviderConnectionAction.CONNECT -> {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.widthIn(min = 112.dp, max = 180.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.provider_connect),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ProviderConnectionAction.DISCONNECT -> {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.widthIn(min = 112.dp, max = 180.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.provider_disconnect),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ProviderConnectionAction.NONE -> Unit
            }
            OutlinedButton(
                onClick = onAddWidget,
                modifier = Modifier.widthIn(min = 112.dp, max = 180.dp)
            ) {
                Text(
                    text = stringResource(R.string.provider_add_widget),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { showColorDialog = true },
                modifier = Modifier.widthIn(min = 112.dp, max = 180.dp)
            ) {
                Text(
                    text = stringResource(R.string.provider_usage_color_button),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (showColorDialog) {
        ProviderGaugeColorDialog(
            selectedColor = gaugeColorHex,
            onDismiss = { showColorDialog = false },
            onApply = { color ->
                onGaugeColorChange(color)
                showColorDialog = false
            }
        )
    }
}

@Composable
private fun ProviderUsageLineRow(
    line: ProviderUsageLine,
    providerId: ProviderId,
    lineIndex: Int,
    layoutMetrics: AppLayoutMetrics,
    gaugeColorHex: String?
) {
    val colors = AIQuotaTheme.colors
    val locale = java.util.Locale.getDefault()
    val resetText = displayResetTextForLocale(line.effectiveResetText(), locale)
    val gaugeColor = remember(gaugeColorHex, colors.progress) {
        ProviderGaugeColor.toArgbOrNull(gaugeColorHex)?.let(::Color) ?: colors.progress
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = colors.contentAlt,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(layoutMetrics.cardPaddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(layoutMetrics.cardSpacingDp.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = line.displayUsageLabel(providerId, lineIndex, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary
                )
                Text(
                    text = displayRemainingText(line.remainingText, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            line.remainingPercent?.let { remainingPercent ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ProviderGaugeHeight)
                        .clip(RoundedCornerShape(0.dp))
                        .background(colors.progressTrack)
                ) {
                    LinearProgressIndicator(
                        progress = { remainingPercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ProviderGaugeHeight),
                        color = gaugeColor,
                        trackColor = colors.progressTrack
                    )
                }
            }
            resetText?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProviderGaugeColorDialog(
    selectedColor: String?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit
) {
    var input by remember(selectedColor) { mutableStateOf(selectedColor.orEmpty()) }
    var showGradientPicker by remember { mutableStateOf(false) }
    val normalizedInput = ProviderGaugeColor.normalize(input)
    val showError = input.isNotBlank() && normalizedInput == null

    val colors = AIQuotaTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp),
            shape = RoundedCornerShape(if (colors.theme == AppTheme.MACOS) 16.dp else 2.dp),
            color = colors.panel,
            border = BorderStroke(if (colors.theme == AppTheme.MACOS) 1.dp else 2.dp, colors.border),
            shadowElevation = if (colors.theme == AppTheme.MACOS) 12.dp else 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.provider_usage_color_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                ProviderGaugeColorPalette(
                    selectedColor = normalizedInput ?: selectedColor,
                    onColorSelected = { input = it },
                    onGradientClick = { showGradientPicker = true }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = {
                            Text(
                                text = stringResource(R.string.provider_usage_color_input_label),
                                color = colors.textSecondary
                            )
                        },
                        singleLine = true,
                        isError = showError,
                        supportingText = {
                            if (showError) {
                                Text(
                                    text = stringResource(R.string.provider_usage_color_invalid),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = ProviderGaugeColor.toArgbOrNull(normalizedInput)?.let(::Color) ?: Color.Transparent,
                        border = BorderStroke(1.dp, colors.border),
                        content = {}
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onApply(null) }) {
                        Text(
                            text = stringResource(R.string.provider_usage_color_reset),
                            color = colors.primary
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.settings_close),
                            color = colors.primary
                        )
                    }
                    TextButton(
                        enabled = normalizedInput != null,
                        onClick = { onApply(normalizedInput) }
                    ) {
                        Text(
                            text = stringResource(R.string.provider_usage_color_apply),
                            color = colors.primary
                        )
                    }
                }
            }
        }
    }
    if (showGradientPicker) {
        ProviderGaugeGradientPickerDialog(
            selectedColor = normalizedInput ?: selectedColor,
            onColorSelected = { input = it },
            onDismiss = { showGradientPicker = false }
        )
    }
}

@Composable
private fun ProviderGaugeGradientButton(
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .width(34.dp)
            .height(152.dp)
            .clip(shape)
            .background(Brush.verticalGradient(GaugeGradientColors))
            .clickable(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(152.dp),
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, AIQuotaTheme.colors.border),
            content = {}
        )
    }
}

@Composable
private fun ProviderGaugeGradientPickerDialog(
    selectedColor: String?,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AIQuotaTheme.colors
    var pickerSize by remember { mutableStateOf(IntSize.Zero) }
    var pickerPosition by remember(selectedColor) {
        mutableStateOf(pickerPositionFromColor(selectedColor))
    }
    val selectedHex = pickerColorAt(pickerPosition)
    val selectedPreviewColor = ProviderGaugeColor.toArgbOrNull(selectedHex)?.let(::Color) ?: colors.progress

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(if (colors.theme == AppTheme.MACOS) 16.dp else 2.dp),
            color = colors.panel,
            border = BorderStroke(if (colors.theme == AppTheme.MACOS) 1.dp else 2.dp, colors.border),
            shadowElevation = if (colors.theme == AppTheme.MACOS) 12.dp else 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.provider_usage_color_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = selectedPreviewColor,
                        border = BorderStroke(1.dp, colors.border),
                        content = {}
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.horizontalGradient(GaugeGradientColors))
                        .onSizeChanged { pickerSize = it }
                        .pointerInput(pickerSize) {
                            detectTapGestures { position ->
                                pickerPosition = normalizedPickerPosition(position, pickerSize)
                                onColorSelected(pickerColorAt(pickerPosition))
                            }
                        }
                        .pointerInput(pickerSize) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    pickerPosition = normalizedPickerPosition(position, pickerSize)
                                    onColorSelected(pickerColorAt(pickerPosition))
                                },
                                onDrag = { change, _ ->
                                    pickerPosition = normalizedPickerPosition(change.position, pickerSize)
                                    onColorSelected(pickerColorAt(pickerPosition))
                                    change.consume()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                        val marker = Offset(
                            x = pickerPosition.x * size.width,
                            y = pickerPosition.y * size.height
                        )
                        drawCircle(Color.White, radius = 9.dp.toPx(), center = marker, style = Stroke(width = 3.dp.toPx()))
                        drawCircle(Color.Black, radius = 5.dp.toPx(), center = marker, style = Stroke(width = 2.dp.toPx()))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .width(68.dp)
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = selectedPreviewColor,
                        border = BorderStroke(1.dp, colors.border),
                        content = {}
                    )
                    Text(
                        text = selectedHex,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderGaugeColorPalette(
    selectedColor: String?,
    onColorSelected: (String) -> Unit,
    onGradientClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderGaugeColor.palette.chunked(6).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowColors.forEach { hexColor ->
                        ProviderGaugeColorSwatch(
                            hexColor = hexColor,
                            selected = hexColor.equals(selectedColor, ignoreCase = true),
                            onClick = { onColorSelected(hexColor) }
                        )
                    }
                }
            }
        }
        ProviderGaugeGradientButton(onClick = onGradientClick)
    }
}

@Composable
private fun ProviderGaugeColorSwatch(
    hexColor: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = AIQuotaTheme.colors
    val swatchArgb = ProviderGaugeColor.toArgbOrNull(hexColor)
    val swatchColor = swatchArgb?.let(::Color) ?: Color.Transparent
    Surface(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = swatchColor,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) colors.primary else colors.border
        )
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleSmall,
                    color = selectedSwatchMarkColor(swatchArgb),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun normalizedPickerPosition(offset: Offset, size: IntSize): Offset {
    val width = size.width.coerceAtLeast(1).toFloat()
    val height = size.height.coerceAtLeast(1).toFloat()
    return Offset(
        x = (offset.x / width).coerceIn(0f, 1f),
        y = (offset.y / height).coerceIn(0f, 1f)
    )
}

private fun pickerPositionFromColor(color: String?): Offset {
    val argb = ProviderGaugeColor.toArgbOrNull(color) ?: return Offset(0f, 0f)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return Offset(
        x = (hsv[0] / 360f).coerceIn(0f, 1f),
        y = ((1f - hsv[2]) / PICKER_VALUE_DARKENING).coerceIn(0f, 1f)
    )
}

private fun pickerColorAt(position: Offset): String {
    val hue = position.x.coerceIn(0f, 1f) * 360f
    val value = (1f - (position.y.coerceIn(0f, 1f) * PICKER_VALUE_DARKENING)).coerceIn(0f, 1f)
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.72f, value))
    return "#%02X%02X%02X".format(
        java.util.Locale.US,
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb)
    )
}

private fun selectedSwatchMarkColor(argb: Int?): Color {
    if (argb == null) return Color.White
    val red = android.graphics.Color.red(argb) / 255.0
    val green = android.graphics.Color.green(argb) / 255.0
    val blue = android.graphics.Color.blue(argb) / 255.0
    val luminance = (0.299 * red) + (0.587 * green) + (0.114 * blue)
    return if (luminance > 0.58) Color.Black else Color.White
}

private val GaugeGradientColors = listOf(
    Color(0xFFE15A4F),
    Color(0xFFF59E0B),
    Color(0xFFD6A23F),
    Color(0xFF4CAF50),
    Color(0xFF19A7A0),
    Color(0xFF3D8BFF),
    Color(0xFF6554C0),
    Color(0xFFC95EC8),
    Color(0xFFE15A4F)
)

private const val PICKER_VALUE_DARKENING = 0.7f

@Composable
private fun UsageAnalysisSection(snapshot: ProviderUsageSnapshot) {
    val colors = AIQuotaTheme.colors
    val measuredLines = snapshot.lines.mapNotNull { line ->
        line.remainingPercent?.let { remainingPercent -> line to remainingPercent }
    }
    val lowestLine = measuredLines.minByOrNull { it.second }
    val lowestResetText = lowestLine?.first?.effectiveResetText()?.let { displayResetTextForLocale(it) }

    ClassicSectionTitle(text = stringResource(R.string.provider_analysis_title))
    if (snapshot.lines.isEmpty()) {
        Text(
            text = stringResource(R.string.provider_analysis_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ClassicInfoLine(
            text = stringResource(R.string.provider_analysis_count, snapshot.lines.size),
            bold = true
        )
        lowestLine?.let { (line, remainingPercent) ->
            ClassicInfoLine(
                text = stringResource(
                    R.string.provider_analysis_lowest,
                    line.displayUsageLabel(snapshot.providerId, snapshot.lines.indexOf(line)),
                    percentText(remainingPercent)
                )
            )
        } ?: ClassicInfoLine(text = stringResource(R.string.provider_analysis_no_percent))
        lowestLine?.let {
            if (!lowestResetText.isNullOrBlank()) {
                ClassicInfoLine(text = stringResource(R.string.provider_analysis_reset, lowestResetText))
            }
        }
    }
}

@Composable
private fun ClassicSectionTitle(text: String) {
    val colors = AIQuotaTheme.colors

    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary
    )
}

@Composable
private fun ClassicInfoLine(
    text: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false
) {
    val colors = AIQuotaTheme.colors

    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ClassicWindowButton(index: Int) {
    val colors = AIQuotaTheme.colors
    val macColor = when (index) {
        0 -> colors.trafficRed
        1 -> colors.trafficYellow
        else -> colors.trafficGreen
    }

    Surface(
        modifier = Modifier.size(if (colors.theme == AppTheme.MACOS) 10.dp else 11.dp),
        shape = RoundedCornerShape(if (colors.theme == AppTheme.MACOS) 999.dp else 0.dp),
        color = if (colors.theme == AppTheme.MACOS) macColor else colors.cardChrome,
        border = BorderStroke(1.dp, if (colors.theme == AppTheme.MACOS) macColor else colors.borderSoft)
    ) {}
}

private fun providerDetailWindowTitle(snapshot: ProviderUsageSnapshot): String {
    return snapshot.displayName.ifBlank { snapshot.providerId.displayName }
}

@Composable
private fun providerStatus(snapshot: ProviderUsageSnapshot, isBusy: Boolean): String {
    if (isBusy) return stringResource(R.string.provider_status_working)
    if (snapshot.connectionState == ProviderConnectionState.CONNECTING) {
        return stringResource(R.string.provider_status_connecting)
    }
    if (snapshot.connectionState == ProviderConnectionState.COLLECTING || snapshot.refreshState == ProviderRefreshState.REFRESHING) {
        return stringResource(R.string.provider_status_collecting)
    }
    return when (snapshot.connectionState) {
        ProviderConnectionState.DISCONNECTED -> stringResource(R.string.provider_status_disconnected)
        ProviderConnectionState.CONNECTING -> stringResource(R.string.provider_status_connecting)
        ProviderConnectionState.CONNECTED -> stringResource(R.string.provider_status_connected)
        ProviderConnectionState.COLLECTING -> stringResource(R.string.provider_status_collecting)
        ProviderConnectionState.STALE -> if (snapshot.lines.isEmpty()) {
            stringResource(R.string.provider_status_auth_required)
        } else {
            stringResource(R.string.provider_status_connected)
        }
        ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> stringResource(R.string.provider_status_auth_required)
        ProviderConnectionState.UNAVAILABLE -> stringResource(R.string.provider_unavailable)
        ProviderConnectionState.ERROR -> stringResource(R.string.provider_status_error)
        ProviderConnectionState.NOT_CONNECTED -> stringResource(R.string.provider_status_disconnected)
    }
}

private fun percentText(value: Float): String {
    return "${(value.coerceIn(0f, 1f) * 100).roundToInt()}%"
}

private fun ProviderUsageLine.metadataText(): String? {
    val parts = buildList {
        category?.let { add("category: $it") }
        unit?.let { add("unit: $it") }
        windowText?.let { add("window: $it") }
        startsAt?.let { add("startsAt: $it") }
        resetsAt?.let { add("resetsAt: $it") }
        sourceLabel?.let { add("source: $it") }
        confidence?.let { add("confidence: ${(it.coerceIn(0f, 1f) * 100).roundToInt()}%") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
}
