package com.aiquota.mobile.update

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.tasks.await

object AppUpdateAvailabilityChecker {
    suspend fun hasStoreUpdateAvailable(context: Context): Boolean {
        val appUpdateInfo = AppUpdateManagerFactory.create(context).appUpdateInfo.await()
        return AppUpdatePolicy.shouldShowStoreUpdatePrompt(
            AppUpdateSignal(
                updateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
                immediateAllowed = false,
                developerTriggeredUpdateInProgress = false
            )
        )
    }
}
