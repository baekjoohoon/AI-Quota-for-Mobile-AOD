package com.aiusage.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGaugeLayoutTest {
    @Test
    fun unifiedTwoRowWidgetLeavesRoomForResetTextAndSpacing() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 3, cellHeight = 2)

        assertEquals(2, spec.maxProviderCount)
        assertTrue(spec.rowHeightDp >= 48)
    }

    @Test
    fun unifiedThreeRowWidgetAvoidsCrowdingProviders() {
        val spec = unifiedWidgetLayoutSpec(cellWidth = 3, cellHeight = 3)

        assertEquals(4, spec.maxProviderCount)
        assertTrue(spec.rowHeightDp >= 42)
    }

    @Test
    fun providerWidgetKeepsEnoughLabelWidthForTwoByOneText() {
        val spec = providerWidgetLayoutSpec(cellWidth = 2, cellHeight = 1)

        assertTrue(providerWidgetLabelWidthDp(spec) >= 76)
        assertTrue(providerWidgetValueWidthDp(spec) >= 48)
    }
}
