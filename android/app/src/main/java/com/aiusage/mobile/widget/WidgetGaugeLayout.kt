package com.aiusage.mobile.widget

private const val EXPANDED_GAUGE_WIDTH_DP = 204
private const val EXPANDED_HORIZONTAL_PADDING_DP = 10
private const val EXPANDED_ICON_SPACER_DP = 8

data class WidgetGaugeLayoutSpec(
    val iconSizeDp: Int,
    val gaugeWidthDp: Int,
    val gaugeHeightDp: Int,
    val gaugeRadiusDp: Int,
    val rowSpacerHeightDp: Int,
    val rowHeightDp: Int = 0
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
