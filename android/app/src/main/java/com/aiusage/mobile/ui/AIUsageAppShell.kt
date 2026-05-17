package com.aiusage.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.aiusage.mobile.R
import com.aiusage.mobile.local.AppTheme
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesRepository
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.local.ThemePreferencesRepository
import com.aiusage.mobile.notification.UsageLimitNotificationController
import com.aiusage.mobile.providers.CodexOAuthRepository
import com.aiusage.mobile.providers.ProviderConnectorRegistry
import com.aiusage.mobile.providers.ProviderHostAllowlist
import com.aiusage.mobile.providers.ProviderUsageCollectionService
import com.aiusage.mobile.providers.WebLoginActivity
import com.aiusage.mobile.ui.dashboard.ProviderCardOrder
import com.aiusage.mobile.ui.dashboard.UnifiedDashboardScreen
import com.aiusage.mobile.ui.provider.ProviderDetailScreen
import com.aiusage.mobile.ui.provider.ProviderIconImage
import com.aiusage.mobile.ui.settings.SettingsPanel
import com.aiusage.mobile.widget.AIUsageUnifiedGlanceWidget
import com.aiusage.mobile.widget.ProviderUsageGlanceWidget
import com.aiusage.mobile.widget.WidgetSnapshotCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun AIUsageAppShell(
    context: Context,
    initialRoute: AppRoute = AppRoute.Home,
    modifier: Modifier = Modifier
) {
    val appContext = remember(context) { context.applicationContext }
    val launchContext = LocalContext.current
    val providerPreferencesRepository = remember(appContext) {
        ProviderPreferencesRepository(appContext)
    }
    val themePreferencesRepository = remember(appContext) {
        ThemePreferencesRepository(appContext)
    }
    val localUsageRepository = remember(appContext) {
        LocalUsageRepository(appContext)
    }
    val connectorRegistry = remember { ProviderConnectorRegistry.default() }
    val widgetSnapshotCache = remember(appContext) { WidgetSnapshotCache(appContext) }
    val coroutineScope = rememberCoroutineScope()
    val layoutMetrics = rememberAppLayoutMetrics()
    val snackbarHostState = remember { SnackbarHostState() }

    var route by remember { mutableStateOf(initialRoute) }
    var currentTheme by remember { mutableStateOf(themePreferencesRepository.currentTheme()) }
    var providerOrder by remember { mutableStateOf(providerPreferencesRepository.providerOrder()) }
    var hiddenProviders by remember { mutableStateOf(providerPreferencesRepository.hiddenProviders()) }
    var snapshots by remember { mutableStateOf(localUsageRepository.readSnapshots()) }
    var busyProvider by remember { mutableStateOf<ProviderId?>(null) }
    var canPostNotifications by remember {
        mutableStateOf(UsageLimitNotificationController.canPostNotifications(launchContext))
    }
    var notificationEnabled by remember {
        mutableStateOf(UsageLimitNotificationController.isEnabled(appContext) && canPostNotifications)
    }
    val themeColors = aiUsageThemeColors(currentTheme)

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

    fun reorderVisibleProvider(providerId: ProviderId, targetVisibleIndex: Int) {
        val nextOrder = ProviderCardOrder.moveToVisibleTargetIndex(
            order = providerOrder,
            hidden = hiddenProviders,
            providerId = providerId,
            targetVisibleIndex = targetVisibleIndex
        )
        providerPreferencesRepository.saveProviderOrder(nextOrder)
        providerOrder = providerPreferencesRepository.providerOrder()
    }

    fun hideProviderWithNotice(providerId: ProviderId) {
        setProviderHidden(providerId, true)
        if ((route as? AppRoute.ProviderDetail)?.providerId == providerId) {
            route = AppRoute.Home
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = launchContext.getString(R.string.provider_hidden_snackbar, providerId.displayName),
                duration = SnackbarDuration.Short
            )
        }
    }

    fun connectProvider(providerId: ProviderId) {
        val connector = connectorRegistry.connectorFor(providerId)
        val loginStartUrl = if (providerId == ProviderId.CODEX) {
            CodexOAuthRepository(appContext).beginAuthorizationUrl()
        } else {
            connector.startUrl
        }
        val now = Instant.now().toString()
        route = AppRoute.ProviderDetail(providerId)

        if (!ProviderHostAllowlist.isAllowed(providerId, loginStartUrl)) {
            saveProviderSnapshot(
                ProviderUsageSnapshot.unavailable(
                    providerId = providerId,
                    message = launchContext.getString(
                        R.string.provider_login_unavailable_message,
                        providerId.displayName
                    )
                ).copy(updatedAt = now)
            )
            return
        }

        val currentSnapshot = snapshots
            .firstOrNull { it.providerId == providerId }
            ?: ProviderUsageSnapshot.disconnected(providerId)
        saveProviderSnapshot(
            currentSnapshot.copy(
                connectionState = ProviderConnectionState.CONNECTING,
                refreshState = ProviderRefreshState.REFRESHING,
                updatedAt = now,
                message = launchContext.getString(R.string.provider_login_opened_message)
            )
        )

        val launchResult = runCatching {
            val intent = WebLoginActivity.createIntent(launchContext, providerId, loginStartUrl)
            if (launchContext !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchContext.startActivity(intent)
        }

        val nextSnapshot = launchResult.fold(
            onSuccess = {
                null
            },
            onFailure = {
                ProviderUsageSnapshot(
                    providerId = providerId,
                    connectionState = ProviderConnectionState.ERROR,
                    refreshState = ProviderRefreshState.IDLE,
                    updatedAt = now,
                    message = launchContext.getString(R.string.provider_login_open_failed_message)
                )
            }
        )
        nextSnapshot?.let(::saveProviderSnapshot)
    }

    fun refreshProvider(providerId: ProviderId) {
        val now = Instant.now().toString()
        val currentSnapshot = snapshots
            .firstOrNull { it.providerId == providerId }
            ?: ProviderUsageSnapshot.disconnected(providerId)
        route = AppRoute.ProviderDetail(providerId)

        saveProviderSnapshot(
            currentSnapshot.copy(
                refreshState = ProviderRefreshState.REFRESHING,
                updatedAt = now,
                message = launchContext.getString(R.string.provider_refresh_started_message)
            )
        )

        val launchResult = runCatching {
            ProviderUsageCollectionService.start(
                context = appContext,
                providerId = providerId,
                source = ProviderUsageCollectionService.SOURCE_REFRESH
            )
        }
        if (launchResult.isFailure) {
            saveProviderSnapshot(
                currentSnapshot.copy(
                    connectionState = ProviderConnectionState.ERROR,
                    refreshState = ProviderRefreshState.IDLE,
                    updatedAt = now,
                    message = launchContext.getString(R.string.provider_login_open_failed_message)
                )
            )
        }
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

    fun refreshNotificationState() {
        canPostNotifications = UsageLimitNotificationController.canPostNotifications(launchContext)
        notificationEnabled = UsageLimitNotificationController.isEnabled(appContext) && canPostNotifications
    }

    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${appContext.packageName}"))
        }
        if (launchContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchContext.startActivity(intent)
    }

    fun setNotificationEnabled(enabled: Boolean) {
        if (!enabled) {
            UsageLimitNotificationController.setEnabled(appContext, false)
            notificationEnabled = false
            return
        }

        canPostNotifications = UsageLimitNotificationController.canPostNotifications(launchContext)
        if (!canPostNotifications) {
            UsageLimitNotificationController.setEnabled(appContext, true)
            notificationEnabled = false
            openNotificationSettings()
            return
        }

        UsageLimitNotificationController.setEnabled(appContext, true)
        UsageLimitNotificationController.updateFromCache(appContext)
        notificationEnabled = true
    }

    fun applyTheme(theme: AppTheme) {
        themePreferencesRepository.saveTheme(theme)
        currentTheme = themePreferencesRepository.currentTheme()
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = launchContext.getString(
                    R.string.settings_theme_applied,
                    launchContext.getString(theme.labelResource())
                ),
                duration = SnackbarDuration.Short
            )
        }
    }

    val lifecycleOwner = remember(launchContext) { launchContext.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshSnapshots()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    DisposableEffect(localUsageRepository) {
        val listener = localUsageRepository.registerSnapshotListener {
            coroutineScope.launch {
                refreshSnapshots()
            }
        }
        onDispose {
            localUsageRepository.unregisterSnapshotListener(listener)
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

    LaunchedEffect(route) {
        if (route is AppRoute.Settings) {
            refreshNotificationState()
        }
    }

    CompositionLocalProvider(LocalAIUsageThemeColors provides themeColors) {
        MaterialTheme(colorScheme = aiUsageMaterialColorScheme(themeColors)) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = themeColors.appBackground,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                },
                topBar = {
                    AppTopBar(
                        route = route,
                        layoutMetrics = layoutMetrics,
                        onSettingsClick = { route = AppRoute.Settings }
                    )
                },
                bottomBar = {
                    AppNavigationBar(
                        route = route,
                        layoutMetrics = layoutMetrics,
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
                            onReorderProvider = ::reorderVisibleProvider,
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
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        AppRoute.Settings -> SettingsPanel(
                            notificationEnabled = notificationEnabled,
                            canPostNotifications = canPostNotifications,
                            onNotificationEnabledChanged = ::setNotificationEnabled,
                            onOpenNotificationSettings = ::openNotificationSettings,
                            hiddenProviders = hiddenProviders,
                            onShowProvider = { setProviderHidden(it, false) },
                            currentTheme = currentTheme,
                            onThemeSelected = ::applyTheme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? {
    return when (this) {
        is LifecycleOwner -> this
        is ContextWrapper -> baseContext.findLifecycleOwner()
        else -> null
    }
}

private fun aiUsageMaterialColorScheme(colors: AIUsageThemeColors) = lightColorScheme(
    primary = colors.primary,
    onPrimary = Color.White,
    primaryContainer = colors.selectedNav,
    onPrimaryContainer = colors.textPrimary,
    secondary = colors.primaryActive,
    background = colors.appBackground,
    surface = colors.panel,
    surfaceVariant = colors.unselectedNav,
    onSurface = colors.textPrimary,
    onSurfaceVariant = colors.textSecondary,
    outline = colors.border,
    outlineVariant = colors.borderSoft,
    error = Color(0xFFC64545)
)

private fun AppTheme.labelResource(): Int {
    return when (this) {
        AppTheme.MACOS -> R.string.settings_theme_macos
        AppTheme.WINDOWS -> R.string.settings_theme_windows
    }
}

@Composable
private fun AppTopBar(
    route: AppRoute,
    layoutMetrics: AppLayoutMetrics,
    onSettingsClick: () -> Unit
) {
    val colors = AIUsageTheme.colors

    Surface(
        color = colors.appBackground,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.padding(
                start = layoutMetrics.topBarHorizontalPaddingDp.dp,
                top = (layoutMetrics.topBarVerticalPaddingDp + layoutMetrics.topBarTopExtraPaddingDp).dp,
                end = layoutMetrics.topBarHorizontalPaddingDp.dp,
                bottom = (layoutMetrics.topBarVerticalPaddingDp - layoutMetrics.mainContentTopLiftDp)
                    .coerceAtLeast(0)
                    .dp
            ),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                modifier = Modifier.offset(y = layoutMetrics.topBarSettingsYOffsetDp.dp),
                onClick = onSettingsClick
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.nav_settings),
                    tint = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun AppNavigationBar(
    route: AppRoute,
    layoutMetrics: AppLayoutMetrics,
    onRouteSelected: (AppRoute) -> Unit
) {
    val colors = AIUsageTheme.colors

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            tonalElevation = 0.dp,
            color = colors.appBackground,
            modifier = Modifier
                .widthIn(max = layoutMetrics.navBarMaxWidthDp.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = layoutMetrics.navBarMinHeightDp.dp)
                    .padding(
                        PaddingValues(
                            start = layoutMetrics.navHorizontalPaddingDp.dp,
                            top = layoutMetrics.navVerticalPaddingDp.dp,
                            end = layoutMetrics.navHorizontalPaddingDp.dp,
                            bottom = (layoutMetrics.navVerticalPaddingDp + layoutMetrics.navBottomExtraPaddingDp).dp
                        )
                    ),
                horizontalArrangement = Arrangement.spacedBy(layoutMetrics.navGapDp.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RouteChip(
                    label = stringResource(R.string.nav_home),
                    selected = route is AppRoute.Home,
                    layoutMetrics = layoutMetrics,
                    onClick = { onRouteSelected(AppRoute.Home) }
                )
                ProviderId.defaultOrder().forEach { providerId ->
                    ProviderNavigationChip(
                        providerId = providerId,
                        selected = route is AppRoute.ProviderDetail && route.providerId == providerId,
                        layoutMetrics = layoutMetrics,
                        onClick = { onRouteSelected(AppRoute.ProviderDetail(providerId)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteChip(
    label: String,
    selected: Boolean,
    layoutMetrics: AppLayoutMetrics,
    onClick: () -> Unit
) {
    val colors = AIUsageTheme.colors
    val backgroundColor = if (selected) {
        colors.selectedNav
    } else {
        colors.unselectedNav
    }
    val textColor = if (selected) {
        if (colors.theme == AppTheme.MACOS) Color.White else colors.textPrimary
    } else {
        colors.textSecondary
    }

    Surface(
        modifier = Modifier
            .width(layoutMetrics.navChipWidthDp.dp)
            .height(layoutMetrics.navChipHeightDp.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = layoutMetrics.navChipVerticalPaddingDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nav_home),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProviderNavigationChip(
    providerId: ProviderId,
    selected: Boolean,
    layoutMetrics: AppLayoutMetrics,
    onClick: () -> Unit
) {
    val colors = AIUsageTheme.colors
    val backgroundColor = if (selected) {
        colors.selectedNav
    } else {
        colors.unselectedNav
    }
    val textColor = if (selected && colors.theme == AppTheme.MACOS) Color.White else colors.textSecondary

    Surface(
        modifier = Modifier
            .width(layoutMetrics.navChipWidthDp.dp)
            .height(layoutMetrics.navChipHeightDp.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = layoutMetrics.navChipVerticalPaddingDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProviderIconImage(
                providerId = providerId,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = providerNavigationLabel(providerId),
                modifier = Modifier.fillMaxWidth(),
                color = textColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun providerNavigationLabel(providerId: ProviderId): String {
    return when (providerId) {
        ProviderId.CLAUDE -> "Claude"
        ProviderId.CODEX -> "Codex"
        ProviderId.GEMINI -> "Gemini"
        ProviderId.COPILOT -> "Copilot"
        ProviderId.CURSOR -> "Cursor"
    }
}
