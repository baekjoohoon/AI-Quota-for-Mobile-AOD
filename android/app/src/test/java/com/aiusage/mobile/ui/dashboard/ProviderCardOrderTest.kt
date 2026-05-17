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

    @Test
    fun moveToVisibleTargetIndexKeepsHiddenSlotsAndReordersVisibleProviders() {
        val moved = ProviderCardOrder.moveToVisibleTargetIndex(
            order = ProviderId.defaultOrder(),
            hidden = setOf(ProviderId.CODEX),
            providerId = ProviderId.CURSOR,
            targetVisibleIndex = 0
        )

        assertEquals(
            listOf(
                ProviderId.CURSOR,
                ProviderId.CODEX,
                ProviderId.CLAUDE,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            moved
        )
        assertEquals(
            listOf(ProviderId.CURSOR, ProviderId.CLAUDE, ProviderId.GEMINI, ProviderId.COPILOT),
            ProviderPreferencesCodec.visibleProviders(moved, setOf(ProviderId.CODEX))
        )
    }

    @Test
    fun dragTargetIndexUsesMeasuredCardCenters() {
        val cardCenters = listOf(
            DashboardCardCenter(x = 0f, y = 40f),
            DashboardCardCenter(x = 0f, y = 160f),
            DashboardCardCenter(x = 0f, y = 315f),
            DashboardCardCenter(x = 0f, y = 530f)
        )

        assertEquals(
            3,
            dragTargetIndexFromCenter(
                cardCenters = cardCenters,
                currentVisibleIndex = 0,
                draggedCenter = DashboardCardCenter(x = 0f, y = 500f)
            )
        )
        assertEquals(
            1,
            dragTargetIndexFromCenter(
                cardCenters = cardCenters,
                currentVisibleIndex = 3,
                draggedCenter = DashboardCardCenter(x = 0f, y = 170f)
            )
        )
    }

    @Test
    fun dragTargetIndexUsesNearestGridCardCenterOnTablet() {
        val cardCenters = listOf(
            DashboardCardCenter(x = 240f, y = 160f),
            DashboardCardCenter(x = 760f, y = 160f),
            DashboardCardCenter(x = 240f, y = 420f),
            DashboardCardCenter(x = 760f, y = 420f)
        )

        assertEquals(
            3,
            dragTargetIndexFromCenter(
                cardCenters = cardCenters,
                currentVisibleIndex = 0,
                draggedCenter = DashboardCardCenter(x = 760f, y = 410f)
            )
        )
        assertEquals(
            1,
            dragTargetIndexFromCenter(
                cardCenters = cardCenters,
                currentVisibleIndex = 2,
                draggedCenter = DashboardCardCenter(x = 760f, y = 165f)
            )
        )
    }

    @Test
    fun dragTargetIndexFallsBackForMissingMeasurements() {
        assertEquals(
            2,
            dragTargetIndexFromCenter(
                cardCenters = listOf(
                    DashboardCardCenter(x = Float.NaN, y = Float.NaN),
                    DashboardCardCenter(x = Float.NaN, y = Float.NaN),
                    DashboardCardCenter(x = Float.NaN, y = Float.NaN)
                ),
                currentVisibleIndex = 2,
                draggedCenter = DashboardCardCenter(x = 0f, y = 180f)
            )
        )
    }
}
