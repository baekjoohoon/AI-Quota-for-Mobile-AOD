package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircularWidgetSizeTest {
    @Test
    fun circularWidgetShowsSixProvidersWhenHeightReachesThreeCells() {
        assertEquals(4, circularWidgetMaxGaugeCountForSizeDp(widthDp = 110, heightDp = 110))
        assertEquals(6, circularWidgetMaxGaugeCountForSizeDp(widthDp = 180, heightDp = 180))
    }

    @Test
    fun circularWidgetHidesManualRefreshControlWhenExpandedToSixProviders() {
        assertFalse(circularWidgetShowsRefreshControlForSizeDp(widthDp = 110, heightDp = 110))
        assertFalse(circularWidgetShowsRefreshControlForSizeDp(widthDp = 180, heightDp = 180))
    }

    @Test
    fun circularWidgetUsesLauncherMinimumBoundsToAvoidOverRenderingThinSizes() {
        assertEquals(2, circularWidgetMaxGaugeCountForSizeRange(minWidthDp = 110, maxWidthDp = 180, minHeightDp = 40, maxHeightDp = 110))
        assertEquals(3, circularWidgetMaxGaugeCountForSizeRange(minWidthDp = 180, maxWidthDp = 240, minHeightDp = 40, maxHeightDp = 110))
    }

    @Test
    fun circularWidgetLayoutDefinesSixGaugeSlots() {
        val layout = File("src/main/res/layout/ai_quota_widget_circular.xml").readText()
        val source = File("src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt").readText()

        (0..8).forEach { index ->
            assertTrue(layout.contains("@+id/circular_gauge_$index"))
        }
        assertTrue(layout.contains("@+id/circular_gauge_row_2"))
        assertTrue(layout.contains("""android:scaleType="fitCenter""""))
        assertFalse(source.contains("\"setOrientation\""))
    }

    @Test
    fun circularWidgetBitmapPayloadStaysBelowLauncherFriendlyBudget() {
        assertTrue(circularWidgetEstimatedBitmapPayloadBytes(6) > 256 * 1024)
        assertTrue(circularWidgetEstimatedBitmapPayloadBytes(6) <= 512 * 1024)
    }

    @Test
    fun circularWidgetSupportsThinHorizontalAndVerticalSizes() {
        val twoByOne = circularWidgetLayoutSpecForSize(widthDp = 110, heightDp = 40, availableGaugeCount = 6)
        val threeByOne = circularWidgetLayoutSpecForSize(widthDp = 180, heightDp = 40, availableGaugeCount = 6)
        val oneByTwo = circularWidgetLayoutSpecForSize(widthDp = 40, heightDp = 110, availableGaugeCount = 6)
        val oneByThree = circularWidgetLayoutSpecForSize(widthDp = 40, heightDp = 180, availableGaugeCount = 6)

        assertEquals(2, twoByOne.maxGaugeCount)
        assertEquals(2, twoByOne.columnCount)
        assertEquals(1, twoByOne.rowCount)
        assertEquals(3, threeByOne.maxGaugeCount)
        assertEquals(3, threeByOne.columnCount)
        assertEquals(1, threeByOne.rowCount)
        assertEquals(2, oneByTwo.maxGaugeCount)
        assertEquals(1, oneByTwo.columnCount)
        assertEquals(2, oneByTwo.rowCount)
        assertEquals(3, oneByThree.maxGaugeCount)
        assertEquals(1, oneByThree.columnCount)
        assertEquals(3, oneByThree.rowCount)
    }

    @Test
    fun circularWidgetTreatsLauncherReportedNarrowPortraitBoundsAsOneColumn() {
        listOf(76, 90, 109).forEach { widthDp ->
            val narrowPortrait = circularWidgetLayoutSpecForSize(widthDp = widthDp, heightDp = 180, availableGaugeCount = 6)

            assertEquals(3, narrowPortrait.maxGaugeCount)
            assertEquals(1, narrowPortrait.columnCount)
            assertEquals(3, narrowPortrait.rowCount)
        }

        val twoByThree = circularWidgetLayoutSpecForSize(widthDp = 110, heightDp = 180, availableGaugeCount = 6)
        assertEquals(6, twoByThree.maxGaugeCount)
        assertEquals(2, twoByThree.columnCount)
        assertEquals(3, twoByThree.rowCount)
    }

    @Test
    fun circularWidgetResizeContractLetsLaunchersReportOneCellBoundsWhileKeepingTwoByTwoDefault() {
        val providerXml = File("src/main/res/xml/ai_quota_widget_circular.xml").readText()

        assertTrue(providerXml.contains("""android:targetCellWidth="2""""))
        assertTrue(providerXml.contains("""android:targetCellHeight="2""""))
        assertTrue(providerXml.contains("""android:minWidth="40dp""""))
        assertTrue(providerXml.contains("""android:minHeight="40dp""""))
        assertTrue(providerXml.contains("""android:minResizeWidth="40dp""""))
        assertTrue(providerXml.contains("""android:minResizeHeight="40dp""""))
    }

    @Test
    fun circularWidgetKeepsTwoCellPortraitAsTwoColumns() {
        listOf(110, 120, 144).forEach { widthDp ->
            val portraitTwoCell = circularWidgetLayoutSpecForSize(widthDp = widthDp, heightDp = 180, availableGaugeCount = 6)

            assertEquals(6, portraitTwoCell.maxGaugeCount)
            assertEquals(2, portraitTwoCell.columnCount)
            assertEquals(3, portraitTwoCell.rowCount)
        }
    }

    @Test
    fun circularWidgetRebalancesGridWhenConfiguredProvidersAreHidden() {
        val sixVisible = circularWidgetLayoutSpecForSize(widthDp = 180, heightDp = 110, availableGaugeCount = 6)
        val fourVisible = circularWidgetLayoutSpecForSize(widthDp = 180, heightDp = 110, availableGaugeCount = 4)

        assertEquals(6, sixVisible.maxGaugeCount)
        assertEquals(3, sixVisible.columnCount)
        assertEquals(2, sixVisible.rowCount)
        assertEquals(4, fourVisible.visibleGaugeCount)
        assertEquals(2, fourVisible.columnCount)
        assertEquals(2, fourVisible.rowCount)
    }

    @Test
    fun circularWidgetUsesPortraitGridWhenWidthDoesNotExceedHeight() {
        val square = circularWidgetLayoutSpecForSize(widthDp = 180, heightDp = 180, availableGaugeCount = 6)
        val portrait = circularWidgetLayoutSpecForSize(widthDp = 110, heightDp = 180, availableGaugeCount = 6)
        val landscape = circularWidgetLayoutSpecForSize(widthDp = 180, heightDp = 110, availableGaugeCount = 6)

        assertEquals(2, square.columnCount)
        assertEquals(3, square.rowCount)
        assertEquals(2, portrait.columnCount)
        assertEquals(3, portrait.rowCount)
        assertEquals(3, landscape.columnCount)
        assertEquals(2, landscape.rowCount)
    }

    @Test
    fun circularWidgetResizeContractAllowsShrinkingBelowDefaultTwoByTwo() {
        val providerXml = File("src/main/res/xml/ai_quota_widget_circular.xml").readText()

        assertTrue(providerXml.contains("""android:targetCellWidth="2""""))
        assertTrue(providerXml.contains("""android:targetCellHeight="2""""))
        assertTrue(providerXml.contains("""android:minResizeWidth="40dp""""))
        assertTrue(providerXml.contains("""android:minResizeHeight="40dp""""))
    }

    @Test
    fun circularWidgetUsesSizeMappedRemoteViewsForLauncherSpecificBounds() {
        val source = File("src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt").readText()

        assertTrue(source.contains("AppWidgetManager.OPTION_APPWIDGET_SIZES"))
        assertTrue(source.contains("RemoteViews(sizeMappedViews)"))
        assertTrue(source.contains("widthDp = size.width.roundToInt()"))
        assertTrue(source.contains("heightDp = size.height.roundToInt()"))
    }
}
