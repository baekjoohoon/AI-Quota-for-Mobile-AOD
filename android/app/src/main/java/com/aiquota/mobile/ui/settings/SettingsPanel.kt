package com.aiquota.mobile.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aiquota.mobile.BuildConfig
import com.aiquota.mobile.R
import com.aiquota.mobile.local.AppTheme
import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesCodec
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageSnapshot
import com.aiquota.mobile.support.BugReportCategory
import com.aiquota.mobile.support.BugReportRequest
import com.aiquota.mobile.ui.AIQuotaTheme
import com.aiquota.mobile.ui.compactProviderLineBreakStyle
import com.aiquota.mobile.ui.provider.ProviderIconImage
import com.aiquota.mobile.ui.rememberAppLayoutMetrics

@Composable
fun SettingsPanel(
    notificationEnabled: Boolean,
    canPostNotifications: Boolean,
    liveRefreshState: SettingsLiveRefreshState = SettingsLiveRefreshState.STOPPED,
    batteryOptimizationExempt: Boolean = true,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit = {},
    providerOrder: List<ProviderId> = ProviderId.defaultOrder(),
    snapshots: List<ProviderUsageSnapshot> = emptyList(),
    onConnectProvider: (ProviderId) -> Unit = {},
    onDisconnectProvider: (ProviderId) -> Unit = {},
    onDisconnectAllProviders: () -> Unit = {},
    currentTheme: AppTheme = AppTheme.DEFAULT,
    onThemeSelected: (AppTheme) -> Unit = {},
    onReportBug: (BugReportRequest) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val layoutMetrics = rememberAppLayoutMetrics()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = layoutMetrics.contentHorizontalPaddingDp.dp,
                vertical = layoutMetrics.contentVerticalPaddingDp.dp
            ),
        verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacingDp.dp)
    ) {
        NotificationSettingsSection(
            notificationEnabled = notificationEnabled,
            canPostNotifications = canPostNotifications,
            liveRefreshState = liveRefreshState,
            batteryOptimizationExempt = batteryOptimizationExempt,
            onNotificationEnabledChanged = onNotificationEnabledChanged,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings
        )
        ThemeSettingsSection(
            currentTheme = currentTheme,
            onThemeSelected = onThemeSelected
        )
        ConnectionManagementSection(
            providerOrder = providerOrder,
            snapshots = snapshots,
            onConnectProvider = onConnectProvider,
            onDisconnectProvider = onDisconnectProvider,
            onDisconnectAllProviders = onDisconnectAllProviders
        )
        SupportSettingsSection(
            onReportBug = onReportBug
        )
    }
}

@Composable
private fun NotificationSettingsSection(
    notificationEnabled: Boolean,
    canPostNotifications: Boolean,
    liveRefreshState: SettingsLiveRefreshState,
    batteryOptimizationExempt: Boolean,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit
) {
    val layoutMetrics = rememberAppLayoutMetrics()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_status_bar_gauges),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.settings_status_bar_gauges_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
                Switch(
                    checked = notificationEnabled,
                    onCheckedChange = onNotificationEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.panel,
                        checkedTrackColor = colors.primary,
                        checkedBorderColor = colors.primary,
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.progressTrack,
                        uncheckedBorderColor = colors.border,
                        disabledCheckedThumbColor = colors.panel.copy(alpha = 0.72f),
                        disabledCheckedTrackColor = colors.primary.copy(alpha = 0.42f),
                        disabledCheckedBorderColor = colors.primary.copy(alpha = 0.48f),
                        disabledUncheckedThumbColor = colors.textMuted.copy(alpha = 0.78f),
                        disabledUncheckedTrackColor = colors.progressTrack,
                        disabledUncheckedBorderColor = colors.border
                    )
                )
            }
            Text(
                text = stringResource(
                    R.string.settings_live_refresh_status,
                    stringResource(settingsLiveRefreshStateLabel(liveRefreshState))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            if (!canPostNotifications) {
                Text(
                    text = stringResource(R.string.settings_notifications_permission_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedButton(onClick = onOpenNotificationSettings) {
                    Text(stringResource(R.string.settings_open_notification_settings))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_battery_optimization_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
                if (!batteryOptimizationExempt) {
                    OutlinedButton(onClick = onOpenBatteryOptimizationSettings) {
                        Text(stringResource(R.string.settings_open_battery_optimization_settings))
                    }
                }
            }
            Text(
                text = stringResource(
                    if (batteryOptimizationExempt) {
                        R.string.settings_battery_optimization_exempt
                    } else {
                        R.string.settings_battery_optimization_recommended
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (batteryOptimizationExempt) colors.textMuted else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ConnectionManagementSection(
    providerOrder: List<ProviderId>,
    snapshots: List<ProviderUsageSnapshot>,
    onConnectProvider: (ProviderId) -> Unit,
    onDisconnectProvider: (ProviderId) -> Unit,
    onDisconnectAllProviders: () -> Unit
) {
    val layoutMetrics = rememberAppLayoutMetrics()
    val colors = AIQuotaTheme.colors
    val snapshotsByProvider = snapshots.associateBy { it.providerId }
    val orderedProviders = settingsConnectionProviderOrder(providerOrder)
    val hasDisconnectableProvider = orderedProviders.any { providerId ->
        settingsConnectionDisconnectable(snapshotsByProvider[providerId])
    }

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
                text = stringResource(R.string.settings_connection_management_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_connection_management_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            Button(
                onClick = onDisconnectAllProviders,
                enabled = hasDisconnectableProvider,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_disconnect_all_providers))
            }
            orderedProviders.forEach { providerId ->
                val snapshot = snapshotsByProvider[providerId]
                val action = settingsConnectionAction(providerId, snapshot)
                val actionTextColor = settingsConnectionActionTextColor(
                    action = action,
                    connectColor = colors.primary,
                    disconnectColor = colors.textMuted,
                    disabledColor = colors.textMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProviderIconImage(
                        providerId = providerId,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = providerId.displayName,
                            style = compactProviderLineBreakStyle(providerId, MaterialTheme.typography.bodyLarge),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(settingsConnectionStatusLabel(providerId, snapshot)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    OutlinedButton(
                        enabled = action != SettingsConnectionAction.NONE,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = actionTextColor
                        ),
                        onClick = {
                            when (action) {
                                SettingsConnectionAction.CONNECT -> onConnectProvider(providerId)
                                SettingsConnectionAction.DISCONNECT -> onDisconnectProvider(providerId)
                                SettingsConnectionAction.NONE -> Unit
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                when (action) {
                                    SettingsConnectionAction.CONNECT -> R.string.provider_connect
                                    SettingsConnectionAction.DISCONNECT -> R.string.provider_disconnect
                                    SettingsConnectionAction.NONE -> R.string.provider_action_coming_soon
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSettingsSection(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    val layoutMetrics = rememberAppLayoutMetrics()
    val colors = AIQuotaTheme.colors
    val currentThemeLabel = stringResource(themeLabelResource(currentTheme))
    var showThemeOptions by rememberSaveable { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_theme_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.settings_theme_body, currentThemeLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted
                    )
                }
                OutlinedButton(onClick = { showThemeOptions = !showThemeOptions }) {
                    Text(stringResource(R.string.settings_theme_button))
                }
            }
        }
    }

    if (showThemeOptions) {
        ThemePickerDialog(
            currentTheme = currentTheme,
            onThemeSelected = {
                onThemeSelected(it)
                showThemeOptions = false
            },
            onDismissRequest = {
                showThemeOptions = false
            }
        )
    }
}

@Composable
private fun ThemePickerDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismissRequest: () -> Unit
) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    val colors = AIQuotaTheme.colors

    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(animationSpec = tween(durationMillis = 150)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(durationMillis = 150)
                )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.panel,
                border = BorderStroke(1.dp, colors.borderSoft),
                shadowElevation = 8.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.titleBar)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_theme_picker_title),
                            color = colors.titleText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.settings_theme_body,
                                stringResource(themeLabelResource(currentTheme))
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                        Button(
                            onClick = { onThemeSelected(AppTheme.MACOS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_theme_macos))
                        }
                        Button(
                            onClick = { onThemeSelected(AppTheme.WINDOWS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_theme_windows))
                        }
                    }
                }
            }
        }
    }
}

@StringRes
private fun themeLabelResource(theme: AppTheme): Int {
    return when (theme) {
        AppTheme.MACOS -> R.string.settings_theme_macos
        AppTheme.WINDOWS -> R.string.settings_theme_windows
    }
}

@Composable
private fun SupportSettingsSection(
    onReportBug: (BugReportRequest) -> Unit
) {
    val layoutMetrics = rememberAppLayoutMetrics()
    val colors = AIQuotaTheme.colors
    var showBugReportDialog by rememberSaveable { mutableStateOf(false) }

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
                text = stringResource(R.string.settings_support_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_support_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            Text(
                text = "버전 : ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            OutlinedButton(
                onClick = { showBugReportDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_bug_report_button))
            }
        }
    }

    if (showBugReportDialog) {
        BugReportDialog(
            onSubmit = {
                showBugReportDialog = false
                onReportBug(it)
            },
            onDismiss = { showBugReportDialog = false }
        )
    }
}

@Composable
private fun BugReportDialog(
    onSubmit: (BugReportRequest) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AIQuotaTheme.colors
    var category by rememberSaveable { mutableStateOf(BugReportCategory.LOGIN) }
    var providerId by rememberSaveable { mutableStateOf<ProviderId?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var includeDiagnostics by rememberSaveable { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.panel,
            border = BorderStroke(1.dp, colors.borderSoft),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_bug_report_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    text = stringResource(R.string.settings_bug_report_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted
                )
                Text(
                    text = stringResource(R.string.settings_bug_report_category),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BugReportCategory.entries.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { option ->
                                BugReportChoiceButton(
                                    selected = category == option,
                                    label = stringResource(bugReportCategoryLabel(option)),
                                    onClick = { category = option },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Column(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_bug_report_provider),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val providerOptions = listOf<ProviderId?>(null) + ProviderId.defaultOrder()
                    providerOptions.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { option ->
                                BugReportChoiceButton(
                                    selected = providerId == option,
                                    label = option?.displayName ?: stringResource(R.string.settings_bug_report_provider_all),
                                    onClick = { providerId = option },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Column(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    label = { Text(stringResource(R.string.settings_bug_report_description)) },
                    placeholder = { Text(stringResource(R.string.settings_bug_report_description_hint)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_bug_report_include_diagnostics),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.settings_bug_report_privacy_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Switch(
                        checked = includeDiagnostics,
                        onCheckedChange = { includeDiagnostics = it }
                    )
                }
                Button(
                    onClick = {
                        onSubmit(
                            BugReportRequest(
                                category = category,
                                providerId = providerId,
                                description = description,
                                includeDiagnostics = includeDiagnostics
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_bug_report_send))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_bug_report_cancel))
                }
            }
        }
    }
}

@Composable
private fun BugReportChoiceButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@StringRes
private fun bugReportCategoryLabel(category: BugReportCategory): Int {
    return when (category) {
        BugReportCategory.LOGIN -> R.string.settings_bug_report_category_login
        BugReportCategory.USAGE_COLLECTION -> R.string.settings_bug_report_category_usage
        BugReportCategory.WIDGET -> R.string.settings_bug_report_category_widget
        BugReportCategory.LIVE_REFRESH -> R.string.settings_bug_report_category_live_refresh
        BugReportCategory.OTHER -> R.string.settings_bug_report_category_other
    }
}

enum class SettingsLiveRefreshState {
    RUNNING,
    STOPPED
}

internal fun settingsLiveRefreshState(
    notificationEnabled: Boolean,
    canPostNotifications: Boolean,
    heartbeatStale: Boolean
): SettingsLiveRefreshState {
    return if (notificationEnabled && canPostNotifications && !heartbeatStale) {
        SettingsLiveRefreshState.RUNNING
    } else {
        SettingsLiveRefreshState.STOPPED
    }
}

@StringRes
private fun settingsLiveRefreshStateLabel(state: SettingsLiveRefreshState): Int {
    return when (state) {
        SettingsLiveRefreshState.RUNNING -> R.string.settings_live_refresh_status_running
        SettingsLiveRefreshState.STOPPED -> R.string.settings_live_refresh_status_stopped
    }
}

internal fun settingsConnectionProviderOrder(providerOrder: List<ProviderId>): List<ProviderId> {
    return ProviderPreferencesCodec.visibleProviders(providerOrder, emptySet())
}

internal fun settingsConnectionDisconnectable(snapshot: ProviderUsageSnapshot?): Boolean {
    return settingsConnectionAction(snapshot) == SettingsConnectionAction.DISCONNECT
}

internal fun settingsConnectionAction(providerId: ProviderId, snapshot: ProviderUsageSnapshot?): SettingsConnectionAction {
    return settingsConnectionAction(snapshot)
}

internal fun settingsConnectionAction(snapshot: ProviderUsageSnapshot?): SettingsConnectionAction {
    if (snapshot == null) return SettingsConnectionAction.CONNECT
    return if (snapshot.connectionState in setOf(
        ProviderConnectionState.DISCONNECTED,
        ProviderConnectionState.NOT_CONNECTED
    )) {
        SettingsConnectionAction.CONNECT
    } else {
        SettingsConnectionAction.DISCONNECT
    }
}

internal fun settingsConnectionActionTextColor(
    action: SettingsConnectionAction,
    connectColor: Color,
    disconnectColor: Color,
    disabledColor: Color = disconnectColor
): Color {
    return when (action) {
        SettingsConnectionAction.CONNECT -> connectColor
        SettingsConnectionAction.DISCONNECT -> disconnectColor
        SettingsConnectionAction.NONE -> disabledColor
    }
}

internal enum class SettingsConnectionAction {
    CONNECT,
    DISCONNECT,
    NONE
}

@StringRes
private fun settingsConnectionStatusLabel(providerId: ProviderId, snapshot: ProviderUsageSnapshot?): Int {
    return settingsConnectionStatusLabel(snapshot)
}

@StringRes
private fun settingsConnectionStatusLabel(snapshot: ProviderUsageSnapshot?): Int {
    val state = snapshot?.connectionState ?: ProviderConnectionState.DISCONNECTED
    return when (state) {
        ProviderConnectionState.DISCONNECTED,
        ProviderConnectionState.NOT_CONNECTED -> R.string.provider_status_disconnected
        ProviderConnectionState.CONNECTING -> R.string.provider_status_connecting
        ProviderConnectionState.CONNECTED -> R.string.provider_status_connected
        ProviderConnectionState.COLLECTING -> R.string.provider_status_collecting
        ProviderConnectionState.STALE -> R.string.provider_status_stale
        ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> R.string.provider_status_auth_required
        ProviderConnectionState.UNAVAILABLE -> R.string.provider_unavailable
        ProviderConnectionState.ERROR -> R.string.provider_status_error
    }
}
