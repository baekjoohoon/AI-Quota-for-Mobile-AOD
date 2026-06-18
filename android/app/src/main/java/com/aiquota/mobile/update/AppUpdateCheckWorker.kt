package com.aiquota.mobile.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiquota.mobile.notification.AppUpdateNotificationController

class AppUpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val updateAvailable = runCatching {
            AppUpdateAvailabilityChecker.hasImmediateUpdateAvailable(applicationContext)
        }.getOrDefault(false)

        if (updateAvailable) {
            AppUpdateNotificationController.notifyUpdateAvailable(applicationContext)
        } else {
            AppUpdateNotificationController.cancel(applicationContext)
        }

        return Result.success()
    }
}
