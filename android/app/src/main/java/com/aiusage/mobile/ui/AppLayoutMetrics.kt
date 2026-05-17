package com.aiusage.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.ceil
import kotlin.math.roundToInt

data class AppLayoutMetrics(
    val contentHorizontalPaddingDp: Int,
    val contentVerticalPaddingDp: Int,
    val topBarHorizontalPaddingDp: Int,
    val topBarVerticalPaddingDp: Int,
    val topBarTopExtraPaddingDp: Int,
    val topBarSettingsYOffsetDp: Int,
    val mainContentTopLiftDp: Int,
    val navHorizontalPaddingDp: Int,
    val navVerticalPaddingDp: Int,
    val navGapDp: Int,
    val navBarMinHeightDp: Int,
    val navBarMaxWidthDp: Int,
    val navBottomExtraPaddingDp: Int,
    val navChipWidthDp: Int,
    val navChipHeightDp: Int,
    val navChipHorizontalPaddingDp: Int,
    val navChipVerticalPaddingDp: Int,
    val sectionSpacingDp: Int,
    val cardPaddingDp: Int,
    val cardSpacingDp: Int,
    val dashboardVisibleProviderCount: Int,
    val dashboardGridColumnCount: Int,
    val dashboardTitleHeightDp: Int,
    val dashboardCardMinHeightDp: Int
)

fun appLayoutMetrics(
    screenWidthDp: Int,
    screenHeightDp: Int
): AppLayoutMetrics {
    val compactWidth = screenWidthDp.coerceAtLeast(320)
    val compactHeight = screenHeightDp.coerceAtLeast(480)
    val widthScale = (compactWidth / 411f).coerceIn(1f, 1.8f)
    val heightScale = (compactHeight / 800f).coerceIn(0.95f, 1.25f)
    val isTablet = compactWidth >= 600

    fun scaled(base: Int, max: Int): Int {
        return (base * widthScale * heightScale).roundToInt().coerceAtMost(max)
    }

    val narrowPhone = !isTablet && compactWidth <= 360
    val navHorizontalPadding = when {
        isTablet -> scaled(14, 28)
        narrowPhone -> 8
        else -> scaled(10, 12)
    }
    val navVerticalPadding = if (isTablet) scaled(12, 18) else scaled(9, 12)
    val navChipVerticalPadding = if (isTablet) scaled(9, 12) else scaled(7, 9)
    val navGap = when {
        isTablet -> scaled(6, 10)
        narrowPhone -> 3
        else -> scaled(5, 6)
    }
    val navBottomExtraPadding = 30
    val routeCount = 6
    val availableNavWidth = compactWidth - (navHorizontalPadding * 2) - (navGap * (routeCount - 1))
    val fluidChipWidth = (availableNavWidth / routeCount.toFloat()).toInt()
    val navChipWidth = if (isTablet) {
        fluidChipWidth.coerceIn(72, 92)
    } else {
        fluidChipWidth.coerceIn(48, 64)
    }
    val navChipHeight = if (isTablet) scaled(56, 68) else scaled(56, 62)
    val navContentWidth = navChipWidth * routeCount + navGap * (routeCount - 1) + navHorizontalPadding * 2
    val dashboardGridColumnCount = if (isTablet) 2 else 1
    val dashboardVisibleProviderCount = if (isTablet) 4 else 3

    return AppLayoutMetrics(
        contentHorizontalPaddingDp = if (isTablet) scaled(22, 38) else scaled(14, 20),
        contentVerticalPaddingDp = if (isTablet) scaled(18, 28) else scaled(12, 18),
        topBarHorizontalPaddingDp = if (isTablet) scaled(22, 36) else scaled(16, 22),
        topBarVerticalPaddingDp = if (isTablet) scaled(18, 26) else scaled(14, 18),
        topBarTopExtraPaddingDp = if (isTablet) 0 else 13,
        topBarSettingsYOffsetDp = if (isTablet) 5 else 4,
        mainContentTopLiftDp = if (isTablet) 8 else 8,
        navHorizontalPaddingDp = navHorizontalPadding,
        navVerticalPaddingDp = navVerticalPadding,
        navGapDp = navGap,
        navBarMinHeightDp = navVerticalPadding * 2 + navChipHeight + navBottomExtraPadding,
        navBarMaxWidthDp = if (isTablet) navContentWidth else compactWidth,
        navBottomExtraPaddingDp = navBottomExtraPadding,
        navChipWidthDp = navChipWidth,
        navChipHeightDp = navChipHeight,
        navChipHorizontalPaddingDp = if (isTablet) scaled(8, 12) else scaled(6, 8),
        navChipVerticalPaddingDp = navChipVerticalPadding,
        sectionSpacingDp = if (isTablet) scaled(14, 20) else scaled(9, 12),
        cardPaddingDp = if (isTablet) scaled(14, 20) else scaled(9, 12),
        cardSpacingDp = if (isTablet) scaled(10, 14) else scaled(7, 10),
        dashboardVisibleProviderCount = dashboardVisibleProviderCount,
        dashboardGridColumnCount = dashboardGridColumnCount,
        dashboardTitleHeightDp = if (isTablet) scaled(38, 48) else scaled(36, 42),
        dashboardCardMinHeightDp = if (isTablet) scaled(180, 220) else scaled(176, 220)
    )
}

fun dashboardProviderCardHeightDp(
    viewportHeightDp: Int,
    layoutMetrics: AppLayoutMetrics
): Int {
    val visibleCount = layoutMetrics.dashboardVisibleProviderCount.coerceAtLeast(1)
    val columnCount = layoutMetrics.dashboardGridColumnCount.coerceAtLeast(1)
    val visibleRowCount = ceil(visibleCount / columnCount.toFloat())
        .roundToInt()
        .coerceAtLeast(1)
    val availableHeight = viewportHeightDp -
        layoutMetrics.contentVerticalPaddingDp -
        layoutMetrics.dashboardTitleHeightDp -
        layoutMetrics.sectionSpacingDp * visibleRowCount
    val fittedHeight = ceil(availableHeight / visibleRowCount.toFloat())
        .roundToInt()

    return fittedHeight.coerceAtLeast(layoutMetrics.dashboardCardMinHeightDp)
}

@Composable
fun rememberAppLayoutMetrics(): AppLayoutMetrics {
    val configuration = LocalConfiguration.current
    return appLayoutMetrics(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp
    )
}
