package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshMotionTest {
    @Test
    fun circularWidgetUsesUnifiedDotRefreshMotion() {
        val layout = File("src/main/res/layout/ai_quota_widget_circular.xml").readText()

        assertFalse(
            "2x2 circular widget must not use the platform ProgressBar refresh motion.",
            layout.contains("<ProgressBar")
        )
        assertTrue(
            "2x2 circular widget must expose the same refresh progress view id.",
            layout.contains("@+id/circular_refresh_progress")
        )
        assertTrue(
            "2x2 circular widget must use the shared dot refresh motion drawable.",
            layout.contains("@drawable/widget_refresh_spinner_dots")
        )
    }

    @Test
    fun glanceWidgetsUseUnifiedDotRefreshMotion() {
        val dashboardWidget = File("src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt").readText()
        val providerWidget = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt").readText()

        assertFalse(dashboardWidget.contains("CircularProgressIndicator"))
        assertFalse(providerWidget.contains("CircularProgressIndicator"))
        assertTrue(dashboardWidget.contains("R.drawable.widget_refresh_spinner_dots"))
        assertTrue(providerWidget.contains("R.drawable.widget_refresh_spinner_dots"))
    }
}
