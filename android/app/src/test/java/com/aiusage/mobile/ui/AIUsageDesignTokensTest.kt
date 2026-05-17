package com.aiusage.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AIUsageDesignTokensTest {
    @Test
    fun macosThemeUsesLightFinderSurfaces() {
        assertEquals(AIUsageColors.MacOSCanvas, MacOSAIUsageThemeColors.appBackground)
        assertEquals(AIUsageColors.MacOSSurfaceCard, MacOSAIUsageThemeColors.cardChrome)
        assertEquals(AIUsageColors.MacOSSurfaceSoft, MacOSAIUsageThemeColors.titleBar)
        assertEquals(AIUsageColors.MacOSInk, MacOSAIUsageThemeColors.titleText)
        assertEquals(AIUsageColors.MacOSCanvas, MacOSAIUsageThemeColors.content)
        assertEquals(AIUsageColors.MacOSSurfaceSoft, MacOSAIUsageThemeColors.contentAlt)
        assertEquals(AIUsageColors.MacOSHairline, MacOSAIUsageThemeColors.border)
    }
}
