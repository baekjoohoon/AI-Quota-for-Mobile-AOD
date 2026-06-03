package com.aiquota.mobile.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aiquota.mobile.R
import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesCodec
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import com.aiquota.mobile.local.displayRemainingText
import com.aiquota.mobile.local.displayResetTextForLocale
import com.aiquota.mobile.local.displayUsageLabel
import com.aiquota.mobile.local.effectiveResetText
import com.aiquota.mobile.local.shouldShowDashboardConnectAction
import com.aiquota.mobile.ui.AIQuotaColors
import com.aiquota.mobile.ui.AIQuotaTheme
import com.aiquota.mobile.ui.AppLayoutMetrics
import com.aiquota.mobile.ui.dashboardProviderCardHeightDp
import com.aiquota.mobile.ui.rememberAppLayoutMetrics
import com.aiquota.mobile.ui.provider.ProviderIconImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ExplorerAccentColor = AIQuotaColors.SurfaceRaised
private const val DashboardGaugeBaseHeightDp = 4f
private const val DashboardGaugeMaxScale = 2f
private const val DashboardGaugeFullExtraHeightDp = 80f
private const val DashboardUsageLineSpacingMaxExtraDp = 4f
private val ClassicWindowChrome = AIQuotaColors.WindowChrome
private val ClassicTitleBlue = AIQuotaColors.SurfaceStrong
private val ClassicTitleText = AIQuotaColors.SurfaceMuted
private val ClassicBorderDark = AIQuotaColors.BorderDefault
private val ClassicBorderLight = AIQuotaColors.SurfaceMuted
private val ClassicContent = AIQuotaColors.SurfaceMuted

internal data class DashboardCardCenter(
    val x: Float,
    val y: Float
)

internal data class DashboardCardBounds(
    val left: Float,
    val top: Float,
    val width: Int,
    val height: Int
) {
    val center: DashboardCardCenter
        get() = DashboardCardCenter(
            x = left + (width / 2f),
            y = top + (height / 2f)
        )
}

private enum class DashboardDropPlacement {
    Top,
    Bottom
}

internal fun dashboardGaugeHeightDp(
    cardHeightDp: Int,
    layoutMetrics: AppLayoutMetrics
): Float {
    val heightRatio = dashboardExtraHeightRatio(cardHeightDp, layoutMetrics)
    return DashboardGaugeBaseHeightDp * (1f + ((DashboardGaugeMaxScale - 1f) * heightRatio))
}

internal fun dashboardUsageLineSpacingDp(
    cardHeightDp: Int,
    layoutMetrics: AppLayoutMetrics,
    baseSpacingDp: Float
): Float {
    val heightRatio = dashboardExtraHeightRatio(cardHeightDp, layoutMetrics)
    return baseSpacingDp + (DashboardUsageLineSpacingMaxExtraDp * heightRatio)
}

private fun dashboardExtraHeightRatio(
    cardHeightDp: Int,
    layoutMetrics: AppLayoutMetrics
): Float {
    val extraHeightDp = (cardHeightDp - layoutMetrics.dashboardCardMinHeightDp)
        .coerceAtLeast(0)
    return (extraHeightDp / DashboardGaugeFullExtraHeightDp).coerceIn(0f, 1f)
}

@Composable
fun UnifiedDashboardScreen(
    providerOrder: List<ProviderId>,
    hiddenProviders: Set<ProviderId>,
    snapshots: List<ProviderUsageSnapshot>,
    onProviderSelected: (ProviderId) -> Unit,
    onConnectProvider: (ProviderId) -> Unit,
    onReorderProvider: (ProviderId, Int) -> Unit,
    onAddWidget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutMetrics = rememberAppLayoutMetrics()
    val colors = AIQuotaTheme.colors
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val visibleProviders = ProviderPreferencesCodec.visibleProviders(providerOrder, hiddenProviders)
    val snapshotsByProvider = snapshots.associateBy { it.providerId }
    val cardCenters = remember { mutableStateMapOf<ProviderId, DashboardCardCenter>() }
    var draggedProvider by remember { mutableStateOf<ProviderId?>(null) }
    var previewDragTargetIndex by remember { mutableStateOf<Int?>(null) }
    var dropSlotIndex by remember { mutableStateOf<Int?>(null) }
    var dashboardRootPosition by remember { mutableStateOf(DashboardCardCenter(0f, 0f)) }
    var dragOverlayBounds by remember { mutableStateOf<DashboardCardBounds?>(null) }
    var dragOverlayOffsetX by remember { mutableStateOf(0f) }
    var dragOverlayOffsetY by remember { mutableStateOf(0f) }
    val previewProviders = ProviderCardOrder.previewVisibleOrder(
        visibleOrder = visibleProviders,
        draggedProvider = draggedProvider,
        targetVisibleIndex = previewDragTargetIndex
    )
    val visibleCardCenters = previewProviders.map {
        cardCenters[it] ?: DashboardCardCenter(Float.NaN, Float.NaN)
    }

    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            dashboardRootPosition = DashboardCardCenter(position.x, position.y)
        }
    ) {
        val density = LocalDensity.current
        val cardHeightDp = dashboardProviderCardHeightDp(
            viewportHeightDp = maxHeight.value.roundToInt(),
            layoutMetrics = layoutMetrics
        )
        val gridColumnCount = layoutMetrics.dashboardGridColumnCount.coerceAtLeast(1)
        val viewportTopY = dashboardRootPosition.y
        val viewportBottomY = dashboardRootPosition.y + with(density) { maxHeight.toPx() }
        val edgeThresholdPx = with(density) { 96.dp.toPx() }
        val onAutoScroll: (Float) -> Unit = { draggedCenterY ->
            val delta = dashboardAutoScrollDelta(
                draggedCenterY = draggedCenterY,
                viewportTopY = viewportTopY,
                viewportBottomY = viewportBottomY,
                edgeThresholdPx = edgeThresholdPx
            )
            if (delta != 0f) {
                coroutineScope.launch { scrollState.scrollBy(delta) }
            }
        }
        val onDragOverlayChanged: (DashboardCardBounds?, Float, Float) -> Unit = { bounds, offsetX, offsetY ->
            dragOverlayBounds = bounds
            dragOverlayOffsetX = offsetX
            dragOverlayOffsetY = offsetY
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = layoutMetrics.contentHorizontalPaddingDp.dp,
                    vertical = layoutMetrics.contentVerticalPaddingDp.dp
                ),
            verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacingDp.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layoutMetrics.dashboardTitleHeightDp.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedButton(
                    onClick = onAddWidget,
                    modifier = Modifier.widthIn(min = 104.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_add_widget),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (visibleProviders.isEmpty()) {
                EmptyDashboardState(layoutMetrics = layoutMetrics)
            } else {
                if (gridColumnCount == 1) {
                    previewProviders.forEachIndexed { visibleIndex, providerId ->
                        key(providerId) {
                            ProviderUsageCard(
                                providerId = providerId,
                                snapshot = snapshotsByProvider[providerId] ?: ProviderUsageSnapshot.disconnected(providerId),
                                visibleIndex = visibleIndex,
                                visibleCardCenters = visibleCardCenters,
                                dropPlacement = dashboardDropPlacement(
                                    visibleIndex = visibleIndex,
                                    visibleCount = previewProviders.size,
                                    draggedProvider = draggedProvider,
                                    dropSlotIndex = dropSlotIndex
                                ),
                                previewTargetIndex = previewDragTargetIndex,
                                cardHeightDp = cardHeightDp,
                                layoutMetrics = layoutMetrics,
                                modifier = Modifier.fillMaxWidth(),
                                isPlaceholder = providerId == draggedProvider,
                                onProviderSelected = onProviderSelected,
                                onConnectProvider = onConnectProvider,
                                onReorderProvider = onReorderProvider,
                                onCardCenterChanged = { id, center -> cardCenters[id] = center },
                                onDragStateChanged = { id, dragging ->
                                    draggedProvider = id.takeIf { dragging }
                                    if (dragging) {
                                        previewDragTargetIndex = visibleProviders.indexOf(id).takeIf { it >= 0 }
                                    } else {
                                        previewDragTargetIndex = null
                                        dropSlotIndex = null
                                    }
                                },
                                onDropSlotChanged = { slot -> dropSlotIndex = slot },
                                onPreviewTargetChanged = { target -> previewDragTargetIndex = target },
                                onDragOverlayChanged = onDragOverlayChanged,
                                onAutoScroll = onAutoScroll
                            )
                        }
                    }
                } else {
                    val rowCount = ((previewProviders.size + gridColumnCount - 1) / gridColumnCount).coerceAtLeast(1)
                    val gridHeightDp = (cardHeightDp * rowCount) +
                        (layoutMetrics.sectionSpacingDp * (rowCount - 1).coerceAtLeast(0))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumnCount),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeightDp.dp),
                        userScrollEnabled = false,
                        horizontalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacingDp.dp)
                    ) {
                        itemsIndexed(
                            items = previewProviders,
                            key = { _, providerId -> providerId.name }
                        ) { visibleIndex, providerId ->
                            val cardModifier = if (providerId == draggedProvider) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                            }
                            ProviderUsageCard(
                                providerId = providerId,
                                snapshot = snapshotsByProvider[providerId] ?: ProviderUsageSnapshot.disconnected(providerId),
                                visibleIndex = visibleIndex,
                                visibleCardCenters = visibleCardCenters,
                                dropPlacement = dashboardDropPlacement(
                                    visibleIndex = visibleIndex,
                                    visibleCount = previewProviders.size,
                                    draggedProvider = draggedProvider,
                                    dropSlotIndex = dropSlotIndex
                                ),
                                previewTargetIndex = previewDragTargetIndex,
                                cardHeightDp = cardHeightDp,
                                layoutMetrics = layoutMetrics,
                                modifier = cardModifier,
                                isPlaceholder = providerId == draggedProvider,
                                onProviderSelected = onProviderSelected,
                                onConnectProvider = onConnectProvider,
                                onReorderProvider = onReorderProvider,
                                onCardCenterChanged = { id, center -> cardCenters[id] = center },
                                onDragStateChanged = { id, dragging ->
                                    draggedProvider = id.takeIf { dragging }
                                    if (dragging) {
                                        previewDragTargetIndex = visibleProviders.indexOf(id).takeIf { it >= 0 }
                                    } else {
                                        previewDragTargetIndex = null
                                        dropSlotIndex = null
                                    }
                                },
                                onDropSlotChanged = { slot -> dropSlotIndex = slot },
                                onPreviewTargetChanged = { target -> previewDragTargetIndex = target },
                                onDragOverlayChanged = onDragOverlayChanged,
                                onAutoScroll = onAutoScroll
                            )
                        }
                    }
                }
            }
        }

        val overlayProvider = draggedProvider
        val overlayBounds = dragOverlayBounds
        if (overlayProvider != null && overlayBounds != null) {
            DashboardDragOverlay(
                providerId = overlayProvider,
                snapshot = snapshotsByProvider[overlayProvider] ?: ProviderUsageSnapshot.disconnected(overlayProvider),
                bounds = overlayBounds,
                offsetX = dragOverlayOffsetX,
                offsetY = dragOverlayOffsetY,
                rootPosition = dashboardRootPosition,
                cardHeightDp = cardHeightDp,
                layoutMetrics = layoutMetrics
            )
        }
    }
}

@Composable
private fun EmptyDashboardState(layoutMetrics: AppLayoutMetrics) {
    val colors = AIQuotaTheme.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.panel,
        border = BorderStroke(1.dp, colors.borderSoft)
    ) {
        Column(
            modifier = Modifier.padding(layoutMetrics.cardPaddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(layoutMetrics.cardSpacingDp.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_empty_title),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.dashboard_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
        }
    }
}

@Composable
private fun DashboardDragOverlay(
    providerId: ProviderId,
    snapshot: ProviderUsageSnapshot,
    bounds: DashboardCardBounds,
    offsetX: Float,
    offsetY: Float,
    rootPosition: DashboardCardCenter,
    cardHeightDp: Int,
    layoutMetrics: AppLayoutMetrics
) {
    val density = LocalDensity.current
    val widthDp = with(density) { bounds.width.toDp() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        ProviderUsageCard(
            providerId = providerId,
            snapshot = snapshot,
            visibleIndex = 0,
            visibleCardCenters = emptyList(),
            dropPlacement = null,
            previewTargetIndex = null,
            cardHeightDp = cardHeightDp,
            layoutMetrics = layoutMetrics,
            modifier = Modifier
                .width(widthDp)
                .graphicsLayer {
                    translationX = bounds.left - rootPosition.x + offsetX
                    translationY = bounds.top - rootPosition.y + offsetY
                },
            isPlaceholder = false,
            dragEnabled = false,
            forceDraggingVisual = true,
            onProviderSelected = {},
            onConnectProvider = {},
            onReorderProvider = { _, _ -> },
            onCardCenterChanged = { _, _ -> },
            onDragStateChanged = { _, _ -> },
            onDropSlotChanged = {},
            onPreviewTargetChanged = {},
            onDragOverlayChanged = { _, _, _ -> },
            onAutoScroll = {}
        )
    }
}

@Composable
private fun ProviderUsageCard(
    providerId: ProviderId,
    snapshot: ProviderUsageSnapshot,
    visibleIndex: Int,
    visibleCardCenters: List<DashboardCardCenter>,
    dropPlacement: DashboardDropPlacement?,
    previewTargetIndex: Int?,
    cardHeightDp: Int,
    layoutMetrics: AppLayoutMetrics,
    modifier: Modifier,
    isPlaceholder: Boolean = false,
    dragEnabled: Boolean = true,
    forceDraggingVisual: Boolean = false,
    onProviderSelected: (ProviderId) -> Unit,
    onConnectProvider: (ProviderId) -> Unit,
    onReorderProvider: (ProviderId, Int) -> Unit,
    onCardCenterChanged: (ProviderId, DashboardCardCenter) -> Unit,
    onDragStateChanged: (ProviderId, Boolean) -> Unit,
    onDropSlotChanged: (Int?) -> Unit,
    onPreviewTargetChanged: (Int?) -> Unit,
    onDragOverlayChanged: (DashboardCardBounds?, Float, Float) -> Unit,
    onAutoScroll: (Float) -> Unit
) {
    val colors = AIQuotaTheme.colors
    val isCompactDashboardCard = layoutMetrics.dashboardGridColumnCount == 1
    val titleBarHeight = when {
        colors.theme == com.aiquota.mobile.local.AppTheme.MACOS && isCompactDashboardCard -> 26.dp
        colors.theme == com.aiquota.mobile.local.AppTheme.MACOS -> 30.dp
        else -> 22.dp
    }
    val locationRowVerticalPadding = if (isCompactDashboardCard) 3.dp else 4.dp
    val cardContentPadding = if (isCompactDashboardCard) {
        (layoutMetrics.cardPaddingDp - 3).coerceAtLeast(6).dp
    } else {
        layoutMetrics.cardPaddingDp.dp
    }
    val cardContentSpacing = if (isCompactDashboardCard) {
        (layoutMetrics.cardSpacingDp - 4).coerceAtLeast(3).dp
    } else {
        layoutMetrics.cardSpacingDp.dp
    }
    val providerIconSize = if (isCompactDashboardCard) 38.dp else 46.dp
    val baseUsageColumnSpacingDp = if (isCompactDashboardCard) 2f else 4f
    val usageColumnSpacing = dashboardUsageLineSpacingDp(
        cardHeightDp = cardHeightDp,
        layoutMetrics = layoutMetrics,
        baseSpacingDp = baseUsageColumnSpacingDp
    ).dp
    val dashboardGaugeHeight = dashboardGaugeHeightDp(cardHeightDp, layoutMetrics).dp
    var dragOffsetX by remember(providerId) { mutableStateOf(0f) }
    var dragOffsetY by remember(providerId) { mutableStateOf(0f) }
    var cardCenter by remember(providerId) {
        mutableStateOf(DashboardCardCenter(Float.NaN, Float.NaN))
    }
    var cardBounds by remember(providerId) { mutableStateOf<DashboardCardBounds?>(null) }
    var dragOriginBounds by remember(providerId) { mutableStateOf<DashboardCardBounds?>(null) }
    var dragOriginCenter by remember(providerId) { mutableStateOf<DashboardCardCenter?>(null) }
    var isDragging by remember(providerId) { mutableStateOf(false) }
    var dragStartVisibleIndex by remember(providerId) { mutableStateOf<Int?>(null) }
    val currentVisibleIndex by rememberUpdatedState(visibleIndex)
    val currentVisibleCardCenters by rememberUpdatedState(visibleCardCenters)
    val currentPreviewTargetIndex by rememberUpdatedState(previewTargetIndex)
    val dragHandleModifier = if (dragEnabled) Modifier.pointerInput(providerId) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                isDragging = true
                dragOriginBounds = cardBounds
                dragOriginCenter = cardBounds?.center?.takeIf { it.isValid() } ?: cardCenter.takeIf { it.isValid() }
                dragStartVisibleIndex = currentVisibleIndex
                onDragOverlayChanged(dragOriginBounds, 0f, 0f)
                onDragStateChanged(providerId, true)
                onDropSlotChanged(currentVisibleIndex)
                onPreviewTargetChanged(currentVisibleIndex)
            },
            onDragCancel = {
                isDragging = false
                dragOriginBounds = null
                dragOriginCenter = null
                dragStartVisibleIndex = null
                dragOffsetX = 0f
                dragOffsetY = 0f
                onDragOverlayChanged(null, 0f, 0f)
                onDropSlotChanged(null)
                onPreviewTargetChanged(null)
                onDragStateChanged(providerId, false)
            },
            onDragEnd = {
                val dragBaseCenter = dragOriginCenter?.takeIf { it.isValid() } ?: cardCenter
                val draggedCenter = DashboardCardCenter(
                    x = dragBaseCenter.x + dragOffsetX,
                    y = dragBaseCenter.y + dragOffsetY
                )
                val slotIndex = dragInsertionSlotFromCenter(
                    cardCenters = currentVisibleCardCenters,
                    currentVisibleIndex = currentVisibleIndex,
                    draggedCenter = draggedCenter
                )
                val targetIndex = targetIndexFromInsertionSlot(
                    slotIndex = slotIndex,
                    currentVisibleIndex = currentVisibleIndex,
                    itemCount = currentVisibleCardCenters.size
                )
                val finalTargetIndex = currentPreviewTargetIndex ?: targetIndex
                val originalIndex = dragStartVisibleIndex ?: currentVisibleIndex
                if (finalTargetIndex != originalIndex) {
                    onReorderProvider(providerId, finalTargetIndex)
                }
                isDragging = false
                dragOriginBounds = null
                dragOriginCenter = null
                dragStartVisibleIndex = null
                dragOffsetX = 0f
                dragOffsetY = 0f
                onDragOverlayChanged(null, 0f, 0f)
                onDropSlotChanged(null)
                onPreviewTargetChanged(null)
                onDragStateChanged(providerId, false)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragOffsetX += dragAmount.x
                dragOffsetY += dragAmount.y

                val dragBaseCenter = dragOriginCenter?.takeIf { it.isValid() } ?: cardCenter
                val draggedCenter = DashboardCardCenter(
                    x = dragBaseCenter.x + dragOffsetX,
                    y = dragBaseCenter.y + dragOffsetY
                )
                onDragOverlayChanged(dragOriginBounds, dragOffsetX, dragOffsetY)
                onAutoScroll(draggedCenter.y)
                val slotIndex = dragInsertionSlotFromCenter(
                        cardCenters = currentVisibleCardCenters,
                        currentVisibleIndex = currentVisibleIndex,
                        draggedCenter = draggedCenter
                    )
                onDropSlotChanged(slotIndex)
                onPreviewTargetChanged(
                    targetIndexFromInsertionSlot(
                        slotIndex = slotIndex,
                        currentVisibleIndex = currentVisibleIndex,
                        itemCount = currentVisibleCardCenters.size
                    )
                )

            }
        )
    } else {
        Modifier
    }
    val effectiveDragging = forceDraggingVisual || (isDragging && !isPlaceholder)
    val cardAlpha = when {
        isPlaceholder -> 0f
        effectiveDragging -> 0.94f
        else -> 1f
    }

    Box(
        modifier = modifier
            .height(cardHeightDp.dp)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                val bounds = DashboardCardBounds(
                    left = position.x,
                    top = position.y,
                    width = coordinates.size.width,
                    height = coordinates.size.height
                )
                cardBounds = bounds
                val center = bounds.center
                cardCenter = center
                onCardCenterChanged(providerId, center)
            }
            .zIndex(if (effectiveDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = if (effectiveDragging) 1.035f else 1f
                scaleY = if (effectiveDragging) 1.035f else 1f
                alpha = cardAlpha
            }
            .clickable(enabled = !isPlaceholder && dragEnabled) { onProviderSelected(providerId) }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) 12.dp else 2.dp),
            color = colors.cardChrome,
            tonalElevation = if (effectiveDragging) 4.dp else 0.dp,
            shadowElevation = if (effectiveDragging) 6.dp else 1.dp,
            border = BorderStroke(
                if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) 1.dp else 2.dp,
                if (effectiveDragging) colors.primary else colors.border
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(titleBarHeight)
                    .background(if (effectiveDragging) colors.primary else colors.titleBar)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) {
                    ClassicWindowButton(index = 0)
                    Spacer(modifier = Modifier.width(6.dp))
                    ClassicWindowButton(index = 1)
                    Spacer(modifier = Modifier.width(6.dp))
                    ClassicWindowButton(index = 2)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = snapshot.displayName.ifBlank { providerId.displayName },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.titleText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                DashboardDragHandle(
                    modifier = dragHandleModifier,
                    isDragging = effectiveDragging
                )
                if (colors.theme != com.aiquota.mobile.local.AppTheme.MACOS) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (colors.theme != com.aiquota.mobile.local.AppTheme.MACOS) {
                    ClassicWindowButton(index = 0)
                    Spacer(modifier = Modifier.width(3.dp))
                    ClassicWindowButton(index = 1)
                    Spacer(modifier = Modifier.width(3.dp))
                    ClassicWindowButton(index = 2)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardChrome)
                    .padding(horizontal = 6.dp, vertical = locationRowVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderIconImage(
                    providerId = providerId,
                    modifier = Modifier.size(18.dp)
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(0.dp),
                    color = colors.contentAlt,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) {
                            "~/AI Quota/${providerId.displayName}"
                        } else {
                            "C:\\AI Quota\\${providerId.displayName}"
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) colors.titleText else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                shape = RoundedCornerShape(0.dp),
                color = colors.content,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(cardContentPadding),
                    verticalArrangement = Arrangement.spacedBy(cardContentSpacing)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProviderIconImage(
                                providerId = providerId,
                                modifier = Modifier.size(providerIconSize)
                            )
                            Text(
                                text = dashboardProviderIdentityLabel(providerId),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) colors.titleText else colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(usageColumnSpacing)
                        ) {
                            Text(
                                text = snapshot.statusLabel(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) colors.titleText else colors.textPrimary,
                                maxLines = 1
                            )
                            if (snapshot.lines.isEmpty()) {
                                Text(
                                    text = snapshot.message ?: stringResource(R.string.dashboard_no_lines),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) colors.textMuted else colors.textSecondary
                                )
                            } else {
                                dashboardUsagePreviewLines(snapshot).forEachIndexed { index, line ->
                                    UsageLinePreview(
                                        line = line,
                                        providerId = providerId,
                                        lineIndex = index,
                                        compact = isCompactDashboardCard,
                                        gaugeHeight = dashboardGaugeHeight
                                    )
                                }
                            }
                        }
                    }

                    if (snapshot.shouldShowDashboardConnectAction()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = { onConnectProvider(providerId) }) {
                                Text(stringResource(R.string.provider_connect))
                            }
                        }
                    }
                }
            }
        }
        }
        if (!effectiveDragging && !isPlaceholder && dropPlacement != null) {
            DashboardDropIndicatorLine(dropPlacement)
        }
    }
}

internal fun dashboardProviderIdentityLabel(providerId: ProviderId): String {
    return when (providerId) {
        ProviderId.ANTIGRAVITY -> "Anti\nGravity"
        else -> providerId.displayName
    }
}

internal fun dashboardUsagePreviewLines(snapshot: ProviderUsageSnapshot): List<ProviderUsageLine> {
    if (snapshot.providerId != ProviderId.ANTIGRAVITY) return snapshot.lines.take(2)
    val byKey = snapshot.lines.associateBy { dashboardAntigravityLineKey(it.label) }
    val preferred = listOfNotNull(
        byKey["gemini35flashhigh"],
        byKey["gemini35flashmedium"]
    )
    return preferred.takeIf { it.isNotEmpty() } ?: snapshot.lines.take(2)
}

private fun dashboardAntigravityLineKey(label: String): String {
    return label.lowercase(java.util.Locale.US).replace(Regex("[^a-z0-9]+"), "")
}

@Composable
private fun DashboardDragHandle(modifier: Modifier, isDragging: Boolean) {
    val colors = AIQuotaTheme.colors
    val handleDescription = stringResource(R.string.provider_reorder_handle)
    val lineColor = if (isDragging) colors.content else colors.titleText

    Column(
        modifier = modifier
            .semantics {
                contentDescription = handleDescription
                role = Role.Button
            }
            .size(width = 32.dp, height = 22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(lineColor)
            )
            if (index < 2) {
                Spacer(modifier = Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun BoxScope.DashboardDropIndicatorLine(placement: DashboardDropPlacement) {
    val colors = AIQuotaTheme.colors
    val alignment = when (placement) {
        DashboardDropPlacement.Top -> Alignment.TopCenter
        DashboardDropPlacement.Bottom -> Alignment.BottomCenter
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.primary)
            .align(alignment)
    )
}

@Composable
private fun ClassicWindowButton(index: Int) {
    val colors = AIQuotaTheme.colors
    val macColor = when (index) {
        0 -> colors.trafficRed
        1 -> colors.trafficYellow
        else -> colors.trafficGreen
    }

    Surface(
        modifier = Modifier.size(if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) 10.dp else 11.dp),
        shape = RoundedCornerShape(if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) 999.dp else 0.dp),
        color = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) macColor else colors.cardChrome,
        border = BorderStroke(1.dp, if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) macColor else colors.borderSoft)
    ) {}
}

internal fun dragTargetIndexFromCenter(
    cardCenters: List<DashboardCardCenter>,
    currentVisibleIndex: Int,
    draggedCenter: DashboardCardCenter
): Int {
    val slotIndex = dragInsertionSlotFromCenter(
        cardCenters = cardCenters,
        currentVisibleIndex = currentVisibleIndex,
        draggedCenter = draggedCenter
    )

    return targetIndexFromInsertionSlot(
        slotIndex = slotIndex,
        currentVisibleIndex = currentVisibleIndex,
        itemCount = cardCenters.size
    )
}

internal fun dragInsertionSlotFromCenter(
    cardCenters: List<DashboardCardCenter>,
    currentVisibleIndex: Int,
    draggedCenter: DashboardCardCenter
): Int {
    if (
        cardCenters.isEmpty() ||
        currentVisibleIndex !in cardCenters.indices ||
        !draggedCenter.isValid()
    ) {
        return currentVisibleIndex
    }

    if (cardCenters.any { !it.isValid() }) {
        return currentVisibleIndex
    }

    return (0..cardCenters.size)
        .map { slot -> slot to insertionSlotCenter(cardCenters, slot) }
        .minByOrNull { (_, slotCenter) ->
            val dx = slotCenter.x - draggedCenter.x
            val dy = slotCenter.y - draggedCenter.y
            dx * dx + dy * dy
        }
        ?.first
        ?: currentVisibleIndex
}

internal fun dashboardAutoScrollDelta(
    draggedCenterY: Float,
    viewportTopY: Float,
    viewportBottomY: Float,
    edgeThresholdPx: Float,
    maxDeltaPx: Float = 28f
): Float {
    if (edgeThresholdPx <= 0f || viewportBottomY <= viewportTopY) return 0f
    val topEdge = viewportTopY + edgeThresholdPx
    val bottomEdge = viewportBottomY - edgeThresholdPx
    return when {
        draggedCenterY < topEdge -> {
            val ratio = ((topEdge - draggedCenterY) / edgeThresholdPx).coerceIn(0f, 1f)
            -maxDeltaPx * ratio
        }
        draggedCenterY > bottomEdge -> {
            val ratio = ((draggedCenterY - bottomEdge) / edgeThresholdPx).coerceIn(0f, 1f)
            maxDeltaPx * ratio
        }
        else -> 0f
    }
}

private fun dashboardDropPlacement(
    visibleIndex: Int,
    visibleCount: Int,
    draggedProvider: ProviderId?,
    dropSlotIndex: Int?
): DashboardDropPlacement? {
    if (draggedProvider == null || dropSlotIndex == null || visibleCount <= 0) return null
    return when {
        dropSlotIndex == visibleIndex -> DashboardDropPlacement.Top
        dropSlotIndex == visibleCount && visibleIndex == visibleCount - 1 -> DashboardDropPlacement.Bottom
        else -> null
    }
}

private fun insertionSlotCenter(cardCenters: List<DashboardCardCenter>, slotIndex: Int): DashboardCardCenter {
    if (cardCenters.size == 1) return cardCenters.first()
    return when (slotIndex) {
        0 -> {
            val first = cardCenters[0]
            val second = cardCenters[1]
            DashboardCardCenter(
                x = first.x - ((second.x - first.x) / 2f),
                y = first.y - ((second.y - first.y) / 2f)
            )
        }
        cardCenters.size -> {
            val previous = cardCenters[cardCenters.lastIndex - 1]
            val last = cardCenters.last()
            DashboardCardCenter(
                x = last.x + ((last.x - previous.x) / 2f),
                y = last.y + ((last.y - previous.y) / 2f)
            )
        }
        else -> {
            val before = cardCenters[slotIndex - 1]
            val after = cardCenters[slotIndex]
            DashboardCardCenter(
                x = (before.x + after.x) / 2f,
                y = (before.y + after.y) / 2f
            )
        }
    }
}

private fun targetIndexFromInsertionSlot(
    slotIndex: Int,
    currentVisibleIndex: Int,
    itemCount: Int
): Int {
    val boundedSlot = slotIndex.coerceIn(0, itemCount)
    val targetIndex = if (boundedSlot > currentVisibleIndex) boundedSlot - 1 else boundedSlot
    return targetIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
}

private fun DashboardCardCenter.isValid(): Boolean {
    return !x.isNaN() && !x.isInfinite() && !y.isNaN() && !y.isInfinite()
}

@Composable
private fun UsageLinePreview(
    line: ProviderUsageLine,
    providerId: ProviderId,
    lineIndex: Int,
    compact: Boolean,
    gaugeHeight: Dp
) {
    val colors = AIQuotaTheme.colors
    val locale = java.util.Locale.getDefault()
    val labelStyle = if (compact) {
        MaterialTheme.typography.bodySmall.copy(lineHeight = 14.sp)
    } else {
        MaterialTheme.typography.bodySmall
    }
    val resetStyle = if (compact) {
        MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp)
    } else {
        MaterialTheme.typography.labelSmall
    }
    val resetText = displayResetTextForLocale(line.effectiveResetText(), locale)
        ?: if (line.remainingPercent != null) {
            stringResource(R.string.dashboard_reset_timer_pending)
        } else {
            null
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = line.displayUsageLabel(providerId, lineIndex, locale),
                modifier = Modifier.weight(1f),
                style = labelStyle,
                fontWeight = FontWeight.Medium,
                color = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) colors.titleText else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = displayRemainingText(line.remainingText, locale),
                style = labelStyle,
                color = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) colors.textMuted else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        line.remainingPercent?.let { remainingPercent ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gaugeHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.progressTrack)
            ) {
                LinearProgressIndicator(
                    progress = { remainingPercent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gaugeHeight),
                    color = colors.progress,
                    trackColor = colors.progressTrack
                )
            }
        }
        resetText?.let { resetDisplay ->
            Text(
                text = resetDisplay,
                modifier = Modifier.fillMaxWidth(),
                style = resetStyle,
                color = if (colors.theme == com.aiquota.mobile.local.AppTheme.MACOS) colors.textMuted else colors.textSecondary,
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProviderUsageSnapshot.statusLabel(): String {
    if (connectionState == ProviderConnectionState.CONNECTING) {
        return stringResource(R.string.provider_status_connecting)
    }
    if (connectionState == ProviderConnectionState.COLLECTING || refreshState == ProviderRefreshState.REFRESHING) {
        return stringResource(R.string.provider_status_collecting)
    }
    return when (connectionState) {
        ProviderConnectionState.DISCONNECTED -> stringResource(R.string.provider_status_disconnected)
        ProviderConnectionState.CONNECTING -> stringResource(R.string.provider_status_connecting)
        ProviderConnectionState.CONNECTED -> stringResource(R.string.provider_status_connected)
        ProviderConnectionState.COLLECTING -> stringResource(R.string.provider_status_collecting)
        ProviderConnectionState.STALE -> if (lines.isEmpty()) {
            stringResource(R.string.provider_status_auth_required)
        } else {
            stringResource(R.string.provider_status_connected)
        }
        ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> stringResource(R.string.provider_status_auth_required)
        ProviderConnectionState.UNAVAILABLE -> stringResource(R.string.provider_unavailable)
        ProviderConnectionState.ERROR -> stringResource(R.string.provider_status_error)
        ProviderConnectionState.NOT_CONNECTED -> stringResource(R.string.provider_status_disconnected)
    }
}
