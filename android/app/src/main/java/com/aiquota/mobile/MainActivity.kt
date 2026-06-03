package com.aiquota.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aiquota.mobile.notification.UsageLimitNotificationController
import com.aiquota.mobile.sync.ForegroundRefreshController
import com.aiquota.mobile.ui.AIQuotaAppShell

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseGatewayBootstrap.install()
        postCachedNotificationWhenAllowed()
        setContent {
            AIQuotaAppShell(context = this)
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
        fun createHomeIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}
