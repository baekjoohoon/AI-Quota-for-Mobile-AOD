package com.aiquota.mobile.update

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckTest {
    @Test
    fun immediateUpdatePolicyRequiresAvailableImmediateUpdateOrInProgressFlow() {
        assertTrue(
            AppUpdatePolicy.shouldStartImmediateUpdate(
                AppUpdateSignal(
                    updateAvailable = true,
                    immediateAllowed = true,
                    developerTriggeredUpdateInProgress = false
                )
            )
        )
        assertTrue(
            AppUpdatePolicy.shouldStartImmediateUpdate(
                AppUpdateSignal(
                    updateAvailable = false,
                    immediateAllowed = false,
                    developerTriggeredUpdateInProgress = true
                )
            )
        )
        assertFalse(
            AppUpdatePolicy.shouldStartImmediateUpdate(
                AppUpdateSignal(
                    updateAvailable = true,
                    immediateAllowed = false,
                    developerTriggeredUpdateInProgress = false
                )
            )
        )
    }

    @Test
    fun appEntryStartsAndResumesPlayCoreImmediateUpdateChecks() {
        val build = File("build.gradle.kts").readText()
        val mainActivity = File("src/main/java/com/aiquota/mobile/MainActivity.kt").readText()
        val coordinator = File("src/main/java/com/aiquota/mobile/update/AppUpdateCoordinator.kt").readText()

        assertTrue(build.contains("com.google.android.play:app-update-ktx:2.1.0"))
        assertTrue(mainActivity.contains("AppUpdateCoordinator(this)"))
        assertTrue(mainActivity.contains("appUpdateCoordinator.checkForRequiredUpdate()"))
        assertTrue(mainActivity.contains("appUpdateCoordinator.resumeRequiredUpdateIfNeeded()"))
        assertTrue(coordinator.contains("AppUpdateManagerFactory.create"))
        assertTrue(coordinator.contains("UpdateAvailability.UPDATE_AVAILABLE"))
        assertTrue(coordinator.contains("UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS"))
        assertTrue(coordinator.contains("AppUpdateType.IMMEDIATE"))
        assertTrue(coordinator.contains("AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)"))
        assertTrue(coordinator.contains("ActivityResultContracts.StartIntentSenderForResult"))
    }

    @Test
    fun backgroundUpdateChecksAreScheduledAfterInstallAndAppStartup() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val application = File("src/main/java/com/aiquota/mobile/AIQuotaApplication.kt").readText()
        val scheduler = File("src/main/java/com/aiquota/mobile/update/AppUpdateCheckScheduler.kt").readText()
        val worker = File("src/main/java/com/aiquota/mobile/update/AppUpdateCheckWorker.kt").readText()
        val receiver = File("src/main/java/com/aiquota/mobile/update/AppUpdatedReceiver.kt").readText()
        val notification = File("src/main/java/com/aiquota/mobile/notification/AppUpdateNotificationController.kt").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(application.contains("AppUpdateCheckScheduler.schedule"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(manifest.contains(".update.AppUpdatedReceiver"))
        assertTrue(receiver.contains("AppUpdateCheckScheduler.schedule"))
        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<AppUpdateCheckWorker>"))
        assertTrue(scheduler.contains("OneTimeWorkRequestBuilder<AppUpdateCheckWorker>"))
        assertTrue(worker.contains("AppUpdateAvailabilityChecker.hasImmediateUpdateAvailable"))
        assertTrue(worker.contains("AppUpdateNotificationController.notifyUpdateAvailable"))
        assertTrue(notification.contains("market://details?id="))
        assertTrue(notification.contains("https://play.google.com/store/apps/details?id="))
        assertTrue(english.contains("Update AI Quota"))
        assertTrue(korean.contains("AI Quota 업데이트"))
    }
}
