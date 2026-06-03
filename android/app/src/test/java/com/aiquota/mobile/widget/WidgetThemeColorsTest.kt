package com.aiquota.mobile.widget

import com.aiquota.mobile.local.AppTheme
import com.aiquota.mobile.ui.MacOSAIQuotaThemeColors
import com.aiquota.mobile.ui.WindowsAIQuotaThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetThemeColorsTest {
    @Test
    fun macosWidgetColorsFollowAppThemeTokens() {
        val colors = widgetThemeColors(AppTheme.MACOS)

        assertEquals(MacOSAIQuotaThemeColors.content, colors.background)
        assertEquals(MacOSAIQuotaThemeColors.textSecondary, colors.caption)
        assertEquals(MacOSAIQuotaThemeColors.progressTrack, colors.gaugeTrack)
        assertEquals(MacOSAIQuotaThemeColors.progress, colors.gaugeHealthy)
    }

    @Test
    fun windowsWidgetColorsFollowAppThemeTokens() {
        val colors = widgetThemeColors(AppTheme.WINDOWS)

        assertEquals(WindowsAIQuotaThemeColors.content, colors.background)
        assertEquals(WindowsAIQuotaThemeColors.textSecondary, colors.caption)
        assertEquals(WindowsAIQuotaThemeColors.progressTrack, colors.gaugeTrack)
        assertEquals(WindowsAIQuotaThemeColors.progress, colors.gaugeHealthy)
    }

    @Test
    fun widgetThemesUseDifferentVisualPalettes() {
        val macos = widgetThemeColors(AppTheme.MACOS)
        val windows = widgetThemeColors(AppTheme.WINDOWS)

        assertNotEquals(macos.background, windows.background)
        assertNotEquals(macos.gaugeHealthy, windows.gaugeHealthy)
    }
}
