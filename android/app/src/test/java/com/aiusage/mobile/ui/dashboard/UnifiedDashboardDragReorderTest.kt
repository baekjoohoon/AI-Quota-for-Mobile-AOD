package com.aiusage.mobile.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class UnifiedDashboardDragReorderTest {
    @Test
    fun dragTargetIndexUsesInsertionSlotAfterTargetCard() {
        val centers = listOf(
            DashboardCardCenter(x = 100f, y = 100f),
            DashboardCardCenter(x = 100f, y = 300f),
            DashboardCardCenter(x = 100f, y = 500f),
            DashboardCardCenter(x = 100f, y = 700f)
        )

        val targetIndex = dragTargetIndexFromCenter(
            cardCenters = centers,
            currentVisibleIndex = 0,
            draggedCenter = DashboardCardCenter(x = 100f, y = 610f)
        )

        assertEquals(2, targetIndex)
    }

    @Test
    fun dragInsertionSlotReportsExactDropGap() {
        val centers = listOf(
            DashboardCardCenter(x = 100f, y = 100f),
            DashboardCardCenter(x = 100f, y = 300f),
            DashboardCardCenter(x = 100f, y = 500f),
            DashboardCardCenter(x = 100f, y = 700f)
        )

        val slotIndex = dragInsertionSlotFromCenter(
            cardCenters = centers,
            currentVisibleIndex = 0,
            draggedCenter = DashboardCardCenter(x = 100f, y = 610f)
        )

        assertEquals(3, slotIndex)
    }

    @Test
    fun dragTargetIndexUsesInsertionSlotBeforeTargetCard() {
        val centers = listOf(
            DashboardCardCenter(x = 100f, y = 100f),
            DashboardCardCenter(x = 100f, y = 300f),
            DashboardCardCenter(x = 100f, y = 500f),
            DashboardCardCenter(x = 100f, y = 700f)
        )

        val targetIndex = dragTargetIndexFromCenter(
            cardCenters = centers,
            currentVisibleIndex = 3,
            draggedCenter = DashboardCardCenter(x = 100f, y = 200f)
        )

        assertEquals(1, targetIndex)
    }

    @Test
    fun dragTargetIndexSupportsGridInsertionSlots() {
        val centers = listOf(
            DashboardCardCenter(x = 100f, y = 100f),
            DashboardCardCenter(x = 300f, y = 100f),
            DashboardCardCenter(x = 100f, y = 300f),
            DashboardCardCenter(x = 300f, y = 300f)
        )

        val targetIndex = dragTargetIndexFromCenter(
            cardCenters = centers,
            currentVisibleIndex = 0,
            draggedCenter = DashboardCardCenter(x = 200f, y = 300f)
        )

        assertEquals(2, targetIndex)
    }
}
