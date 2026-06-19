package com.aiquota.mobile.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiquota.mobile.notification.AppUpdateNotificationController
import com.aiquota.mobile.notification.UsageLimitNotificationController
import com.aiquota.mobile.sync.ForegroundRefreshController

class AppUpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val updateAvailable = runCatching {
            AppUpdateAvailabilityChecker.hasStoreUpdateAvailable(applicationContext)
        }.getOrDefault(false)

        AppUpdateStateStore.setUpdateAvailable(applicationContext, updateAvailable)
        if (updateAvailable) {
            AppUpdateNotificationController.notifyUpdateAvailable(applicationContext)
        } else {
            AppUpdateNotificationController.cancel(applicationContext)
        }
        if (ForegroundRefreshController(applicationContext).liveMonitoringEnabled()) {
            UsageLimitNotificationController.updateFromCache(applicationContext)
        }

        return Result.success()
    }
}
