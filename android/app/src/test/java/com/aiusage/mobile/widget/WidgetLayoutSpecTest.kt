package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutSpecTest {
    @Test
    fun unifiedWidgetLayoutSpecsCoverTwoByTwoThroughThreeByFour() {
        for (cellWidth in 2..3) {
            for (cellHeight in 2..4) {
                val spec = unifiedWidgetLayoutSpec(cellWidth, cellHeight)

                assertEquals(cellWidth, spec.cellWidth)
                assertEquals(cellHeight, spec.cellHeight)
                assertEquals(spec, unifiedWidgetLayoutSpec(cellWidth, cellHeight))
                assertTrue(spec.titleTextSizeSp > 0)
                assertTrue(spec.providerTextSizeSp > 0)
                assertTrue(spec.detailTextSizeSp > 0)
                assertTrue(spec.gaugeWidthDp > 0)
                assertTrue(spec.gaugeHeightDp > 0)
                assertTrue(spec.gaugeRadiusDp > 0)
                assertTrue(spec.rowHeightDp > 0)
                assertTrue(spec.maxProviderCount > 0)
            }
        }
    }

    @Test
    fun providerWidgetLayoutSpecsCoverTwoByOneThroughThreeByThree() {
        for (cellWidth in 2..3) {
            for (cellHeight in 1..3) {
                val spec = providerWidgetLayoutSpec(cellWidth, cellHeight)

                assertEquals(cellWidth, spec.cellWidth)
                assertEquals(cellHeight, spec.cellHeight)
                assertEquals(spec, providerWidgetLayoutSpec(cellWidth, cellHeight))
                assertTrue(spec.displayNameTextSizeSp > 0)
                assertTrue(spec.statusTextSizeSp > 0)
                assertTrue(spec.lineTextSizeSp > 0)
                assertTrue(spec.detailTextSizeSp > 0)
                assertTrue(spec.gaugeWidthDp > 0)
                assertTrue(spec.gaugeHeightDp > 0)
                assertTrue(spec.gaugeRadiusDp > 0)
                assertTrue(spec.lineRowHeightDp > 0)
                assertTrue(spec.maxLineCount > 0)
            }
        }
    }
}
