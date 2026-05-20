package com.aiusage.mobile.widget

import com.aiusage.mobile.local.AppTheme
import com.aiusage.mobile.ui.MacOSAIUsageThemeColors
import com.aiusage.mobile.ui.WindowsAIUsageThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetThemeColorsTest {
    @Test
    fun macosWidgetColorsFollowAppThemeTokens() {
        val colors = widgetThemeColors(AppTheme.MACOS)

        assertEquals(MacOSAIUsageThemeColors.content, colors.background)
        assertEquals(MacOSAIUsageThemeColors.textSecondary, colors.caption)
        assertEquals(MacOSAIUsageThemeColors.progressTrack, colors.gaugeTrack)
        assertEquals(MacOSAIUsageThemeColors.progress, colors.gaugeHealthy)
    }

    @Test
    fun windowsWidgetColorsFollowAppThemeTokens() {
        val colors = widgetThemeColors(AppTheme.WINDOWS)

        assertEquals(WindowsAIUsageThemeColors.content, colors.background)
        assertEquals(WindowsAIUsageThemeColors.textSecondary, colors.caption)
        assertEquals(WindowsAIUsageThemeColors.progressTrack, colors.gaugeTrack)
        assertEquals(WindowsAIUsageThemeColors.progress, colors.gaugeHealthy)
    }

    @Test
    fun widgetThemesUseDifferentVisualPalettes() {
        val macos = widgetThemeColors(AppTheme.MACOS)
        val windows = widgetThemeColors(AppTheme.WINDOWS)

        assertNotEquals(macos.background, windows.background)
        assertNotEquals(macos.gaugeHealthy, windows.gaugeHealthy)
    }
}
