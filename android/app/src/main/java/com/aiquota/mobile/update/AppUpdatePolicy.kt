package com.aiquota.mobile.update

data class AppUpdateSignal(
    val updateAvailable: Boolean,
    val immediateAllowed: Boolean,
    val developerTriggeredUpdateInProgress: Boolean
)

object AppUpdatePolicy {
    fun shouldShowStoreUpdatePrompt(signal: AppUpdateSignal): Boolean {
        return signal.updateAvailable
    }
}
