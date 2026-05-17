package com.aiusage.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aiusage.mobile.local.AppTheme

object AIUsageColors {
    val BorderDefault = Color(0xFF1C293C)
    val TextPrimary = Color(0xFF1C293C)
    val TextSecondary = Color(0xCC1C293C)
    val TextTertiary = Color(0x991C293C)
    val TextInverse = Color(0xE61C293C)
    val SurfaceBase = Color(0xFF000000)
    val SurfaceMuted = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFFDC800)
    val SurfaceStrong = Color(0xFF432DD7)
    val WindowChrome = Color(0xFFE7E4DC)

    val MacOSCanvas = Color(0xFFFAF9F5)
    val MacOSSurfaceSoft = Color(0xFFF5F0E8)
    val MacOSSurfaceCard = Color(0xFFEFE9DE)
    val MacOSSurfaceCreamStrong = Color(0xFFE8E0D2)
    val MacOSPrimary = Color(0xFFCC785C)
    val MacOSPrimaryActive = Color(0xFFA9583E)
    val MacOSSurfaceDark = Color(0xFF181715)
    val MacOSSurfaceDarkElevated = Color(0xFF252320)
    val MacOSSurfaceDarkSoft = Color(0xFF1F1E1B)
    val MacOSHairline = Color(0xFFE6DFD8)
    val MacOSInk = Color(0xFF141413)
    val MacOSBody = Color(0xFF3D3D3A)
    val MacOSMuted = Color(0xFF6C6A64)
    val MacOSMutedSoft = Color(0xFF8E8B82)
    val MacOSOnDark = Color(0xFFFAF9F5)
    val MacOSOnDarkSoft = Color(0xFFA09D96)
    val MacOSSuccess = Color(0xFF5DB872)
}

data class AIUsageThemeColors(
    val theme: AppTheme,
    val appBackground: Color,
    val panel: Color,
    val cardChrome: Color,
    val titleBar: Color,
    val titleText: Color,
    val content: Color,
    val contentAlt: Color,
    val border: Color,
    val borderSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryActive: Color,
    val selectedNav: Color,
    val unselectedNav: Color,
    val progress: Color,
    val progressTrack: Color,
    val shadow: Color,
    val trafficRed: Color,
    val trafficYellow: Color,
    val trafficGreen: Color
)

val WindowsAIUsageThemeColors = AIUsageThemeColors(
    theme = AppTheme.WINDOWS,
    appBackground = AIUsageColors.SurfaceMuted,
    panel = AIUsageColors.SurfaceMuted,
    cardChrome = AIUsageColors.WindowChrome,
    titleBar = AIUsageColors.SurfaceStrong,
    titleText = AIUsageColors.SurfaceMuted,
    content = AIUsageColors.SurfaceMuted,
    contentAlt = AIUsageColors.SurfaceMuted,
    border = AIUsageColors.BorderDefault,
    borderSoft = AIUsageColors.SurfaceMuted,
    textPrimary = AIUsageColors.TextPrimary,
    textSecondary = AIUsageColors.TextSecondary,
    textMuted = AIUsageColors.TextTertiary,
    primary = AIUsageColors.SurfaceStrong,
    primaryActive = Color(0xFF2F1FAA),
    selectedNav = AIUsageColors.SurfaceRaised,
    unselectedNav = AIUsageColors.WindowChrome,
    progress = AIUsageColors.SurfaceStrong,
    progressTrack = AIUsageColors.WindowChrome,
    shadow = AIUsageColors.BorderDefault.copy(alpha = 0.24f),
    trafficRed = AIUsageColors.WindowChrome,
    trafficYellow = AIUsageColors.WindowChrome,
    trafficGreen = AIUsageColors.WindowChrome
)

val MacOSAIUsageThemeColors = AIUsageThemeColors(
    theme = AppTheme.MACOS,
    appBackground = AIUsageColors.MacOSCanvas,
    panel = AIUsageColors.MacOSCanvas,
    cardChrome = AIUsageColors.MacOSSurfaceCard,
    titleBar = AIUsageColors.MacOSSurfaceSoft,
    titleText = AIUsageColors.MacOSInk,
    content = AIUsageColors.MacOSCanvas,
    contentAlt = AIUsageColors.MacOSSurfaceSoft,
    border = AIUsageColors.MacOSHairline,
    borderSoft = AIUsageColors.MacOSHairline,
    textPrimary = AIUsageColors.MacOSInk,
    textSecondary = AIUsageColors.MacOSBody,
    textMuted = AIUsageColors.MacOSMuted,
    primary = AIUsageColors.MacOSPrimary,
    primaryActive = AIUsageColors.MacOSPrimaryActive,
    selectedNav = AIUsageColors.MacOSPrimary,
    unselectedNav = AIUsageColors.MacOSSurfaceCard,
    progress = AIUsageColors.MacOSPrimary,
    progressTrack = AIUsageColors.MacOSSurfaceCreamStrong,
    shadow = AIUsageColors.MacOSInk.copy(alpha = 0.08f),
    trafficRed = Color(0xFFFF5F57),
    trafficYellow = Color(0xFFFFBD2E),
    trafficGreen = Color(0xFF28C840)
)

fun aiUsageThemeColors(theme: AppTheme): AIUsageThemeColors {
    return when (theme) {
        AppTheme.MACOS -> MacOSAIUsageThemeColors
        AppTheme.WINDOWS -> WindowsAIUsageThemeColors
    }
}

val LocalAIUsageThemeColors = staticCompositionLocalOf { MacOSAIUsageThemeColors }

object AIUsageTheme {
    val colors: AIUsageThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAIUsageThemeColors.current
}
