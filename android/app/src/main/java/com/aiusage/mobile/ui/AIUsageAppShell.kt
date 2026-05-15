package com.aiusage.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesRepository
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.notification.UsageLimitNotificationController
import com.aiusage.mobile.providers.ProviderConnectorRegistry
import com.aiusage.mobile.providers.ProviderHostAllowlist
import com.aiusage.mobile.providers.WebLoginActivity
import com.aiusage.mobile.ui.dashboard.ProviderCardOrder
import com.aiusage.mobile.ui.dashboard.UnifiedDashboardScreen
import com.aiusage.mobile.ui.provider.ProviderDetailScreen
import com.aiusage.mobile.ui.settings.LegacyWindowsSyncPanel
import com.aiusage.mobile.widget.AIUsageUnifiedGlanceWidget
import com.aiusage.mobile.widget.ProviderUsageGlanceWidget
import com.aiusage.mobile.widget.WidgetSnapshotCache
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun AIUsageAppShell(
    context: Context,
    legacyWindowsSyncContent: (@Composable () -> Unit)? = null,
    initialRoute: AppRoute = AppRoute.Home,
    modifier: Modifier = Modifier
) {
    val appContext = remember(context) { context.applicationContext }
    val launchContext = LocalContext.current
    val providerPreferencesRepository = remember(appContext) {
        ProviderPreferencesRepository(appContext)
    }
    val localUsageRepository = remember(appContext) {
        LocalUsageRepository(appContext)
    }
    val connectorRegistry = remember { ProviderConnectorRegistry.default() }
    val widgetSnapshotCache = remember(appContext) { WidgetSnapshotCache(appContext) }
    val coroutineScope = rememberCoroutineScope()

    var route by remember { mutableStateOf(initialRoute) }
    var providerOrder by remember { mutableStateOf(providerPreferencesRepository.providerOrder()) }
    var hiddenProviders by remember { mutableStateOf(providerPreferencesRepository.hiddenProviders()) }
    var snapshots by remember { mutableStateOf(localUsageRepository.readSnapshots()) }
    var busyProvider by remember { mutableStateOf<ProviderId?>(null) }

    fun refreshSnapshots() {
        snapshots = localUsageRepository.readSnapshots()
    }

    fun saveProviderSnapshot(snapshot: ProviderUsageSnapshot) {
        localUsageRepository.saveSnapshot(snapshot)
        refreshSnapshots()
    }

    fun setProviderHidden(providerId: ProviderId, hidden: Boolean) {
        providerPreferencesRepository.setProviderHidden(providerId, hidden)
        hiddenProviders = providerPreferencesRepository.hiddenProviders()
    }

    fun moveProvider(providerId: ProviderId, targetIndex: Int) {
        val nextOrder = ProviderCardOrder.moveToTargetIndex(providerOrder, providerId, targetIndex)
        providerPreferencesRepository.saveProviderOrder(nextOrder)
        providerOrder = providerPreferencesRepository.providerOrder()
    }

    fun connectProvider(providerId: ProviderId) {
        val connector = connectorRegistry.connectorFor(providerId)
        val now = Instant.now().toString()
        route = AppRoute.ProviderDetail(providerId)

        if (!ProviderHostAllowlist.isAllowed(providerId, connector.startUrl)) {
            saveProviderSnapshot(
                ProviderUsageSnapshot.unavailable(
                    providerId = providerId,
                    message = "Local web login is unavailable for ${providerId.displayName}."
                ).copy(updatedAt = now)
            )
            return
        }

        val launchResult = runCatching {
            val intent = WebLoginActivity.createIntent(launchContext, providerId, connector.startUrl)
            if (launchContext !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchContext.startActivity(intent)
        }

        val currentSnapshot = snapshots
            .firstOrNull { it.providerId == providerId }
            ?: ProviderUsageSnapshot.disconnected(providerId)
        val nextSnapshot = launchResult.fold(
            onSuccess = {
                currentSnapshot.copy(
                    refreshState = ProviderRefreshState.IDLE,
                    updatedAt = now,
                    message = "Opened local web login. Refresh when local web login capture is available."
                )
            },
            onFailure = { error ->
                ProviderUsageSnapshot(
                    providerId = providerId,
                    connectionState = ProviderConnectionState.ERROR,
                    refreshState = ProviderRefreshState.IDLE,
                    updatedAt = now,
                    message = error.message ?: "Could not open provider login."
                )
            }
        )
        saveProviderSnapshot(nextSnapshot)
    }

    fun refreshProvider(providerId: ProviderId) {
        val currentSnapshot = snapshots
            .firstOrNull { it.providerId == providerId }
            ?: ProviderUsageSnapshot.disconnected(providerId)
        saveProviderSnapshot(
            currentSnapshot.copy(
                refreshState = ProviderRefreshState.IDLE,
                message = "Open Connect to capture visible usage text first."
            )
        )
        route = AppRoute.ProviderDetail(providerId)
    }

    fun disconnectProvider(providerId: ProviderId) {
        busyProvider = providerId
        coroutineScope.launch {
            runCatching { connectorRegistry.connectorFor(providerId).disconnect() }
            localUsageRepository.removeProviderSnapshot(providerId)
            refreshSnapshots()
            busyProvider = null
        }
    }

    LaunchedEffect(providerOrder, hiddenProviders, snapshots) {
        val updatedAt = Instant.now().toString()
        val displayOnlyJson = localUsageRepository.exportDisplayOnlyCache(
            order = providerOrder,
            hidden = hiddenProviders,
            updatedAt = updatedAt
        )
        widgetSnapshotCache.writeLocalDisplaySnapshot(displayOnlyJson, updatedAt)
        UsageLimitNotificationController.updateFromCache(appContext)
        AIUsageUnifiedGlanceWidget().updateAll(appContext)
        ProviderUsageGlanceWidget().updateAll(appContext)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppTopBar(route = route)
        },
        bottomBar = {
            AppNavigationBar(
                route = route,
                providerOrder = providerOrder,
                onRouteSelected = { route = it }
            )
        }
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when (val currentRoute = route) {
                AppRoute.Home -> UnifiedDashboardScreen(
                    providerOrder = providerOrder,
                    hiddenProviders = hiddenProviders,
                    snapshots = snapshots,
                    onProviderSelected = { route = AppRoute.ProviderDetail(it) },
                    onConnectProvider = ::connectProvider,
                    onMoveProvider = ::moveProvider,
                    onHideProvider = { setProviderHidden(it, true) },
                    modifier = Modifier.fillMaxSize()
                )
                is AppRoute.ProviderDetail -> {
                    val snapshot = snapshots
                        .firstOrNull { it.providerId == currentRoute.providerId }
                        ?: ProviderUsageSnapshot.disconnected(currentRoute.providerId)
                    ProviderDetailScreen(
                        snapshot = snapshot,
                        isHidden = currentRoute.providerId in hiddenProviders,
                        isBusy = busyProvider == currentRoute.providerId,
                        onConnect = { connectProvider(currentRoute.providerId) },
                        onRefresh = { refreshProvider(currentRoute.providerId) },
                        onDisconnect = { disconnectProvider(currentRoute.providerId) },
                        onShowOnDashboard = { setProviderHidden(currentRoute.providerId, false) },
                        onHideFromDashboard = { setProviderHidden(currentRoute.providerId, true) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AppRoute.Settings -> LegacyWindowsSyncPanel(
                    legacyContent = legacyWindowsSyncContent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun AppTopBar(route: AppRoute) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = route.title(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Local-first usage tracking",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppNavigationBar(
    route: AppRoute,
    providerOrder: List<ProviderId>,
    onRouteSelected: (AppRoute) -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RouteChip(
                label = "Home",
                selected = route is AppRoute.Home,
                onClick = { onRouteSelected(AppRoute.Home) }
            )
            ProviderCardOrder.normalizedOrder(providerOrder).forEach { providerId ->
                RouteChip(
                    label = providerId.displayName,
                    selected = route is AppRoute.ProviderDetail && route.providerId == providerId,
                    onClick = { onRouteSelected(AppRoute.ProviderDetail(providerId)) }
                )
            }
            RouteChip(
                label = "Settings",
                selected = route is AppRoute.Settings,
                onClick = { onRouteSelected(AppRoute.Settings) }
            )
        }
    }
}

@Composable
private fun RouteChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun AppRoute.title(): String {
    return when (this) {
        AppRoute.Home -> "AI Usage"
        is AppRoute.ProviderDetail -> providerId.displayName
        AppRoute.Settings -> "Settings"
    }
}
