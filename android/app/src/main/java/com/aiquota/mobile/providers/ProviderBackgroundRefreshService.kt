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
            scheduleNextTick(delayMillis = 0L)
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
        val startingSnapshot = repository.readSnapshots().firstOrNull { it.providerId == job.providerId }
        repository.markCollecting(job.providerId)
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        val outcome = when (job.mode) {
            ProviderRefreshMode.NATIVE_API -> collectNativeProviderUsage(job)
            ProviderRefreshMode.HIDDEN_WEB_COLLECTOR -> collectWebProviderUsage(job)
        }
        if (repository.readSnapshots().none { it.providerId == job.providerId }) {
            destroyProviderWebView(job.providerId)
            return
        }
        when (outcome) {
            is ServiceRefreshOutcome.Snapshot -> {
                repository.saveSnapshot(outcome.snapshot)
            }
            is ServiceRefreshOutcome.Payload -> {
                val snapshot = ProviderUsageNormalizer.normalize(
                    job.providerId,
                    outcome.rawPayload,
                    ProviderPayloadSource.STRUCTURED_SCRIPT
                )
                if (snapshot != null) {
                    repository.saveSnapshot(snapshot)
                } else if (
                    ProviderRefreshFailureClassifier.requiresInteractiveAuth(
                        job.providerId,
                        ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD
                    )
                ) {
                    if (ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(job.providerId)) {
                        ProviderSessionResetter(applicationContext).disconnect(job.providerId)
                    }
                    destroyProviderWebView(job.providerId)
                    repository.markSessionExpired(
                        job.providerId,
                        getString(R.string.provider_status_auth_required)
                    )
                } else if (job.providerId == ProviderId.GEMINI || job.providerId == ProviderId.ANTIGRAVITY) {
                    repository.markGoogleUsagePending(job.providerId, GoogleUsagePendingRetryPolicy.PENDING_MESSAGE)
                } else {
                    repository.failKeepingPrevious(job.providerId, "Background collector ran. No trusted usage payload found.")
                }
            }
            is ServiceRefreshOutcome.Failure -> {
                refreshStateRepository.recordFailure(outcome.failure.kind.name)
                if (ProviderRefreshFailureClassifier.requiresInteractiveAuth(job.providerId, outcome.failure.kind)) {
                    if (ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(job.providerId)) {
                        ProviderSessionResetter(applicationContext).disconnect(job.providerId)
                    }
                    destroyProviderWebView(job.providerId)
                    repository.markSessionExpired(
                        job.providerId,
                        outcome.failure.message
                    )
                } else if (job.providerId == ProviderId.GEMINI || job.providerId == ProviderId.ANTIGRAVITY) {
                    repository.markGoogleUsagePending(job.providerId, outcome.failure.message)
                } else {
                    repository.failKeepingPrevious(job.providerId, outcome.failure.message)
                }
                if (ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(outcome.failure.kind)) {
                    destroyProviderWebView(job.providerId)
                }
            }
            is ServiceRefreshOutcome.Cancelled -> {
                startingSnapshot?.let(repository::saveSnapshot)
            }
        }
        UsageSurfaceRefresher.refresh(applicationContext, repository)
    }

    private suspend fun collectNativeProviderUsage(job: ProviderRefreshJob): ServiceRefreshOutcome {
        if (job.providerId == ProviderId.GEMINI || job.providerId == ProviderId.ANTIGRAVITY) {
            val payload = withContext(Dispatchers.IO) {
                if (job.providerId == ProviderId.GEMINI) {
                    GeminiCliOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()
                } else {
                    AntigravityOAuthRepository(applicationContext).fetchUsagePayloadFromStoredCredential()
                }
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

    private suspend fun collectWebProviderUsage(job: ProviderRefreshJob): ServiceRefreshOutcome {
        val requestId = ++nextRequestId
        val result = withTimeoutOrNull(ProviderRefreshPlan.timeoutMillisFor(job.providerId)) {
            suspendCancellableCoroutine { continuation ->
                mainHandler.post {
                    activeWebJob = ServiceWebRefreshJob(requestId, job)
                    activeWebContinuation = continuation
                    startWebCollection(requestId, job)
                }
                continuation.invokeOnCancellation {
                    mainHandler.post {
                        clearActiveWebJob(requestId)
                    }
                }
            }
        }
        clearActiveWebJob(requestId)
        return result ?: ServiceRefreshOutcome.Failure(
            ProviderRefreshFailure(ProviderRefreshFailureKind.TIMEOUT, "Background refresh timed out.")
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWebCollection(requestId: Long, job: ProviderRefreshJob) {
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
        Log.d(TAG, "load provider=${job.providerId.storageId} start=${hostOf(job.startUrl)}${pathOf(job.startUrl)} request=$requestId")
        webView.loadUrl(job.startUrl)
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

    private fun injectCollectorIfReady(providerId: ProviderId, view: WebView, url: String, pageText: String) {
        val active = currentWebJobFor(providerId) ?: return
        val cookies = cookiesFor(url)
        if (!ProviderWebCollectorScripts.shouldRunCollector(providerId, url, cookies, pageText)) {
            Log.d(TAG, "skipInject provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)}")
            return
        }
        val injectionKey = "${active.requestId}:${providerId.storageId}:${hostOf(url)}:${pathOf(url)}"
        if (!collectorInjectionKeys.add(injectionKey)) return
        val script = ProviderWebCollectorScripts.build(
            providerId = providerId,
            cookies = cookies,
            geminiCollectorAsset = geminiCollectorAsset,
            antigravityCollectorAsset = antigravityCollectorAsset,
            observedAccountId = observedCodexAccountId,
            pageText = pageText,
            pageUrl = url
        )
        Log.d(TAG, "inject provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)}")
        view.evaluateJavascript(script, null)
    }

    private inner class ServiceCollectorWebViewClient(
        private val ownerProviderId: ProviderId
    ) : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            val active = currentWebJobFor(ownerProviderId) ?: return
            if (ProviderWebCollectorScripts.isRefreshLoginPage(ownerProviderId, url)) {
                completeWebJob(active.requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                return
            }
            injectCollectorIfReady(ownerProviderId, view, url, "")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val active = currentWebJobFor(ownerProviderId) ?: return true
            val url = request.url.toString()
            return !ProviderDefinitionRegistry.isCollectorNavigationAllowed(active.job.providerId, url)
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            captureCodexAccountId(ownerProviderId, request.url.toString())
            return null
        }

        override fun onLoadResource(view: WebView, url: String) {
            val active = currentWebJobFor(ownerProviderId) ?: return
            val pageUrl = view.url ?: url
            if (!ProviderWebCollectorScripts.shouldRunCollectorFromResource(ownerProviderId, pageUrl, url)) return
            val requestId = active.requestId
            view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
                if (currentWebJobFor(ownerProviderId)?.requestId != requestId) return@evaluateJavascript
                val pageText = decodeJsString(encoded)
                if (ProviderWebCollectorScripts.isRefreshLoginPage(ownerProviderId, pageUrl, pageText)) {
                    completeWebJob(requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                    return@evaluateJavascript
                }
                injectCollectorIfReady(ownerProviderId, view, pageUrl, pageText)
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            val active = currentWebJobFor(ownerProviderId) ?: return
            val requestId = active.requestId
            Log.d(TAG, "pageFinished provider=${ownerProviderId.storageId} url=${hostOf(url)}${pathOf(url)}")
            if (ProviderWebCollectorScripts.isRefreshLoginPage(ownerProviderId, url)) {
                completeWebJob(requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                return
            }
            view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
                if (currentWebJobFor(ownerProviderId)?.requestId != requestId) return@evaluateJavascript
                val pageText = decodeJsString(encoded)
                if (ProviderWebCollectorScripts.isRefreshLoginPage(ownerProviderId, url, pageText)) {
                    completeWebJob(requestId, ServiceRefreshOutcome.Failure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE)))
                    return@evaluateJavascript
                }
                injectCollectorIfReady(ownerProviderId, view, url, pageText)
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
            val active = currentWebJobFor(ownerProviderId) ?: return
            completeWebJob(
                active.requestId,
                ServiceRefreshOutcome.Failure(
                    ProviderRefreshFailure(
                        ProviderRefreshFailureKind.TRANSIENT_HTTP,
                        "Background refresh returned HTTP ${errorResponse.statusCode}."
                    )
                )
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
                    Log.d(TAG, "dropPayload provider=${ownerProviderId.storageId} reason=untrusted_bridge_page")
                    return@post
                }
                CookieManager.getInstance().flush()
                completeWebJob(active.requestId, ServiceRefreshOutcome.Payload(rawPayload))
            }
        }

        @JavascriptInterface
        fun postCollectorError(rawError: String) {
            mainHandler.post {
                val active = currentWebJobFor(ownerProviderId) ?: return@post
                val pageUrl = retainedWebViews[ownerProviderId]?.url.orEmpty().ifBlank { active.job.startUrl }
                if (!ProviderWebCollectorScripts.shouldAcceptCollectorError(ownerProviderId, pageUrl)) {
                    Log.d(TAG, "dropCollectorError provider=${ownerProviderId.storageId} reason=untrusted_bridge_page")
                    return@post
                }
                val errorKind = ProviderCollectorErrorPolicy.errorKind(rawError)
                val retryCount = active.collectorRetryCounts[errorKind] ?: 0
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
            val pageUrl = retainedWebViews[providerId]?.url.orEmpty().ifBlank { active.job.startUrl }
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

    private fun decodeJsString(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return runCatching { JSONObject("""{"value":$value}""").optString("value") }.getOrDefault("")
    }

    private data class ServiceWebRefreshJob(
        val requestId: Long,
        val job: ProviderRefreshJob,
        val collectorRetryCounts: MutableMap<String, Int> = mutableMapOf()
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
        fun createSessionResetIntent(context: Context, providerId: ProviderId): Intent {
            return Intent(ACTION_PROVIDER_SESSION_RESET)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PROVIDER_ID, providerId.storageId)
        }
        private const val BRIDGE_NAME = "AIQuotaCollectorBridge"
        private const val LOGIN_PAGE_REACHED_MESSAGE = "Background refresh reached a provider login page."
        private const val TAG = "AIQuotaBgRefreshService"
        private const val PAGE_CAPTURE_SCRIPT =
            "(function(){return (document.documentElement.innerText||document.title||'').slice(0,12000);})()"
    }
}
