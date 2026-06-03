package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDashboardWidgetSizeTest {
    @Test
    fun unifiedDashboardUsesExactSizeForLauncherSpecificHeights() {
        val source = File("src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt").readText()

        assertTrue(source.contains("SizeMode.Exact"))
        assertFalse(source.contains("SizeMode.Responsive"))
        assertFalse(source.contains("DpSize(width ="))
    }

    @Test
    fun unifiedDashboardProviderInfoCapsLauncherResizeAtThreeByThree() {
        val xml = File("src/main/res/xml/ai_quota_widget_large.xml").readText()

        assertTrue(xml.contains("""android:maxResizeWidth="240dp""""))
        assertTrue(xml.contains("""android:maxResizeHeight="240dp""""))
        assertEquals(3, unifiedWidgetLayoutSpec(cellWidth = 4, cellHeight = 4).cellWidth)
        assertEquals(3, unifiedWidgetLayoutSpec(cellWidth = 4, cellHeight = 4).cellHeight)
    }

    @Test
    fun unifiedDashboardDoesNotUseLauncherHostHeightAsCurrentHeight() {
        val source = File("src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt").readText()

        assertFalse(source.contains("unifiedWidgetHostOptionHeightDp"))
        assertFalse(source.contains("unifiedWidgetMeasuredHeightDp("))
        assertFalse(source.contains("AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT"))
        assertFalse(source.contains("AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT"))
    }
}
