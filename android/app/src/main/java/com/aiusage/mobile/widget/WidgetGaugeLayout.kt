package com.aiusage.mobile.widget

private const val EXPANDED_GAUGE_WIDTH_DP = 204
private const val EXPANDED_HORIZONTAL_PADDING_DP = 10
private const val EXPANDED_ICON_SPACER_DP = 8
private const val WIDGET_CELL_WIDTH_DP = 80
private const val WIDGET_CELL_HEIGHT_DP = 80
private const val UNIFIED_HEADER_HEIGHT_DP = 28
private const val PROVIDER_HEADER_HEIGHT_DP = 26

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
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val titleTextSizeSp: Int,
    val providerTextSizeSp: Int,
    val detailTextSizeSp: Int,
    val gaugeWidthDp: Int,
    val gaugeHeightDp: Int,
    val gaugeRadiusDp: Int,
    val rowHeightDp: Int,
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
    val maxLineCount: Int
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

fun unifiedWidgetLayoutSpec(cellWidth: Int, cellHeight: Int): UnifiedWidgetLayoutSpec {
    val normalizedCellWidth = cellWidth.coerceIn(2, 3)
    val normalizedCellHeight = cellHeight.coerceIn(2, 4)
    val horizontalPaddingDp = if (normalizedCellWidth == 2) 12 else 16
    val verticalPaddingDp = if (normalizedCellHeight == 2) 12 else 16
    val maxProviderCount = when (normalizedCellHeight) {
        2 -> if (normalizedCellWidth == 2) 3 else 4
        3 -> if (normalizedCellWidth == 2) 4 else 5
        else -> 5
    }
    val gaugeHeightDp = when (normalizedCellHeight) {
        2 -> 8
        3 -> 10
        else -> 12
    }
    val availableRowHeightDp = (
        widgetCellSpanHeightDp(normalizedCellHeight) -
            (verticalPaddingDp * 2) -
            UNIFIED_HEADER_HEIGHT_DP
        ) / maxProviderCount

    return UnifiedWidgetLayoutSpec(
        cellWidth = normalizedCellWidth,
        cellHeight = normalizedCellHeight,
        horizontalPaddingDp = horizontalPaddingDp,
        verticalPaddingDp = verticalPaddingDp,
        titleTextSizeSp = if (normalizedCellWidth == 2) 13 else 14,
        providerTextSizeSp = if (normalizedCellWidth == 2) 11 else 12,
        detailTextSizeSp = if (normalizedCellHeight == 2) 9 else 10,
        gaugeWidthDp = (widgetCellSpanWidthDp(normalizedCellWidth) - (horizontalPaddingDp * 2)).coerceAtLeast(1),
        gaugeHeightDp = gaugeHeightDp,
        gaugeRadiusDp = (gaugeHeightDp / 2).coerceAtLeast(1),
        rowHeightDp = availableRowHeightDp.coerceAtLeast(gaugeHeightDp + 12),
        maxProviderCount = maxProviderCount
    )
}

fun providerWidgetLayoutSpec(cellWidth: Int, cellHeight: Int): ProviderWidgetLayoutSpec {
    val normalizedCellWidth = cellWidth.coerceIn(2, 3)
    val normalizedCellHeight = cellHeight.coerceIn(1, 3)
    val horizontalPaddingDp = if (normalizedCellWidth == 2) 12 else 16
    val verticalPaddingDp = if (normalizedCellHeight == 1) 10 else 14
    val maxLineCount = normalizedCellHeight
    val gaugeHeightDp = when (normalizedCellHeight) {
        1 -> 8
        2 -> 10
        else -> 12
    }
    val availableLineHeightDp = (
        widgetCellSpanHeightDp(normalizedCellHeight) -
            (verticalPaddingDp * 2) -
            PROVIDER_HEADER_HEIGHT_DP
        ) / maxLineCount

    return ProviderWidgetLayoutSpec(
        cellWidth = normalizedCellWidth,
        cellHeight = normalizedCellHeight,
        horizontalPaddingDp = horizontalPaddingDp,
        verticalPaddingDp = verticalPaddingDp,
        displayNameTextSizeSp = if (normalizedCellWidth == 2) 13 else 15,
        statusTextSizeSp = if (normalizedCellWidth == 2) 10 else 11,
        lineTextSizeSp = if (normalizedCellWidth == 2) 11 else 12,
        detailTextSizeSp = if (normalizedCellHeight == 1) 9 else 10,
        gaugeWidthDp = (widgetCellSpanWidthDp(normalizedCellWidth) - (horizontalPaddingDp * 2)).coerceAtLeast(1),
        gaugeHeightDp = gaugeHeightDp,
        gaugeRadiusDp = (gaugeHeightDp / 2).coerceAtLeast(1),
        lineRowHeightDp = availableLineHeightDp.coerceAtLeast(gaugeHeightDp + 14),
        maxLineCount = maxLineCount
    )
}

private fun widgetCellSpanWidthDp(cellWidth: Int): Int {
    return cellWidth * WIDGET_CELL_WIDTH_DP
}

private fun widgetCellSpanHeightDp(cellHeight: Int): Int {
    return cellHeight * WIDGET_CELL_HEIGHT_DP
}
