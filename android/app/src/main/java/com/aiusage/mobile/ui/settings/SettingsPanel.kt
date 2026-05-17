package com.aiusage.mobile.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aiusage.mobile.R
import com.aiusage.mobile.local.AppTheme
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.ui.AIUsageTheme
import com.aiusage.mobile.ui.provider.ProviderIconImage
import com.aiusage.mobile.ui.rememberAppLayoutMetrics

@Composable
fun SettingsPanel(
    notificationEnabled: Boolean,
    canPostNotifications: Boolean,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    hiddenProviders: Set<ProviderId> = emptySet(),
    onShowProvider: (ProviderId) -> Unit = {},
    currentTheme: AppTheme = AppTheme.DEFAULT,
    onThemeSelected: (AppTheme) -> Unit = {},
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
            onNotificationEnabledChanged = onNotificationEnabledChanged,
            onOpenNotificationSettings = onOpenNotificationSettings
        )
        HiddenProvidersSection(
            hiddenProviders = hiddenProviders,
            onShowProvider = onShowProvider
        )
        ThemeSettingsSection(
            currentTheme = currentTheme,
            onThemeSelected = onThemeSelected
        )
    }
}

@Composable
private fun NotificationSettingsSection(
    notificationEnabled: Boolean,
    canPostNotifications: Boolean,
    onNotificationEnabledChanged: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val layoutMetrics = rememberAppLayoutMetrics()
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
        }
    }
}

@Composable
private fun HiddenProvidersSection(
    hiddenProviders: Set<ProviderId>,
    onShowProvider: (ProviderId) -> Unit
) {
    val layoutMetrics = rememberAppLayoutMetrics()
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
                text = stringResource(R.string.settings_hidden_providers_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (hiddenProviders.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_hidden_providers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted
                )
            } else {
                hiddenProviders.sortedBy { it.displayName }.forEach { providerId ->
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
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.settings_hidden_providers_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                        Button(onClick = { onShowProvider(providerId) }) {
                            Text(stringResource(R.string.settings_restore_provider))
                        }
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
    val colors = AIUsageTheme.colors
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
    val colors = AIUsageTheme.colors

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
