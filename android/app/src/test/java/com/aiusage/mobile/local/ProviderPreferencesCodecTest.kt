package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPreferencesCodecTest {
    @Test
    fun decodesBlankOrderAsDefaultOrder() {
        assertEquals(ProviderId.defaultOrder(), ProviderPreferencesCodec.decodeOrder(""))
    }

    @Test
    fun decodeOrderDropsUnknownIdsAndAppendsMissingDefaults() {
        val order = ProviderPreferencesCodec.decodeOrder("cursor,unknown,claude")

        assertEquals(
            listOf(
                ProviderId.CURSOR,
                ProviderId.CLAUDE,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            order
        )
    }

    @Test
    fun moveProviderReordersWithinBounds() {
        val moved = ProviderPreferencesCodec.moveProvider(
            order = ProviderId.defaultOrder(),
            providerId = ProviderId.CURSOR,
            targetIndex = 1
        )

        assertEquals(
            listOf(
                ProviderId.CLAUDE,
                ProviderId.CURSOR,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            moved
        )
    }

    @Test
    fun moveProviderCoercesOutOfBoundsTargetIndex() {
        val movedToStart = ProviderPreferencesCodec.moveProvider(
            order = ProviderId.defaultOrder(),
            providerId = ProviderId.CURSOR,
            targetIndex = -10
        )
        val movedToEnd = ProviderPreferencesCodec.moveProvider(
            order = ProviderId.defaultOrder(),
            providerId = ProviderId.CLAUDE,
            targetIndex = 50
        )

        assertEquals(ProviderId.CURSOR, movedToStart.first())
        assertEquals(ProviderId.CLAUDE, movedToEnd.last())
    }

    @Test
    fun hiddenIdsRoundTripThroughStorageEncoding() {
        val hidden = setOf(ProviderId.GEMINI, ProviderId.CURSOR)
        val encoded = ProviderPreferencesCodec.encodeHidden(hidden)

        assertEquals(hidden, ProviderPreferencesCodec.decodeHidden(encoded))
    }

    @Test
    fun setHiddenAddsAndRemovesProvider() {
        val hidden = ProviderPreferencesCodec.setHidden(
            hidden = setOf(ProviderId.CODEX),
            providerId = ProviderId.CURSOR,
            hiddenState = true
        )
        val visibleAgain = ProviderPreferencesCodec.setHidden(
            hidden = hidden,
            providerId = ProviderId.CODEX,
            hiddenState = false
        )

        assertEquals(setOf(ProviderId.CODEX, ProviderId.CURSOR), hidden)
        assertEquals(setOf(ProviderId.CURSOR), visibleAgain)
    }

    @Test
    fun visibleProvidersUseOrderAndHiddenState() {
        val visible = ProviderPreferencesCodec.visibleProviders(
            order = ProviderId.defaultOrder(),
            hidden = setOf(ProviderId.CODEX, ProviderId.CURSOR)
        )

        assertFalse(visible.contains(ProviderId.CODEX))
        assertFalse(visible.contains(ProviderId.CURSOR))
        assertTrue(visible.contains(ProviderId.CLAUDE))
        assertEquals(listOf(ProviderId.CLAUDE, ProviderId.GEMINI, ProviderId.COPILOT), visible)
    }
}
