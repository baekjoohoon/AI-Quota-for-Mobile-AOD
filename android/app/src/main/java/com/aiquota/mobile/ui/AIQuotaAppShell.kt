package com.aiquota.mobile.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.window.Dialog
import com.aiquota.mobile.R
import com.aiquota.mobile.local.AppTheme
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesCodec
import com.aiquota.mobile.local.ProviderPreferencesRepository
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageSnapshot
import com.aiquota.mobile.local.ThemePreferencesRepository
import com.aiquota.mobile.notification.UsageLimitNotificationController
import com.aiquota.mobile.providers.AntigravityLoopbackOAuthActivity
import com.aiquota.mobile.providers.GeminiCliLoopbackOAuthActivity
import com.aiquota.mobile.providers.ProviderConnectorRegistry
import com.aiquota.mobile.providers.ProviderHostAllowlist
import com.aiquota.mobile.providers.ProviderBackgroundRefreshStateRepository
import com.aiquota.mobile.providers.ProviderSessionResetter
import com.aiquota.mobile.providers.UsageSurfaceRefresher
import com.aiquota.mobile.providers.WebLoginActivity
import com.aiquota.mobile.sync.ForegroundRefreshController
import com.aiquota.mobile.sync.ForegroundRefreshPolicy
import com.aiquota.mobile.sync.LiveRefreshPromptPolicy
import com.aiquota.mobile.ui.dashboard.ProviderCardOrder
import com.aiquota.mobile.ui.dashboard.UnifiedDashboardScreen
import com.aiquota.mobile.ui.provider.ProviderDetailScreen
import com.aiquota.mobile.ui.provider.ProviderIconImage
import com.aiquota.mobile.ui.settings.SettingsPanel
import com.aiquota.mobile.ui.settings.settingsLiveRefreshState
import com.aiquota.mobile.widget.DashboardPinnedWidgetType
import com.aiquota.mobile.widget.PinnedWidgetRequester
import com.aiquota.mobile.widget.WidgetPinRequestStatus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.aiquota.mobile.BuildConfig
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.aiquota.mobile.support.BugReportDiagnostics
import com.aiquota.mobile.support.BugReportEmailComposer
import com.aiquota.mobile.support.BugReportRequest

@Composable
fun AIQuotaAppShell(
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
    val connectorRegistry = remember(appContext) { ProviderConnectorRegistry.default(appContext) }
    val foregroundRefreshController = remember(appContext) { ForegroundRefreshController(appContext) }
    val refreshStateRepository = remember(appContext) {
        ProviderBackgroundRefreshStateRepository(appContext)
    }
    val providerSessionResetter = remember(appContext) { ProviderSessionResetter(appContext) }
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
    var liveMonitoringEnabled by remember {
        mutableStateOf(foregroundRefreshController.liveMonitoringEnabled())
    }
    var liveRefreshStatusNowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showLiveRefreshPrompt by remember { mutableStateOf(false) }
    var liveRefreshPromptDismissed by remember { mutableStateOf(false) }
    var showDashboardWidgetPicker by remember { mutableStateOf(false) }
    val liveRefreshState = settingsLiveRefreshState(
        notificationEnabled = liveMonitoringEnabled,
        canPostNotifications = canPostNotifications,
        heartbeatStale = refreshStateRepository.isHeartbeatStale(liveRefreshStatusNowMillis)
    )
    val themeColors = aiQuotaThemeColors(currentTheme)

    fun refreshSnapshots() {
        snapshots = localUsageRepository.readSnapshots()
    }

    fun scheduleTransientStateExpiryRefresh() {
        listOf(
            CONNECTING_STATE_MAX_MILLIS,
            REFRESH_STATE_MAX_MILLIS,
            GOOGLE_REFRESH_STATE_MAX_MILLIS
        ).distinct().forEach { timeoutMillis ->
            coroutineScope.launch {
                delay(timeoutMillis + 100L)
                refreshSnapshots()
            }
        }
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
        val now = Instant.now().toString()
        route = AppRoute.ProviderDetail(providerId)
        val currentSnapshot = snapshots
            .firstOrNull { it.providerId == providerId }
            ?: ProviderUsageSnapshot.disconnected(providerId)
        val loginStartUrl = connector.startUrl

        if (providerId != ProviderId.GEMINI &&
            loginStartUrl.isNotBlank() &&
            !ProviderHostAllowlist.isAllowed(providerId, loginStartUrl)
        ) {
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

        saveProviderSnapshot(
            currentSnapshot.copy(
                connectionState = ProviderConnectionState.CONNECTING,
                refreshState = ProviderRefreshState.REFRESHING,
                updatedAt = now,
                message = launchContext.getString(R.string.provider_login_opened_message)
            )
        )
        scheduleTransientStateExpiryRefresh()

        if (providerId == ProviderId.GEMINI || providerId == ProviderId.ANTIGRAVITY) {
            val launchResult = runCatching {
                val intent = if (providerId == ProviderId.GEMINI) {
                    GeminiCliLoopbackOAuthActivity.createIntent(launchContext)
                } else {
                    AntigravityLoopbackOAuthActivity.createIntent(launchContext)
                }
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
            return
        }

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

    fun disconnectProvider(providerId: ProviderId) {
        busyProvider = providerId
        coroutineScope.launch {
            runCatching { connectorRegistry.connectorFor(providerId).disconnect() }
            providerSessionResetter.disconnectAndWait(providerId)
            localUsageRepository.removeProviderSnapshot(providerId)
            refreshSnapshots()
            busyProvider = null
        }
    }

    fun disconnectAllProviders() {
        val providerIds = ProviderPreferencesCodec.visibleProviders(providerOrder, emptySet())
            .filter { providerId ->
                val snapshot = snapshots.firstOrNull { it.providerId == providerId }
                snapshot != null && snapshot.connectionState !in setOf(
                    ProviderConnectionState.DISCONNECTED,
                    ProviderConnectionState.NOT_CONNECTED
                )
            }
        if (providerIds.isEmpty()) return
        busyProvider = null
        coroutineScope.launch {
            providerIds.forEach { providerId ->
                runCatching { connectorRegistry.connectorFor(providerId).disconnect() }
                providerSessionResetter.disconnectAndWait(providerId)
                localUsageRepository.removeProviderSnapshot(providerId)
            }
            refreshSnapshots()
            UsageSurfaceRefresher.refresh(appContext, localUsageRepository)
        }
    }

    fun refreshNotificationState() {
        canPostNotifications = UsageLimitNotificationController.canPostNotifications(launchContext)
        notificationEnabled = UsageLimitNotificationController.isEnabled(appContext) && canPostNotifications
        liveMonitoringEnabled = foregroundRefreshController.liveMonitoringEnabled()
        liveRefreshStatusNowMillis = System.currentTimeMillis()
    }

    fun enableLiveMonitoringWhenAllowed(): Boolean {
        canPostNotifications = UsageLimitNotificationController.canPostNotifications(launchContext)
        if (!canPostNotifications) return false
        UsageLimitNotificationController.setEnabled(appContext, true)
        UsageLimitNotificationController.updateFromCache(appContext)
        foregroundRefreshController.setLiveMonitoringEnabled(true)
        liveMonitoringEnabled = true
        notificationEnabled = true
        showLiveRefreshPrompt = false
        liveRefreshPromptDismissed = false
        liveRefreshStatusNowMillis = System.currentTimeMillis()
        return true
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        canPostNotifications = granted || UsageLimitNotificationController.canPostNotifications(launchContext)
        if (canPostNotifications) {
            enableLiveMonitoringWhenAllowed()
        } else {
            notificationEnabled = false
            liveMonitoringEnabled = foregroundRefreshController.liveMonitoringEnabled()
            showLiveRefreshPrompt = true
        }
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

    fun requestLiveMonitoringFromPrompt() {
        UsageLimitNotificationController.setEnabled(appContext, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications) {
            UsageLimitNotificationController.markNotificationPermissionRequested(appContext)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        enableLiveMonitoringWhenAllowed()
    }

    fun setNotificationEnabled(enabled: Boolean) {
        if (!enabled) {
            UsageLimitNotificationController.setEnabled(appContext, false)
            foregroundRefreshController.setLiveMonitoringEnabled(false)
            liveMonitoringEnabled = false
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

        enableLiveMonitoringWhenAllowed()
    }

    fun showWidgetPinFeedback(status: WidgetPinRequestStatus) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                message = launchContext.getString(status.messageRes()),
                duration = SnackbarDuration.Short
            )
        }
    }

    fun requestProviderWidget(providerId: ProviderId) {
        showWidgetPinFeedback(PinnedWidgetRequester.requestProviderWidget(launchContext, providerId))
    }

    fun requestDashboardWidget(type: DashboardPinnedWidgetType) {
        showDashboardWidgetPicker = false
        showWidgetPinFeedback(PinnedWidgetRequester.requestDashboardWidget(launchContext, type))
    }

    fun reportBug(request: BugReportRequest) {
        val email = BugReportEmailComposer.compose(
            request = request,
            diagnostics = BugReportDiagnostics(
                appVersionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                buildType = BuildConfig.BUILD_TYPE,
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                localeTag = Locale.getDefault().toLanguageTag(),
                liveMonitoringEnabled = liveMonitoringEnabled,
                canPostNotifications = canPostNotifications,
                notificationEnabled = UsageLimitNotificationController.isEnabled(appContext),
                liveRefreshState = liveRefreshState.name,
                snapshots = snapshots
            )
        )
        val intent = Intent(Intent.ACTION_SENDTO)
            .setData(Uri.parse(BugReportEmailComposer.mailtoUri(email)))
            .putExtra(Intent.EXTRA_SUBJECT, email.subject)
            .putExtra(Intent.EXTRA_TEXT, email.body)
        if (launchContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching { launchContext.startActivity(intent) }.isSuccess
        if (!opened) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = launchContext.getString(R.string.settings_bug_report_no_email_app),
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun applyTheme(theme: AppTheme) {
        themePreferencesRepository.saveTheme(theme)
        currentTheme = themePreferencesRepository.currentTheme()
        UsageSurfaceRefresher.refresh(appContext, localUsageRepository)
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
                    canPostNotifications = UsageLimitNotificationController.canPostNotifications(launchContext)
                    liveMonitoringEnabled = foregroundRefreshController.liveMonitoringEnabled()
                    liveRefreshStatusNowMillis = System.currentTimeMillis()
                    if (
                        ForegroundRefreshPolicy.shouldRunForegroundLoop(
                            snapshots = localUsageRepository.readSnapshots(),
                            liveMonitoringEnabled = liveMonitoringEnabled,
                            canPostNotifications = canPostNotifications
                        ) &&
                        refreshStateRepository.isHeartbeatStale()
                    ) {
                        runCatching { foregroundRefreshController.startPreciseRefresh() }
                    }
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

    LaunchedEffect(route, liveMonitoringEnabled, canPostNotifications) {
        if (route != AppRoute.Settings) return@LaunchedEffect
        while (true) {
            liveRefreshStatusNowMillis = System.currentTimeMillis()
            delay(LIVE_REFRESH_STATUS_POLL_MILLIS)
        }
    }

    LaunchedEffect(providerOrder, hiddenProviders, snapshots, currentTheme) {
        UsageSurfaceRefresher.refresh(
            context = appContext,
            repository = localUsageRepository,
            order = providerOrder,
            hidden = hiddenProviders
        )
    }

    LaunchedEffect(snapshots) {
        while (snapshots.any { it.refreshState == ProviderRefreshState.REFRESHING }) {
            delay(TRANSIENT_STATE_POLL_MILLIS)
            refreshSnapshots()
        }
    }

    LaunchedEffect(snapshots, liveMonitoringEnabled, canPostNotifications) {
        if (
            ForegroundRefreshPolicy.shouldRunForegroundLoop(
                snapshots = snapshots,
                liveMonitoringEnabled = liveMonitoringEnabled,
                canPostNotifications = canPostNotifications
            )
        ) {
            runCatching { foregroundRefreshController.startPreciseRefresh() }
        } else {
            foregroundRefreshController.stopPreciseRefresh()
        }
    }

    LaunchedEffect(snapshots, liveMonitoringEnabled, canPostNotifications, liveRefreshPromptDismissed) {
        val shouldShowPrompt = LiveRefreshPromptPolicy.shouldShowOnAppEntry(
            snapshots = snapshots,
            liveMonitoringEnabled = liveMonitoringEnabled,
            canPostNotifications = canPostNotifications
        )
        if (!shouldShowPrompt) {
            showLiveRefreshPrompt = false
            liveRefreshPromptDismissed = false
        } else if (!liveRefreshPromptDismissed) {
            showLiveRefreshPrompt = true
        }
    }

    LaunchedEffect(route) {
        if (route is AppRoute.Settings) {
            refreshNotificationState()
        }
    }

    CompositionLocalProvider(LocalAIQuotaThemeColors provides themeColors) {
        MaterialTheme(colorScheme = aiQuotaMaterialColorScheme(themeColors)) {
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
                        providerOrder = providerOrder,
                        hiddenProviders = hiddenProviders,
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
                            onAddWidget = { showDashboardWidgetPicker = true },
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
                                onDisconnect = { disconnectProvider(currentRoute.providerId) },
                                onAddWidget = { requestProviderWidget(currentRoute.providerId) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        AppRoute.Settings -> SettingsPanel(
                            notificationEnabled = liveMonitoringEnabled,
                            canPostNotifications = canPostNotifications,
                            liveRefreshState = liveRefreshState,
                            onNotificationEnabledChanged = ::setNotificationEnabled,
                            onOpenNotificationSettings = ::openNotificationSettings,
                            providerOrder = providerOrder,
                            snapshots = snapshots,
                            onConnectProvider = ::connectProvider,
                            onDisconnectProvider = ::disconnectProvider,
                            onDisconnectAllProviders = ::disconnectAllProviders,
                            currentTheme = currentTheme,
                            onThemeSelected = ::applyTheme,
                            onReportBug = ::reportBug,
                            modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (showLiveRefreshPrompt) {
                        LiveRefreshPermissionDialog(
                            canPostNotifications = canPostNotifications,
                            onEnable = ::requestLiveMonitoringFromPrompt,
                            onOpenSettings = {
                                UsageLimitNotificationController.markNotificationPermissionRequested(appContext)
                                openNotificationSettings()
                            },
                            onDismiss = {
                                showLiveRefreshPrompt = false
                                liveRefreshPromptDismissed = true
                            }
                        )
                    }
                    if (showDashboardWidgetPicker) {
                        DashboardWidgetPickerDialog(
                            onSelect = ::requestDashboardWidget,
                            onDismiss = { showDashboardWidgetPicker = false }
                        )
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

private fun aiQuotaMaterialColorScheme(colors: AIQuotaThemeColors) = lightColorScheme(
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

private fun WidgetPinRequestStatus.messageRes(): Int {
    return when (this) {
        WidgetPinRequestStatus.REQUESTED -> R.string.widget_pin_requested
        WidgetPinRequestStatus.UNSUPPORTED -> R.string.widget_pin_not_supported
        WidgetPinRequestStatus.FAILED -> R.string.widget_pin_failed
    }
}

@Composable
private fun DashboardWidgetPickerDialog(
    onSelect: (DashboardPinnedWidgetType) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AIQuotaTheme.colors
    val isMac = colors.theme == AppTheme.MACOS

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(if (isMac) 16.dp else 2.dp),
            color = colors.panel,
            border = BorderStroke(if (isMac) 1.dp else 2.dp, colors.border),
            shadowElevation = if (isMac) 12.dp else 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.dashboard_widget_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = { onSelect(DashboardPinnedWidgetType.DASHBOARD) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dashboard_widget_picker_dashboard))
                }
                OutlinedButton(
                    onClick = { onSelect(DashboardPinnedWidgetType.BATTERY) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.dashboard_widget_picker_battery))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_close))
                }
            }
        }
    }
}

@Composable
private fun LiveRefreshPermissionDialog(
    canPostNotifications: Boolean,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AIQuotaTheme.colors
    val isMac = colors.theme == AppTheme.MACOS

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(if (isMac) 16.dp else 2.dp),
            color = colors.panel,
            border = BorderStroke(if (isMac) 1.dp else 2.dp, colors.border),
            shadowElevation = if (isMac) 12.dp else 2.dp
        ) {
            Column {
                if (isMac) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = colors.trafficRed,
                            content = {}
                        )
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = colors.trafficYellow,
                            content = {}
                        )
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = colors.trafficGreen,
                            content = {}
                        )
                        Text(
                            text = stringResource(R.string.live_refresh_prompt_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.titleText,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.titleBar,
                        tonalElevation = 0.dp
                    ) {
                        Text(
                            text = stringResource(R.string.live_refresh_prompt_title),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.titleText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.live_refresh_prompt_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Button(
                        onClick = onEnable,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(
                                if (canPostNotifications) {
                                    R.string.live_refresh_prompt_enable
                                } else {
                                    R.string.live_refresh_prompt_allow_notifications
                                }
                            )
                        )
                    }
                    if (!canPostNotifications) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.live_refresh_prompt_open_settings))
                        }
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.live_refresh_prompt_later))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(
    route: AppRoute,
    layoutMetrics: AppLayoutMetrics,
    onSettingsClick: () -> Unit
) {
    val colors = AIQuotaTheme.colors

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
    providerOrder: List<ProviderId>,
    hiddenProviders: Set<ProviderId>,
    layoutMetrics: AppLayoutMetrics,
    onRouteSelected: (AppRoute) -> Unit
) {
    val colors = AIQuotaTheme.colors

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
                    .horizontalScroll(rememberScrollState())
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
                navigationProviderOrder(providerOrder, hiddenProviders).forEach { providerId ->
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
    val colors = AIQuotaTheme.colors
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
                maxLines = 2,
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
    val colors = AIQuotaTheme.colors
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
                maxLines = 2,
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
        ProviderId.ANTIGRAVITY -> "Anti\nGravity"
        ProviderId.CURSOR -> "Cursor"
    }
}

internal fun navigationProviderOrder(
    providerOrder: List<ProviderId>,
    hiddenProviders: Set<ProviderId>
): List<ProviderId> {
    return ProviderPreferencesCodec.visibleProviders(providerOrder, hiddenProviders)
}

private const val CONNECTING_STATE_MAX_MILLIS = 3_000L
private const val REFRESH_STATE_MAX_MILLIS = 30_000L
private const val GOOGLE_REFRESH_STATE_MAX_MILLIS = 90_000L
private const val TRANSIENT_STATE_POLL_MILLIS = 5_000L
private const val LIVE_REFRESH_STATUS_POLL_MILLIS = 10_000L
