package com.aiquota.mobile.widget

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.aiquota.mobile.local.AppTheme
import com.aiquota.mobile.ui.aiQuotaThemeColors
import kotlin.math.roundToInt

internal data class WidgetConfigureStyle(
    val theme: AppTheme,
    val backgroundColor: Int,
    val cardColor: Int,
    val accentColor: Int,
    val textColor: Int,
    val mutedTextColor: Int,
    val borderColor: Int,
    val primaryButtonTextColor: Int,
    val rowCornerRadiusDp: Int,
    val buttonCornerRadiusDp: Int
)

internal fun Context.widgetConfigureStyle(theme: AppTheme): WidgetConfigureStyle {
    val colors = aiQuotaThemeColors(theme)
    return WidgetConfigureStyle(
        theme = theme,
        backgroundColor = colors.appBackground.toArgb(),
        cardColor = colors.contentAlt.toArgb(),
        accentColor = colors.primary.toArgb(),
        textColor = colors.textPrimary.toArgb(),
        mutedTextColor = colors.textMuted.toArgb(),
        borderColor = colors.border.toArgb(),
        primaryButtonTextColor = colors.content.toArgb(),
        rowCornerRadiusDp = when (theme) {
            AppTheme.MACOS -> 14
            AppTheme.WINDOWS -> 6
        },
        buttonCornerRadiusDp = when (theme) {
            AppTheme.MACOS -> 18
            AppTheme.WINDOWS -> 4
        }
    )
}

internal fun ComponentActivity.applyWidgetConfigureWindow(style: WidgetConfigureStyle) {
    val systemBarScrim = style.backgroundColor
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(systemBarScrim, systemBarScrim),
        navigationBarStyle = SystemBarStyle.auto(systemBarScrim, systemBarScrim)
    )
}

internal fun Context.widgetConfigureRoundedBackground(
    fillColor: Int,
    strokeColor: Int,
    cornerRadiusDp: Int,
    strokeWidthDp: Int = 1
): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fillColor)
        cornerRadius = cornerRadiusDp.widgetConfigureDp(this@widgetConfigureRoundedBackground).toFloat()
        setStroke(strokeWidthDp.widgetConfigureDp(this@widgetConfigureRoundedBackground), strokeColor)
    }
}

internal fun TextView.applyWidgetConfigureText(
    style: WidgetConfigureStyle,
    textSizeSp: Float,
    bold: Boolean = false,
    muted: Boolean = false
) {
    textSize = textSizeSp
    setTextColor(if (muted) style.mutedTextColor else style.textColor)
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    includeFontPadding = false
}

internal fun View.applyWidgetConfigureRowBackground(style: WidgetConfigureStyle) {
    background = context.widgetConfigureRoundedBackground(
        fillColor = style.cardColor,
        strokeColor = style.borderColor,
        cornerRadiusDp = style.rowCornerRadiusDp
    )
}

internal fun Int.widgetConfigureDp(context: Context): Int {
    return (this * context.resources.displayMetrics.density).roundToInt()
}

internal fun Context.widgetConfigureStatusBarInsetPx(): Int {
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resourceId > 0) {
        resources.getDimensionPixelSize(resourceId)
    } else {
        24.widgetConfigureDp(this)
    }
}
