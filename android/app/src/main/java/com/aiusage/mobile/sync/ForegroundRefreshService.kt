package com.aiusage.mobile.sync

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.notification.UsageLimitNotificationController
import com.aiusage.mobile.providers.ProviderUsageCollectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ForegroundRefreshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = UsageLimitNotificationController.foregroundNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startRefreshLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startRefreshLoop() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            val repository = LocalUsageRepository(applicationContext)
            while (isActive) {
                val cycleStartedAt = System.currentTimeMillis()
                val providers = ForegroundRefreshPolicy.connectedProviders(repository.readSnapshots())
                if (providers.isEmpty()) {
                    stopSelf()
                    return@launch
                }
                providers.forEach { providerId ->
                    if (!isActive) return@forEach
                    val previousUpdatedAt = repository.readSnapshots()
                        .firstOrNull { it.providerId == providerId }
                        ?.updatedAt
                        .orEmpty()
                    runCatching {
                        ProviderUsageCollectionService.start(
                            context = applicationContext,
                            providerId = providerId,
                            source = ProviderUsageCollectionService.SOURCE_REFRESH
                        )
                    }
                    waitForProviderRefresh(repository, providerId, previousUpdatedAt)
                }
                val elapsed = System.currentTimeMillis() - cycleStartedAt
                delay((ForegroundRefreshPolicy.REFRESH_INTERVAL_MS - elapsed).coerceAtLeast(0L))
            }
        }
    }

    private suspend fun waitForProviderRefresh(
        repository: LocalUsageRepository,
        providerId: ProviderId,
        previousUpdatedAt: String
    ) {
        delay(ForegroundRefreshPolicy.PROVIDER_REFRESH_START_GRACE_MS)
        val startedAt = System.currentTimeMillis()
        var seenRefreshing = false
        while (
            refreshJob?.isActive == true &&
            System.currentTimeMillis() - startedAt < ForegroundRefreshPolicy.PROVIDER_REFRESH_TIMEOUT_MS
        ) {
            val snapshot = repository.readSnapshots().firstOrNull { it.providerId == providerId }
            val refreshing = snapshot?.refreshState == ProviderRefreshState.REFRESHING
            seenRefreshing = seenRefreshing || refreshing
            if (snapshot != null && snapshot.updatedAt != previousUpdatedAt && !refreshing) return
            if (seenRefreshing && !refreshing) return
            delay(ForegroundRefreshPolicy.PROVIDER_REFRESH_POLL_MS)
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
