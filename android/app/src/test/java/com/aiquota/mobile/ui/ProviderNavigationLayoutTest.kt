package com.aiquota.mobile.ui

import com.aiquota.mobile.local.ProviderId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderNavigationLayoutTest {
    @Test
    fun providerNavigationCanScrollWhenProviderCountGrows() {
        val source = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()

        assertTrue(
            "Provider navigation must remain reachable after adding providers.",
            source.contains("horizontalScroll(rememberScrollState())")
        )
        assertTrue(
            "Antigravity should be shown as a centered two-line navigation label.",
            source.contains("ProviderId.ANTIGRAVITY -> \"Anti\\nGravity\"")
        )
        assertTrue(
            "Provider navigation text must allow the two-line Antigravity label.",
            source.substringAfter("private fun ProviderNavigationChip")
                .substringBefore("private fun providerNavigationLabel")
                .contains("maxLines = 2")
        )
    }

    @Test
    fun providerNavigationFollowsDashboardProviderOrder() {
        val order = listOf(
            ProviderId.CURSOR,
            ProviderId.ANTIGRAVITY,
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.GEMINI,
            ProviderId.COPILOT
        )

        assertEquals(
            listOf(
                ProviderId.CURSOR,
                ProviderId.ANTIGRAVITY,
                ProviderId.CLAUDE,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            navigationProviderOrder(order, emptySet())
        )
    }
}
