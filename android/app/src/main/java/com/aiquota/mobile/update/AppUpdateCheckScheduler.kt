package com.aiquota.mobile.update

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AppUpdateCheckScheduler {
    private const val WORK_NAME = "ai_quota_app_update_check"
    private const val ONE_TIME_WORK_NAME = "ai_quota_app_update_check_now"
    private const val CHECK_INTERVAL_HOURS = 6L
    private const val STARTUP_CHECK_DELAY_MINUTES = 1L

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val workManager = WorkManager.getInstance(appContext)
        val periodicWork = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(
            CHECK_INTERVAL_HOURS,
            TimeUnit.HOURS
        ).build()
        val oneTimeWork = OneTimeWorkRequestBuilder<AppUpdateCheckWorker>()
            .setInitialDelay(STARTUP_CHECK_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeWork
        )
    }
}
