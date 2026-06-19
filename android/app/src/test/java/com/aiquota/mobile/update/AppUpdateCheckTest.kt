package com.aiquota.mobile.update

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckTest {
    @Test
    fun storeUpdatePolicyRequiresAvailableUpdateOnly() {
        assertTrue(
            AppUpdatePolicy.shouldShowStoreUpdatePrompt(
                AppUpdateSignal(
                    updateAvailable = true,
                    immediateAllowed = false,
                    developerTriggeredUpdateInProgress = false
                )
            )
        )
        assertFalse(
            AppUpdatePolicy.shouldShowStoreUpdatePrompt(
                AppUpdateSignal(
                    updateAvailable = false,
                    immediateAllowed = true,
                    developerTriggeredUpdateInProgress = true
                )
            )
        )
        assertFalse(
            AppUpdatePolicy.shouldShowStoreUpdatePrompt(
                AppUpdateSignal(
                    updateAvailable = false,
                    immediateAllowed = false,
                    developerTriggeredUpdateInProgress = false
                )
            )
        )
    }

    @Test
    fun appEntryShowsUpdatePromptAndOpensStoreWithoutImmediateRedirect() {
        val build = File("build.gradle.kts").readText()
        val mainActivity = File("src/main/java/com/aiquota/mobile/MainActivity.kt").readText()
        val coordinator = File("src/main/java/com/aiquota/mobile/update/AppUpdateCoordinator.kt").readText()
        val navigator = File("src/main/java/com/aiquota/mobile/update/AppUpdateStoreNavigator.kt").readText()

        assertTrue(build.contains("com.google.android.play:app-update-ktx:2.1.0"))
        assertTrue(mainActivity.contains("AppUpdateCoordinator(this)"))
        assertTrue(mainActivity.contains("appUpdateCoordinator.checkForStoreUpdate()"))
        assertTrue(mainActivity.contains("AppUpdatePromptDialog"))
        assertTrue(mainActivity.contains("openStoreListing()"))
        assertFalse(mainActivity.contains("resumeRequiredUpdateIfNeeded"))
        assertTrue(coordinator.contains("AppUpdateManagerFactory.create"))
        assertTrue(coordinator.contains("UpdateAvailability.UPDATE_AVAILABLE"))
        assertTrue(coordinator.contains("onUpdateAvailable"))
        assertTrue(coordinator.contains("AppUpdateStoreNavigator.open"))
        assertTrue(navigator.contains("market://details?id="))
        assertTrue(navigator.contains("https://play.google.com/store/apps/details?id="))
        assertFalse(coordinator.contains("startUpdateFlowForResult"))
        assertFalse(coordinator.contains("AppUpdateType.IMMEDIATE"))
        assertFalse(coordinator.contains("ActivityResultContracts.StartIntentSenderForResult"))
    }

    @Test
    fun backgroundUpdateChecksAreScheduledAfterInstallAndAppStartup() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val application = File("src/main/java/com/aiquota/mobile/AIQuotaApplication.kt").readText()
        val scheduler = File("src/main/java/com/aiquota/mobile/update/AppUpdateCheckScheduler.kt").readText()
        val worker = File("src/main/java/com/aiquota/mobile/update/AppUpdateCheckWorker.kt").readText()
        val receiver = File("src/main/java/com/aiquota/mobile/update/AppUpdatedReceiver.kt").readText()
        val notification = File("src/main/java/com/aiquota/mobile/notification/AppUpdateNotificationController.kt").readText()
        val navigator = File("src/main/java/com/aiquota/mobile/update/AppUpdateStoreNavigator.kt").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(application.contains("AppUpdateCheckScheduler.schedule"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(manifest.contains(".update.AppUpdatedReceiver"))
        assertTrue(receiver.contains("AppUpdateCheckScheduler.schedule"))
        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<AppUpdateCheckWorker>"))
        assertTrue(scheduler.contains("OneTimeWorkRequestBuilder<AppUpdateCheckWorker>"))
        assertTrue(worker.contains("AppUpdateAvailabilityChecker.hasStoreUpdateAvailable"))
        assertTrue(worker.contains("AppUpdateStateStore.setUpdateAvailable"))
        assertTrue(worker.contains("ForegroundRefreshController"))
        assertTrue(worker.contains("UsageLimitNotificationController.updateFromCache"))
        assertTrue(worker.contains("AppUpdateNotificationController.notifyUpdateAvailable"))
        assertTrue(notification.contains("AppUpdateStoreNavigator.storeIntent"))
        assertTrue(navigator.contains("market://details?id="))
        assertTrue(navigator.contains("https://play.google.com/store/apps/details?id="))
        assertTrue(english.contains("app_update_status_bar_text"))
        assertTrue(english.contains("Update AI Quota"))
        assertTrue(korean.contains("AI Quota 업데이트"))
    }
}
