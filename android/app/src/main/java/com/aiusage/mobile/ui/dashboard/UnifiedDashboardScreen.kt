package com.aiusage.mobile.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aiusage.mobile.R
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesCodec
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.local.displayRemainingText
import com.aiusage.mobile.local.displayResetTextForLocale
import com.aiusage.mobile.local.displayUsageLabel
import com.aiusage.mobile.local.effectiveResetText
import com.aiusage.mobile.ui.AIUsageColors
import com.aiusage.mobile.ui.AIUsageTheme
import com.aiusage.mobile.ui.AppLayoutMetrics
import com.aiusage.mobile.ui.dashboardProviderCardHeightDp
import com.aiusage.mobile.ui.rememberAppLayoutMetrics
import com.aiusage.mobile.ui.provider.ProviderIconImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ExplorerAccentColor = AIUsageColors.SurfaceRaised
private val ClassicWindowChrome = AIUsageColors.WindowChrome
private val ClassicTitleBlue = AIUsageColors.SurfaceStrong
private val ClassicTitleText = AIUsageColors.SurfaceMuted
private val ClassicBorderDark = AIUsageColors.BorderDefault
private val ClassicBorderLight = AIUsageColors.SurfaceMuted
private val ClassicContent = AIUsageColors.SurfaceMuted

internal data class DashboardCardCenter(
    val x: Float,
    val y: Float
)

@Composable
fun UnifiedDashboardScreen(
    providerOrder: List<ProviderId>,
    hiddenProviders: Set<ProviderId>,
    snapshots: List<ProviderUsageSnapshot>,
    onProviderSelected: (ProviderId) -> Unit,
    onConnectProvider: (ProviderId) -> Unit,
    onReorderProvider: (ProviderId, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutMetrics = rememberAppLayoutMetrics()
    val colors = AIUsageTheme.colors
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val visibleProviders = ProviderPreferencesCodec.visibleProviders(providerOrder, hiddenProviders)
    val snapshotsByProvider = snapshots.associateBy { it.providerId }
    val cardCenters = remember { mutableStateMapOf<ProviderId, DashboardCardCenter>() }
    val visibleCardCenters = visibleProviders.map {
        cardCenters[it] ?: DashboardCardCenter(Float.NaN, Float.NaN)
    }

    BoxWithConstraints(modifier = modifier) {
        val cardHeightDp = dashboardProviderCardHeightDp(
            viewportHeightDp = maxHeight.value.roundToInt(),
            layoutMetrics = layoutMetrics
        )
        val gridColumnCount = layoutMetrics.dashboardGridColumnCount.coerceAtLeast(1)

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layoutMetrics.dashboardTitleHeightDp.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (visibleProviders.isEmpty()) {
                EmptyDashboardState(layoutMetrics = layoutMetrics)
            } else {
                if (gridColumnCount == 1) {
                    visibleProviders.forEachIndexed { visibleIndex, providerId ->
                        ProviderUsageCard(
                            providerId = providerId,
                            snapshot = snapshotsByProvider[providerId] ?: ProviderUsageSnapshot.disconnected(providerId),
                            visibleIndex = visibleIndex,
                            visibleCardCenters = visibleCardCenters,
                            cardHeightDp = cardHeightDp,
                            layoutMetrics = layoutMetrics,
                            modifier = Modifier.fillMaxWidth(),
                            onProviderSelected = onProviderSelected,
                            onConnectProvider = onConnectProvider,
                            onReorderProvider = onReorderProvider,
                            onCardCenterChanged = { id, center -> cardCenters[id] = center },
                            onAutoScroll = { delta ->
                                coroutineScope.launch {
                                    scrollState.scrollBy(delta)
                                }
                            }
                        )
                    }
                } else {
                    visibleProviders.chunked(gridColumnCount).forEachIndexed { rowIndex, rowProviders ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacingDp.dp)
                        ) {
                            rowProviders.forEachIndexed { columnIndex, providerId ->
                                val visibleIndex = rowIndex * gridColumnCount + columnIndex
                                ProviderUsageCard(
                                    providerId = providerId,
                                    snapshot = snapshotsByProvider[providerId] ?: ProviderUsageSnapshot.disconnected(providerId),
                                    visibleIndex = visibleIndex,
                                    visibleCardCenters = visibleCardCenters,
                                    cardHeightDp = cardHeightDp,
                                    layoutMetrics = layoutMetrics,
                                    modifier = Modifier.weight(1f),
                                    onProviderSelected = onProviderSelected,
                                    onConnectProvider = onConnectProvider,
                                    onReorderProvider = onReorderProvider,
                                    onCardCenterChanged = { id, center -> cardCenters[id] = center },
                                    onAutoScroll = { delta ->
                                        coroutineScope.launch {
                                            scrollState.scrollBy(delta)
                                        }
                                    }
                                )
                            }
                            repeat(gridColumnCount - rowProviders.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboardState(layoutMetrics: AppLayoutMetrics) {
    val colors = AIUsageTheme.colors

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
private fun ProviderUsageCard(
    providerId: ProviderId,
    snapshot: ProviderUsageSnapshot,
    visibleIndex: Int,
    visibleCardCenters: List<DashboardCardCenter>,
    cardHeightDp: Int,
    layoutMetrics: AppLayoutMetrics,
    modifier: Modifier,
    onProviderSelected: (ProviderId) -> Unit,
    onConnectProvider: (ProviderId) -> Unit,
    onReorderProvider: (ProviderId, Int) -> Unit,
    onCardCenterChanged: (ProviderId, DashboardCardCenter) -> Unit,
    onAutoScroll: (Float) -> Unit
) {
    val colors = AIUsageTheme.colors
    val density = LocalDensity.current
    val autoScrollThresholdPx = with(density) { 220.dp.toPx() }
    val autoScrollStepPx = with(density) { 34.dp.toPx() }
    var dragOffsetX by remember(providerId) { mutableStateOf(0f) }
    var dragOffsetY by remember(providerId) { mutableStateOf(0f) }
    var cardCenter by remember(providerId) {
        mutableStateOf(DashboardCardCenter(Float.NaN, Float.NaN))
    }
    var isDragging by remember(providerId) { mutableStateOf(false) }
    val currentVisibleIndex by rememberUpdatedState(visibleIndex)
    val currentVisibleCardCenters by rememberUpdatedState(visibleCardCenters)
    val dragHandleModifier = Modifier.pointerInput(providerId) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                isDragging = true
            },
            onDragCancel = {
                isDragging = false
                dragOffsetX = 0f
                dragOffsetY = 0f
            },
            onDragEnd = {
                val targetIndex = dragTargetIndexFromCenter(
                    cardCenters = currentVisibleCardCenters,
                    currentVisibleIndex = currentVisibleIndex,
                    draggedCenter = DashboardCardCenter(
                        x = cardCenter.x + dragOffsetX,
                        y = cardCenter.y + dragOffsetY
                    )
                )
                if (targetIndex != currentVisibleIndex) {
                    onReorderProvider(providerId, targetIndex)
                }
                isDragging = false
                dragOffsetX = 0f
                dragOffsetY = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                dragOffsetX += dragAmount.x
                dragOffsetY += dragAmount.y
                when {
                    dragOffsetY > autoScrollThresholdPx -> {
                        onAutoScroll(autoScrollStepPx)
                        dragOffsetY += autoScrollStepPx
                    }
                    dragOffsetY < -autoScrollThresholdPx -> {
                        onAutoScroll(-autoScrollStepPx)
                        dragOffsetY -= autoScrollStepPx
                    }
                }
            }
        )
    }

    Surface(
        modifier = modifier
            .height(cardHeightDp.dp)
            .onGloballyPositioned { coordinates ->
                if (!isDragging) {
                    val position = coordinates.positionInParent()
                    val center = DashboardCardCenter(
                        x = position.x + (coordinates.size.width / 2f),
                        y = position.y + (coordinates.size.height / 2f)
                    )
                    cardCenter = center
                    onCardCenterChanged(providerId, center)
                }
            }
            .zIndex(if (isDragging) 1f else 0f)
            .offset { IntOffset(x = dragOffsetX.roundToInt(), y = dragOffsetY.roundToInt()) }
            .clickable { onProviderSelected(providerId) },
        shape = RoundedCornerShape(if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) 12.dp else 2.dp),
        color = colors.cardChrome,
        tonalElevation = if (isDragging) 4.dp else 0.dp,
        shadowElevation = if (isDragging) 6.dp else 1.dp,
        border = BorderStroke(
            if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) 1.dp else 2.dp,
            if (isDragging) colors.primary else colors.border
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) 30.dp else 22.dp)
                    .background(if (isDragging) colors.primary else colors.titleBar)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) {
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
                    isDragging = isDragging
                )
                if (colors.theme != com.aiusage.mobile.local.AppTheme.MACOS) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (colors.theme != com.aiusage.mobile.local.AppTheme.MACOS) {
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
                    .padding(horizontal = 6.dp, vertical = 4.dp),
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
                        text = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) {
                            "~/AI Usage/${providerId.displayName}"
                        } else {
                            "C:\\AI Usage\\${providerId.displayName}"
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) colors.titleText else colors.textPrimary,
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
                    modifier = Modifier.padding(layoutMetrics.cardPaddingDp.dp),
                    verticalArrangement = Arrangement.spacedBy(layoutMetrics.cardSpacingDp.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ProviderIconImage(
                                providerId = providerId,
                                modifier = Modifier.size(46.dp)
                            )
                            Text(
                                text = providerId.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) colors.titleText else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = snapshot.connectionState.label(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) colors.titleText else colors.textPrimary,
                                maxLines = 1
                            )
                            if (snapshot.lines.isEmpty()) {
                                Text(
                                    text = snapshot.message ?: stringResource(R.string.dashboard_no_lines),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) colors.textMuted else colors.textSecondary
                                )
                            } else {
                                snapshot.lines.take(2).forEachIndexed { index, line ->
                                    UsageLinePreview(line, providerId, index)
                                }
                            }
                        }
                    }

                    if (snapshot.connectionState != ProviderConnectionState.CONNECTED) {
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
}

@Composable
private fun DashboardDragHandle(modifier: Modifier, isDragging: Boolean) {
    val colors = AIUsageTheme.colors
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
private fun ClassicWindowButton(index: Int) {
    val colors = AIUsageTheme.colors
    val macColor = when (index) {
        0 -> colors.trafficRed
        1 -> colors.trafficYellow
        else -> colors.trafficGreen
    }

    Surface(
        modifier = Modifier.size(if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) 10.dp else 11.dp),
        shape = RoundedCornerShape(if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) 999.dp else 0.dp),
        color = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) macColor else colors.cardChrome,
        border = BorderStroke(1.dp, if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) macColor else colors.borderSoft)
    ) {}
}

internal fun dragTargetIndexFromCenter(
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

    return cardCenters
        .mapIndexedNotNull { index, center ->
            if (!center.isValid()) {
                null
            } else {
                val dx = center.x - draggedCenter.x
                val dy = center.y - draggedCenter.y
                index to (dx * dx + dy * dy)
            }
        }
        .minByOrNull { it.second }
        ?.first
        ?: currentVisibleIndex
}

private fun DashboardCardCenter.isValid(): Boolean {
    return !x.isNaN() && !x.isInfinite() && !y.isNaN() && !y.isInfinite()
}

@Composable
private fun UsageLinePreview(line: ProviderUsageLine, providerId: ProviderId, lineIndex: Int) {
    val colors = AIUsageTheme.colors
    val locale = java.util.Locale.getDefault()
    val resetText = displayResetTextForLocale(line.effectiveResetText(), locale)
        ?: if (line.remainingPercent != null) {
            stringResource(R.string.dashboard_reset_timer_pending)
        } else {
            null
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = line.displayUsageLabel(providerId, lineIndex, locale),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) colors.titleText else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = displayRemainingText(line.remainingText, locale),
                style = MaterialTheme.typography.bodySmall,
                color = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) colors.textMuted else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        line.remainingPercent?.let { remainingPercent ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.progressTrack)
            ) {
                LinearProgressIndicator(
                    progress = { remainingPercent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = colors.progress,
                    trackColor = colors.progressTrack
                )
            }
        }
        resetText?.let { resetDisplay ->
            Text(
                text = resetDisplay,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = if (colors.theme == com.aiusage.mobile.local.AppTheme.MACOS) colors.textMuted else colors.textSecondary,
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProviderConnectionState.label(): String {
    return when (this) {
        ProviderConnectionState.DISCONNECTED -> stringResource(R.string.provider_status_disconnected)
        ProviderConnectionState.CONNECTING -> stringResource(R.string.provider_status_connecting)
        ProviderConnectionState.CONNECTED -> stringResource(R.string.provider_status_connected)
        ProviderConnectionState.UNAVAILABLE -> stringResource(R.string.provider_unavailable)
        ProviderConnectionState.ERROR -> stringResource(R.string.provider_status_error)
    }
}
