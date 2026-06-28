package com.aiquota.mobile.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aiquota.mobile.notification.UsageLimitNotificationController
import java.util.concurrent.TimeUnit

object ForegroundRefreshHealthScheduler {
    private const val WORK_NAME = "ai_quota_live_refresh_health"
    private const val ONE_TIME_WORK_NAME = "ai_quota_live_refresh_health_now"
    private const val CHECK_INTERVAL_MINUTES = 15L
    private const val STARTUP_CHECK_DELAY_MINUTES = 1L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val periodicWork = PeriodicWorkRequestBuilder<ForegroundRefreshHealthWorker>(
            CHECK_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        ).build()
        val oneTimeWork = OneTimeWorkRequestBuilder<ForegroundRefreshHealthWorker>()
            .setInitialDelay(STARTUP_CHECK_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            oneTimeWork
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)

        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
        UsageLimitNotificationController.cancelLiveRefreshIssue(appContext)
    }
}
