package com.aiquota.mobile.update

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.tasks.await

object AppUpdateAvailabilityChecker {
    suspend fun hasImmediateUpdateAvailable(context: Context): Boolean {
        val appUpdateInfo = AppUpdateManagerFactory.create(context).appUpdateInfo.await()
        return AppUpdatePolicy.shouldStartImmediateUpdate(
            AppUpdateSignal(
                updateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
                immediateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                developerTriggeredUpdateInProgress = appUpdateInfo.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
            )
        )
    }
}
