package com.aiquota.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.notification.UsageLimitNotificationController
import com.aiquota.mobile.providers.ProviderBackgroundRefreshStateRepository

class ForegroundRefreshHealthWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return runCatching {
            checkLiveRefreshHealth()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    private fun checkLiveRefreshHealth() {
        val context = applicationContext
        val canPostNotifications = UsageLimitNotificationController.canPostNotifications(context)
        val shouldRunForegroundLoop = ForegroundRefreshPolicy.shouldRunForegroundLoop(
            snapshots = LocalUsageRepository(context).readSnapshots(),
            liveMonitoringEnabled = ForegroundRefreshController(context).liveMonitoringEnabled(),
            canPostNotifications = canPostNotifications
        )

        if (!shouldRunForegroundLoop) {
            UsageLimitNotificationController.cancelLiveRefreshIssue(context)
            return
        }

        if (ProviderBackgroundRefreshStateRepository(context).isHeartbeatStale()) {
            runCatching {
                ForegroundRefreshController(context).startPreciseRefresh()
            }
            UsageLimitNotificationController.notifyLiveRefreshIssue(context)
        } else {
            UsageLimitNotificationController.cancelLiveRefreshIssue(context)
        }
    }
}
