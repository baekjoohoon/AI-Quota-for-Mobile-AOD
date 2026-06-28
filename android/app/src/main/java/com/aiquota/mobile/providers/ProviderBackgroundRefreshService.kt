package com.aiquota.mobile.providers

import android.annotation.SuppressLint
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aiquota.mobile.R
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageSnapshot
import com.aiquota.mobile.notification.UsageLimitNotificationController
import com.aiquota.mobile.update.AppUpdatedRefreshCooldown
import com.aiquota.mobile.widget.WidgetRefreshActions
import com.aiquota.mobile.widget.WidgetRefreshFeedback
import java.net.URI
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class ProviderBackgroundRefreshService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val retainedWebViews = mutableMapOf<ProviderId, WebView>()
    private val collectorInjectionKeys = mutableSetOf<String>()

    private lateinit var repository: LocalUsageRepository
    private var running = false
    private var refreshInProgress = false
    private var tickScheduled = false
    private var nextRequestId = 0L
    private var activeWebJob: ServiceWebRefreshJob? = null
    private var activeWebContinuation: CancellableContinuation<ServiceRefreshOutcome>? = null
    private val webJobLastUrls = mutableMapOf<Long, String>()
    private var observedCodexAccountId: String? = null
    private var pendingManualProviderId: ProviderId? = null
    private var pendingManualWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var sessionResetReceiverRegistered = false
    private val sessionResetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PROVIDER_SESSION_RESET) return
            val providerId = ProviderId.fromStorageId(intent.getStringExtra(EXTRA_PROVIDER_ID)) ?: return
            handleProviderSessionReset(providerId)
        }
    }
    private val geminiCollectorAsset by lazy {
        runCatching {
            assets.open("gemini_collector.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    private val antigravityCollectorAsset by lazy {
        runCatching {
            assets.open("antigravity_collector.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    private lateinit var refreshStateRepository: ProviderBackgroundRefreshStateRepository
    private val tickRunnable = Runnable {
        tickScheduled = false
        if (!running) return@Runnable
        serviceScope.launch {
            val startedAt = System.currentTimeMillis()
            runRefreshCycle()
            val elapsedMillis = System.currentTimeMillis() - startedAt
            val nextDelayMillis = if (hasPendingManualRefresh()) 0L else {
                ProviderRefreshPlan.nextAutoRefreshDelayMillis(elapsedMillis)
            }
            scheduleNextTick(nextDelayMillis)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = LocalUsageRepository(applicationContext)
        refreshStateRepository = ProviderBackgroundRefreshStateRepository(applicationContext)
        CookieManager.getInstance().setAcceptCookie(true)
        registerSessionResetReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                com.aiquota.mobile.sync.ForegroundRefreshController(applicationContext)
                    .setLiveMonitoringEnabled(false)
                stopRefreshLoop()
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> requestImmediateRefresh(intent)
            else -> startRefreshLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterSessionResetReceiver()
        stopRefreshLoop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRefreshLoop() {
        if (!startForegroundNotification()) {
            stopSelf()
            return
        }
        refreshStateRepository.recordStarted(source = "loop")
        if (!running) {
            running = true
        }
        if (!tickScheduled && !refreshInProgress) {
            scheduleNextTick(delayMillis = initialAutoRefreshDelayMillis())
        }
    }

    private fun requestImmediateRefresh(intent: Intent?) {
        if (!startForegroundNotification()) {
            stopSelf()
            return
        }
        refreshStateRepository.recordStarted(source = "manual")
        pendingManualProviderId = ProviderId.fromStorageId(
            intent?.getStringExtra(WidgetRefreshActions.EXTRA_PROVIDER_ID)
        )
        pendingManualWidgetId = intent?.getIntExtra(
            WidgetRefreshActions.EXTRA_APP_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        running = true
        if (!refreshInProgress) {
            scheduleNextTick(delayMillis = 0L)
        }
    }

    private fun stopRefreshLoop() {
        running = false
        tickScheduled = false
        mainHandler.removeCallbacks(tickRunnable)
        completeActiveWebJob(ServiceRefreshOutcome.Cancelled)
        retainedWebViews.values.toList().forEach { webView ->
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
        }
        retainedWebViews.clear()
        collectorInjectionKeys.clear()
        refreshStateRepository.recordStopped()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        UsageLimitNotificationController.cancel(applicationContext)
    }

    private fun registerSessionResetReceiver() {
        if (sessionResetReceiverRegistered) return
        val filter = IntentFilter(ACTION_PROVIDER_SESSION_RESET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sessionResetReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(sessionResetReceiver, filter)
        }
        sessionResetReceiverRegistered = true
    }

    private fun unregisterSessionResetReceiver() {
        if (!sessionResetReceiverRegistered) return
        runCatching { unregisterReceiver(sessionResetReceiver) }
        sessionResetReceiverRegistered = false
    }

    private fun handleProviderSessionReset(providerId: ProviderId) {
        Log.d(TAG, "sessionReset provider=${providerId.storageId}")
        repository.removeProviderSnapshot(providerId)
        collectorInjectionKeys.removeAll { it.contains(":${providerId.storageId}:") }
        val active = activeWebJob?.takeIf { it.job.providerId == providerId }
        destroyProviderWebView(providerId)
        active?.let { completeWebJob(it.requestId, ServiceRefreshOutcome.Cancelled) }
        UsageSurfaceRefresher.refresh(applicationContext, repository)
    }

    private fun startForegroundNotification(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    UsageLimitNotificationController.NOTIFICATION_ID,
                    UsageLimitNotificationController.foregroundNotification(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    UsageLimitNotificationController.NOTIFICATION_ID,
                    UsageLimitNotificationController.foregroundNotification(this)
                )
            }
        }.onFailure { error ->
            Log.e(TAG, "failedToStartForeground=${error::class.java.simpleName}")
        }.isSuccess
    }

    private fun scheduleNextTick(delayMillis: Long = ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS) {
        if (!running) return
        mainHandler.removeCallbacks(tickRunnable)
        tickScheduled = true
        mainHandler.postDelayed(tickRunnable, delayMillis)
    }

    private fun hasPendingManualRefresh(): Boolean {
        return pendingManualProviderId != null ||
            pendingManualWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private suspend fun runRefreshCycle() {
        if (refreshInProgress) return
        refreshInProgress = true
        refreshStateRepository.recordHeartbeat()
        val manualProviderId = pendingManualProviderId
        val manualWidgetId = pendingManualWidgetId
        pendingManualProviderId = null
        pendingManualWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        try {
            val jobs = if (manualProviderId != null) {
                ProviderRefreshPlan.manualCycleJobsFor(manualProviderId, repository.readSnapshots())
            } else {
                ProviderRefreshPlan.automaticJobsFor(repository.readSnapshots())
            }
            if (jobs.isEmpty()) {
                running = false
                stopSelf()
                return
            }
            Log.d(TAG, "cycleStart providers=${jobs.joinToString(",") { it.providerId.storageId }}")
            jobs.forEach { job ->
                refreshProvider(job, automaticRefresh = manualProviderId == null)
            }
        } finally {
            if (manualWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetRefreshFeedback.clearWidgetRefresh(applicationContext, manualWidgetId)
                UsageSurfaceRefresher.refresh(applicationContext, repository)
            }
            refreshInProgress = false
        }
    }

    private suspend fun refreshProvider(job: ProviderRefreshJob, automaticRefresh: Boolean) {
        val effectiveJob = resolveRuntimeRefreshJob(job)
        val startingSnapshot = repository.readSnapshots().firstOrNull { it.providerId == effectiveJob.providerId }
        repository.markCollecting(effectiveJob.providerId)
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        val outcome = when (effectiveJob.mode) {
            ProviderRefreshMode.NATIVE_API -> collectNativeProviderUsage(effectiveJob)
            ProviderRefreshMode.HIDDEN_WEB_COLLECTOR -> ProviderWebSessionMaintenanceGate.withMaintenanceLock {
                collectWebProviderUsage(effectiveJob, automaticRefresh)
            }
        }
        Log.d(TAG, "outcome provider=${effectiveJob.providerId.storageId} type=${outcome::class.java.simpleName}")
        if (repository.readSnapshots().none { it.providerId == effectiveJob.providerId }) {
            destroyProviderWebView(effectiveJob.providerId)
            return
        }
        when (outcome) {
            is ServiceRefreshOutcome.Snapshot -> {
                Log.d(
                    TAG,
                    "saveSnapshot provider=${effectiveJob.providerId.storageId} source=native lineCount=${outcome.snapshot.lines.size} " +
                        "planPresent=${outcome.snapshot.planLabel != null} accountPresent=${outcome.snapshot.account != null}"
                )
                repository.saveSnapshot(outcome.snapshot)
            }
            is ServiceRefreshOutcome.Payload -> {
                val snapshot = ProviderUsageNormalizer.normalize(
                    effectiveJob.providerId,
                    outcome.rawPayload,
                    ProviderPayloadSource.STRUCTURED_SCRIPT
                )
                if (snapshot != null) {
                    Log.d(
                        TAG,
                        "saveSnapshot provider=${effectiveJob.providerId.storageId} source=webview lineCount=${snapshot.lines.size} " +
                            "planPresent=${snapshot.planLabel != null} accountPresent=${snapshot.account != null}"
                    )
                    repository.saveSnapshot(snapshot)
                } else if (
                    ProviderRefreshFailureClassifier.requiresInteractiveAuth(
                        effectiveJob.providerId,
                        ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD
                    )
                ) {
                    Log.w(
                        TAG,
                        "normalizeFailed provider=${effectiveJob.providerId.storageId} authClassified=true summary=${payloadSignal(outcome.rawPayload)}"
                    )
                    handleRefreshAuthFailure(
                        providerId = effectiveJob.providerId,
                        message = getString(R.string.provider_status_auth_required),
                        automaticRefresh = automaticRefresh
                    )
                } else if (effectiveJob.providerId == ProviderId.GEMINI || effectiveJob.providerId == ProviderId.ANTIGRAVITY) {
                    Log.w(
                        TAG,
                        "normalizeFailed provider=${effectiveJob.providerId.storageId} googlePending=true summary=${payloadSignal(outcome.rawPayload)}"
                    )
                    repository.markGoogleUsagePending(effectiveJob.providerId, GoogleUsagePendingRetryPolicy.PENDING_MESSAGE)
                } else {
                    Log.w(
                        TAG,
                        "normalizeFailed provider=${effectiveJob.providerId.storageId} keptPrevious=true summary=${payloadSignal(outcome.rawPayload)}"
                    )
                    repository.failKeepingPrevious(effectiveJob.providerId, "Background collector ran. No trusted usage payload found.")
                }
            }
            is ServiceRefreshOutcome.Failure -> {
                Log.w(
                    TAG,
                    "failure provider=${effectiveJob.providerId.storageId} kind=${outcome.failure.kind} message=${safeLogValue(outcome.failure.message)}"
                )
                refreshStateRepository.recordFailure(outcome.failure.kind.name)
                if (ProviderRefreshFailureClassifier.requiresInteractiveAuth(effectiveJob.providerId, outcome.failure.kind)) {
                    handleRefreshAuthFailure(
                        providerId = effectiveJob.providerId,
                        message = outcome.failure.message,
                        automaticRefresh = automaticRefresh
                    )
                } else if (
                    effectiveJob.providerId == ProviderId.GLM &&
                    GlmNoSubscriptionPolicy.isNoSubscriptionMessage(outcome.failure.message)
                ) {
                    repository.markConnectedWithoutPlan(
                        providerId = ProviderId.GLM,
                        planLabel = GlmNoSubscriptionPolicy.PLAN_LABEL,
                        message = GlmNoSubscriptionPolicy.MESSAGE
                    )
                } else if (effectiveJob.providerId == ProviderId.GEMINI || effectiveJob.providerId == ProviderId.ANTIGRAVITY) {
                    repository.markGoogleUsagePending(effectiveJob.providerId, outcome.failure.message)
                } else {
                    repository.failKeepingPrevious(effectiveJob.providerId, outcome.failure.message)
                }
                if (ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(outcome.failure.kind)) {
                    destroyProviderWebView(effectiveJob.providerId)
                }
            }
            is ServiceRefreshOutcome.Cancelled -> {
                startingSnapshot?.let(repository::saveSnapshot)
            }
        }
        UsageSurfaceRefresher.refresh(applicationContext, repository)
    }

    private fun resolveRuntimeRefreshJob(job: ProviderRefreshJob): ProviderRefreshJob {
        if (job.providerId == ProviderId.OPENCODE) {
            val usageUrl = ProviderScopedStateRepository(applicationContext).readOpenCodeUsageUrl()
            if (!usageUrl.isNullOrBlank()) {
                return job.copy(startUrl = usageUrl)
            }
            return job
        }
        return GlmRuntimeRefreshJobs.resolve(
            job,
            GlmUsageRepository(applicationContext).connectionMode()
        )
    }

    private fun handleRefreshAuthFailure(
        providerId: ProviderId,
        message: String,
        automaticRefresh: Boolean
    ) {
        destroyProviderWebView(providerId)
        if (!automaticRefresh) {
            if (ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)) {
                ProviderSessionResetter(applicationContext).disconnect(providerId)
            }
            repository.markSessionExpired(providerId, message)
            return
        }
        if (automaticRefresh && providerId == ProviderId.GEMINI) {
            repository.markGoogleUsagePending(providerId, GoogleUsagePendingRetryPolicy.PENDING_MESSAGE)
            return
        }
        if (automaticRefresh) {
            repository.failKeepingPrevious(providerId, message)
            return
        }
    }

    private suspend fun collectNativeProviderUsage(job: ProviderRefreshJob): ServiceRefreshOutcome {
        if (job.providerId == ProviderId.GLM) {
            val result = withContext(Dispatchers.IO) {
                GlmUsageRepository(applicationContext).fetchUsagePayloadFromStoredCredential()
            }
            val snapshot = result.payload?.let {
                ProviderUsageNormalizer.normalize(
                    job.providerId,
                    it,
                    ProviderPayloadSource.PROVIDER_API
                )
            }
            return when {
                snapshot != null -> ServiceRefreshOutcome.Snapshot(snapshot)
                result.requiresAuth -> ServiceRefreshOutcome.Failure(
                    ProviderRefreshFailure.interactiveAuthRequired("GLM API key is invalid or expired.")
                )
                else -> ServiceRefreshOutcome.Failure(
                    ProviderRefreshFailure(
                        ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD,
                        "GLM usage payload was not available: ${result.diagnostic}"
                    )
                )
            }
        }
        if (job.providerId == ProviderId.GEMINI || job.providerId == ProviderId.ANTIGRAVITY) {
            val payload = withContext(Dispatchers.IO) {
                fetchGoogleNativeOrWebSessionPayload(job.providerId)
            }
            val snapshot = payload?.let {
                ProviderUsageNormalizer.normalize(
                    job.providerId,
                    it,
                    ProviderPayloadSource.PROVIDER_API
                )
            }
            return if (snapshot != null) {
                ServiceRefreshOutcome.Snapshot(snapshot)
            } else {
                ServiceRefreshOutcome.Failure(
                    ProviderRefreshFailure(
                        ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD,
                        "${job.providerId.displayName} OAuth collection returned no trusted usage payload."
                    )
                )
            }
        }
        return ServiceRefreshOutcome.Failure(
            ProviderRefreshFailure(
                ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD,
                "Native refresh is not available for ${job.providerId.displayName}."
            )
        )
    }

    private fun fetchGoogleNativeOrWebSessionPayload(providerId: ProviderId): String? {
        val nativePayload = if (providerId == ProviderId.GEMINI) {
            GeminiCliOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()
        } else {
            AntigravityOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()
        }
        if (nativePayload != null) return nativePayload
        if (!GoogleWebSessionCodeAssistFetcher.hasSessionCookie(providerId)) return null
        val fallback = GoogleWebSessionCodeAssistFetcher.fetchUsagePayload(providerId)
        Log.i(
            TAG,
            "googleWebSessionFallback provider=${providerId.storageId} " +
                "payloadPresent=${fallback.payload != null} diagnostic=${fallback.diagnostic} " +
                "statuses=${fallback.statuses.joinToString(",")}"
        )
        return fallback.payload
    }

    private suspend fun collectWebProviderUsage(
        job: ProviderRefreshJob,
        automaticRefresh: Boolean
    ): ServiceRefreshOutcome {
        if (job.providerId == ProviderId.GLM) {
            Log.i(TAG, "glmWebSessionCollect start automatic=$automaticRefresh")
            return when (val result = GlmIsolatedWebSession.collectUsage(
                context = applicationContext,
                startUrl = job.startUrl,
                timeoutMillis = ProviderRefreshPlan.timeoutMillisFor(job.providerId)
            )) {
                is GlmIsolatedUsageResult.Payload -> {
                    GlmUsageRepository(applicationContext).saveWebSessionCookieHeader(result.cookieHeader)
                    ServiceRefreshOutcome.Payload(result.rawPayload)
                }
                is GlmIsolatedUsageResult.Failure -> ServiceRefreshOutcome.Failure(result.failure)
            }
        }
        val requestId = ++nextRequestId
        webJobLastUrls[requestId] = job.startUrl
        val result = withTimeoutOrNull(timeoutMillisForWebJob(job)) {
            suspendCancellableCoroutine { continuation ->
                mainHandler.post {
                    val warmUpUrl = webSessionWarmUpUrl(job)
                    val active = ServiceWebRefreshJob(
                        requestId = requestId,
                        job = job,
                        warmUpUrl = warmUpUrl,
                        warmUpPending = warmUpUrl != null
                    )
                    activeWebJob = active
                    activeWebContinuation = continuation
                    startWebCollection(active)
                }
                continuation.invokeOnCancellation {
                    mainHandler.post {
                        clearActiveWebJob(requestId)
                    }
                }
            }
        }
        val lastUrl = webJobLastUrls[requestId]
        clearActiveWebJob(requestId)
        webJobLastUrls.remove(requestId)
        if (result == null && job.providerId == ProviderId.GEMINI) {
            val safeLastUrl = lastUrl.orEmpty()
            Log.d(TAG, "timeout provider=gemini last=${hostOf(safeLastUrl)}${pathOf(safeLastUrl)}")
        }
        return result ?: ServiceRefreshOutcome.Failure(ProviderRefreshTimeoutPolicy.failureFor(job.providerId, lastUrl))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebCollection(active: ServiceWebRefreshJob) {
        val job = active.job
        val webView = retainedWebViews.getOrPut(job.providerId) {
            WebView(this).apply {
                val cookieManager = CookieManager.getInstance()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setSupportMultipleWindows(false)
                settings.userAgentString = ProviderWebViewUserAgent.hiddenCollectorUserAgent(this@ProviderBackgroundRefreshService, job.providerId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                }
                addJavascriptInterface(ServiceUsageBridge(job.providerId), BRIDGE_NAME)
                webChromeClient = ServiceCollectorChromeClient()
                webViewClient = ServiceCollectorWebViewClient(job.providerId)
            }
        }
        val initialUrl = active.warmUpUrl ?: job.startUrl
        webJobLastUrls[active.requestId] = initialUrl
        prepareSharedWebSessionForCollection(webView, job.providerId)
        Log.d(
            TAG,
            "load provider=${job.providerId.storageId} start=${hostOf(initialUrl)}${pathOf(initialUrl)} " +
                "warmUp=${active.warmUpPending} request=${active.requestId}"
        )
        webView.loadUrl(initialUrl)
    }

    private fun prepareSharedWebSessionForCollection(webView: WebView, providerId: ProviderId) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
        CookieManager.getInstance().flush()
        webView.onResume()
        webView.resumeTimers()
    }

    private fun destroyProviderWebView(providerId: ProviderId) {
        retainedWebViews.remove(providerId)?.let { webView ->
            runCatching { webView.stopLoading() }
            runCatching { webView.removeJavascriptInterface(BRIDGE_NAME) }
            runCatching { webView.destroy() }
        }
    }

    private fun currentWebJobFor(providerId: ProviderId): ServiceWebRefreshJob? {
        return activeWebJob?.takeIf { it.job.providerId == providerId }
    }

    private fun completeActiveWebJob(outcome: ServiceRefreshOutcome) {
        val requestId = activeWebJob?.requestId ?: return
        completeWebJob(requestId, outcome)
    }

    private fun completeWebJob(requestId: Long, outcome: ServiceRefreshOutcome) {
        val active = activeWebJob ?: return
        if (active.requestId != requestId) return
        val continuation = activeWebContinuation
        activeWebJob = null
        activeWebContinuation = null
        if (continuation?.isActive == true) {
            continuation.resume(outcome)
        }
    }

    private fun clearActiveWebJob(requestId: Long) {
        if (activeWebJob?.requestId != requestId) return
        activeWebJob = null
        activeWebContinuation = null
    }

    private fun recordWebJobUrl(requestId: Long, url: String) {
        webJobLastUrls[requestId] = url
    }

    private fun saveOpenCodeUsageUrl(url: String) {
        ProviderScopedStateRepository(applicationContext).saveOpenCodeUsageUrl(url)
    }

    private fun injectCollectorIfReady(providerId: ProviderId, view: WebView, url: String, pageText: String) {
        val active = currentWebJobFor(providerId) ?: return
        val cookies = cookiesFor(url)
        if (!ProviderWebCollectorScripts.shouldRunCollector(providerId, url, cookies, pageText)) {
            Log.d(
                TAG,
                "skipInject provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)} " +
                    "reason=${ProviderWebCollectorScripts.collectorReadinessDiagnostic(providerId, url, cookies, pageText)} " +
                    "page=${pageSignal(pageText)}"
            )
            return
        }
        val injectionKey = "${active.requestId}:${providerId.storageId}:${hostOf(url)}:${routeKeyOf(url)}"
        val firstInjectionForPage = collectorInjectionKeys.add(injectionKey)
        if (!firstInjectionForPage && !ProviderWebCollectorScripts.shouldAllowCollectorReinjection(providerId)) return
        val script = ProviderWebCollectorScripts.build(
            providerId = providerId,
            cookies = cookies,
            geminiCollectorAsset = geminiCollectorAsset,
            antigravityCollectorAsset = antigravityCollectorAsset,
            observedAccountId = observedCodexAccountId,
            pageText = pageText,
            pageUrl = url,
            awaitInteractiveLoginUsage = providerId == ProviderId.GEMINI
        )
        Log.d(
            TAG,
            "${if (firstInjectionForPage) "inject" else "reinject"} " +
                "provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)}"
        )
        view.evaluateJavascript(script, null)
    }

    private fun maybeRedirectGeminiRefreshToUsage(active: ServiceWebRefreshJob, view: WebView, url: String): Boolean {
        val providerId = active.job.providerId
        if (providerId != ProviderId.GEMINI) return false
        if (GeminiUsagePageRoutes.isUsageUrl(url)) return false
        val usageUrl = GeminiUsagePageRoutes.usageUrlFrom(url) ?: return false
        if (active.geminiUsageRedirectAttempts >= GEMINI_USAGE_REDIRECT_MAX_ATTEMPTS) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty()
        val redirectKey = "$host:$path"
        val now = SystemClock.elapsedRealtime()
        if (redirectKey == active.lastGeminiRefreshRedirectKey &&
            now - active.lastGeminiRefreshRedirectAtMs < GEMINI_USAGE_REDIRECT_MIN_INTERVAL_MS
        ) {
            return false
        }
        active.lastGeminiRefreshRedirectKey = redirectKey
        active.lastGeminiRefreshRedirectAtMs = now
        active.geminiUsageRedirectAttempts += 1
        collectorInjectionKeys.removeAll { it.contains(":${ProviderId.GEMINI.storageId}:") }
        Log.d(TAG, "redirectUsage provider=gemini from=${hostOf(url)}${pathOf(url)}")
        view.stopLoading()
        view.loadUrl(usageUrl)
        return true
    }

    private fun maybeRedirectOpenCodeRefreshToGo(active: ServiceWebRefreshJob, view: WebView, url: String): Boolean {
        if (active.job.providerId != ProviderId.OPENCODE) return false
        val goUsageUrl = OpenCodeUsagePageRoutes.goUsageUrlFrom(url) ?: return false
        collectorInjectionKeys.removeAll { it.contains(":${ProviderId.OPENCODE.storageId}:") }
        saveOpenCodeUsageUrl(goUsageUrl)
        Log.d(TAG, "redirectUsage provider=opencode from=${hostOf(url)}${pathOf(url)}")
        view.stopLoading()
        view.loadUrl(goUsageUrl)
        return true
    }

    private fun maybeCompleteWebSessionWarmUp(active: ServiceWebRefreshJob, view: WebView, url: String): Boolean {
        if (!isWebSessionWarmUpPage(active, url)) return false
        active.warmUpPending = false
        collectorInjectionKeys.removeAll { it.contains(":${active.job.providerId.storageId}:") }
        Log.d(
            TAG,
            "warmUpComplete provider=${active.job.providerId.storageId} from=${hostOf(url)}${pathOf(url)} " +
                "to=${hostOf(active.job.startUrl)}${pathOf(active.job.startUrl)}"
        )
        view.stopLoading()
        view.loadUrl(active.job.startUrl)
        return true
    }

    private fun isWebSessionWarmUpPage(active: ServiceWebRefreshJob, url: String): Boolean {
        if (!active.warmUpPending) return false
        val warmUpUrl = active.warmUpUrl ?: return false
        return hostOf(url) == hostOf(warmUpUrl)
    }

    private fun maybeScheduleGeminiTerminalCheck(active: ServiceWebRefreshJob, view: WebView, url: String) {
        if (active.job.providerId != ProviderId.GEMINI) return
        if (active.geminiTerminalCheckScheduled) return
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        val host = uri.host.orEmpty().lowercase()
        if (host != "gemini.google.com" ||
            (!GeminiUsagePageRoutes.isLoginLandingUrl(url) && !GeminiUsagePageRoutes.isUsageUrl(url))
        ) {
            return
        }
        active.geminiTerminalCheckScheduled = true
        val requestId = active.requestId
        Log.d(TAG, "terminalCheckScheduled provider=gemini url=${hostOf(url)}${pathOf(url)}")
        mainHandler.postDelayed(
            {
                val current = currentWebJobFor(ProviderId.GEMINI) ?: return@postDelayed
                if (current.requestId != requestId) return@postDelayed
                val pageUrl = view.url ?: url
                view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
                    if (currentWebJobFor(ProviderId.GEMINI)?.requestId != requestId) return@evaluateJavascript
                    val pageText = decodeJsString(encoded)
                if (ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GEMINI, pageUrl, pageText)) {
                    val failure = ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)
                        Log.d(TAG, "terminalCheck provider=gemini kind=${failure.kind} url=${hostOf(pageUrl)}${pathOf(pageUrl)} page=${pageSignal(pageText)}")
                        completeWebJob(requestId, ServiceRefreshOutcome.Failure(failure))
                    }
                }
                mainHandler.postDelayed(
                    {
                        if (currentWebJobFor(ProviderId.GEMINI)?.requestId != requestId) return@postDelayed
                        Log.d(TAG, "terminalCheckFallback provider=gemini url=${hostOf(pageUrl)}${pathOf(pageUrl)}")
                        completeWebJob(
                            requestId,
                            ServiceRefreshOutcome.Failure(
                                ProviderRefreshFailure(
                                    ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD,
                                    "Gemini usage payload was not available."
                                )
                            )
                        )
                    },
                    GEMINI_TERMINAL_CHECK_FALLBACK_DELAY_MS
                )
            },
            GEMINI_TERMINAL_CHECK_DELAY_MS
        )
    }

    private fun maybeClickGeminiRefreshSignIn(
        active: ServiceWebRefreshJob,
        view: WebView,
        url: String,
        pageText: String
    ): Boolean {
        if (active.job.providerId != ProviderId.GEMINI) return false
        if (active.geminiSignInClickAttempts >= GEMINI_SIGN_IN_CLICK_MAX_ATTEMPTS) return false
        if (!ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GEMINI, url, pageText)) return false
        active.geminiSignInClickAttempts += 1
        collectorInjectionKeys.removeAll { it.contains(":${ProviderId.GEMINI.storageId}:") }
        Log.d(TAG, "clickSignIn provider=gemini from=${hostOf(url)}${pathOf(url)}")
        view.evaluateJavascript(
            """
            (function(){
              var labels = ["로그인", "sign in", "log in"];
              var elements = Array.prototype.slice.call(document.querySelectorAll("a[href], button, [role='button']"));
              for (var i = 0; i < elements.length; i += 1) {
                var text = String(elements[i].innerText || elements[i].textContent || elements[i].getAttribute("aria-label") || "").trim().toLowerCase();
                var href = String(elements[i].href || "");
                for (var j = 0; j < labels.length; j += 1) {
                  if (text.indexOf(labels[j]) >= 0 || href.indexOf("accounts.google.com/ServiceLogin") >= 0) {
                    if (href.indexOf("accounts.google.com/ServiceLogin") >= 0) {
                      location.href = href;
                    } else {
                      elements[i].click();
                    }
                    return true;
                  }
                }
              }
              return false;
            })();
            """.trimIndent(),
            null
        )
        return true
    }

    private fun shouldWaitForGeminiRefreshSignInRedirect(active: ServiceWebRefreshJob, url: String): Boolean {
        if (active.job.providerId != ProviderId.GEMINI) return false
        if (active.geminiSignInClickAttempts <= 0) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase()
        return host == "accounts.google.com" || host.startsWith("accounts.google.")
    }

    private fun isGeminiRefreshInteractiveSignInPage(active: ServiceWebRefreshJob, url: String): Boolean {
        if (active.job.providerId != ProviderId.GEMINI) return false
        if (active.geminiSignInClickAttempts <= 0) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase()
        if (host != "accounts.google.com" && !host.startsWith("accounts.google.")) return false
        val path = uri.path.orEmpty().lowercase()
        return path.contains("/signin/identifier")
    }

    private inner class ServiceCollectorWebViewClient(
        private val ownerProviderId: ProviderId
    ) : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            val active = currentWebJobFor(ownerProviderId) ?: return
            recordWebJobUrl(active.requestId, url)
            if (isWebSessionWarmUpPage(active, url)) return
            if (maybeRedirectOpenCodeRefreshToGo(active, view, url)) return
            if (maybeRedirectGeminiRefreshToUsage(active, view, url)) return
            if (isGeminiRefreshInteractiveSignInPage(active, url)) {
                Log.d(TAG, "interactiveSignInRequired provider=gemini at=${hostOf(url)}${pathOf(url)}")
                completeWebJob(active.requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                return
            }
            if (ProviderWebCollectorScripts.isRefreshLoginPage(ownerProviderId, url)) {
                if (shouldWaitForGeminiRefreshSignInRedirect(active, url)) return
                completeWebJob(active.requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                return
            }
            injectCollectorIfReady(ownerProviderId, view, url, "")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val active = currentWebJobFor(ownerProviderId) ?: return true
            val url = request.url.toString()
            if (shouldWaitForGeminiRefreshSignInRedirect(active, url)) {
                Log.d(TAG, "allowSignInRedirect provider=gemini to=${hostOf(url)}${pathOf(url)}")
                return false
            }
            return !ProviderDefinitionRegistry.isCollectorNavigationAllowed(active.job.providerId, url)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            captureCodexAccountId(ownerProviderId, request.url.toString())
            return null
        }

        override fun onLoadResource(view: WebView, url: String) {
            val active = currentWebJobFor(ownerProviderId) ?: return
            val pageUrl = view.url ?: url
            recordWebJobUrl(active.requestId, pageUrl)
            if (isWebSessionWarmUpPage(active, pageUrl)) return
            if (!ProviderWebCollectorScripts.shouldRunCollectorFromResource(ownerProviderId, pageUrl, url)) return
            val requestId = active.requestId
            view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
                if (currentWebJobFor(ownerProviderId)?.requestId != requestId) return@evaluateJavascript
                val pageText = decodeJsString(encoded)
                if (ProviderWebCollectorScripts.isRefreshLoginPage(ownerProviderId, pageUrl, pageText)) {
                    Log.d(
                        TAG,
                        "loginPage provider=${ownerProviderId.storageId} phase=resource url=${hostOf(pageUrl)}${pathOf(pageUrl)} page=${pageSignal(pageText)}"
                    )
                    if (maybeClickGeminiRefreshSignIn(active, view, pageUrl, pageText)) return@evaluateJavascript
                    if (shouldWaitForGeminiRefreshSignInRedirect(active, pageUrl)) return@evaluateJavascript
                    completeWebJob(requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                    return@evaluateJavascript
                }
                injectCollectorIfReady(ownerProviderId, view, pageUrl, pageText)
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            val active = currentWebJobFor(ownerProviderId) ?: return
            val requestId = active.requestId
            recordWebJobUrl(requestId, url)
            Log.d(TAG, "pageFinished provider=${ownerProviderId.storageId} url=${hostOf(url)}${pathOf(url)}")
            if (maybeCompleteWebSessionWarmUp(active, view, url)) return
            if (maybeRedirectOpenCodeRefreshToGo(active, view, url)) return
            if (maybeRedirectGeminiRefreshToUsage(active, view, url)) return
            if (isGeminiRefreshInteractiveSignInPage(active, url)) {
                Log.d(TAG, "interactiveSignInRequired provider=gemini at=${hostOf(url)}${pathOf(url)}")
                completeWebJob(requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                return
            }
            if (ProviderWebCollectorScripts.isRefreshLoginPage(ownerProviderId, url)) {
                if (shouldWaitForGeminiRefreshSignInRedirect(active, url)) return
                completeWebJob(requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                return
            }
            view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
                if (currentWebJobFor(ownerProviderId)?.requestId != requestId) return@evaluateJavascript
                val pageText = decodeJsString(encoded)
                if (ProviderWebCollectorScripts.isRefreshLoginPage(ownerProviderId, url, pageText)) {
                    Log.d(
                        TAG,
                        "loginPage provider=${ownerProviderId.storageId} phase=finished url=${hostOf(url)}${pathOf(url)} page=${pageSignal(pageText)}"
                    )
                    if (maybeClickGeminiRefreshSignIn(active, view, url, pageText)) return@evaluateJavascript
                    if (shouldWaitForGeminiRefreshSignInRedirect(active, url)) return@evaluateJavascript
                    completeWebJob(requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                    return@evaluateJavascript
                }
                injectCollectorIfReady(ownerProviderId, view, url, pageText)
                maybeScheduleGeminiTerminalCheck(active, view, url)
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val active = currentWebJobFor(ownerProviderId) ?: return
            completeWebJob(
                active.requestId,
                ServiceRefreshOutcome.Failure(
                    ProviderRefreshFailure(
                        ProviderRefreshFailureKind.TRANSIENT_PAGE_LOAD,
                        "Background refresh page failed to load."
                    )
                )
            )
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (!request.isForMainFrame || errorResponse.statusCode < 400) return
            Log.d(
                TAG,
                "httpError provider=${ownerProviderId.storageId} status=${errorResponse.statusCode} url=${hostOf(request.url.toString())}${pathOf(request.url.toString())}"
            )
            if (ProviderRefreshHttpErrorPolicy.shouldIgnoreMainFrameHttpError(
                    ownerProviderId,
                    request.url.toString(),
                    errorResponse.statusCode
                )
            ) {
                return
            }
            val active = currentWebJobFor(ownerProviderId) ?: return
            val failure = ProviderRefreshHttpErrorPolicy.failureForMainFrameHttpError(
                ownerProviderId,
                request.url.toString(),
                errorResponse.statusCode
            )
            completeWebJob(
                active.requestId,
                ServiceRefreshOutcome.Failure(failure)
            )
        }
    }

    private inner class ServiceUsageBridge(
        private val ownerProviderId: ProviderId
    ) {
        @JavascriptInterface
        fun postUsagePayload(rawPayload: String) {
            mainHandler.post {
                val active = currentWebJobFor(ownerProviderId) ?: return@post
                val pageUrl = retainedWebViews[ownerProviderId]?.url.orEmpty().ifBlank { active.job.startUrl }
                if (!ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ownerProviderId, pageUrl, rawPayload)) {
                    Log.d(
                        TAG,
                        "dropPayload provider=${ownerProviderId.storageId} reason=untrusted_bridge_page " +
                            "url=${hostOf(pageUrl)}${pathOf(pageUrl)} summary=${payloadSignal(rawPayload)}"
                    )
                    return@post
                }
                Log.d(
                    TAG,
                    "payload provider=${ownerProviderId.storageId} url=${hostOf(pageUrl)}${pathOf(pageUrl)} " +
                        "summary=${payloadSignal(rawPayload)}"
                )
                CookieManager.getInstance().flush()
                completeWebJob(active.requestId, ServiceRefreshOutcome.Payload(rawPayload))
            }
        }

        @JavascriptInterface
        fun postCollectorError(rawError: String) {
            mainHandler.post {
                val active = currentWebJobFor(ownerProviderId) ?: return@post
                val pageUrl = retainedWebViews[ownerProviderId]?.url.orEmpty().ifBlank { active.job.startUrl }
                if (!ProviderWebCollectorScripts.shouldAcceptCollectorError(ownerProviderId, pageUrl, rawError)) {
                    Log.d(TAG, "dropCollectorError provider=${ownerProviderId.storageId} reason=untrusted_bridge_page")
                    return@post
                }
                val errorKind = ProviderCollectorErrorPolicy.errorKind(rawError)
                val retryCount = active.collectorRetryCounts[errorKind] ?: 0
                Log.d(
                    TAG,
                    "collectorError provider=${ownerProviderId.storageId} errorKind=$errorKind retry=$retryCount " +
                        "url=${hostOf(pageUrl)}${pathOf(pageUrl)} summary=${payloadSignal(rawError)}"
                )
                if (CodexCollectorRetryPolicy.shouldRetry(ownerProviderId, errorKind, retryCount)) {
                    active.collectorRetryCounts[errorKind] = retryCount + 1
                    collectorInjectionKeys.removeAll {
                        it.startsWith("${active.requestId}:${ownerProviderId.storageId}:")
                    }
                    Log.d(
                        TAG,
                        "retryCollector provider=${ownerProviderId.storageId} errorKind=$errorKind retry=${retryCount + 1}"
                    )
                    retainedWebViews[ownerProviderId]?.let { webView ->
                        runCatching { webView.stopLoading() }
                        if (webView.url.isNullOrBlank()) {
                            webView.loadUrl(active.job.startUrl)
                        } else {
                            webView.reload()
                        }
                    }
                    return@post
                }
                val failure = ProviderCollectorErrorPolicy.failureFor(ownerProviderId, rawError)
                completeWebJob(active.requestId, ServiceRefreshOutcome.Failure(failure))
            }
        }

        @JavascriptInterface
        fun fetchCursorJson(url: String, body: String?): String {
            if (ownerProviderId != ProviderId.CURSOR) {
                return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
            }
            if (!isNativeFetchBridgePageAllowed(ownerProviderId)) {
                return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
            }
            return CursorNativeUsageFetcher.fetchJson(url, body)
        }

        @JavascriptInterface
        fun fetchCopilotJson(url: String): String {
            if (ownerProviderId != ProviderId.COPILOT) {
                return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
            }
            if (!isNativeFetchBridgePageAllowed(ownerProviderId)) {
                return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
            }
            return CopilotNativeUsageFetcher.fetchJson(url)
        }

        @JavascriptInterface
        fun fetchCopilotJsonWithAuthorization(url: String, authorizationHeader: String): String {
            if (ownerProviderId != ProviderId.COPILOT) {
                return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
            }
            if (!isNativeFetchBridgePageAllowed(ownerProviderId)) {
                return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
            }
            return CopilotNativeUsageFetcher.fetchJsonWithAuthorization(url, authorizationHeader)
        }

        private fun isNativeFetchBridgePageAllowed(providerId: ProviderId): Boolean {
            val active = currentWebJobFor(providerId) ?: return false
            val pageUrl = webJobLastUrls[active.requestId].orEmpty().ifBlank { active.job.startUrl }
            return ProviderWebCollectorScripts.shouldAcceptCollectorPayload(providerId, pageUrl)
        }

    }

    private class ServiceCollectorChromeClient : WebChromeClient()

    private fun captureCodexAccountId(providerId: ProviderId, url: String) {
        if (providerId != ProviderId.CODEX) return
        val accountId = runCatching {
            val uri = URI(url)
            if (uri.path != "/backend-api/subscriptions") return@runCatching null
            uri.query
                ?.split("&")
                ?.firstOrNull { it.startsWith("account_id=") }
                ?.substringAfter("=")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: return
        observedCodexAccountId = accountId
    }

    private fun cookiesFor(url: String): Map<String, String> {
        return CookieManager.getInstance().getCookie(url)
            ?.split(";")
            ?.mapNotNull { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            ?.toMap()
            .orEmpty()
    }

    private fun hostOf(url: String): String {
        return runCatching { URI(url).host.orEmpty() }.getOrDefault("")
    }

    private fun pathOf(url: String): String {
        return runCatching { URI(url).path.orEmpty() }.getOrDefault("")
    }

    private fun routeKeyOf(url: String): String {
        return runCatching {
            val uri = URI(url)
            buildString {
                append(uri.path.orEmpty())
                uri.rawQuery?.takeIf { it.isNotBlank() }?.let { append("?").append(it) }
                uri.rawFragment?.takeIf { it.isNotBlank() }?.let { append("#").append(it) }
            }
        }.getOrDefault(pathOf(url))
    }

    private fun pageSignal(pageText: String): String {
        val text = pageText.lowercase()
        return listOf(
            "len=${pageText.length}",
            "signIn=${text.contains("sign in") || text.contains("log in") || text.contains("로그인")}",
            "usage=${text.contains("usage") || text.contains("사용량")}",
            "limit=${text.contains("limit") || text.contains("한도")}",
            "quota=${text.contains("quota")}",
            "error=${text.contains("error") || text.contains("오류")}"
        ).joinToString(",")
    }

    private fun payloadSignal(rawPayload: String): String {
        val json = runCatching { JSONObject(rawPayload) }.getOrNull()
            ?: return "invalid_json,len=${rawPayload.length}"
        val rootKeys = json.keys().asSequence()
            .filterNot(::isSensitiveLogKey)
            .take(12)
            .joinToString("|")
            .ifBlank { "empty" }
        val lineCount = json.optJSONArray("lines")?.length()
            ?: json.optJSONObject("usage")?.optJSONArray("limits")?.length()
            ?: json.optJSONArray("limits")?.length()
            ?: 0
        return listOf(
            "root=$rootKeys",
            "provider=${safeLogValue(json.optString("provider"))}",
            "collectorMode=${safeLogValue(json.optString("collectorMode"))}",
            "lineCount=$lineCount",
            "hasUsage=${json.has("usage")}",
            "hasData=${json.has("data")}",
            "hasError=${json.has("errorKind") || json.has("error")}"
        ).joinToString(",")
    }

    private fun isSensitiveLogKey(key: String): Boolean {
        return key.contains("token", ignoreCase = true) ||
            key.contains("cookie", ignoreCase = true) ||
            key.contains("authorization", ignoreCase = true) ||
            key.contains("secret", ignoreCase = true)
    }

    private fun safeLogValue(value: String?): String {
        return value.orEmpty()
            .replace(Regex("code=[^\\s&]+"), "code=redacted")
            .replace(Regex("token=[^\\s&]+"), "token=redacted")
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "<email>")
            .take(180)
            .ifBlank { "none" }
    }

    private fun decodeJsString(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return runCatching { JSONObject("""{"value":$value}""").optString("value") }.getOrDefault("")
    }

    private data class ServiceWebRefreshJob(
        val requestId: Long,
        val job: ProviderRefreshJob,
        val warmUpUrl: String? = null,
        var warmUpPending: Boolean = false,
        val collectorRetryCounts: MutableMap<String, Int> = mutableMapOf(),
        var lastGeminiRefreshRedirectKey: String? = null,
        var lastGeminiRefreshRedirectAtMs: Long = 0L,
        var geminiUsageRedirectAttempts: Int = 0,
        var geminiTerminalCheckScheduled: Boolean = false,
        var geminiSignInClickAttempts: Int = 0
    )

    private sealed class ServiceRefreshOutcome {
        data class Snapshot(val snapshot: ProviderUsageSnapshot) : ServiceRefreshOutcome()
        data class Payload(val rawPayload: String) : ServiceRefreshOutcome()
        data class Failure(val failure: ProviderRefreshFailure) : ServiceRefreshOutcome()
        data object Cancelled : ServiceRefreshOutcome()
    }

    companion object {
        const val ACTION_START = "com.aiquota.mobile.action.START_BACKGROUND_REFRESH"
        const val ACTION_STOP = "com.aiquota.mobile.action.STOP_BACKGROUND_REFRESH"
        const val ACTION_REFRESH = "com.aiquota.mobile.action.REFRESH"
        const val ACTION_PROVIDER_SESSION_RESET = "com.aiquota.mobile.action.PROVIDER_SESSION_RESET"
        const val EXTRA_PROVIDER_ID = "provider_id"
        fun createRefreshIntent(
            context: Context,
            providerId: ProviderId?,
            appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
        ): Intent {
            return Intent(context, ProviderBackgroundRefreshService::class.java)
                .setAction(ACTION_REFRESH)
                .putExtra(WidgetRefreshActions.EXTRA_APP_WIDGET_ID, appWidgetId)
                .apply {
                    providerId?.let { putExtra(WidgetRefreshActions.EXTRA_PROVIDER_ID, it.storageId) }
                }
        }

        fun createControlIntents(context: Context, action: String): List<Intent> {
            val defaultIntent = Intent(context, ProviderBackgroundRefreshService::class.java)
                .setAction(action)
            return listOf(defaultIntent)
        }

        fun createSessionResetIntent(context: Context, providerId: ProviderId): Intent {
            return Intent(ACTION_PROVIDER_SESSION_RESET)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PROVIDER_ID, providerId.storageId)
        }
        private const val BRIDGE_NAME = "AIQuotaCollectorBridge"
        private const val INITIAL_AUTO_REFRESH_DELAY_MILLIS = 3_000L
        private const val WEB_SESSION_WARM_UP_TIMEOUT_MILLIS = 8_000L
        private const val GEMINI_USAGE_REDIRECT_MIN_INTERVAL_MS = 1_500L
        private const val GEMINI_USAGE_REDIRECT_MAX_ATTEMPTS = 2
        private const val GEMINI_SIGN_IN_CLICK_MAX_ATTEMPTS = 1
        private const val GEMINI_TERMINAL_CHECK_DELAY_MS = 4_000L
        private const val GEMINI_TERMINAL_CHECK_FALLBACK_DELAY_MS = 24_000L
        private const val LOGIN_PAGE_REACHED_MESSAGE = "Background refresh reached a provider login page."
        private const val TAG = "AIQuotaBgRefreshService"
        private const val PAGE_CAPTURE_SCRIPT =
            "(function(){return (document.documentElement.innerText||document.title||'').slice(0,12000);})()"
    }

    private fun initialAutoRefreshDelayMillis(): Long {
        return maxOf(
            INITIAL_AUTO_REFRESH_DELAY_MILLIS,
            AppUpdatedRefreshCooldown.remainingDelayMillis(applicationContext)
        )
    }

    private fun timeoutMillisForWebJob(job: ProviderRefreshJob): Long {
        val baseTimeout = ProviderRefreshPlan.timeoutMillisFor(job.providerId)
        return if (webSessionWarmUpUrl(job) == null) baseTimeout else baseTimeout + WEB_SESSION_WARM_UP_TIMEOUT_MILLIS
    }

    private fun webSessionWarmUpUrl(job: ProviderRefreshJob): String? {
        return when (job.providerId) {
            ProviderId.GEMINI -> "https://gemini.google.com/"
            ProviderId.COPILOT -> "https://github.com/"
            ProviderId.OPENCODE -> "https://opencode.ai/"
            ProviderId.CURSOR -> "https://cursor.com/"
            else -> null
        }?.takeUnless { it == job.startUrl }
    }
}
