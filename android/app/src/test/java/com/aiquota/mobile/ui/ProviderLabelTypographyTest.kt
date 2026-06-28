package com.aiquota.mobile.ui

import androidx.compose.material3.Typography
import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLabelTypographyTest {
    @Test
    fun compactProviderLineBreakLabelsUseThirtyPercentTighterLineHeight() {
        assertEquals(0.7f, ProviderLineBreakLineHeightMultiplier)
    }

    @Test
    fun compactProviderLineBreakStyleAppliesOnlyToTwoLineProviderLabels() {
        assertTrue(usesCompactProviderLineBreak(ProviderId.ANTIGRAVITY))
        assertFalse(usesCompactProviderLineBreak(ProviderId.CODEX))

        val labelMedium = Typography().labelMedium
        val compactStyle = compactProviderLineBreakStyle(ProviderId.ANTIGRAVITY, labelMedium)

        assertEquals(
            labelMedium.lineHeight * ProviderLineBreakLineHeightMultiplier,
            compactStyle.lineHeight
        )
    }
}
