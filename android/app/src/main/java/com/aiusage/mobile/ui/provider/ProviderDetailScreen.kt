package com.aiusage.mobile.ui.provider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiusage.mobile.R
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot

@Composable
fun ProviderDetailScreen(
    snapshot: ProviderUsageSnapshot,
    isHidden: Boolean,
    isBusy: Boolean,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onShowOnDashboard: () -> Unit,
    onHideFromDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = snapshot.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = providerStatus(snapshot, isBusy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConnect,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.provider_connect))
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.provider_refresh))
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        enabled = !isBusy && snapshot.connectionState != ProviderConnectionState.DISCONNECTED,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.provider_disconnect))
                    }
                }

                if (isHidden) {
                    TextButton(onClick = onShowOnDashboard) {
                        Text("Show on dashboard")
                    }
                } else {
                    TextButton(onClick = onHideFromDashboard) {
                        Text("Hide from dashboard")
                    }
                }

                snapshot.planLabel?.let { planLabel ->
                    Text(
                        text = planLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                snapshot.updatedAt.takeIf { it.isNotBlank() }?.let { updatedAt ->
                    Text(
                        text = "Updated $updatedAt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                snapshot.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (snapshot.connectionState) {
                            ProviderConnectionState.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        Text(
            text = "Usage",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (snapshot.lines.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = "No local usage lines available.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            snapshot.lines.forEach { line ->
                ProviderUsageLineRow(line)
            }
        }
    }
}

@Composable
private fun ProviderUsageLineRow(line: ProviderUsageLine) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(line.label, fontWeight = FontWeight.Medium)
                Text(
                    text = line.remainingText,
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
            line.resetText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            line.detailText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun providerStatus(snapshot: ProviderUsageSnapshot, isBusy: Boolean): String {
    if (isBusy) return "Working"
    return when (snapshot.connectionState) {
        ProviderConnectionState.DISCONNECTED -> "Disconnected"
        ProviderConnectionState.CONNECTING -> "Connecting"
        ProviderConnectionState.CONNECTED -> "Connected"
        ProviderConnectionState.UNAVAILABLE -> stringResource(R.string.provider_unavailable)
        ProviderConnectionState.ERROR -> "Error"
    }
}
