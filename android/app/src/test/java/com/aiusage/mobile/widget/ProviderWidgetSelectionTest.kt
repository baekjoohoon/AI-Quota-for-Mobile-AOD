package com.aiusage.mobile.widget

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderWidgetSelectionTest {
    @Test
    fun usesSavedSelectionWhenItIsVisible() {
        val selection = visibleProviderWidgetSelection(
            savedSelection = ProviderId.CURSOR,
            visibleProviders = listOf(ProviderId.CLAUDE, ProviderId.CURSOR)
        )

        assertEquals(ProviderId.CURSOR, selection)
    }

    @Test
    fun fallsBackToFirstVisibleProviderWhenSavedSelectionIsHidden() {
        val selection = visibleProviderWidgetSelection(
            savedSelection = ProviderId.CURSOR,
            visibleProviders = listOf(ProviderId.CLAUDE, ProviderId.CODEX)
        )

        assertEquals(ProviderId.CLAUDE, selection)
    }

    @Test
    fun returnsNullWhenAllProvidersAreHidden() {
        val selection = visibleProviderWidgetSelection(
            savedSelection = ProviderId.CURSOR,
            visibleProviders = emptyList()
        )

        assertNull(selection)
    }
}
