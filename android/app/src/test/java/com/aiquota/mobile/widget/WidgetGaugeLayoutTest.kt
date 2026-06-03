package com.aiquota.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.glance.text.TextAlign
import org.junit.Test

class WidgetGaugeLayoutTest {
    @Test
    fun unifiedTwoByTwoWidgetShowsFourProviders() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 2)

        assertEquals(4, spec.maxProviderCount)
        assertEquals(40, spec.rowHeightDp)
    }

    @Test
    fun unifiedThreeByThreeWidgetShowsAllProviders() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 3, cellHeight = 3)

        assertEquals(6, spec.maxProviderCount)
        assertEquals(41, spec.rowHeightDp)
        assertEquals(4, spec.gaugeHeightDp)
        assertTrue(spec.detailTextSizeSp >= 10)
        assertFalse(unifiedWidgetShowsResetCaption(spec))
    }

    @Test
    fun unifiedTwoByThreeWidgetShowsAllProvidersWithoutWidening() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3)

        assertEquals(6, spec.maxProviderCount)
        assertEquals(4, spec.gaugeHeightDp)
        assertEquals(41, spec.rowHeightDp)
        assertEquals(10, spec.verticalPaddingDp)
        assertFalse(unifiedWidgetShowsResetCaption(spec))
    }

    @Test
    fun unifiedDashboardCapsExtraHeightSoRowsDoNotOverflow() {
        val fixedCellSpec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3)
        val tallerSpec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3, widgetHeightDp = 400)

        assertEquals(6, tallerSpec.maxProviderCount)
        assertTrue(tallerSpec.rowHeightDp > fixedCellSpec.rowHeightDp)
        assertTrue(unifiedWidgetEstimatedContentHeightDp(tallerSpec) <= 400)
        assertTrue(unifiedWidgetUnusedVerticalHeightDp(tallerSpec) > 10)
    }

    @Test
    fun unifiedDashboardTwoByTwoContentFitsReportedHeight() {
        val spec = unifiedWidgetLayoutSpec(
            cellWidth = 2,
            cellHeight = 2,
            widgetHeightDp = 160
        )

        assertEquals(4, spec.maxProviderCount)
        assertEquals(10, spec.verticalPaddingDp)
        assertTrue(unifiedWidgetEstimatedContentHeightDp(spec) <= 160)
    }

    @Test
    fun unifiedDashboardTwoByThreeContentFitsReportedHeight() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3, widgetHeightDp = 240)

        assertEquals(6, spec.maxProviderCount)
        assertEquals(36, spec.rowHeightDp)
        assertEquals(10, spec.verticalPaddingDp)
        assertTrue(unifiedWidgetEstimatedContentHeightDp(spec) <= 240)
    }

    @Test
    fun unifiedDashboardAdaptsRowsToActualVisibleProviderCount() {
        val full = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3, widgetHeightDp = 240)
        val reduced = unifiedWidgetLayoutSpec(
            cellWidth = 2,
            cellHeight = 3,
            widgetHeightDp = 240,
            visibleProviderCount = 2
        )

        assertEquals(6, full.maxProviderCount)
        assertEquals(2, reduced.maxProviderCount)
        assertTrue(reduced.rowHeightDp > full.rowHeightDp)
        assertTrue(reduced.providerTextSizeSp >= full.providerTextSizeSp)
        assertTrue(reduced.detailTextSizeSp >= full.detailTextSizeSp)
        assertTrue(reduced.gaugeHeightDp >= full.gaugeHeightDp)
        assertTrue(unifiedWidgetEstimatedContentHeightDp(reduced) <= 240)
    }

    @Test
    fun unifiedWideTwoCellDashboardShowsAllProvidersOnTablet() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 3, cellHeight = 2, widgetHeightDp = 160)

        assertEquals(6, spec.maxProviderCount)
        assertEquals(23, spec.rowHeightDp)
        assertEquals(10, spec.verticalPaddingDp)
        assertEquals(12, spec.providerIconSizeDp)
        assertTrue(unifiedWidgetEstimatedContentHeightDp(spec) <= 160)
    }

    @Test
    fun unifiedWideTabletIntermediateHeightAddsBreathingRoom() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 3, cellHeight = 3, widgetHeightDp = 200)

        assertEquals(6, spec.maxProviderCount)
        assertTrue(spec.rowHeightDp >= 24)
        assertEquals(10, spec.verticalPaddingDp)
        assertTrue(unifiedWidgetEstimatedContentHeightDp(spec) <= 200)
    }

    @Test
    fun unifiedDashboardUsesVisualHeightBaselineForLauncherReportedSizes() {
        val smallReportedTwoByTwo = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 2, widgetHeightDp = 160)
        val smallReportedTwoByThree = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3, widgetHeightDp = 240)

        assertEquals(35, smallReportedTwoByTwo.rowHeightDp)
        assertEquals(36, smallReportedTwoByThree.rowHeightDp)
    }

    @Test
    fun unifiedDashboardHeightIsCappedAtThreeCells() {
        assertEquals(3, unifiedWidgetCellHeightForDp(241))
    }

    @Test
    fun unifiedDashboardKeepsTwoCellLayoutForTallTwoByTwoLauncherHeights() {
        assertEquals(2, unifiedWidgetCellHeightForDp(160))
        assertEquals(2, unifiedWidgetCellHeightForDp(200))
        assertEquals(2, unifiedWidgetCellHeightForDp(219))
        assertEquals(3, unifiedWidgetCellHeightForDp(220))
    }

    @Test
    fun unifiedDashboardUsesExtraReportedHeightForRowsAndGaugeThickness() {
        val normal = unifiedWidgetLayoutSpec(
            cellWidth = 2,
            cellHeight = unifiedWidgetCellHeightForDp(160),
            widgetHeightDp = 160,
            visibleProviderCount = 4
        )
        val tall = unifiedWidgetLayoutSpec(
            cellWidth = 2,
            cellHeight = unifiedWidgetCellHeightForDp(200),
            widgetHeightDp = 200,
            visibleProviderCount = 4
        )

        assertEquals(2, tall.cellHeight)
        assertEquals(10, tall.verticalPaddingDp)
        assertTrue(tall.rowHeightDp > normal.rowHeightDp)
        assertTrue(tall.gaugeHeightDp > normal.gaugeHeightDp)
        assertTrue(tall.gaugeHeightDp <= (normal.gaugeHeightDp * 13 / 10).coerceAtLeast(normal.gaugeHeightDp + 1))
        assertTrue(unifiedWidgetEstimatedContentHeightDp(tall) <= 200)
    }

    @Test
    fun providerWidgetKeepsEnoughLabelWidthForTwoByOneText() {
        val spec = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1)

        assertEquals(7, spec.gaugeHeightDp)
        assertTrue(spec.displayNameTextSizeSp <= 12)
        assertEquals(10, spec.verticalPaddingDp)
        assertTrue(providerWidgetLabelWidthDp(spec) >= 76)
        assertTrue(providerWidgetValueWidthDp(spec) >= 48)
    }

    @Test
    fun providerWidgetKeepsOneRowUntilTwoRowsCanFitWithoutClipping() {
        assertEquals(1, providerWidgetCellHeightForDp(110))
        assertEquals(1, providerWidgetCellHeightForDp(144))
        assertEquals(2, providerWidgetCellHeightForDp(145))
        assertEquals(2, providerWidgetCellHeightForDp(194))
        assertEquals(3, providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 2).maxLineCount)
    }

    @Test
    fun providerWidgetKeepsCompactLocalHeightFromRenderingExpandedRows() {
        val measuredCellHeight = providerWidgetCellHeightForDp(
            providerWidgetMeasuredHeightDp(localHeightDp = 80, hostOptionHeightDp = 160)
        )

        assertEquals(2, measuredCellHeight)
        assertEquals(
            1,
            providerWidgetEffectiveCellHeight(
                cellWidth = 2,
                localHeightDp = 80,
                measuredCellHeight = measuredCellHeight
            )
        )
        assertEquals(2, providerWidgetCellHeightForDp(providerWidgetMeasuredHeightDp(localHeightDp = 160, hostOptionHeightDp = 40)))
        assertEquals(3, providerWidgetCellHeightForDp(providerWidgetMeasuredHeightDp(localHeightDp = 240, hostOptionHeightDp = 40)))
        assertEquals(4, providerWidgetCellHeightForDp(providerWidgetMeasuredHeightDp(localHeightDp = 320, hostOptionHeightDp = 40)))
    }

    @Test
    fun providerWidgetKeepsTwoByOneCompactWhenHostRangeStaysBelowTwoCells() {
        val hostHeight = providerWidgetResolvedHostHeightDp(minHeightDp = 40, maxHeightDp = 110)
        val measuredHeight = providerWidgetMeasuredHeightDp(localHeightDp = 80, hostOptionHeightDp = hostHeight)

        assertEquals(40, hostHeight)
        assertEquals(1, providerWidgetCellHeightForDp(measuredHeight))
        assertEquals(1, providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1).maxLineCount)
    }

    @Test
    fun providerWidgetKeepsTwoByOneCompactWhenLauncherReportsTallMaxHeight() {
        val hostHeight = providerWidgetResolvedHostHeightDp(minHeightDp = 40, maxHeightDp = 160)
        val measuredHeight = providerWidgetMeasuredHeightDp(localHeightDp = 80, hostOptionHeightDp = hostHeight)

        assertEquals(40, hostHeight)
        assertEquals(1, providerWidgetCellHeightForDp(measuredHeight))
        assertEquals(1, providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1).maxLineCount)
    }

    @Test
    fun providerWidgetUsesResponsiveLocalSizeForTwoByTwoRows() {
        val hostHeight = providerWidgetResolvedHostHeightDp(minHeightDp = 160, maxHeightDp = 160)
        val measuredHeight = providerWidgetMeasuredHeightDp(localHeightDp = 160, hostOptionHeightDp = hostHeight)

        assertEquals(160, hostHeight)
        assertEquals(2, providerWidgetCellHeightForDp(measuredHeight))
        assertEquals(3, providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 2).maxLineCount)
    }

    @Test
    fun providerTwoByTwoWidgetFitsThreeUsageRows() {
        val spec = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 2)

        assertEquals(3, spec.maxLineCount)
        assertEquals(9, spec.gaugeHeightDp)
        assertTrue(spec.lineRowHeightDp >= 31)
        assertFalse(spec.showResetCaption)
        assertTrue(providerWidgetEstimatedContentHeightDp(spec) <= 160)
        assertEquals(3, spec.lineGapDp)
    }

    @Test
    fun providerWidgetUsesExtraReportedHeightForSessionGapBeforeGaugeThickness() {
        val normal = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 2)
        val tall = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 2, widgetHeightDp = 220)

        assertEquals(normal.maxLineCount, tall.maxLineCount)
        assertEquals(normal.lineTextSizeSp, tall.lineTextSizeSp)
        assertEquals(normal.detailTextSizeSp, tall.detailTextSizeSp)
        assertTrue(tall.lineGapDp > normal.lineGapDp)
        assertTrue(tall.gaugeHeightDp > normal.gaugeHeightDp)
        assertTrue(tall.gaugeHeightDp <= normal.gaugeHeightDp * 12 / 10)
        assertTrue(tall.lineRowHeightDp > normal.lineRowHeightDp)
        assertTrue(providerWidgetEstimatedContentHeightDp(tall) <= 220)
    }

    @Test
    fun providerWideTwoByTwoUsesReportedHeightForThirdRowBreathingRoom() {
        val normal = providerWidgetLayoutSpec(cellWidth = 3, cellHeight = 2)
        val tall = providerWidgetLayoutSpec(cellWidth = 3, cellHeight = 2, widgetHeightDp = 220)

        assertEquals(3, tall.maxLineCount)
        assertTrue(tall.lineGapDp > normal.lineGapDp)
        assertTrue(tall.lineRowHeightDp > normal.lineRowHeightDp)
        assertTrue(tall.showResetCaption)
        assertTrue(providerWidgetEstimatedContentHeightDp(tall) <= 220)
    }

    @Test
    fun providerWideTwoByTwoWidgetShowsThirdRowWithoutResetCaption() {
        val spec = providerWidgetLayoutSpec(cellWidth = 3, cellHeight = 2)

        assertEquals(3, spec.maxLineCount)
        assertEquals(9, spec.gaugeHeightDp)
        assertFalse(spec.showResetCaption)
        assertTrue(spec.lineRowHeightDp >= 31)
        assertTrue(providerWidgetEstimatedContentHeightDp(spec) <= 160)
    }

    @Test
    fun providerWidgetExpandsVisibleLinesAsProviderWidgetIsResized() {
        assertEquals(1, providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1).maxLineCount)
        assertEquals(3, providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 2).maxLineCount)
        assertEquals(4, providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 3).maxLineCount)
        assertEquals(5, providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 4).maxLineCount)
        assertEquals(3, providerWidgetLayoutSpec(cellWidth = 3, cellHeight = 2).maxLineCount)
        assertEquals(4, providerWidgetLayoutSpec(cellWidth = 3, cellHeight = 3).maxLineCount)
        assertEquals(5, providerWidgetLayoutSpec(cellWidth = 3, cellHeight = 4).maxLineCount)
    }

    @Test
    fun providerTallWidgetsKeepResetCaptionWhileShowingFourRows() {
        val twoByThree = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 3)
        val threeByThree = providerWidgetLayoutSpec(cellWidth = 3, cellHeight = 3)

        assertEquals(4, twoByThree.maxLineCount)
        assertEquals(4, threeByThree.maxLineCount)
        assertTrue(twoByThree.showResetCaption)
        assertTrue(threeByThree.showResetCaption)
        assertTrue(providerWidgetEstimatedContentHeightDp(twoByThree) <= 240)
        assertTrue(providerWidgetEstimatedContentHeightDp(threeByThree) <= 240)
    }

    @Test
    fun providerFourCellWidgetsKeepResetCaptionWhileShowingFiveRows() {
        val twoByFour = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 4)
        val threeByFour = providerWidgetLayoutSpec(cellWidth = 3, cellHeight = 4)

        assertEquals(5, twoByFour.maxLineCount)
        assertEquals(5, threeByFour.maxLineCount)
        assertEquals(11, twoByFour.gaugeHeightDp)
        assertEquals(11, threeByFour.gaugeHeightDp)
        assertEquals(10, twoByFour.verticalPaddingDp)
        assertEquals(10, threeByFour.verticalPaddingDp)
        assertTrue(twoByFour.showResetCaption)
        assertTrue(threeByFour.showResetCaption)
        assertTrue(providerWidgetEstimatedContentHeightDp(twoByFour) <= 320)
        assertTrue(providerWidgetEstimatedContentHeightDp(threeByFour) <= 320)
    }

    @Test
    fun unifiedDashboardSafeZoneIsExcludedBeforeRowsAreSized() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3, widgetHeightDp = 240)
        val usableHeight = unifiedWidgetUsableHeightDp(spec)

        assertEquals(10, spec.verticalPaddingDp)
        assertEquals(220, usableHeight)
        assertTrue(unifiedWidgetEstimatedContentHeightDp(spec) <= 240)
        assertTrue(unifiedWidgetUnusedVerticalHeightDp(spec) <= 4)
    }

    @Test
    fun unifiedDashboardSafeZoneUsesActualResponsiveHeight() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 3, cellHeight = 3, widgetHeightDp = 200)

        assertEquals(10, spec.verticalPaddingDp)
        assertEquals(180, unifiedWidgetUsableHeightDp(spec))
        assertTrue(unifiedWidgetEstimatedContentHeightDp(spec) <= 200)
        assertTrue(unifiedWidgetUnusedVerticalHeightDp(spec) <= 4)
    }

    @Test
    fun unifiedDashboardCentersVisibleProviderContentInsideRows() {
        val mobileSpec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3, widgetHeightDp = 240)
        val tabletSpec = unifiedWidgetLayoutSpec(cellWidth = 3, cellHeight = 2, widgetHeightDp = 160)

        assertTrue(mobileSpec.rowContentVerticalInsetDp > 0)
        assertTrue(tabletSpec.rowContentVerticalInsetDp > 0)
        assertTrue(unifiedWidgetRowVisibleContentHeightDp(mobileSpec) <= mobileSpec.rowHeightDp)
        assertTrue(unifiedWidgetRowVisibleContentHeightDp(tabletSpec) <= tabletSpec.rowHeightDp)
    }

    @Test
    fun unifiedDashboardCentersProviderBlockWhenHostIsTallerThanResponsiveBucket() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3, widgetHeightDp = 240)

        assertTrue(unifiedWidgetCentersProviderBlock(spec))
    }

    @Test
    fun unifiedDashboardDoesNotShiftFirstRowValueForRefreshOverlay() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 2, cellHeight = 3, widgetHeightDp = 240)

        assertFalse(unifiedWidgetReservesFirstRowTopEndSpace(spec))
    }

    @Test
    fun providerTabletWideTwoByOneKeepsCompactHeightButShowsResetCaption() {
        val effectiveHeight = providerWidgetEffectiveCellHeight(
            cellWidth = 3,
            localHeightDp = 80,
            measuredCellHeight = 2
        )
        val spec = providerWidgetLayoutSpec(cellWidth = 3, cellHeight = effectiveHeight)

        assertEquals(1, effectiveHeight)
        assertEquals(1, spec.maxLineCount)
        assertTrue(spec.showResetCaption)
        assertTrue(spec.lineRowHeightDp >= 30)
        assertEquals(10, spec.verticalPaddingDp)
        assertTrue(spec.displayNameTextSizeSp <= 14)
        assertTrue(spec.lineTextSizeSp <= 11)
        assertTrue(spec.detailTextSizeSp <= 8)
    }

    @Test
    fun providerMobileTwoByOneKeepsResetCaptionHidden() {
        val effectiveHeight = providerWidgetEffectiveCellHeight(
            cellWidth = 2,
            localHeightDp = 80,
            measuredCellHeight = 2
        )
        val spec = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1)

        assertEquals(1, effectiveHeight)
        assertFalse(spec.showResetCaption)
    }

    @Test
    fun providerWidgetCentersContentForCompactAndExpandedSizes() {
        assertTrue(providerWidgetUsesCenteredContent(localHeightDp = 80))
        assertTrue(providerWidgetUsesCenteredContent(localHeightDp = 160))
    }

    @Test
    fun providerWidgetLineTextUsesResponsiveColumns() {
        assertTrue(providerWidgetLineUsesResponsiveColumns(providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 2)))
        assertTrue(unifiedWidgetLineUsesResponsiveColumns(unifiedWidgetLayoutSpec(cellWidth = 3, cellHeight = 4)))
    }

    @Test
    fun providerWidgetShrinksOnlyLongLabelInsideTwoByOneColumns() {
        val spec = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1)
        val textSpec = providerWidgetLineTextStyleSpec(
            spec = spec,
            label = "GPT-5.3-Spark 5시간",
            remainingText = "100% 남음"
        )

        assertTrue(textSpec.labelTextSizeSp < spec.lineTextSizeSp)
        assertEquals(spec.lineTextSizeSp.toFloat(), textSpec.remainingTextSizeSp)
        assertTrue(textSpec.labelTextSizeSp >= 10.0f)
    }

    @Test
    fun providerWidgetUsesOneDpGapBetweenLabelAndRemainingText() {
        val spec = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1)

        assertEquals(1, providerWidgetLabelValueGapDp(spec))
    }

    @Test
    fun providerWidgetKeepsShortLineTextAtDefaultSize() {
        val spec = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1)
        val textSpec = providerWidgetLineTextStyleSpec(
            spec = spec,
            label = "5시간 세션",
            remainingText = "94% 남음"
        )

        assertEquals(spec.lineTextSizeSp.toFloat(), textSpec.labelTextSizeSp)
        assertEquals(spec.lineTextSizeSp.toFloat(), textSpec.remainingTextSizeSp)
    }

    @Test
    fun widgetResetCaptionIsRightAligned() {
        assertEquals(TextAlign.End, widgetResetCaptionTextAlign())
    }
}
