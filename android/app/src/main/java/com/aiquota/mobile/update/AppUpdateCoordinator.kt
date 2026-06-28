package com.aiquota.mobile.update

import androidx.activity.ComponentActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

class AppUpdateCoordinator(
    private val activity: ComponentActivity,
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
) {
    var onUpdateAvailable: () -> Unit = {}

    fun checkForStoreUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            val updateAvailable = AppUpdatePolicy.shouldShowStoreUpdatePrompt(appUpdateInfo.toSignal())
            AppUpdateStateStore.setUpdateAvailable(activity, updateAvailable)
            if (updateAvailable) {
                onUpdateAvailable()
            }
        }
    }

    fun openStoreListing() {
        AppUpdateStoreNavigator.open(activity)
    }

    private fun AppUpdateInfo.toSignal(): AppUpdateSignal {
        return AppUpdateSignal(
            updateAvailable = updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE,
            immediateAllowed = false,
            developerTriggeredUpdateInProgress = false
        )
    }
}
