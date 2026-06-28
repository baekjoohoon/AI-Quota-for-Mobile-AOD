package com.aiquota.mobile.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aiquota.mobile.MainActivity
import com.aiquota.mobile.R
import com.aiquota.mobile.local.ProviderGaugeColor
import com.aiquota.mobile.update.AppUpdateStateStore
import com.aiquota.mobile.ui.provider.providerIconRes as sharedProviderIconRes
import com.aiquota.mobile.widget.WidgetSnapshotCache

object UsageLimitNotificationController {
    private const val CHANNEL_ID = "usage_limits"
    private const val LIVE_REFRESH_ISSUE_CHANNEL_ID = "live_refresh_health"
    const val NOTIFICATION_ID = 1001
    const val LIVE_REFRESH_ISSUE_NOTIFICATION_ID = 1002
    private const val PREFS = "ai_quota_notifications"
    private const val KEY_ENABLED = "status_bar_summary_enabled"
    private const val KEY_PERMISSION_REQUESTED = "post_notifications_permission_requested"
    private const val NOTIFICATION_COMPACT_FIRST_ROW_ITEM_COUNT = 2
    private const val NOTIFICATION_COMPACT_ITEM_COUNT = 4

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        if (!enabled) cancel(context)
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun shouldRequestNotificationPermissionOnLaunch(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (canPostNotifications(context)) return false
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSION_REQUESTED, false)
    }

    fun markNotificationPermissionRequested(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSION_REQUESTED, true)
            .apply()
    }

    fun updateFromCache(context: Context) {
        update(context, WidgetSnapshotCache(context).readState().snapshotJson)
    }

    fun foregroundNotification(context: Context): Notification {
        val snapshotJson = WidgetSnapshotCache(context).readState().snapshotJson
        val content = buildUsageNotificationContentForContext(context, snapshotJson)
        createChannel(context)
        return buildNotification(context, content)
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context, snapshotJson: String) {
        if (!isEnabled(context)) return
        if (!canPostNotifications(context)) return

        val content = buildUsageNotificationContentForContext(context, snapshotJson)
        createChannel(context)
        val notification = buildNotification(context, content)

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    fun notifyLiveRefreshIssue(context: Context) {
        if (!canPostNotifications(context)) return

        createLiveRefreshIssueChannel(context)
        val notification = NotificationCompat.Builder(context, LIVE_REFRESH_ISSUE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ai_quota)
            .setContentTitle(context.getString(R.string.live_refresh_issue_title))
            .setContentText(context.getString(R.string.live_refresh_issue_body))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(LIVE_REFRESH_ISSUE_NOTIFICATION_ID, notification)
        }
    }

    fun cancelLiveRefreshIssue(context: Context) {
        NotificationManagerCompat.from(context).cancel(LIVE_REFRESH_ISSUE_NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Usage limits",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Pinned AI quota limit gauges"
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createLiveRefreshIssueChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            LIVE_REFRESH_ISSUE_CHANNEL_ID,
            "Live monitoring alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when live monitoring may have stopped"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(context: Context, content: UsageNotificationContent): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ai_quota)
            .setContentTitle(content.compactTitle)
            .setContentText(content.summary)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compactRemoteViews(context, content))
            .setCustomBigContentView(remoteViews(context, content))
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = MainActivity.createHomeIntent(context)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun settingsIntent(context: Context): PendingIntent {
        val intent = MainActivity.createSettingsIntent(context)
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun remoteViews(context: Context, content: UsageNotificationContent): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_usage_gauges)
        views.setOnClickPendingIntent(R.id.notification_settings_button, settingsIntent(context))
        val rows = rowIds()
        content.gaugeRows.forEachIndexed { index, gauge ->
            val row = rows[index]
            views.setViewVisibility(row.containerId, View.VISIBLE)
            views.setImageViewResource(row.iconId, providerIconRes(gauge.providerId))
            applyProgressColor(views, row.progressId, gauge.gaugeColorHex)
            views.setProgressBar(row.progressId, 100, (gauge.remainingRatio * 100).toInt().coerceIn(0, 100), false)
            views.setTextViewText(row.remainingTextId, gauge.remainingText)
            views.setTextViewText(row.resetTextId, gauge.resetText)
        }
        for (index in content.gaugeRows.size until rows.size) {
            views.setViewVisibility(rows[index].containerId, View.GONE)
        }
        if (content.updateMessage == null) {
            views.setViewVisibility(R.id.notification_update_status, View.GONE)
        } else {
            views.setViewVisibility(R.id.notification_update_status, View.VISIBLE)
            views.setTextViewText(R.id.notification_update_status, content.updateMessage)
        }
        return views
    }

    private fun compactRemoteViews(context: Context, content: UsageNotificationContent): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_usage_compact)
        val items = compactItemIds()
        val compactRows = content.gaugeRows.take(NOTIFICATION_COMPACT_ITEM_COUNT)
        if (content.gaugeRows.isEmpty()) {
            views.setViewVisibility(R.id.notification_compact_summary, View.VISIBLE)
            views.setViewVisibility(R.id.notification_compact_grid, View.GONE)
            views.setTextViewText(R.id.notification_compact_summary, content.summary)
            return views
        }

        views.setViewVisibility(R.id.notification_compact_summary, View.GONE)
        views.setViewVisibility(R.id.notification_compact_grid, View.VISIBLE)
        views.setViewVisibility(R.id.notification_compact_row_0, View.VISIBLE)
        views.setViewVisibility(
            R.id.notification_compact_row_1,
            if (compactRows.size > NOTIFICATION_COMPACT_FIRST_ROW_ITEM_COUNT) View.VISIBLE else View.GONE
        )
        compactRows.forEachIndexed { index, gauge ->
            val item = items[index]
            views.setViewVisibility(item.containerId, View.VISIBLE)
            views.setImageViewResource(item.iconId, providerIconRes(gauge.providerId))
            views.setTextViewText(item.textId, gauge.compactRemainingText)
            applyProgressColor(views, item.progressId, gauge.gaugeColorHex)
            views.setProgressBar(item.progressId, 100, (gauge.remainingRatio * 100).toInt().coerceIn(0, 100), false)
        }
        for (index in compactRows.size until items.size) {
            views.setViewVisibility(items[index].containerId, View.INVISIBLE)
        }
        return views
    }

    private fun providerIconRes(providerId: String): Int {
        return sharedProviderIconRes(providerId)
    }

    private fun applyProgressColor(views: RemoteViews, progressId: Int, gaugeColorHex: String?) {
        val color = ProviderGaugeColor.toArgbOrNull(gaugeColorHex) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(progressId, "setProgressTintList", ColorStateList.valueOf(color))
        }
    }

    private fun buildUsageNotificationContentForContext(
        context: Context,
        snapshotJson: String
    ): UsageNotificationContent {
        val updateMessage = if (AppUpdateStateStore.isUpdateAvailable(context)) {
            context.getString(R.string.app_update_status_bar_text)
        } else {
            null
        }
        return buildUsageNotificationContent(
            snapshotJson = snapshotJson,
            updateMessage = updateMessage
        )
    }

    private fun rowIds(): List<NotificationGaugeRow> {
        return listOf(
            NotificationGaugeRow(R.id.notification_row_0, R.id.notification_icon_0, R.id.notification_progress_0, R.id.notification_remaining_0, R.id.notification_reset_0),
            NotificationGaugeRow(R.id.notification_row_1, R.id.notification_icon_1, R.id.notification_progress_1, R.id.notification_remaining_1, R.id.notification_reset_1),
            NotificationGaugeRow(R.id.notification_row_2, R.id.notification_icon_2, R.id.notification_progress_2, R.id.notification_remaining_2, R.id.notification_reset_2),
            NotificationGaugeRow(R.id.notification_row_3, R.id.notification_icon_3, R.id.notification_progress_3, R.id.notification_remaining_3, R.id.notification_reset_3),
            NotificationGaugeRow(R.id.notification_row_4, R.id.notification_icon_4, R.id.notification_progress_4, R.id.notification_remaining_4, R.id.notification_reset_4),
            NotificationGaugeRow(R.id.notification_row_5, R.id.notification_icon_5, R.id.notification_progress_5, R.id.notification_remaining_5, R.id.notification_reset_5),
            NotificationGaugeRow(R.id.notification_row_6, R.id.notification_icon_6, R.id.notification_progress_6, R.id.notification_remaining_6, R.id.notification_reset_6),
            NotificationGaugeRow(R.id.notification_row_7, R.id.notification_icon_7, R.id.notification_progress_7, R.id.notification_remaining_7, R.id.notification_reset_7)
        )
    }

    private fun compactItemIds(): List<NotificationCompactItem> {
        return listOf(
            NotificationCompactItem(R.id.notification_compact_item_0, R.id.notification_compact_icon_0, R.id.notification_compact_text_0, R.id.notification_compact_progress_0),
            NotificationCompactItem(R.id.notification_compact_item_1, R.id.notification_compact_icon_1, R.id.notification_compact_text_1, R.id.notification_compact_progress_1),
            NotificationCompactItem(R.id.notification_compact_item_2, R.id.notification_compact_icon_2, R.id.notification_compact_text_2, R.id.notification_compact_progress_2),
            NotificationCompactItem(R.id.notification_compact_item_3, R.id.notification_compact_icon_3, R.id.notification_compact_text_3, R.id.notification_compact_progress_3)
        )
    }
}

private data class NotificationGaugeRow(
    val containerId: Int,
    val iconId: Int,
    val progressId: Int,
    val remainingTextId: Int,
    val resetTextId: Int
)

private data class NotificationCompactItem(
    val containerId: Int,
    val iconId: Int,
    val textId: Int,
    val progressId: Int
)
