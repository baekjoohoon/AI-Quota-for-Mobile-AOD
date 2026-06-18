package com.aiquota.mobile.providers

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderPreferencesRepository
import com.aiquota.mobile.notification.UsageLimitNotificationController
import com.aiquota.mobile.sync.ForegroundRefreshController
import com.aiquota.mobile.widget.AIQuotaCircularWidgetProvider
import com.aiquota.mobile.widget.AIQuotaUnifiedGlanceWidget
import com.aiquota.mobile.widget.ProviderUsageWidgetProvider
import com.aiquota.mobile.widget.WidgetSnapshotCache
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object UsageSurfaceRefresher {
    private const val NOTIFICATION_UPDATE_DEBOUNCE_MS = 2_000L
    private const val WIDGET_SURFACE_UPDATE_DEBOUNCE_MS = 2_000L

    private val surfaceUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val notificationUpdateLock = Any()
    private val surfaceUpdateLock = Any()
    private var scheduledNotificationUpdate: Job? = null
    private var pendingNotificationUpdate: PendingNotificationUpdate? = null
    private var scheduledWidgetUpdate: Job? = null
    private var pendingWidgetUpdateContext: Context? = null

    fun refresh(
        context: Context,
        repository: LocalUsageRepository,
        order: List<ProviderId>? = null,
        hidden: Set<ProviderId>? = null
    ) {
        val appContext = context.applicationContext
        val preferences = ProviderPreferencesRepository(appContext)
        val updatedAt = Instant.now().toString()
        val displayJson = repository.exportDisplayOnlyCache(
            order = order ?: preferences.providerOrder(),
            hidden = hidden ?: preferences.hiddenProviders(),
            updatedAt = updatedAt
        )
        val widgetDisplayJson = repository.exportDisplayOnlyCache(
            order = preferences.providerOrder(),
            hidden = emptySet(),
            updatedAt = updatedAt
        )
        val cache = WidgetSnapshotCache(appContext)
        cache.write(repository.exportDisplayJson(), updatedAt = updatedAt)
        cache.writeLocalDisplaySnapshot(widgetDisplayJson, updatedAt)
        if (ForegroundRefreshController(appContext).liveMonitoringEnabled()) {
            schedulePinnedNotificationUpdate(appContext, displayJson)
        }
        scheduleWidgetSurfaceUpdate(appContext)
    }

    fun refreshWidgetSurfaces(context: Context) {
        scheduleWidgetSurfaceUpdate(context.applicationContext)
    }

    private fun scheduleWidgetSurfaceUpdate(appContext: Context) {
        synchronized(surfaceUpdateLock) {
            pendingWidgetUpdateContext = appContext.applicationContext
            if (scheduledWidgetUpdate?.isActive == true) return
            scheduledWidgetUpdate = surfaceUpdateScope.launch {
                while (true) {
                    delay(WIDGET_SURFACE_UPDATE_DEBOUNCE_MS)
                    val contextToUpdate = synchronized(surfaceUpdateLock) {
                        pendingWidgetUpdateContext.also {
                            pendingWidgetUpdateContext = null
                        }
                    } ?: continue

                    updateWidgetSurfaces(contextToUpdate)

                    val isDrained = synchronized(surfaceUpdateLock) {
                        if (pendingWidgetUpdateContext == null) {
                            scheduledWidgetUpdate = null
                            true
                        } else {
                            false
                        }
                    }
                    if (isDrained) return@launch
                }
            }
        }
    }

    private fun schedulePinnedNotificationUpdate(appContext: Context, snapshotJson: String) {
        synchronized(notificationUpdateLock) {
            pendingNotificationUpdate = PendingNotificationUpdate(
                context = appContext.applicationContext,
                snapshotJson = snapshotJson
            )
            if (scheduledNotificationUpdate?.isActive == true) return
            scheduledNotificationUpdate = surfaceUpdateScope.launch {
                while (true) {
                    delay(NOTIFICATION_UPDATE_DEBOUNCE_MS)
                    val update = synchronized(notificationUpdateLock) {
                        pendingNotificationUpdate.also {
                            pendingNotificationUpdate = null
                        }
                    } ?: continue

                    UsageLimitNotificationController.update(update.context, update.snapshotJson)

                    val isDrained = synchronized(notificationUpdateLock) {
                        if (pendingNotificationUpdate == null) {
                            scheduledNotificationUpdate = null
                            true
                        } else {
                            false
                        }
                    }
                    if (isDrained) return@launch
                }
            }
        }
    }

    private suspend fun updateWidgetSurfaces(appContext: Context) {
        runCatching { AIQuotaCircularWidgetProvider.updateAll(appContext) }
            .onFailure { error ->
                Log.w(TAG, "Circular widget update failed.", error)
            }
        runCatching { AIQuotaUnifiedGlanceWidget().updateAll(appContext) }
        runCatching { ProviderUsageWidgetProvider.updateAll(appContext) }
    }

    private const val TAG = "AIQuotaSurfaceRefresh"

    private data class PendingNotificationUpdate(
        val context: Context,
        val snapshotJson: String
    )
}
