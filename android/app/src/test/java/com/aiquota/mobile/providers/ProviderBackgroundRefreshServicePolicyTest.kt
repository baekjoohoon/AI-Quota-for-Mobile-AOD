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
        assertTrue(scheduler.contains("setInitialDelay(STARTUP_CHECK_DELAY_MINUTES, TimeUnit.MINUTES)"))
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
        val geminiAutomaticBranch = authFailureHandler.substringAfter("if (automaticRefresh && providerId == ProviderId.GEMINI)")
            .substringBefore("if (automaticRefresh)")
        val automaticBranch = authFailureHandler.substringAfter("if (automaticRefresh)")
            .substringBefore("if (!ProviderRefreshSessionPolicy")
        val payloadBranch = service.substringAfter("is ServiceRefreshOutcome.Payload ->")
            .substringBefore("} else if (effectiveJob.providerId == ProviderId.GEMINI")
        val failureBranch = service.substringAfter("is ServiceRefreshOutcome.Failure ->")
            .substringBefore("if (ProviderHiddenWebViewRetentionPolicy")

        assertTrue(geminiAutomaticBranch.contains("repository.markGoogleUsagePending(providerId, GoogleUsagePendingRetryPolicy.PENDING_MESSAGE)"))
        assertTrue(automaticBranch.contains("repository.failKeepingPrevious(providerId, message)"))
        assertFalse(automaticBranch.contains("repository.markInteractiveAuthRequired(providerId, message)"))
        assertTrue(authFailureHandler.contains("return"))
        assertFalse(automaticBranch.contains("ProviderSessionResetter(applicationContext).disconnect"))
        assertFalse(automaticBranch.contains("repository.markSessionExpired"))
        assertTrue(payloadBranch.contains("handleRefreshAuthFailure("))
        assertTrue(failureBranch.contains("handleRefreshAuthFailure("))
        assertTrue(service.contains("ProviderRefreshMode.HIDDEN_WEB_COLLECTOR -> ProviderWebSessionMaintenanceGate.withMaintenanceLock"))
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
    fun glmAndOpenCodeRefreshAreCollectedInsteadOfDiagnosticPassed() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val refreshProvider = service.substringAfter("private suspend fun refreshProvider")
            .substringBefore("private fun resolveRuntimeRefreshJob")

        assertFalse(refreshProvider.contains("shouldPassRefreshForDiagnostics(effectiveJob.providerId)"))
        assertFalse(refreshProvider.contains("reason=diagnostic_pass"))
        assertFalse(service.contains("private fun shouldPassRefreshForDiagnostics"))
    }

    @Test
    fun glmWebOAuthBackgroundRefreshStartsFromUsagePage() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val runtimeResolver = service.substringAfter("private fun resolveRuntimeRefreshJob")
            .substringBefore("private fun handleRefreshAuthFailure")

        assertTrue(runtimeResolver.contains("GlmRuntimeRefreshJobs.resolve("))
        assertFalse(runtimeResolver.contains("startUrl = GlmProviderUrls.WEB_OAUTH_URL"))
    }

    @Test
    fun opencodeBackgroundRefreshRedirectsWorkspaceShellToGoUsagePage() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val redirect = service.substringAfter("private fun maybeRedirectOpenCodeRefreshToGo")
            .substringBefore("private fun maybeRedirectGeminiRefreshToUsage")
        val pageStarted = service.substringAfter("override fun onPageStarted")
            .substringBefore("override fun shouldOverrideUrlLoading")
        val pageFinished = service.substringAfter("override fun onPageFinished")
            .substringBefore("override fun onReceivedError")

        assertTrue(redirect.contains("active.job.providerId != ProviderId.OPENCODE"))
        assertTrue(redirect.contains("OpenCodeUsagePageRoutes.goUsageUrlFrom(url)"))
        assertTrue(redirect.contains("saveOpenCodeUsageUrl(goUsageUrl)"))
        assertTrue(redirect.contains("view.loadUrl(goUsageUrl)"))
        assertTrue(pageStarted.contains("maybeRedirectOpenCodeRefreshToGo(active, view, url)"))
        assertTrue(pageFinished.contains("maybeRedirectOpenCodeRefreshToGo(active, view, url)"))
    }

    @Test
    fun serviceWebCollectorUsesProductionReinjectionPolicyWithoutRunIdState() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val injectBlock = service.substringAfter("private fun injectCollectorIfReady")
            .substringBefore("private inner class ServiceCollectorWebViewClient")

        assertTrue(injectBlock.contains("val firstInjectionForPage = collectorInjectionKeys.add(injectionKey)"))
        assertTrue(injectBlock.contains("routeKeyOf(url)"))
        assertTrue(injectBlock.contains("ProviderWebCollectorScripts.shouldAllowCollectorReinjection(providerId)"))
        assertFalse(injectBlock.contains("CollectorInjectionTrigger"))
        assertFalse(injectBlock.contains("collectorInjectionCounts"))
        assertFalse(service.contains("MAX_COLLECTOR_REINJECTIONS_PER_PAGE"))
        assertTrue(injectBlock.contains("\"reinject\""))
        assertFalse(injectBlock.contains("collectorRunId = active.requestId.toString()"))
    }

    @Test
    fun geminiBackgroundRefreshRedirectsAppShellBackToUsagePage() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val redirect = service.substringAfter("private fun maybeRedirectGeminiRefreshToUsage")
            .substringBefore("private inner class ServiceCollectorWebViewClient")
        val pageStarted = service.substringAfter("override fun onPageStarted")
            .substringBefore("override fun shouldOverrideUrlLoading")
        val shouldOverride = service.substringAfter("override fun shouldOverrideUrlLoading")
            .substringBefore("override fun shouldInterceptRequest")
        val pageFinished = service.substringAfter("override fun onPageFinished")
            .substringBefore("override fun onReceivedError")

        assertTrue(redirect.contains("providerId != ProviderId.GEMINI"))
        assertTrue(redirect.contains("GeminiUsagePageRoutes.isUsageUrl(url)"))
        assertTrue(redirect.contains("GeminiUsagePageRoutes.usageUrlFrom(url)"))
        assertTrue(redirect.contains("view.loadUrl(usageUrl)"))
        assertTrue(redirect.contains("active.geminiUsageRedirectAttempts >= GEMINI_USAGE_REDIRECT_MAX_ATTEMPTS"))
        assertTrue(service.contains("private fun maybeScheduleGeminiTerminalCheck"))
        assertTrue(service.contains("GeminiUsagePageRoutes.isLoginLandingUrl(url)"))
        assertTrue(service.contains("GeminiUsagePageRoutes.isUsageUrl(url)"))
        assertTrue(service.contains("GEMINI_TERMINAL_CHECK_DELAY_MS"))
        assertTrue(service.contains("GEMINI_TERMINAL_CHECK_FALLBACK_DELAY_MS"))
        assertTrue(service.contains("private const val GEMINI_TERMINAL_CHECK_DELAY_MS = 4_000L"))
        assertTrue(service.contains("private const val GEMINI_TERMINAL_CHECK_FALLBACK_DELAY_MS = 24_000L"))
        assertTrue(service.contains("ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD"))
        assertTrue(service.contains("if (ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GEMINI, pageUrl, pageText))"))
        assertTrue(pageStarted.contains("maybeRedirectGeminiRefreshToUsage(active, view, url)"))
        assertTrue(pageFinished.contains("maybeRedirectGeminiRefreshToUsage(active, view, url)"))
        assertTrue(pageFinished.contains("maybeScheduleGeminiTerminalCheck(active, view, url)"))
        assertTrue(shouldOverride.contains("shouldWaitForGeminiRefreshSignInRedirect(active, url)"))
        assertTrue(shouldOverride.contains("allowSignInRedirect provider=gemini"))
        assertTrue(shouldOverride.contains("return false"))
        assertTrue(service.contains("awaitInteractiveLoginUsage = providerId == ProviderId.GEMINI"))
        assertTrue(service.contains("private fun maybeClickGeminiRefreshSignIn"))
        assertTrue(service.contains("clickSignIn provider=gemini"))
        assertTrue(service.contains("private fun shouldWaitForGeminiRefreshSignInRedirect"))
        assertTrue(service.contains("private fun isGeminiRefreshInteractiveSignInPage"))
        assertTrue(service.contains("interactiveSignInRequired provider=gemini"))
        assertTrue(service.contains("path.contains(\"/signin/identifier\")"))
        assertTrue(service.contains("GEMINI_SIGN_IN_CLICK_MAX_ATTEMPTS"))
    }

    @Test
    fun timedOutWebCollectorsAndBridgePayloadsMatchProductionFlow() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val webCollector = service.substringAfter("private suspend fun collectWebProviderUsage")
            .substringBefore("@SuppressLint(\"SetJavaScriptEnabled\")")
        val bridge = service.substringAfter("private inner class ServiceUsageBridge")
            .substringBefore("private class ServiceCollectorChromeClient")

        assertFalse(webCollector.contains("if (result == null)"))
        assertFalse(webCollector.contains("destroyProviderWebView(job.providerId)"))
        assertFalse(bridge.contains("bridgePayloadForActiveRequest(rawPayload, active)"))
        assertFalse(bridge.contains("bridgeMessageMatchesActiveRequest(rawError"))
        assertFalse(bridge.contains("json.remove(\"collectorRunId\")"))
        assertFalse(bridge.contains("reason=stale_collector"))
        assertTrue(bridge.contains("completeWebJob(active.requestId, ServiceRefreshOutcome.Payload(rawPayload))"))
    }

    @Test
    fun codexBackgroundRefreshDoesNotUseNonProductionPostAuthRedirect() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val pageStarted = service.substringAfter("override fun onPageStarted")
            .substringBefore("override fun shouldOverrideUrlLoading")
        val pageFinished = service.substringAfter("override fun onPageFinished")
            .substringBefore("override fun onReceivedError")

        assertFalse(service.contains("maybeRedirectCodexPostAuthToUsage"))
        assertFalse(service.contains("CODEX_CALLBACK_RECOVERY_URL"))
        assertFalse(service.contains("codexPostAuthRedirected"))
        assertFalse(pageStarted.contains("ProviderLoginStrategy.shouldRedirectCodexPostAuthLandingToUsage"))
        assertFalse(pageFinished.contains("ProviderLoginStrategy.shouldRedirectCodexPostAuthLandingToUsage"))
    }

    @Test
    fun codexBackgroundRefreshDoesNotUseNonProductionRouteApplicationErrorFallback() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val pageFinished = service.substringAfter("override fun onPageFinished")
            .substringBefore("override fun onReceivedError")

        assertFalse(pageFinished.contains("ProviderLoginStrategy.isCodexRouteApplicationError(url, pageText)"))
        assertFalse(service.contains("maybeRecoverCodexUsageRouteApplicationError"))
        assertFalse(pageFinished.contains("Codex usage route returned an application error."))
        assertFalse(service.contains("codexUsageRouteFallbackAttempted"))
    }

    @Test
    fun codexBackgroundRefreshDoesNotUseNonProductionSettingsUsageFallback() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val pageFinished = service.substringAfter("override fun onPageFinished")
            .substringBefore("override fun onReceivedError")

        assertFalse(service.contains("maybeFallbackCodexRootToSettingsUsage"))
        assertFalse(service.contains("CODEX_SETTINGS_USAGE_URL"))
        assertFalse(pageFinished.contains("maybeFallbackCodexRootToSettingsUsage(active, view, url)"))
        assertFalse(service.contains("codexPostAuthRootFinishes"))
        assertFalse(service.contains("codexSettingsUsageFallbackAttempted"))
    }

    @Test
    fun codexBackgroundRefreshDoesNotDeferLoginPageFailureAfterPostAuthRoot() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val pageFinished = service.substringAfter("override fun onPageFinished")
            .substringBefore("override fun onReceivedError")
        val loadResource = service.substringAfter("override fun onLoadResource")
            .substringBefore("override fun onPageFinished")

        assertFalse(service.contains("shouldDeferCodexPostAuthLandingLoginPageFailure"))
        assertFalse(pageFinished.contains("!shouldDeferCodexPostAuthLandingLoginPageFailure(active, url)"))
        assertFalse(loadResource.contains("!shouldDeferCodexPostAuthLandingLoginPageFailure(active, pageUrl)"))
    }

    @Test
    fun manualWebAuthFailuresKeepPreviousUsageButNativeTokenFailuresExpireSession() {
        val repository = File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val authFailureHandler = service.substringAfter("private fun handleRefreshAuthFailure")
            .substringBefore("private suspend fun collectNativeProviderUsage")

        assertTrue(repository.contains("fun markSessionExpired(providerId: ProviderId, message: String)"))
        assertTrue(repository.contains("ProviderUsageSnapshot.disconnected(providerId).copy("))
        assertFalse(authFailureHandler.contains("repository.markInteractiveAuthRequired(providerId, message)"))
        assertTrue(authFailureHandler.contains("!ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)"))
        assertTrue(authFailureHandler.contains("repository.failKeepingPrevious(providerId, message)"))
        assertTrue(authFailureHandler.contains("ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)"))
        assertTrue(authFailureHandler.contains("ProviderSessionResetter(applicationContext).disconnect(providerId)"))
        assertTrue(authFailureHandler.contains("repository.markSessionExpired(providerId, message)"))
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
        assertFalse(nativeRefresh.contains("lastFailureDiagnostic()"))
    }

    @Test
    fun nativeGoogleRefreshFallsBackToWebSessionWhenStoredTokenHasNoPayload() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val nativeRefresh = service.substringAfter("private suspend fun collectNativeProviderUsage")
            .substringBefore("private suspend fun collectWebProviderUsage")

        assertTrue(nativeRefresh.contains("GoogleWebSessionCodeAssistFetcher.hasSessionCookie"))
        assertTrue(nativeRefresh.contains("GoogleWebSessionCodeAssistFetcher.fetchUsagePayload"))
    }

    @Test
    fun nativeFetchJavascriptBridgeUsesProductionWebViewUrlGuard() {
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()
        val bridge = service.substringAfter("private inner class ServiceUsageBridge")
            .substringBefore("private class ServiceCollectorChromeClient")
        val nativeFetchGuard = bridge.substringAfter("private fun isNativeFetchBridgePageAllowed")

        assertTrue(nativeFetchGuard.contains("retainedWebViews[providerId]?.url"))
        assertFalse(nativeFetchGuard.contains("webJobLastUrls[active.requestId]"))
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
