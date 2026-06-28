package com.aiquota.mobile.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aiquota.mobile.R
import com.aiquota.mobile.update.AppUpdateStoreNavigator

object AppUpdateNotificationController {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 1003

    @SuppressLint("MissingPermission")
    fun notifyUpdateAvailable(context: Context) {
        if (!UsageLimitNotificationController.canPostNotifications(context)) return

        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ai_quota)
            .setContentTitle(context.getString(R.string.app_update_notification_title))
            .setContentText(context.getString(R.string.app_update_notification_body))
            .setContentIntent(playStoreIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.app_update_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun playStoreIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            AppUpdateStoreNavigator.storeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
