package com.aiquota.mobile.ui

import androidx.compose.ui.text.TextStyle
import com.aiquota.mobile.local.ProviderId

internal const val ProviderLineBreakLineHeightMultiplier = 0.7f

internal fun usesCompactProviderLineBreak(providerId: ProviderId): Boolean {
    return providerId == ProviderId.ANTIGRAVITY
}

internal fun compactProviderLineBreakStyle(providerId: ProviderId, style: TextStyle): TextStyle {
    return if (usesCompactProviderLineBreak(providerId)) {
        style.copy(lineHeight = style.lineHeight * ProviderLineBreakLineHeightMultiplier)
    } else {
        style
    }
}
