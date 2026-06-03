package com.aiquota.mobile.widget

import androidx.glance.text.TextAlign
import kotlin.math.roundToInt

private const val EXPANDED_GAUGE_WIDTH_DP = 204
private const val EXPANDED_HORIZONTAL_PADDING_DP = 10
private const val EXPANDED_ICON_SPACER_DP = 8
private const val WIDGET_CELL_WIDTH_DP = 80
private const val WIDGET_CELL_HEIGHT_DP = 80
private const val UNIFIED_TWO_CELL_VISUAL_HEIGHT_DP = 180
private const val UNIFIED_THREE_CELL_VISUAL_HEIGHT_DP = 270
private const val UNIFIED_HEADER_HEIGHT_DP = 24
private const val UNIFIED_HEADER_SPACER_HEIGHT_DP = 4
private const val PROVIDER_HEADER_HEIGHT_DP = 26
private const val PROVIDER_ESTIMATED_HEADER_HEIGHT_DP = 32
private const val PROVIDER_HEADER_SPACER_HEIGHT_DP = 4
private const val PROVIDER_TWO_CELL_MAX_ROW_HEIGHT_DP = 48
private const val PROVIDER_THREE_ROW_MAX_ROW_HEIGHT_DP = 31
private const val PROVIDER_FOUR_ROW_MAX_ROW_HEIGHT_DP = 42
private const val WIDGET_VERTICAL_SAFEZONE_DP = 10
private const val UNIFIED_THREE_CELL_HEIGHT_THRESHOLD_DP = 220
private const val PROVIDER_EXPANDED_LINE_GAP_DP = 3
private const val PROVIDER_EXPANDED_MAX_LINE_GAP_DP = 6
private const val PROVIDER_TWO_ROW_ADAPTIVE_MAX_ROW_HEIGHT_DP = 58
private const val PROVIDER_THREE_ROW_ADAPTIVE_MAX_ROW_HEIGHT_DP = 45
private const val PROVIDER_EXPANDED_ADAPTIVE_MAX_ROW_HEIGHT_DP = 44
private const val PROVIDER_WIDGET_LABEL_VALUE_GAP_DP = 1
private const val PROVIDER_TWO_ROW_MIN_HEIGHT_DP = 145

data class WidgetGaugeLayoutSpec(
    val iconSizeDp: Int,
    val gaugeWidthDp: Int,
    val gaugeHeightDp: Int,
    val gaugeRadiusDp: Int,
    val rowSpacerHeightDp: Int,
    val rowHeightDp: Int = 0
)

data class UnifiedWidgetLayoutSpec(
    val cellWidth: Int,
    val cellHeight: Int,
    val layoutHeightDp: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val titleTextSizeSp: Int,
    val providerTextSizeSp: Int,
    val detailTextSizeSp: Int,
    val providerIconSizeDp: Int,
    val gaugeWidthDp: Int,
    val gaugeHeightDp: Int,
    val gaugeRadiusDp: Int,
    val rowHeightDp: Int,
    val rowContentVerticalInsetDp: Int,
    val maxProviderCount: Int
)

data class ProviderWidgetLayoutSpec(
    val cellWidth: Int,
    val cellHeight: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val displayNameTextSizeSp: Int,
    val statusTextSizeSp: Int,
    val lineTextSizeSp: Int,
    val detailTextSizeSp: Int,
    val gaugeWidthDp: Int,
    val gaugeHeightDp: Int,
    val gaugeRadiusDp: Int,
    val lineRowHeightDp: Int,
    val lineGapDp: Int,
    val maxLineCount: Int,
    val showResetCaption: Boolean
)

data class ProviderWidgetLineTextStyleSpec(
    val labelTextSizeSp: Float,
    val remainingTextSizeSp: Float
)

fun compactGaugeLayoutSpec(visibleGaugeCount: Int): WidgetGaugeLayoutSpec {
    return when (visibleGaugeCount.coerceIn(1, 4)) {
        1 -> WidgetGaugeLayoutSpec(iconSizeDp = 12, gaugeWidthDp = 51, gaugeHeightDp = 12, gaugeRadiusDp = 6, rowSpacerHeightDp = 0)
        2 -> WidgetGaugeLayoutSpec(iconSizeDp = 12, gaugeWidthDp = 51, gaugeHeightDp = 9, gaugeRadiusDp = 5, rowSpacerHeightDp = 8)
        3 -> WidgetGaugeLayoutSpec(iconSizeDp = 11, gaugeWidthDp = 53, gaugeHeightDp = 7, gaugeRadiusDp = 4, rowSpacerHeightDp = 5)
        else -> WidgetGaugeLayoutSpec(iconSizeDp = 11, gaugeWidthDp = 53, gaugeHeightDp = 6, gaugeRadiusDp = 3, rowSpacerHeightDp = 3)
    }
}

fun expandedGaugeLayoutSpec(visibleGaugeCount: Int, widgetWidthDp: Int = 0): WidgetGaugeLayoutSpec {
    val base = when (visibleGaugeCount.coerceIn(1, 4)) {
        1 -> WidgetGaugeLayoutSpec(iconSizeDp = 24, gaugeWidthDp = EXPANDED_GAUGE_WIDTH_DP, gaugeHeightDp = 14, gaugeRadiusDp = 7, rowSpacerHeightDp = 0)
        2 -> WidgetGaugeLayoutSpec(iconSizeDp = 22, gaugeWidthDp = EXPANDED_GAUGE_WIDTH_DP, gaugeHeightDp = 12, gaugeRadiusDp = 6, rowSpacerHeightDp = 12)
        3 -> WidgetGaugeLayoutSpec(iconSizeDp = 20, gaugeWidthDp = EXPANDED_GAUGE_WIDTH_DP, gaugeHeightDp = 10, gaugeRadiusDp = 5, rowSpacerHeightDp = 7)
        else -> WidgetGaugeLayoutSpec(iconSizeDp = 18, gaugeWidthDp = EXPANDED_GAUGE_WIDTH_DP, gaugeHeightDp = 10, gaugeRadiusDp = 5, rowSpacerHeightDp = 8)
    }
    if (widgetWidthDp <= 0) return base

    val adaptiveGaugeWidth = (
        widgetWidthDp -
            (EXPANDED_HORIZONTAL_PADDING_DP * 2) -
            base.iconSizeDp -
            EXPANDED_ICON_SPACER_DP
        ).coerceAtLeast(EXPANDED_GAUGE_WIDTH_DP)

    return base.copy(gaugeWidthDp = adaptiveGaugeWidth)
}

fun unifiedWidgetLayoutSpec(
    cellWidth: Int,
    cellHeight: Int,
    widgetHeightDp: Int = 0,
    visibleProviderCount: Int? = null
): UnifiedWidgetLayoutSpec {
    val normalizedCellWidth = cellWidth.coerceIn(2, 3)
    val normalizedCellHeight = cellHeight.coerceIn(2, 3)
    val wideTwoCell = normalizedCellWidth == 3 && normalizedCellHeight == 2
    val horizontalPaddingDp = if (normalizedCellWidth == 2) 12 else 16
    val verticalPaddingDp = WIDGET_VERTICAL_SAFEZONE_DP
    val capacityProviderCount = when (normalizedCellHeight) {
        2 -> if (wideTwoCell) 6 else 4
        else -> 6
    }
    val rowCount = visibleProviderCount
        ?.coerceIn(1, capacityProviderCount)
        ?: capacityProviderCount
    val baseGaugeHeightDp = when {
        wideTwoCell -> if (rowCount <= 3) 8 else 4
        normalizedCellHeight == 2 -> if (rowCount <= 2) 10 else 8
        else -> if (rowCount <= 3) 8 else 4
    }
    val layoutHeightDp = if (widgetHeightDp > 0) {
        widgetHeightDp
    } else {
        when (normalizedCellHeight) {
            2 -> UNIFIED_TWO_CELL_VISUAL_HEIGHT_DP
            else -> UNIFIED_THREE_CELL_VISUAL_HEIGHT_DP
        }
    }
    val availableRowHeightDp = (
        layoutHeightDp -
            (verticalPaddingDp * 2)
        ) / rowCount
    val maxRowHeightDp = when {
        wideTwoCell -> if (rowCount <= 3) 42 else 30
        rowCount <= 2 -> 58
        rowCount <= 3 -> 48
        else -> 44
    }
    val rowHeightDp = availableRowHeightDp
        .coerceAtLeast(baseGaugeHeightDp + 12)
        .coerceAtMost(maxRowHeightDp)
    val extraHeightDp = if (widgetHeightDp > 0) {
        (layoutHeightDp - widgetCellSpanHeightDp(normalizedCellHeight)).coerceAtLeast(0)
    } else {
        0
    }
    val adaptiveGaugeExtraDp = if (rowCount > 0) {
        (extraHeightDp / rowCount) / 4
    } else {
        0
    }
    val adaptiveMaxGaugeHeightDp = (baseGaugeHeightDp * 13 / 10)
        .coerceAtLeast(baseGaugeHeightDp + 1)
    val gaugeHeightDp = if (adaptiveGaugeExtraDp > 0) {
        (baseGaugeHeightDp + adaptiveGaugeExtraDp).coerceAtMost(adaptiveMaxGaugeHeightDp)
    } else {
        baseGaugeHeightDp
    }
    val rowContentVerticalInsetDp = unifiedWidgetRowContentVerticalInsetDp(
        rowHeightDp = rowHeightDp,
        providerIconSizeDp = when {
            wideTwoCell -> if (rowCount <= 3) 16 else 12
            normalizedCellHeight == 2 -> if (rowCount <= 2) 18 else 16
            else -> 18
        },
        providerTextSizeSp = when {
            wideTwoCell -> if (rowCount <= 3) 11 else 9
            normalizedCellWidth == 2 -> if (rowCount <= 3) 12 else 11
            else -> 12
        },
        gaugeHeightDp = gaugeHeightDp
    )

    return UnifiedWidgetLayoutSpec(
        cellWidth = normalizedCellWidth,
        cellHeight = normalizedCellHeight,
        layoutHeightDp = layoutHeightDp,
        horizontalPaddingDp = horizontalPaddingDp,
        verticalPaddingDp = verticalPaddingDp,
        titleTextSizeSp = if (normalizedCellWidth == 2) 13 else 14,
        providerTextSizeSp = when {
            wideTwoCell -> if (rowCount <= 3) 11 else 9
            normalizedCellWidth == 2 -> if (rowCount <= 3) 12 else 11
            else -> 12
        },
        detailTextSizeSp = when {
            wideTwoCell -> if (rowCount <= 3) 9 else 8
            normalizedCellHeight == 2 -> if (rowCount <= 2) 10 else 9
            else -> 10
        },
        providerIconSizeDp = when {
            wideTwoCell -> if (rowCount <= 3) 16 else 12
            normalizedCellHeight == 2 -> if (rowCount <= 2) 18 else 16
            else -> 18
        },
        gaugeWidthDp = (widgetCellSpanWidthDp(normalizedCellWidth) - (horizontalPaddingDp * 2)).coerceAtLeast(1),
        gaugeHeightDp = gaugeHeightDp,
        gaugeRadiusDp = (gaugeHeightDp / 2).coerceAtLeast(1),
        rowHeightDp = rowHeightDp,
        rowContentVerticalInsetDp = rowContentVerticalInsetDp,
        maxProviderCount = rowCount
    )
}

fun providerWidgetLayoutSpec(
    cellWidth: Int,
    cellHeight: Int,
    widgetHeightDp: Int = 0
): ProviderWidgetLayoutSpec {
    val normalizedCellWidth = cellWidth.coerceIn(2, 3)
    val normalizedCellHeight = cellHeight.coerceIn(1, 4)
    val wideCompact = normalizedCellWidth == 3 && normalizedCellHeight == 1
    val horizontalPaddingDp = if (normalizedCellWidth == 2) 12 else 16
    val verticalPaddingDp = WIDGET_VERTICAL_SAFEZONE_DP
    val maxLineCount = when (normalizedCellHeight) {
        1 -> 1
        2 -> 3
        3 -> 4
        else -> 5
    }
    val gaugeHeightDp = when (normalizedCellHeight) {
        1 -> if (wideCompact) 6 else 7
        2 -> 9
        else -> 11
    }
    val availableLineHeightDp = (
        widgetCellSpanHeightDp(normalizedCellHeight) -
            (verticalPaddingDp * 2) -
            PROVIDER_HEADER_HEIGHT_DP -
            PROVIDER_HEADER_SPACER_HEIGHT_DP
        ) / maxLineCount
    val lineRowHeightDp = when {
        maxLineCount >= 4 -> availableLineHeightDp
            .coerceAtLeast(gaugeHeightDp + 14)
            .coerceAtMost(PROVIDER_FOUR_ROW_MAX_ROW_HEIGHT_DP)
        maxLineCount == 3 -> availableLineHeightDp
            .coerceAtLeast(gaugeHeightDp + 14)
            .coerceAtMost(PROVIDER_THREE_ROW_MAX_ROW_HEIGHT_DP)
        normalizedCellHeight == 2 -> availableLineHeightDp
            .coerceAtLeast(gaugeHeightDp + 14)
            .coerceAtMost(PROVIDER_TWO_CELL_MAX_ROW_HEIGHT_DP)
        else -> availableLineHeightDp.coerceAtLeast(gaugeHeightDp + 14)
    }

    val baseSpec = ProviderWidgetLayoutSpec(
        cellWidth = normalizedCellWidth,
        cellHeight = normalizedCellHeight,
        horizontalPaddingDp = horizontalPaddingDp,
        verticalPaddingDp = verticalPaddingDp,
        displayNameTextSizeSp = when {
            wideCompact -> 14
            normalizedCellWidth == 2 -> 12
            else -> 15
        },
        statusTextSizeSp = if (normalizedCellWidth == 2) 10 else 11,
        lineTextSizeSp = when {
            wideCompact -> 11
            normalizedCellWidth == 2 -> 11
            else -> 12
        },
        detailTextSizeSp = when {
            wideCompact -> 8
            normalizedCellHeight == 1 -> 9
            else -> 10
        },
        gaugeWidthDp = (widgetCellSpanWidthDp(normalizedCellWidth) - (horizontalPaddingDp * 2)).coerceAtLeast(1),
        gaugeHeightDp = gaugeHeightDp,
        gaugeRadiusDp = (gaugeHeightDp / 2).coerceAtLeast(1),
        lineRowHeightDp = lineRowHeightDp,
        lineGapDp = if (normalizedCellHeight > 1) PROVIDER_EXPANDED_LINE_GAP_DP else 0,
        maxLineCount = maxLineCount,
        showResetCaption = wideCompact || (normalizedCellHeight > 1 && lineRowHeightDp >= 40)
    )
    if (widgetHeightDp <= 0) return baseSpec

    val extraHeightDp = (widgetHeightDp - providerWidgetEstimatedContentHeightDp(baseSpec))
        .coerceAtLeast(0)
    val lineGapCount = (maxLineCount - 1).coerceAtLeast(0)
    val adaptiveLineGapDp = if (lineGapCount > 0 && baseSpec.lineGapDp > 0) {
        (baseSpec.lineGapDp + (extraHeightDp / lineGapCount))
            .coerceAtMost(PROVIDER_EXPANDED_MAX_LINE_GAP_DP)
    } else {
        baseSpec.lineGapDp
    }
    val lineGapExtraHeightDp = (adaptiveLineGapDp - baseSpec.lineGapDp) * lineGapCount
    val remainingExtraHeightDp = (extraHeightDp - lineGapExtraHeightDp).coerceAtLeast(0)
    val gaugeExtraDp = if (maxLineCount > 0) remainingExtraHeightDp / maxLineCount else 0
    val adaptiveGaugeHeightDp = (gaugeHeightDp + gaugeExtraDp).coerceAtMost(gaugeHeightDp * 12 / 10)
    val gaugeExtraHeightDp = (adaptiveGaugeHeightDp - gaugeHeightDp) * maxLineCount
    val remainingAfterGaugeHeightDp = (remainingExtraHeightDp - gaugeExtraHeightDp).coerceAtLeast(0)
    val lineRowExtraDp = if (maxLineCount > 0) remainingAfterGaugeHeightDp / maxLineCount else 0
    val adaptiveMaxLineRowHeightDp = when (maxLineCount) {
        1 -> lineRowHeightDp
        2 -> PROVIDER_TWO_ROW_ADAPTIVE_MAX_ROW_HEIGHT_DP
        3 -> PROVIDER_THREE_ROW_ADAPTIVE_MAX_ROW_HEIGHT_DP
        else -> PROVIDER_EXPANDED_ADAPTIVE_MAX_ROW_HEIGHT_DP
    }
    val adaptiveLineRowHeightDp = (
        lineRowHeightDp +
            (adaptiveGaugeHeightDp - gaugeHeightDp) +
            lineRowExtraDp
        ).coerceAtMost(adaptiveMaxLineRowHeightDp)

    return baseSpec.copy(
        gaugeHeightDp = adaptiveGaugeHeightDp,
        gaugeRadiusDp = (adaptiveGaugeHeightDp / 2).coerceAtLeast(1),
        lineRowHeightDp = adaptiveLineRowHeightDp,
        lineGapDp = adaptiveLineGapDp,
        showResetCaption = wideCompact || (normalizedCellHeight > 1 && adaptiveLineRowHeightDp >= 40)
    )
}

fun providerWidgetCellHeightForDp(heightDp: Int): Int {
    val boundedHeight = heightDp.coerceAtLeast(1)
    return when {
        boundedHeight < PROVIDER_TWO_ROW_MIN_HEIGHT_DP -> 1
        boundedHeight < 240 -> 2
        boundedHeight < 300 -> 3
        else -> 4
    }
}

fun providerWidgetMeasuredHeightDp(localHeightDp: Int, hostOptionHeightDp: Int): Int {
    return maxOf(localHeightDp, hostOptionHeightDp).coerceAtLeast(1)
}

fun providerWidgetEffectiveCellHeight(
    cellWidth: Int,
    localHeightDp: Int,
    measuredCellHeight: Int
): Int {
    val normalizedHeight = measuredCellHeight.coerceIn(1, 4)
    return if (localHeightDp in 1 until PROVIDER_TWO_ROW_MIN_HEIGHT_DP && normalizedHeight > 1) {
        1
    } else {
        normalizedHeight
    }
}

fun providerWidgetUsesCenteredContent(localHeightDp: Int): Boolean {
    return localHeightDp > 0
}

fun unifiedWidgetCellHeightForDp(heightDp: Int): Int {
    return if (heightDp.coerceAtLeast(1) < UNIFIED_THREE_CELL_HEIGHT_THRESHOLD_DP) 2 else 3
}

fun providerWidgetLabelWidthDp(spec: ProviderWidgetLayoutSpec): Int {
    return (spec.gaugeWidthDp * 58 / 100).coerceAtLeast(1)
}

fun providerWidgetValueWidthDp(spec: ProviderWidgetLayoutSpec): Int {
    return (spec.gaugeWidthDp - providerWidgetLabelWidthDp(spec)).coerceAtLeast(1)
}

fun providerWidgetLabelValueGapDp(spec: ProviderWidgetLayoutSpec): Int {
    return PROVIDER_WIDGET_LABEL_VALUE_GAP_DP
}

fun providerWidgetLineUsesResponsiveColumns(spec: ProviderWidgetLayoutSpec): Boolean {
    return spec.gaugeWidthDp > 0
}

fun providerWidgetLineTextStyleSpec(
    spec: ProviderWidgetLayoutSpec,
    label: String,
    remainingText: String
): ProviderWidgetLineTextStyleSpec {
    val baseTextSizeSp = spec.lineTextSizeSp.toFloat()
    val minimumTextSizeSp = if (spec.cellWidth == 2) 7f else 8f
    val remainingTextSizeSp = baseTextSizeSp
    val remainingWidthDp = providerWidgetEstimatedTextWidthDp(remainingText, remainingTextSizeSp)
        .roundToInt()
        .coerceAtLeast(0)
    val labelAvailableWidthDp = (
        spec.gaugeWidthDp -
            remainingWidthDp -
            providerWidgetLabelValueGapDp(spec)
        ).coerceAtLeast(1)
    val labelTextSizeSp = providerWidgetFittedTextSizeSp(
        text = label,
        availableWidthDp = labelAvailableWidthDp,
        baseTextSizeSp = baseTextSizeSp,
        minimumTextSizeSp = minimumTextSizeSp
    )
    return ProviderWidgetLineTextStyleSpec(
        labelTextSizeSp = labelTextSizeSp,
        remainingTextSizeSp = remainingTextSizeSp
    )
}

fun unifiedWidgetLineUsesResponsiveColumns(spec: UnifiedWidgetLayoutSpec): Boolean {
    return spec.gaugeWidthDp > 0
}

fun unifiedWidgetShowsResetCaption(spec: UnifiedWidgetLayoutSpec): Boolean {
    return false
}

fun unifiedWidgetEstimatedContentHeightDp(spec: UnifiedWidgetLayoutSpec): Int {
    return (spec.verticalPaddingDp * 2) +
        (spec.rowHeightDp * spec.maxProviderCount)
}

fun unifiedWidgetUsableHeightDp(spec: UnifiedWidgetLayoutSpec): Int {
    return spec.layoutHeightDp - (spec.verticalPaddingDp * 2)
}

fun unifiedWidgetUnusedVerticalHeightDp(spec: UnifiedWidgetLayoutSpec): Int {
    return (spec.layoutHeightDp - unifiedWidgetEstimatedContentHeightDp(spec)).coerceAtLeast(0)
}

fun unifiedWidgetCentersProviderBlock(spec: UnifiedWidgetLayoutSpec): Boolean {
    return spec.maxProviderCount > 0
}

fun unifiedWidgetReservesFirstRowTopEndSpace(spec: UnifiedWidgetLayoutSpec): Boolean {
    return false
}

fun unifiedWidgetRowVisibleContentHeightDp(spec: UnifiedWidgetLayoutSpec): Int {
    return (spec.rowContentVerticalInsetDp * 2) +
        maxOf(spec.providerIconSizeDp, spec.providerTextSizeSp + 2) +
        2 +
        spec.gaugeHeightDp
}

fun providerWidgetEstimatedContentHeightDp(spec: ProviderWidgetLayoutSpec): Int {
    return (spec.verticalPaddingDp * 2) +
        PROVIDER_ESTIMATED_HEADER_HEIGHT_DP +
        PROVIDER_HEADER_SPACER_HEIGHT_DP +
        (spec.lineRowHeightDp * spec.maxLineCount) +
        (spec.lineGapDp * (spec.maxLineCount - 1).coerceAtLeast(0))
}

private fun unifiedWidgetRowContentVerticalInsetDp(
    rowHeightDp: Int,
    providerIconSizeDp: Int,
    providerTextSizeSp: Int,
    gaugeHeightDp: Int
): Int {
    val visibleContentHeightDp = maxOf(providerIconSizeDp, providerTextSizeSp + 2) + 2 + gaugeHeightDp
    return ((rowHeightDp - visibleContentHeightDp) / 2).coerceAtLeast(0)
}

private fun providerWidgetFittedTextSizeSp(
    text: String,
    availableWidthDp: Int,
    baseTextSizeSp: Float,
    minimumTextSizeSp: Float
): Float {
    if (text.isBlank() || availableWidthDp <= 0) return baseTextSizeSp
    val estimatedWidthDp = providerWidgetEstimatedTextWidthDp(text, baseTextSizeSp)
    if (estimatedWidthDp <= availableWidthDp) return baseTextSizeSp
    val fitted = baseTextSizeSp * availableWidthDp / estimatedWidthDp
    return ((fitted * 2f).toInt() / 2f).coerceIn(minimumTextSizeSp, baseTextSizeSp)
}

private fun providerWidgetEstimatedTextWidthDp(text: String, textSizeSp: Float): Float {
    val emWidth = text.fold(0f) { total, char ->
        total + when {
            char.isWhitespace() -> 0.25f
            char.code <= 0x007F -> when {
                char.isLetter() -> 0.52f
                char.isDigit() -> 0.5f
                char in "%.-/:()[]" -> 0.3f
                else -> 0.4f
            }
            char.code in 0xAC00..0xD7AF -> 0.9f
            else -> 0.8f
        }
    }
    return emWidth * textSizeSp
}

fun widgetResetCaptionTextAlign(): TextAlign {
    return TextAlign.End
}

private fun widgetCellSpanWidthDp(cellWidth: Int): Int {
    return cellWidth * WIDGET_CELL_WIDTH_DP
}

private fun widgetCellSpanHeightDp(cellHeight: Int): Int {
    return cellHeight * WIDGET_CELL_HEIGHT_DP
}
