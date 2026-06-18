package com.aiquota.mobile.update

data class AppUpdateSignal(
    val updateAvailable: Boolean,
    val immediateAllowed: Boolean,
    val developerTriggeredUpdateInProgress: Boolean
)

object AppUpdatePolicy {
    fun shouldStartImmediateUpdate(signal: AppUpdateSignal): Boolean {
        return signal.developerTriggeredUpdateInProgress ||
            (signal.updateAvailable && signal.immediateAllowed)
    }
}
