package com.aiusage.mobile.widget

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderWidgetSelectionTest {
    @Test
    fun usesSavedSelectionWhenItExistsInProviderOrder() {
        val selection = providerWidgetSelection(
            savedSelection = ProviderId.CURSOR,
            providers = listOf(ProviderId.CLAUDE, ProviderId.CURSOR)
        )

        assertEquals(ProviderId.CURSOR, selection)
    }

    @Test
    fun keepsHiddenProviderSelectionWhenItExistsInProviderOrder() {
        val selection = providerWidgetSelection(
            savedSelection = ProviderId.CURSOR,
            providers = listOf(ProviderId.CLAUDE, ProviderId.CODEX, ProviderId.CURSOR)
        )

        assertEquals(ProviderId.CURSOR, selection)
    }

    @Test
    fun returnsNullWhenNoProvidersExist() {
        val selection = providerWidgetSelection(
            savedSelection = ProviderId.CURSOR,
            providers = emptyList()
        )

        assertNull(selection)
    }
}
