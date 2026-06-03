package com.aiquota.mobile.ui.dashboard

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import com.aiquota.mobile.ui.appLayoutMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnifiedDashboardDragReorderTest {
    @Test
    fun antigravityDashboardIdentityUsesTwoLineCompactLabel() {
        assertEquals("Claude", dashboardProviderIdentityLabel(ProviderId.CLAUDE))
        assertEquals("Anti\nGravity", dashboardProviderIdentityLabel(ProviderId.ANTIGRAVITY))
    }

    @Test
    fun antigravityDashboardPreviewUsesOnlyGemini35FlashHighAndMedium() {
        val snapshot = ProviderUsageSnapshot(
            providerId = ProviderId.ANTIGRAVITY,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(
                ProviderUsageLine(label = "Gemini 3.5 Flash (Medium)", remainingPercent = 1.0f),
                ProviderUsageLine(label = "Claude Opus 4.6 (Thinking)", remainingPercent = 1.0f),
                ProviderUsageLine(label = "Gemini 3.5 Flash (Low)", remainingPercent = 0.9f),
                ProviderUsageLine(label = "Gemini 3.5 Flash (High)", remainingPercent = 1.0f),
                ProviderUsageLine(label = "Gemini 3.1 Pro (Low)", remainingPercent = 0.8f)
            )
        )

        assertEquals(
            listOf("Gemini 3.5 Flash (High)", "Gemini 3.5 Flash (Medium)"),
            dashboardUsagePreviewLines(snapshot).map { it.label }
        )
    }

    @Test
    fun dashboardGaugeHeightGrowsFromFourDpToEightDp() {
        val metrics = appLayoutMetrics(screenWidthDp = 393, screenHeightDp = 852)
        val base = dashboardGaugeHeightDp(metrics.dashboardCardMinHeightDp, metrics)
        val taller = dashboardGaugeHeightDp(metrics.dashboardCardMinHeightDp + 40, metrics)
        val veryTall = dashboardGaugeHeightDp(metrics.dashboardCardMinHeightDp + 400, metrics)

        assertEquals(4f, base, 0.001f)
        assertTrue(taller > base)
        assertEquals(8f, veryTall, 0.001f)
    }

    @Test
    fun dashboardUsageLineSpacingGrowsFromTwoDpToSixDp() {
        val metrics = appLayoutMetrics(screenWidthDp = 393, screenHeightDp = 852)
        val base = dashboardUsageLineSpacingDp(
            cardHeightDp = metrics.dashboardCardMinHeightDp,
            layoutMetrics = metrics,
            baseSpacingDp = 2f
        )
        val taller = dashboardUsageLineSpacingDp(
            cardHeightDp = metrics.dashboardCardMinHeightDp + 40,
            layoutMetrics = metrics,
            baseSpacingDp = 2f
        )
        val veryTall = dashboardUsageLineSpacingDp(
            cardHeightDp = metrics.dashboardCardMinHeightDp + 400,
            layoutMetrics = metrics,
            baseSpacingDp = 2f
        )

        assertEquals(2f, base, 0.001f)
        assertTrue(taller > base)
        assertEquals(6f, veryTall, 0.001f)
    }

    @Test
    fun dashboardCardCentersUsageContentInsideExpandedCards() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()

        assertTrue(dashboardSource.contains("val dashboardGaugeHeight = dashboardGaugeHeightDp(cardHeightDp, layoutMetrics).dp"))
        assertTrue(dashboardSource.contains("val usageColumnSpacing = dashboardUsageLineSpacingDp("))
        assertTrue(dashboardSource.contains("gaugeHeight = dashboardGaugeHeight"))
        assertTrue(
            "Dashboard content row should take remaining content height and center provider usage vertically.",
            dashboardSource.contains(".fillMaxWidth()\n                            .weight(1f)") &&
                dashboardSource.contains("verticalAlignment = Alignment.CenterVertically")
        )
    }

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

    @Test
    fun dragPreviewOrderMovesOnlyTheVisualOrderUntilDrop() {
        val order = listOf(
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.GEMINI,
            ProviderId.COPILOT
        )

        val preview = ProviderCardOrder.previewVisibleOrder(
            visibleOrder = order,
            draggedProvider = ProviderId.CLAUDE,
            targetVisibleIndex = 2
        )

        assertEquals(
            listOf(ProviderId.CODEX, ProviderId.GEMINI, ProviderId.CLAUDE, ProviderId.COPILOT),
            preview
        )
        assertEquals(
            "Drag preview must not mutate the source order before drop.",
            listOf(ProviderId.CLAUDE, ProviderId.CODEX, ProviderId.GEMINI, ProviderId.COPILOT),
            order
        )
    }

    @Test
    fun dragPreviewOrderNormalizesDuplicateVisualInput() {
        val preview = ProviderCardOrder.previewVisibleOrder(
            visibleOrder = listOf(
                ProviderId.ANTIGRAVITY,
                ProviderId.COPILOT,
                ProviderId.CURSOR,
                ProviderId.ANTIGRAVITY,
                ProviderId.COPILOT
            ),
            draggedProvider = ProviderId.CURSOR,
            targetVisibleIndex = 0
        )

        assertEquals(
            listOf(ProviderId.CURSOR, ProviderId.ANTIGRAVITY, ProviderId.COPILOT),
            preview
        )
    }

    @Test
    fun draggedDashboardCardDoesNotAnimateItsGridPlacement() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()
        val gridItemBody = dashboardSource.substringAfter("itemsIndexed(").substringBefore("onProviderSelected = onProviderSelected")

        assertTrue(
            "The dragged dashboard card must float as an overlay; it should not also run LazyGrid placement animation.",
            gridItemBody.contains("val cardModifier = if (providerId == draggedProvider)") &&
                gridItemBody.contains("modifier = cardModifier")
        )
    }

    @Test
    fun draggedDashboardCardOffsetIsDerivedFromStableDragOrigin() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()
        val positionedBody = dashboardSource.substringAfter(".onGloballyPositioned {").substringBefore(".zIndex")

        assertTrue(
            "The dragged card should not mutate drag offset from layout callbacks because repeated placement updates can over-correct and jump.",
            dashboardSource.contains("dragOriginBounds") &&
                dashboardSource.contains("onDragOverlayChanged(dragOriginBounds, dragOffsetX, dragOffsetY)") &&
                !positionedBody.contains("dragOffsetX +=") &&
                !positionedBody.contains("dragOffsetY +=")
        )
    }

    @Test
    fun draggedDashboardCardUsesGraphicsTranslationNotLayoutOffset() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()

        assertTrue(
            "Dragging should translate the floating card visually without changing its layout placement.",
            dashboardSource.contains("translationX = bounds.left - rootPosition.x + offsetX") &&
                dashboardSource.contains("translationY = bounds.top - rootPosition.y + offsetY") &&
                !dashboardSource.contains(".offset { IntOffset")
        )
    }

    @Test
    fun draggedDashboardCardIsRenderedAsOverlayWhileGridKeepsPlaceholder() {
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()

        assertTrue(
            "The visible dragged card should be rendered by a parent overlay while its grid item remains only as a placeholder.",
            dashboardSource.contains("DashboardDragOverlay(") &&
                dashboardSource.contains("isPlaceholder = providerId == draggedProvider") &&
                dashboardSource.contains("onDragOverlayChanged")
        )
    }

    @Test
    fun dashboardAutoScrollDeltaMovesOnlyNearViewportEdges() {
        assertEquals(
            0f,
            dashboardAutoScrollDelta(
                draggedCenterY = 500f,
                viewportTopY = 100f,
                viewportBottomY = 900f,
                edgeThresholdPx = 120f
            ),
            0.001f
        )
        assertTrue(
            dashboardAutoScrollDelta(
                draggedCenterY = 110f,
                viewportTopY = 100f,
                viewportBottomY = 900f,
                edgeThresholdPx = 120f
            ) < 0f
        )
        assertTrue(
            dashboardAutoScrollDelta(
                draggedCenterY = 890f,
                viewportTopY = 100f,
                viewportBottomY = 900f,
                edgeThresholdPx = 120f
            ) > 0f
        )
    }
}
