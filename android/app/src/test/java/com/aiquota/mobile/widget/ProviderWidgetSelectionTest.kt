package com.aiquota.mobile.widget

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderWidgetSelectionTest {
    @Test
    fun savedProviderSelectionSurvivesBeforeCustomOrderExists() {
        assertEquals(
            ProviderId.ANTIGRAVITY,
            providerWidgetSelection(
                savedSelection = ProviderId.ANTIGRAVITY,
                providers = emptyList()
            )
        )
    }

    @Test
    fun missingProviderWidgetSelectionDoesNotFallbackToClaude() {
        assertNull(
            providerWidgetSelection(
                savedSelection = null,
                providers = listOf(ProviderId.CLAUDE, ProviderId.CODEX)
            )
        )
    }
}
