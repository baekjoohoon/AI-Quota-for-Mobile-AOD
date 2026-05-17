package com.aiusage.mobile.ui.provider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiusage.mobile.R
import com.aiusage.mobile.local.AppTheme
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.local.displayRemainingText
import com.aiusage.mobile.local.displayResetTextForLocale
import com.aiusage.mobile.local.displayUsageLabel
import com.aiusage.mobile.local.effectiveResetText
import com.aiusage.mobile.ui.AIUsageColors
import com.aiusage.mobile.ui.AIUsageTheme
import com.aiusage.mobile.ui.AppLayoutMetrics
import com.aiusage.mobile.ui.rememberAppLayoutMetrics
import kotlin.math.roundToInt

private val ClassicWindowChrome = AIUsageColors.WindowChrome
private val ClassicTitleBlue = AIUsageColors.SurfaceStrong
private val ClassicTitleText = AIUsageColors.SurfaceMuted
private val ClassicBorderDark = AIUsageColors.BorderDefault
private val ClassicBorderLight = AIUsageColors.SurfaceMuted
private val ClassicContent = AIUsageColors.SurfaceMuted
private val ClassicText = AIUsageColors.TextPrimary
private val ClassicMutedText = AIUsageColors.TextSecondary
private val ClassicProgress = AIUsageColors.SurfaceStrong
private val ClassicProgressTrack = AIUsageColors.WindowChrome

@Composable
fun ProviderDetailScreen(
    snapshot: ProviderUsageSnapshot,
    isHidden: Boolean,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutMetrics = rememberAppLayoutMetrics()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = layoutMetrics.contentHorizontalPaddingDp.dp,
                vertical = layoutMetrics.contentVerticalPaddingDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacingDp.dp)
    ) {
        ClassicProviderWindow(
            snapshot = snapshot,
            isHidden = isHidden,
            isBusy = isBusy,
            layoutMetrics = layoutMetrics,
            onConnect = onConnect,
            onRefresh = onRefresh,
            onDisconnect = onDisconnect
        )
    }
}

@Composable
private fun ClassicProviderWindow(
    snapshot: ProviderUsageSnapshot,
    isHidden: Boolean,
    isBusy: Boolean,
    layoutMetrics: AppLayoutMetrics,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit
) {
    val colors = AIUsageTheme.colors

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (colors.theme == AppTheme.MACOS) 12.dp else 2.dp),
        color = colors.cardChrome,
        shadowElevation = 1.dp,
        border = BorderStroke(if (colors.theme == AppTheme.MACOS) 1.dp else 2.dp, colors.border)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (colors.theme == AppTheme.MACOS) 30.dp else 22.dp)
                    .background(colors.titleBar)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (colors.theme == AppTheme.MACOS) {
                    ClassicWindowButton(index = 0)
                    Spacer(modifier = Modifier.width(6.dp))
                    ClassicWindowButton(index = 1)
                    Spacer(modifier = Modifier.width(6.dp))
                    ClassicWindowButton(index = 2)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = snapshot.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.titleText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (colors.theme != AppTheme.MACOS) {
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
                    providerId = snapshot.providerId,
                    modifier = Modifier.size(18.dp)
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(0.dp),
                    color = colors.contentAlt,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Text(
                        text = if (colors.theme == AppTheme.MACOS) {
                            "~/AI Usage/${snapshot.providerId.displayName}"
                        } else {
                            "C:\\AI Usage\\${snapshot.providerId.displayName}"
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                shape = RoundedCornerShape(0.dp),
                color = colors.content,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(layoutMetrics.cardPaddingDp.dp),
                    verticalArrangement = Arrangement.spacedBy(layoutMetrics.cardSpacingDp.dp)
                ) {
                    ProviderSummaryBlock(
                        snapshot = snapshot,
                        isHidden = isHidden,
                        isBusy = isBusy,
                        layoutMetrics = layoutMetrics,
                        onConnect = onConnect,
                        onRefresh = onRefresh,
                        onDisconnect = onDisconnect
                    )

                    ClassicSectionTitle(text = stringResource(R.string.provider_usage_title))
                    if (snapshot.lines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.provider_no_lines),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary
                        )
                    } else {
                        snapshot.lines.forEachIndexed { index, line ->
                            ProviderUsageLineRow(
                                line = line,
                                providerId = snapshot.providerId,
                                lineIndex = index,
                                layoutMetrics = layoutMetrics
                            )
                        }
                    }

                    UsageAnalysisSection(snapshot = snapshot)
                }
            }
        }
    }
}

@Composable
private fun ProviderSummaryBlock(
    snapshot: ProviderUsageSnapshot,
    isHidden: Boolean,
    isBusy: Boolean,
    layoutMetrics: AppLayoutMetrics,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit
) {
    val colors = AIUsageTheme.colors
    val isRefreshing = snapshot.refreshState == ProviderRefreshState.REFRESHING
    val isConnected = snapshot.connectionState == ProviderConnectionState.CONNECTED
    val actionEnabled = !isBusy && !isRefreshing
    val refreshEnabled = actionEnabled && snapshot.connectionState != ProviderConnectionState.DISCONNECTED

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProviderIconImage(
                providerId = snapshot.providerId,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = snapshot.providerId.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(layoutMetrics.cardSpacingDp.dp)
        ) {
            ClassicInfoLineWithRefresh(
                text = stringResource(R.string.provider_detail_status, providerStatus(snapshot, isBusy || isRefreshing)),
                isRefreshing = isRefreshing,
                enabled = refreshEnabled,
                onRefresh = onRefresh
            )
            snapshot.planLabel?.let { planLabel ->
                ClassicInfoLine(text = stringResource(R.string.provider_detail_plan, planLabel))
            }
            snapshot.updatedAt.takeIf { it.isNotBlank() }?.let { updatedAt ->
                ClassicInfoLine(text = stringResource(R.string.provider_updated, updatedAt))
            }
            ClassicInfoLine(
                text = if (isHidden) {
                    stringResource(R.string.provider_detail_visibility_hidden)
                } else {
                    stringResource(R.string.provider_detail_visibility_visible)
                }
            )
            snapshot.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (snapshot.connectionState == ProviderConnectionState.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary
                    }
                )
            }
        }
        Button(
            onClick = if (isConnected) onDisconnect else onConnect,
            enabled = actionEnabled,
            modifier = Modifier.widthIn(min = 112.dp, max = 180.dp)
        ) {
            Text(
                text = if (isConnected) {
                    stringResource(R.string.provider_disconnect)
                } else {
                    stringResource(R.string.provider_connect)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ClassicInfoLineWithRefresh(
    text: String,
    isRefreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ClassicInfoLine(text = text, bold = true)
        RefreshIconButton(
            isRefreshing = isRefreshing,
            enabled = enabled,
            onRefresh = onRefresh
        )
    }
}

@Composable
private fun RefreshIconButton(
    isRefreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit
) {
    val colors = AIUsageTheme.colors
    val transition = rememberInfiniteTransition(label = "provider-refresh")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "provider-refresh-rotation"
    )

    Surface(
        modifier = Modifier.size(28.dp),
        shape = RoundedCornerShape(999.dp),
        color = colors.contentAlt,
        border = BorderStroke(1.dp, colors.border)
    ) {
        IconButton(
            onClick = onRefresh,
            enabled = enabled,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = stringResource(R.string.provider_refresh),
                tint = if (enabled || isRefreshing) colors.textMuted else colors.borderSoft,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (isRefreshing) rotation else 0f)
            )
        }
    }
}

@Composable
private fun ProviderUsageLineRow(
    line: ProviderUsageLine,
    providerId: ProviderId,
    lineIndex: Int,
    layoutMetrics: AppLayoutMetrics
) {
    val colors = AIUsageTheme.colors
    val locale = java.util.Locale.getDefault()
    val resetText = displayResetTextForLocale(line.effectiveResetText(), locale)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = colors.contentAlt,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(layoutMetrics.cardPaddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(layoutMetrics.cardSpacingDp.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = line.displayUsageLabel(providerId, lineIndex, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary
                )
                Text(
                    text = displayRemainingText(line.remainingText, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            line.remainingPercent?.let { remainingPercent ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(colors.progressTrack)
                ) {
                    LinearProgressIndicator(
                        progress = { remainingPercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = colors.progress,
                        trackColor = colors.progressTrack
                    )
                }
            }
            resetText?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            line.metadataText()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun UsageAnalysisSection(snapshot: ProviderUsageSnapshot) {
    val colors = AIUsageTheme.colors
    val measuredLines = snapshot.lines.mapNotNull { line ->
        line.remainingPercent?.let { remainingPercent -> line to remainingPercent }
    }
    val lowestLine = measuredLines.minByOrNull { it.second }
    val resetSummary = snapshot.lines
        .mapNotNull { displayResetTextForLocale(it.effectiveResetText()) }
        .distinct()
        .take(3)
        .joinToString(" / ")

    ClassicSectionTitle(text = stringResource(R.string.provider_analysis_title))
    if (snapshot.lines.isEmpty()) {
        Text(
            text = stringResource(R.string.provider_analysis_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = if (colors.theme == AppTheme.MACOS) colors.textMuted else colors.textSecondary
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ClassicInfoLine(
            text = stringResource(R.string.provider_analysis_count, snapshot.lines.size),
            bold = true
        )
        lowestLine?.let { (line, remainingPercent) ->
            ClassicInfoLine(
                text = stringResource(
                    R.string.provider_analysis_lowest,
                    line.displayUsageLabel(snapshot.providerId, snapshot.lines.indexOf(line)),
                    percentText(remainingPercent)
                )
            )
        } ?: ClassicInfoLine(text = stringResource(R.string.provider_analysis_no_percent))
        if (resetSummary.isNotBlank()) {
            ClassicInfoLine(text = stringResource(R.string.provider_analysis_reset, resetSummary))
        }
    }
}

@Composable
private fun ClassicSectionTitle(text: String) {
    val colors = AIUsageTheme.colors

    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary
    )
}

@Composable
private fun ClassicInfoLine(
    text: String,
    bold: Boolean = false
) {
    val colors = AIUsageTheme.colors

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (colors.theme == AppTheme.MACOS) colors.titleText else colors.textPrimary
    )
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
        modifier = Modifier.size(if (colors.theme == AppTheme.MACOS) 10.dp else 11.dp),
        shape = RoundedCornerShape(if (colors.theme == AppTheme.MACOS) 999.dp else 0.dp),
        color = if (colors.theme == AppTheme.MACOS) macColor else colors.cardChrome,
        border = BorderStroke(1.dp, if (colors.theme == AppTheme.MACOS) macColor else colors.borderSoft)
    ) {}
}

@Composable
private fun providerStatus(snapshot: ProviderUsageSnapshot, isBusy: Boolean): String {
    if (isBusy) return stringResource(R.string.provider_status_working)
    return when (snapshot.connectionState) {
        ProviderConnectionState.DISCONNECTED -> stringResource(R.string.provider_status_disconnected)
        ProviderConnectionState.CONNECTING -> stringResource(R.string.provider_status_connecting)
        ProviderConnectionState.CONNECTED -> stringResource(R.string.provider_status_connected)
        ProviderConnectionState.UNAVAILABLE -> stringResource(R.string.provider_unavailable)
        ProviderConnectionState.ERROR -> stringResource(R.string.provider_status_error)
    }
}

private fun percentText(value: Float): String {
    return "${(value.coerceIn(0f, 1f) * 100).roundToInt()}%"
}

private fun ProviderUsageLine.metadataText(): String? {
    val parts = buildList {
        category?.let { add("category: $it") }
        unit?.let { add("unit: $it") }
        windowText?.let { add("window: $it") }
        startsAt?.let { add("startsAt: $it") }
        resetsAt?.let { add("resetsAt: $it") }
        sourceLabel?.let { add("source: $it") }
        confidence?.let { add("confidence: ${(it.coerceIn(0f, 1f) * 100).roundToInt()}%") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
}
