package com.aiusage.mobile.ui.dashboard

import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderCardOrderTest {
    @Test
    fun moveUpKeepsFirstProviderAtStart() {
        val order = ProviderId.defaultOrder()

        assertEquals(order, ProviderCardOrder.moveUp(order, ProviderId.CLAUDE))
    }

    @Test
    fun moveDownKeepsLastProviderAtEnd() {
        val order = ProviderId.defaultOrder()

        assertEquals(order, ProviderCardOrder.moveDown(order, ProviderId.CURSOR))
    }

    @Test
    fun moveDownUsesNormalizedProviderPreferencesOrder() {
        val sparseOrder = listOf(ProviderId.CURSOR, ProviderId.CLAUDE)

        val moved = ProviderCardOrder.moveDown(sparseOrder, ProviderId.CURSOR)

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
    fun targetIndexCoercesMoveWithinNormalizedBounds() {
        val order = ProviderId.defaultOrder()

        assertEquals(0, ProviderCardOrder.targetIndex(order, ProviderId.CLAUDE, -1))
        assertEquals(2, ProviderCardOrder.targetIndex(order, ProviderId.CODEX, 1))
        assertEquals(order.lastIndex, ProviderCardOrder.targetIndex(order, ProviderId.CURSOR, 1))
    }

    @Test
    fun moveToTargetIndexReturnsPersistenceReadyOrder() {
        val orderWithDuplicateAndMissingProviders = listOf(
            ProviderId.CURSOR,
            ProviderId.CURSOR,
            ProviderId.CODEX
        )

        val moved = ProviderCardOrder.moveToTargetIndex(
            order = orderWithDuplicateAndMissingProviders,
            providerId = ProviderId.GEMINI,
            targetIndex = 0
        )
        val persisted = ProviderPreferencesCodec.decodeOrder(ProviderPreferencesCodec.encodeOrder(moved))

        assertEquals(moved, persisted)
        assertEquals(
            listOf(
                ProviderId.GEMINI,
                ProviderId.CURSOR,
                ProviderId.CODEX,
                ProviderId.CLAUDE,
                ProviderId.COPILOT
            ),
            moved
        )
    }
}
