package com.aiquota.mobile.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.aiquota.mobile.local.AppTheme
import com.aiquota.mobile.local.ThemePreferencesRepository
import com.aiquota.mobile.ui.aiQuotaThemeColors

data class WidgetThemeColors(
    val background: Color,
    val caption: Color,
    val gaugeTrack: Color,
    val gaugeHealthy: Color,
    val gaugeWarning: Color,
    val gaugeDanger: Color
) {
    fun gaugeColor(ratio: Float): Color {
        return when {
            ratio < 0.15f -> gaugeDanger
            ratio < 0.35f -> gaugeWarning
            else -> gaugeHealthy
        }
    }
}

fun widgetThemeColors(context: Context): WidgetThemeColors {
    return widgetThemeColors(ThemePreferencesRepository(context).currentTheme())
}

fun widgetThemeColors(theme: AppTheme): WidgetThemeColors {
    val appColors = aiQuotaThemeColors(theme)
    return WidgetThemeColors(
        background = appColors.content,
        caption = appColors.textSecondary,
        gaugeTrack = appColors.progressTrack,
        gaugeHealthy = appColors.progress,
        gaugeWarning = when (theme) {
            AppTheme.MACOS -> Color(0xFFE2A145)
            AppTheme.WINDOWS -> Color(0xFFF59E0B)
        },
        gaugeDanger = when (theme) {
            AppTheme.MACOS -> Color(0xFFD94D45)
            AppTheme.WINDOWS -> Color(0xFFEF4444)
        }
    )
}
