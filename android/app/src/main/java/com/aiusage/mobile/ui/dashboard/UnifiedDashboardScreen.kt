package com.aiusage.mobile.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesCodec
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot

@Composable
fun UnifiedDashboardScreen(
    providerOrder: List<ProviderId>,
    hiddenProviders: Set<ProviderId>,
    snapshots: List<ProviderUsageSnapshot>,
    onProviderSelected: (ProviderId) -> Unit,
    onConnectProvider: (ProviderId) -> Unit,
    onMoveProvider: (ProviderId, Int) -> Unit,
    onHideProvider: (ProviderId) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleProviders = ProviderPreferencesCodec.visibleProviders(providerOrder, hiddenProviders)
    val snapshotsByProvider = snapshots.associateBy { it.providerId }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Local provider usage on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (visibleProviders.isEmpty()) {
            EmptyDashboardState()
        } else {
            visibleProviders.forEach { providerId ->
                ProviderUsageCard(
                    providerId = providerId,
                    snapshot = snapshotsByProvider[providerId] ?: ProviderUsageSnapshot.disconnected(providerId),
                    providerOrder = providerOrder,
                    onProviderSelected = onProviderSelected,
                    onConnectProvider = onConnectProvider,
                    onMoveProvider = onMoveProvider,
                    onHideProvider = onHideProvider
                )
            }
        }
    }
}

@Composable
private fun EmptyDashboardState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("No visible providers", fontWeight = FontWeight.SemiBold)
            Text(
                text = "Open a provider from the navigation bar to show it on the dashboard again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderUsageCard(
    providerId: ProviderId,
    snapshot: ProviderUsageSnapshot,
    providerOrder: List<ProviderId>,
    onProviderSelected: (ProviderId) -> Unit,
    onConnectProvider: (ProviderId) -> Unit,
    onMoveProvider: (ProviderId, Int) -> Unit,
    onHideProvider: (ProviderId) -> Unit
) {
    val moveUpIndex = ProviderCardOrder.targetIndex(providerOrder, providerId, -1)
    val moveDownIndex = ProviderCardOrder.targetIndex(providerOrder, providerId, 1)
    val normalizedOrder = ProviderCardOrder.normalizedOrder(providerOrder)
    val currentIndex = normalizedOrder.indexOf(providerId)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onProviderSelected(providerId) },
                onLongClick = {
                    if (currentIndex > 0) {
                        onMoveProvider(providerId, moveUpIndex)
                    }
                }
            ),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.displayName.ifBlank { providerId.displayName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = snapshot.connectionState.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (snapshot.connectionState != ProviderConnectionState.CONNECTED) {
                    Button(onClick = { onConnectProvider(providerId) }) {
                        Text("Connect")
                    }
                }
            }

            if (snapshot.lines.isEmpty()) {
                Text(
                    text = snapshot.message ?: "No local usage lines yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                snapshot.lines.take(2).forEach { line ->
                    UsageLinePreview(line)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onMoveProvider(providerId, moveUpIndex) },
                    enabled = currentIndex > 0
                ) {
                    Text("Move up")
                }
                OutlinedButton(
                    onClick = { onMoveProvider(providerId, moveDownIndex) },
                    enabled = currentIndex >= 0 && currentIndex < normalizedOrder.lastIndex
                ) {
                    Text("Move down")
                }
                TextButton(onClick = { onHideProvider(providerId) }) {
                    Text("Hide")
                }
            }
        }
    }
}

@Composable
private fun UsageLinePreview(line: ProviderUsageLine) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = line.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = line.remainingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        line.remainingPercent?.let { remainingPercent ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LinearProgressIndicator(
                    progress = { remainingPercent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        line.resetText?.let { resetText ->
            Text(
                text = resetText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        line.detailText?.let { detailText ->
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun ProviderConnectionState.label(): String {
    return when (this) {
        ProviderConnectionState.DISCONNECTED -> "Disconnected"
        ProviderConnectionState.CONNECTING -> "Connecting"
        ProviderConnectionState.CONNECTED -> "Connected"
        ProviderConnectionState.UNAVAILABLE -> "Unavailable"
        ProviderConnectionState.ERROR -> "Error"
    }
}
