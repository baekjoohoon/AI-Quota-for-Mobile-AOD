package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBackgroundRefreshServicePolicyTest {
    @Test
    fun providerRefreshServiceUsesSpecialUseUsageMonitorForegroundServiceOnly() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue(manifest.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
        assertTrue(manifest.contains("android:value=\"usage_monitor\""))
        assertFalse(manifest.contains("FOREGROUND_SERVICE_DATA_SYNC"))
        assertFalse(manifest.contains("foregroundServiceType=\"dataSync\""))
        assertTrue(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE"))
        assertFalse(service.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
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
    fun liveRefreshHealthWorkerRestartsAndNotifiesWhenHeartbeatIsStale() {
        val worker = File("src/main/java/com/aiquota/mobile/sync/ForegroundRefreshHealthWorker.kt").readText()
        val scheduler = File("src/main/java/com/aiquota/mobile/sync/ForegroundRefreshHealthScheduler.kt").readText()
        val controller = File("src/main/java/com/aiquota/mobile/sync/ForegroundRefreshController.kt").readText()
        val notification = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()
        val build = File("build.gradle.kts").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(build.contains("androidx.work:work-runtime-ktx"))
        assertTrue(worker.contains("class ForegroundRefreshHealthWorker"))
        assertTrue(worker.contains("ProviderBackgroundRefreshStateRepository"))
        assertTrue(worker.contains("isHeartbeatStale"))
        assertTrue(worker.contains("ForegroundRefreshController(context).startPreciseRefresh()"))
        assertTrue(worker.contains("notifyLiveRefreshIssue"))
        assertTrue(worker.contains("cancelLiveRefreshIssue"))
        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<ForegroundRefreshHealthWorker>"))
        assertTrue(scheduler.contains("15L"))
        assertTrue(scheduler.contains("ExistingPeriodicWorkPolicy.KEEP"))
        assertTrue(scheduler.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(controller.contains("ForegroundRefreshHealthScheduler.schedule"))
        assertTrue(controller.contains("ForegroundRefreshHealthScheduler.cancel"))
        assertTrue(notification.contains("LIVE_REFRESH_ISSUE_NOTIFICATION_ID = 1002"))
        assertTrue(notification.contains("fun notifyLiveRefreshIssue"))
        assertTrue(english.contains("Live monitoring needs attention"))
        assertTrue(korean.contains("라이브 모니터링 확인 필요"))
    }

    @Test
    fun automaticStaleRefreshDoesNotDisconnectAfterCollectorFailures() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(service.contains("automaticRefresh = manualProviderId == null"))
        assertTrue(service.contains("repository.failKeepingPrevious(effectiveJob.providerId, outcome.failure.message)"))
        assertFalse(service.contains("MAX_STALE_REFRESH_FAILURES"))
        assertFalse(service.contains("staleRefreshFailureCounts"))
        assertFalse(service.contains("Provider session expired after repeated automatic refresh failures."))
    }

    @Test
    fun automaticAuthLikeRefreshFailuresKeepProviderRefreshable() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val authFailureHandler = service.substringAfter("private fun handleRefreshAuthFailure")
            .substringBefore("private suspend fun collectNativeProviderUsage")
        val automaticBranch = authFailureHandler.substringAfter("if (automaticRefresh)")
            .substringBefore("if (!ProviderRefreshSessionPolicy")
        val payloadBranch = service.substringAfter("is ServiceRefreshOutcome.Payload ->")
            .substringBefore("} else if (effectiveJob.providerId == ProviderId.GEMINI")
        val failureBranch = service.substringAfter("is ServiceRefreshOutcome.Failure ->")
            .substringBefore("if (ProviderHiddenWebViewRetentionPolicy")

        assertTrue(authFailureHandler.contains("repository.failKeepingPrevious(providerId, message)"))
        assertTrue(authFailureHandler.contains("return"))
        assertFalse(automaticBranch.contains("ProviderSessionResetter(applicationContext).disconnect"))
        assertFalse(automaticBranch.contains("repository.markSessionExpired"))
        assertTrue(payloadBranch.contains("handleRefreshAuthFailure("))
        assertTrue(failureBranch.contains("handleRefreshAuthFailure("))
    }

    @Test
    fun opencodeRefreshReusesLastTrustedGoUsagePage() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val backgroundCollector = File("src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt").readText()
        val login = File("src/main/java/com/aiquota/mobile/providers/WebLoginActivity.kt").readText()
        val stateRepository = File("src/main/java/com/aiquota/mobile/providers/ProviderScopedStateRepository.kt").readText()
        val runtimeResolver = service.substringAfter("private fun resolveRuntimeRefreshJob")
            .substringBefore("private fun handleRefreshAuthFailure")

        assertTrue(stateRepository.contains("fun saveOpenCodeUsageUrl"))
        assertTrue(stateRepository.contains("fun readOpenCodeUsageUrl"))
        assertTrue(login.contains("saveOpenCodeUsageUrl(pageUrl)"))
        assertTrue(login.contains("saveOpenCodeUsageUrl(goUsageUrl)"))
        assertTrue(backgroundCollector.contains("saveOpenCodeUsageUrl(pageUrl)"))
        assertTrue(runtimeResolver.contains("readOpenCodeUsageUrl()"))
        assertTrue(runtimeResolver.contains("job.copy(startUrl = usageUrl)"))
    }

    @Test
    fun explicitProviderRefreshUsesManualRefreshIntent() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val manualFactory = service.substringAfter("fun createRefreshIntent")
            .substringBefore("fun createControlIntents")

        assertTrue(manualFactory.contains(".setAction(ACTION_REFRESH)"))
        assertTrue(manualFactory.contains("WidgetRefreshActions.EXTRA_PROVIDER_ID"))
        assertTrue(manualFactory.contains("ProviderBackgroundRefreshService::class.java"))
    }

    @Test
    fun serviceWebCollectorCanReinjectAfterInitialPageStartInjection() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val injectBlock = service.substringAfter("private fun injectCollectorIfReady")
            .substringBefore("private inner class ServiceCollectorWebViewClient")

        assertTrue(injectBlock.contains("val firstInjectionForPage = collectorInjectionKeys.add(injectionKey)"))
        assertTrue(injectBlock.contains("ProviderWebCollectorScripts.shouldAllowCollectorReinjection(providerId)"))
        assertTrue(injectBlock.contains("\"reinject\""))
    }

    @Test
    fun manualWebSessionAuthFailuresKeepPreviousUsageButNativeTokenAuthFailuresExpireSession() {
        val repository = File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val authFailureHandler = service.substringAfter("private fun handleRefreshAuthFailure")
            .substringBefore("private suspend fun collectNativeProviderUsage")

        assertTrue(repository.contains("fun markSessionExpired(providerId: ProviderId, message: String)"))
        assertTrue(repository.contains("ProviderUsageSnapshot.disconnected(providerId).copy("))
        assertTrue(authFailureHandler.contains("repository.failKeepingPrevious(providerId, message)"))
        assertTrue(authFailureHandler.contains("!ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)"))
        assertTrue(authFailureHandler.contains("ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)"))
        assertTrue(authFailureHandler.contains("repository.markSessionExpired(providerId, message)"))
        assertFalse(authFailureHandler.contains("repository.markInteractiveAuthRequired("))
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
    fun automaticForegroundRefreshDelaysFirstCycleAfterServiceStart() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val startRefreshLoop = service.substringAfter("private fun startRefreshLoop()")
            .substringBefore("private fun requestImmediateRefresh")
        val manualRefresh = service.substringAfter("private fun requestImmediateRefresh")
            .substringBefore("private fun stopRefreshLoop")

        assertTrue(service.contains("INITIAL_AUTO_REFRESH_DELAY_MILLIS = 3_000L"))
        assertTrue(startRefreshLoop.contains("scheduleNextTick(delayMillis = INITIAL_AUTO_REFRESH_DELAY_MILLIS)"))
        assertTrue(manualRefresh.contains("scheduleNextTick(delayMillis = 0L)"))
    }

    @Test
    fun providerRefreshServiceLogsStayRedacted() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val backgroundCollector = File("src/main/java/com/aiquota/mobile/ui/BackgroundProviderWebCollector.kt").readText()

        assertFalse(service.contains("Log.d(TAG, rawPayload"))
        assertFalse(service.contains("Log.e(TAG, rawPayload"))
        assertFalse(service.contains("Log.d(TAG, cookiesFor"))
        assertFalse(service.contains("Log.e(TAG, cookiesFor"))
        assertFalse(backgroundCollector.contains("message=${'$'}{errorJson?.optString(\"message\").orEmpty()}"))
        assertTrue(backgroundCollector.contains("messagePresent="))
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
