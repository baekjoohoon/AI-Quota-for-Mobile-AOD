package com.aiquota.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.aiquota.mobile.notification.UsageLimitNotificationController
import com.aiquota.mobile.sync.ForegroundRefreshController
import com.aiquota.mobile.ui.AIQuotaAppShell
import com.aiquota.mobile.update.AppUpdateCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private lateinit var appUpdateCoordinator: AppUpdateCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseGatewayBootstrap.install()
        appUpdateCoordinator = AppUpdateCoordinator(this)
        postCachedNotificationWhenAllowed()
        setContent {
            var showUpdatePrompt by remember { mutableStateOf(false) }
            DisposableEffect(appUpdateCoordinator) {
                appUpdateCoordinator.onUpdateAvailable = {
                    showUpdatePrompt = true
                    postCachedNotificationWhenAllowed()
                }
                onDispose {
                    appUpdateCoordinator.onUpdateAvailable = {}
                }
            }
            AIQuotaAppShell(context = this)
            if (showUpdatePrompt) {
                AppUpdatePromptDialog(
                    onUpdate = {
                        showUpdatePrompt = false
                        appUpdateCoordinator.openStoreListing()
                    },
                    onDismiss = { showUpdatePrompt = false }
                )
            }
        }
        lifecycleScope.launch {
            delay(APP_UPDATE_CHECK_STARTUP_DELAY_MS)
            appUpdateCoordinator.checkForStoreUpdate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun postCachedNotificationWhenAllowed() {
        if (
            UsageLimitNotificationController.canPostNotifications(this) &&
            ForegroundRefreshController(this).liveMonitoringEnabled()
        ) {
            UsageLimitNotificationController.updateFromCache(this)
        } else {
            UsageLimitNotificationController.cancel(this)
        }
    }

    companion object {
        private const val APP_UPDATE_CHECK_STARTUP_DELAY_MS = 5_000L

        fun createHomeIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}

@Composable
private fun AppUpdatePromptDialog(
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_update_prompt_title)) },
        text = { Text(stringResource(R.string.app_update_prompt_body)) },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.app_update_prompt_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_update_prompt_later))
            }
        }
    )
}
