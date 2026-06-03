package com.aiquota.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.aiquota.mobile.local.AppTheme

object AIQuotaColors {
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

data class AIQuotaThemeColors(
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

val WindowsAIQuotaThemeColors = AIQuotaThemeColors(
    theme = AppTheme.WINDOWS,
    appBackground = AIQuotaColors.SurfaceMuted,
    panel = AIQuotaColors.SurfaceMuted,
    cardChrome = AIQuotaColors.WindowChrome,
    titleBar = AIQuotaColors.SurfaceStrong,
    titleText = AIQuotaColors.SurfaceMuted,
    content = AIQuotaColors.SurfaceMuted,
    contentAlt = AIQuotaColors.SurfaceMuted,
    border = AIQuotaColors.BorderDefault,
    borderSoft = AIQuotaColors.SurfaceMuted,
    textPrimary = AIQuotaColors.TextPrimary,
    textSecondary = AIQuotaColors.TextSecondary,
    textMuted = AIQuotaColors.TextTertiary,
    primary = AIQuotaColors.SurfaceStrong,
    primaryActive = Color(0xFF2F1FAA),
    selectedNav = AIQuotaColors.SurfaceRaised,
    unselectedNav = AIQuotaColors.WindowChrome,
    progress = AIQuotaColors.SurfaceStrong,
    progressTrack = AIQuotaColors.WindowChrome,
    shadow = AIQuotaColors.BorderDefault.copy(alpha = 0.24f),
    trafficRed = AIQuotaColors.WindowChrome,
    trafficYellow = AIQuotaColors.WindowChrome,
    trafficGreen = AIQuotaColors.WindowChrome
)

val MacOSAIQuotaThemeColors = AIQuotaThemeColors(
    theme = AppTheme.MACOS,
    appBackground = AIQuotaColors.MacOSCanvas,
    panel = AIQuotaColors.MacOSCanvas,
    cardChrome = AIQuotaColors.MacOSSurfaceCard,
    titleBar = AIQuotaColors.MacOSSurfaceSoft,
    titleText = AIQuotaColors.MacOSInk,
    content = AIQuotaColors.MacOSCanvas,
    contentAlt = AIQuotaColors.MacOSSurfaceSoft,
    border = AIQuotaColors.MacOSHairline,
    borderSoft = AIQuotaColors.MacOSHairline,
    textPrimary = AIQuotaColors.MacOSInk,
    textSecondary = AIQuotaColors.MacOSBody,
    textMuted = AIQuotaColors.MacOSMuted,
    primary = AIQuotaColors.MacOSPrimary,
    primaryActive = AIQuotaColors.MacOSPrimaryActive,
    selectedNav = AIQuotaColors.MacOSPrimary,
    unselectedNav = AIQuotaColors.MacOSSurfaceCard,
    progress = AIQuotaColors.MacOSPrimary,
    progressTrack = AIQuotaColors.MacOSSurfaceCreamStrong,
    shadow = AIQuotaColors.MacOSInk.copy(alpha = 0.08f),
    trafficRed = Color(0xFFFF5F57),
    trafficYellow = Color(0xFFFFBD2E),
    trafficGreen = Color(0xFF28C840)
)

fun aiQuotaThemeColors(theme: AppTheme): AIQuotaThemeColors {
    return when (theme) {
        AppTheme.MACOS -> MacOSAIQuotaThemeColors
        AppTheme.WINDOWS -> WindowsAIQuotaThemeColors
    }
}

val LocalAIQuotaThemeColors = staticCompositionLocalOf { MacOSAIQuotaThemeColors }

object AIQuotaTheme {
    val colors: AIQuotaThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAIQuotaThemeColors.current
}
