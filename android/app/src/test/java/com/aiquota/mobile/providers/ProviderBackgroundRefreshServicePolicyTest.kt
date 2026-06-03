package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBackgroundRefreshServicePolicyTest {
    @Test
    fun providerRefreshServiceUsesDataSyncForegroundServiceOnly() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertFalse(manifest.contains("FOREGROUND_SERVICE_SPECIAL_USE"))
        assertFalse(manifest.contains("specialUse"))
        assertTrue(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
    }

    @Test
    fun providerRefreshServiceKeepsSixtySecondCadenceAndHeartbeat() {
        val plan = File("src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val repository = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshStateRepository.kt").readText()

        assertTrue(plan.contains("AUTO_REFRESH_INTERVAL_MILLIS = 60_000L"))
        assertTrue(service.contains("ProviderBackgroundRefreshStateRepository"))
        assertTrue(service.contains("recordHeartbeat"))
        assertTrue(service.contains("recordStopped"))
        assertTrue(repository.contains("fun heartbeatAtMillis"))
    }

    @Test
    fun automaticStaleRefreshDoesNotDisconnectAfterCollectorFailures() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(service.contains("automaticRefresh = manualProviderId == null"))
        assertTrue(service.contains("repository.failKeepingPrevious(job.providerId, outcome.failure.message)"))
        assertFalse(service.contains("MAX_STALE_REFRESH_FAILURES"))
        assertFalse(service.contains("staleRefreshFailureCounts"))
        assertFalse(service.contains("Provider session expired after repeated automatic refresh failures."))
    }

    @Test
    fun explicitAuthFailureMarksProviderDisconnected() {
        val repository = File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(repository.contains("fun markSessionExpired(providerId: ProviderId, message: String)"))
        assertTrue(repository.contains("ProviderUsageSnapshot.disconnected(providerId).copy("))
        assertTrue(service.contains("repository.markSessionExpired("))
        assertFalse(service.contains("repository.markInteractiveAuthRequired("))
    }

    @Test
    fun stoppedBackgroundRefreshDoesNotPersistProviderFailure() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(service.contains("completeActiveWebJob(ServiceRefreshOutcome.Cancelled)"))
        assertTrue(service.contains("data object Cancelled : ServiceRefreshOutcome()"))
        assertTrue(service.contains("is ServiceRefreshOutcome.Cancelled ->"))
        assertFalse(service.contains("ProviderRefreshFailureKind.COLLECTOR_ERROR, \"Background refresh stopped.\""))
    }

    @Test
    fun nativeGoogleRefreshDoesNotBlockMainThread() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val nativeRefresh = service.substringAfter("private suspend fun collectNativeProviderUsage")
            .substringBefore("private suspend fun collectWebProviderUsage")

        assertTrue(nativeRefresh.contains("withContext(Dispatchers.IO)"))
        assertTrue(nativeRefresh.contains("GeminiCliOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()"))
        assertTrue(nativeRefresh.contains("AntigravityOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()"))
    }

    @Test
    fun providerRefreshServiceLogsStayRedacted() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertFalse(service.contains("Log.d(TAG, rawPayload"))
        assertFalse(service.contains("Log.e(TAG, rawPayload"))
        assertFalse(service.contains("Log.d(TAG, cookiesFor"))
        assertFalse(service.contains("Log.e(TAG, cookiesFor"))
        assertTrue(service.contains("provider=${'$'}{job.providerId.storageId}"))
    }

    @Test
    fun pinnedNotificationDoesNotExposeUserStopAction() {
        val notification = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        assertFalse(notification.contains("ACTION_STOP"))
        assertFalse(notification.contains("addAction"))
        assertFalse(strings.contains("notification_stop_live_refresh"))
    }
}
