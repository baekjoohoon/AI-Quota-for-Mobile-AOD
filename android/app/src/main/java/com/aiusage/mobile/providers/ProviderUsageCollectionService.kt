package com.aiusage.mobile.providers

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.aiusage.mobile.R
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderPreferencesRepository
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageSnapshot
import com.aiusage.mobile.notification.UsageLimitNotificationController
import com.aiusage.mobile.widget.AIUsageUnifiedGlanceWidget
import com.aiusage.mobile.widget.ProviderUsageGlanceWidget
import com.aiusage.mobile.widget.WidgetSnapshotCache
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

class ProviderUsageCollectionService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingUrls = ArrayDeque<String>()
    private var webView: WebView? = null
    private var providerId: ProviderId? = null
    private var currentUrl: String? = null
    private var currentAttempt = 0
    private var completed = false
    private var fallbackSessionSnapshot: ProviderUsageSnapshot? = null
    private var fallbackCompletionScheduled = false
    private var loginCompletionSeen = false
    private var claudeDirectUsageStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nextProviderId = intent?.getStringExtra(EXTRA_PROVIDER_ID)
            ?.let(ProviderId::fromStorageId)
        if (nextProviderId == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection start provider=${nextProviderId.storageId} source=" +
                intent.getStringExtra(EXTRA_SOURCE).orEmpty().ifBlank { SOURCE_REFRESH }
        )
        startAsForeground()
        beginCollection(nextProviderId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        finishInterruptedCollection()
        mainHandler.removeCallbacksAndMessages(null)
        destroyWebView()
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = UsageLimitNotificationController.foregroundNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun beginCollection(nextProviderId: ProviderId) {
        providerId = nextProviderId
        completed = false
        currentAttempt = 0
        fallbackSessionSnapshot = null
        fallbackCompletionScheduled = false
        loginCompletionSeen = false
        claudeDirectUsageStarted = false
        pendingUrls.clear()
        pendingUrls.addAll(ProviderUsageProbeTargets.urls(nextProviderId))
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection probes provider=${nextProviderId.storageId} count=${pendingUrls.size}"
        )
        saveRefreshingSnapshot(nextProviderId)
        if (nextProviderId == ProviderId.CODEX && CodexOAuthRepository(applicationContext).hasStoredTokens()) {
            beginCodexOAuthCollection(nextProviderId)
            return
        }
        beginWebViewCollection(nextProviderId)
    }

    private fun beginWebViewCollection(nextProviderId: ProviderId) {
        createWebView(nextProviderId)
        loadNextProbeUrl()
    }

    private fun beginCodexOAuthCollection(nextProviderId: ProviderId) {
        scope.launch(Dispatchers.IO) {
            val snapshot = runCatching {
                CodexOAuthRepository(applicationContext).fetchUsageSnapshot()
            }.getOrNull()
            mainHandler.post {
                if (completed) return@post
                if (snapshot?.connectionState == ProviderConnectionState.CONNECTED) {
                    if (snapshot.lines.isNotEmpty() && snapshot.hasLiveUsageCounters()) {
                        completed = true
                        Log.d(
                            ProviderCollectionDiagnostics.TAG,
                            "collection codexOAuthUsage provider=${nextProviderId.storageId}"
                        )
                        saveUsageSnapshot(snapshot)
                        CookieManager.getInstance().flush()
                        stopSelf()
                        return@post
                    }
                    rememberFallbackSessionSnapshot(snapshot)
                }
                beginWebViewCollection(nextProviderId)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(providerId: ProviderId) {
        destroyWebView()
        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = MOBILE_CHROME_USER_AGENT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(UsageBridge(this@ProviderUsageCollectionService), USAGE_BRIDGE_NAME)
            webViewClient = CollectionWebViewClient(providerId, this@ProviderUsageCollectionService)
        }
    }

    private fun destroyWebView() {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }

    private fun loadNextProbeUrl() {
        if (completed) return
        val nextUrl = pendingUrls.removeFirstOrNull()
        if (nextUrl == null) {
            currentUrl = null
            Log.d(
                ProviderCollectionDiagnostics.TAG,
                "collection exhausted provider=${providerId?.storageId.orEmpty()}"
            )
            finishWithoutUsage()
            return
        }
        currentUrl = nextUrl
        currentAttempt = 0
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection load provider=${providerId?.storageId.orEmpty()} url=" +
                ProviderCollectionDiagnostics.safeUrl(nextUrl)
        )
        webView?.loadUrl(nextUrl)
        mainHandler.postDelayed(
            {
                if (!completed && currentUrl == nextUrl) {
                    loadNextProbeUrl()
                }
            },
            PROBE_TIMEOUT_MS
        )
    }

    private fun scheduleUsageEvaluation(url: String) {
        if (completed) return
        mainHandler.postDelayed(
            {
                val provider = providerId ?: return@postDelayed
                val target = webView ?: return@postDelayed
                target.evaluateJavascript(ProviderLocalUsageCollector.scriptFor(provider)) { rawValue ->
                    val payload = ProviderLocalUsageCollector.decodeJavascriptString(rawValue)
                    Log.d(
                        ProviderCollectionDiagnostics.TAG,
                        "collection payload ${ProviderCollectionDiagnostics.payloadSummary(provider, payload)} " +
                            "url=${ProviderCollectionDiagnostics.safeUrl(target.url ?: url)}"
                    )
                    handleUsagePayload(payload, target.url ?: url)
                }
            },
            PAGE_EVALUATE_DELAY_MS
        )
    }

    private fun installUsageHooks(url: String) {
        if (completed) return
        val provider = providerId ?: return
        val target = webView ?: return
        if (CollectionWebViewClient.shouldBlock(provider, url)) return
        target.evaluateJavascript(ProviderLocalUsageCollector.hookScriptFor(provider), null)
    }

    private fun handleUsagePayload(payload: String, url: String) {
        if (completed) return
        val provider = providerId ?: return
        val snapshot = TextUsageExtractor.extract(provider, payload)
        if (snapshot.connectionState == ProviderConnectionState.CONNECTED) {
            if (snapshot.lines.isNotEmpty() && snapshot.hasLiveUsageCounters()) {
                rememberFallbackSessionSnapshot(snapshot)
                if (!shouldWaitForPlanLabel(provider, snapshot)) {
                    completed = true
                    saveUsageSnapshot(snapshot)
                    CookieManager.getInstance().flush()
                    stopSelf()
                    return
                }
            } else {
                rememberFallbackSessionSnapshot(snapshot)
            }
        }

        val loginComplete = ProviderLoginCompletionDetector.isLoginComplete(provider, url, payload)
        if (loginComplete) {
            loginCompletionSeen = true
            startClaudeDirectUsageFetch()
        }
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection evaluated provider=${provider.storageId} loginComplete=$loginComplete " +
                "attempt=$currentAttempt url=${ProviderCollectionDiagnostics.safeUrl(url)}"
        )
        if (loginComplete && currentAttempt < MAX_EVALUATION_ATTEMPTS) {
            currentAttempt += 1
            scheduleUsageEvaluation(url)
            return
        }
        loadNextProbeUrl()
    }

    private fun handleBridgeUsagePayload(payload: String) {
        if (completed) return
        val provider = providerId ?: return
        val url = webView?.url.orEmpty()
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection bridgePayload ${ProviderCollectionDiagnostics.payloadSummary(provider, payload)} " +
                "url=${ProviderCollectionDiagnostics.safeUrl(url)}"
        )
        val snapshot = TextUsageExtractor.extract(provider, payload)
        if (snapshot.connectionState == ProviderConnectionState.CONNECTED) {
            if (snapshot.lines.isNotEmpty() && snapshot.hasLiveUsageCounters()) {
                rememberFallbackSessionSnapshot(snapshot)
                if (!shouldWaitForPlanLabel(provider, snapshot)) {
                    completed = true
                    saveUsageSnapshot(snapshot)
                    CookieManager.getInstance().flush()
                    stopSelf()
                    return
                }
            } else {
                rememberFallbackSessionSnapshot(snapshot)
            }
        }
        val loginComplete = ProviderLoginCompletionDetector.isLoginComplete(provider, url, payload)
        if (loginComplete) {
            loginCompletionSeen = true
        }
        if (
            provider != ProviderId.CURSOR &&
            loginComplete &&
            payloadHasCollectedProviderSignals(payload) &&
            canFinishWithExistingUsage(provider, snapshot) &&
            finishWithExistingUsage(provider)
        ) {
            return
        }
    }

    private fun finishWithoutUsage() {
        if (completed) return
        completed = true
        val provider = providerId ?: return
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection finishWithoutUsage provider=${provider.storageId}"
        )
        val repository = LocalUsageRepository(applicationContext)
        val currentSnapshot = repository.readSnapshots().firstOrNull { it.providerId == provider }
        val fallbackSession = fallbackSessionSnapshot
        val freshConnectionObserved = loginCompletionSeen ||
            fallbackSession?.connectionState == ProviderConnectionState.CONNECTED
        val existingLines = currentSnapshot?.lines.orEmpty().filter { it.isTrustedStoredLine() }
        val existingCounterLines = existingLines.filter { it.isTrustedCounterLine() }
        val fallbackLines = fallbackSession?.lines.orEmpty().filter { it.isTrustedStoredLine() }
        val snapshotLines = when {
            fallbackLines.isNotEmpty() -> fallbackLines
            existingCounterLines.isNotEmpty() -> existingLines
            freshConnectionObserved -> existingLines
            else -> existingLines
        }
        val sortedSnapshotLines = sortStoredLines(provider, snapshotLines)
        val hasUsageEvidence = sortedSnapshotLines.isNotEmpty()
        repository.saveSnapshot(
            ProviderUsageSnapshot(
                providerId = provider,
                connectionState = if (freshConnectionObserved || hasUsageEvidence) {
                    ProviderConnectionState.CONNECTED
                } else {
                    ProviderConnectionState.UNAVAILABLE
                },
                refreshState = ProviderRefreshState.IDLE,
                planLabel = carriedPlanLabel(
                    provider = provider,
                    explicitPlanLabel = fallbackSession?.planLabel,
                    currentPlanLabel = currentSnapshot?.planLabel
                )?.takeIf { freshConnectionObserved || hasUsageEvidence },
                updatedAt = Instant.now().toString(),
                lines = sortedSnapshotLines,
                message = if (sortedSnapshotLines.isNotEmpty()) {
                    getString(R.string.provider_usage_updated_message)
                } else if (freshConnectionObserved) {
                    getString(R.string.provider_usage_not_found_message, provider.displayName)
                } else {
                    getString(R.string.provider_login_unavailable_message, provider.displayName)
                }
            )
        )
        refreshDisplayOutputs()
        CookieManager.getInstance().flush()
        stopSelf()
    }

    private fun finishWithExistingUsage(provider: ProviderId): Boolean {
        if (completed) return true
        val repository = LocalUsageRepository(applicationContext)
        val currentSnapshot = repository.readSnapshots().firstOrNull { it.providerId == provider }
        if (
            currentSnapshot?.connectionState != ProviderConnectionState.CONNECTED ||
            currentSnapshot.lines.none { it.isTrustedCounterLine() }
        ) {
            return false
        }
        val fallbackPlan = fallbackSessionSnapshot
            ?.takeIf { it.providerId == provider }
            ?.planLabel
            ?.takeIf { it.isNotBlank() }
        completed = true
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection finishWithExistingUsage provider=${provider.storageId}"
        )
        repository.saveSnapshot(
            currentSnapshot.copy(
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                planLabel = carriedPlanLabel(provider, fallbackPlan, currentSnapshot.planLabel),
                lines = dedupeUsageLines(provider, currentSnapshot.lines.filter { it.isTrustedStoredLine() }),
                message = getString(R.string.provider_usage_updated_message)
            )
        )
        refreshDisplayOutputs()
        CookieManager.getInstance().flush()
        stopSelf()
        return true
    }

    private fun rememberFallbackSessionSnapshot(snapshot: ProviderUsageSnapshot) {
        if (snapshot.connectionState != ProviderConnectionState.CONNECTED) return
        val provider = snapshot.providerId
        val current = fallbackSessionSnapshot
        fallbackSessionSnapshot = when {
            current == null -> snapshot
            snapshot.lines.size > current.lines.size -> snapshot.copy(
                planLabel = snapshot.planLabel?.takeIf { it.isNotBlank() } ?: current.planLabel
            )
            current.planLabel.isNullOrBlank() && !snapshot.planLabel.isNullOrBlank() -> current.copy(
                planLabel = snapshot.planLabel,
                updatedAt = snapshot.updatedAt,
                message = snapshot.message
            )
            else -> current
        }
        if (fallbackCompletionScheduled) return
        fallbackCompletionScheduled = true
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection fallbackUsage provider=${provider.storageId} delayMs=$PLAN_ONLY_FALLBACK_DELAY_MS"
        )
        mainHandler.postDelayed(
            {
                if (completed) return@postDelayed
                val fallback = fallbackSessionSnapshot ?: return@postDelayed
                if (
                    shouldWaitForPlanLabel(fallback.providerId, fallback) &&
                    (pendingUrls.isNotEmpty() || currentUrl != null)
                ) {
                    fallbackCompletionScheduled = false
                    Log.d(
                        ProviderCollectionDiagnostics.TAG,
                        "collection waitPlan provider=${fallback.providerId.storageId}"
                    )
                    return@postDelayed
                }
                if (
                    fallback.lines.none { it.isTrustedCounterLine() } &&
                    canFinishWithExistingUsage(fallback.providerId, fallback) &&
                    finishWithExistingUsage(fallback.providerId)
                ) {
                    return@postDelayed
                }
                completed = true
                Log.d(
                    ProviderCollectionDiagnostics.TAG,
                    "collection finishFallbackUsage provider=${fallback.providerId.storageId}"
                )
                saveUsageSnapshot(fallback)
                CookieManager.getInstance().flush()
                stopSelf()
            },
            PLAN_ONLY_FALLBACK_DELAY_MS
        )
    }

    private fun startClaudeDirectUsageFetch() {
        if (providerId != ProviderId.CLAUDE || completed || claudeDirectUsageStarted) return
        val cookies = CookieManager.getInstance().getCookie("https://claude.ai/").orEmpty()
        val organizationId = CLAUDE_LAST_ACTIVE_ORG.find(cookies)?.groupValues?.getOrNull(1)
        if (organizationId.isNullOrBlank()) return
        claudeDirectUsageStarted = true
        val endpoint = "https://claude.ai/api/organizations/$organizationId/usage"
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "collection claudeDirectUsageWebView url=" +
                ProviderCollectionDiagnostics.safeUrl(endpoint)
        )
        webView?.loadUrl(endpoint, mapOf("Accept" to "application/json"))
    }

    private fun shouldWaitForPlanLabel(
        provider: ProviderId,
        snapshot: ProviderUsageSnapshot
    ): Boolean {
        if (provider != ProviderId.CLAUDE && provider != ProviderId.GEMINI) return false
        if (!snapshot.planLabel.isNullOrBlank()) return false
        if (!fallbackSessionSnapshot?.planLabel.isNullOrBlank()) return false
        val currentSnapshot = LocalUsageRepository(applicationContext)
            .readSnapshots()
            .firstOrNull { it.providerId == provider }
        return carriedPlanLabel(provider, null, currentSnapshot?.planLabel).isNullOrBlank()
    }

    private fun canFinishWithExistingUsage(
        provider: ProviderId,
        sessionSnapshot: ProviderUsageSnapshot
    ): Boolean {
        if (provider != ProviderId.CLAUDE && provider != ProviderId.GEMINI) return true
        if (!sessionSnapshot.planLabel.isNullOrBlank()) return true
        if (!fallbackSessionSnapshot?.planLabel.isNullOrBlank()) return true
        val currentSnapshot = LocalUsageRepository(applicationContext)
            .readSnapshots()
            .firstOrNull { it.providerId == provider }
        return !carriedPlanLabel(provider, null, currentSnapshot?.planLabel).isNullOrBlank()
    }

    private fun ProviderUsageSnapshot.hasLiveUsageCounters(): Boolean {
        return lines.any { line ->
            line.isTrustedCounterLine() &&
                (providerId != ProviderId.CURSOR || line.isCursorLiveCounterLine())
        }
    }

    private fun payloadHasCollectedProviderSignals(payload: String): Boolean {
        val response = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        val data = response.optJSONObject("d") ?: response.optJSONObject("data")
        val plan = data?.optString("p").orEmpty()
            .ifBlank { data?.optString("plan").orEmpty() }
        val limits = data?.optJSONArray("x") ?: data?.optJSONArray("limits")
        val endpointSummaries = response.optJSONArray("q")
        return plan.isMeaningfulJsonString() ||
            (limits != null && limits.length() > 0) ||
            (endpointSummaries != null && endpointSummaries.length() > 0)
    }

    private fun String.isMeaningfulJsonString(): Boolean {
        return isNotBlank() && !equals("null", ignoreCase = true)
    }

    private fun finishInterruptedCollection() {
        if (completed) return
        val provider = providerId ?: return
        completed = true
        Log.w(
            ProviderCollectionDiagnostics.TAG,
            "collection interrupted provider=${provider.storageId}"
        )
        val repository = LocalUsageRepository(applicationContext)
        val currentSnapshot = repository.readSnapshots().firstOrNull { it.providerId == provider }
        val wasConnected = currentSnapshot?.connectionState == ProviderConnectionState.CONNECTED
        repository.saveSnapshot(
            (currentSnapshot ?: ProviderUsageSnapshot.disconnected(provider)).copy(
                connectionState = if (wasConnected) {
                    ProviderConnectionState.CONNECTED
                } else {
                    ProviderConnectionState.UNAVAILABLE
                },
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                lines = currentSnapshot?.lines.orEmpty().filter { it.isTrustedStoredLine() },
                message = if (wasConnected) {
                    when {
                        currentSnapshot?.lines?.any { it.isTrustedStoredLine() } == true -> {
                            getString(R.string.provider_usage_updated_message)
                        }
                        currentSnapshot?.message != getString(R.string.provider_refresh_started_message) -> {
                            currentSnapshot?.message
                        }
                        else -> {
                            getString(R.string.provider_usage_not_found_message, provider.displayName)
                        }
                    }
                } else {
                    getString(R.string.provider_login_unavailable_message, provider.displayName)
                }
            )
        )
        refreshDisplayOutputs()
        CookieManager.getInstance().flush()
    }

    private fun saveRefreshingSnapshot(providerId: ProviderId) {
        val repository = LocalUsageRepository(applicationContext)
        val currentSnapshot = repository.readSnapshots().firstOrNull { it.providerId == providerId }
        repository.saveSnapshot(
            (currentSnapshot ?: ProviderUsageSnapshot.disconnected(providerId)).copy(
                connectionState = if (currentSnapshot?.connectionState == ProviderConnectionState.CONNECTED) {
                    ProviderConnectionState.CONNECTED
                } else {
                    ProviderConnectionState.CONNECTING
                },
                refreshState = ProviderRefreshState.REFRESHING,
                planLabel = carriedPlanLabel(providerId, null, currentSnapshot?.planLabel),
                updatedAt = Instant.now().toString(),
                lines = currentSnapshot?.lines.orEmpty().filter { it.isTrustedStoredLine() },
                message = getString(R.string.provider_refresh_started_message)
            )
        )
        refreshDisplayOutputs()
    }

    private fun saveUsageSnapshot(snapshot: ProviderUsageSnapshot) {
        val repository = LocalUsageRepository(applicationContext)
        val currentSnapshot = repository.readSnapshots().firstOrNull { it.providerId == snapshot.providerId }
        val mergedLines = dedupeUsageLines(snapshot.providerId, snapshot.lines.filter { it.isTrustedStoredLine() })
        repository.saveSnapshot(
            snapshot.copy(
                connectionState = ProviderConnectionState.CONNECTED,
                refreshState = ProviderRefreshState.IDLE,
                planLabel = carriedPlanLabel(
                    provider = snapshot.providerId,
                    explicitPlanLabel = snapshot.planLabel,
                    currentPlanLabel = currentSnapshot?.planLabel
                ),
                updatedAt = Instant.now().toString(),
                lines = mergedLines,
                message = if (mergedLines.isNotEmpty()) {
                    getString(R.string.provider_usage_updated_message)
                } else {
                    getString(R.string.provider_usage_not_found_message, snapshot.providerId.displayName)
                }
            )
        )
        refreshDisplayOutputs()
    }

    private fun dedupeUsageLines(
        provider: ProviderId,
        incoming: List<com.aiusage.mobile.local.ProviderUsageLine>
    ): List<com.aiusage.mobile.local.ProviderUsageLine> {
        val deduped = LinkedHashMap<String, com.aiusage.mobile.local.ProviderUsageLine>()
        incoming.forEach { line ->
            val key = listOf(
                line.label,
                line.windowText,
                line.category,
                line.unit,
                normalizedUsageSource(line.sourceLabel)
            )
                .joinToString("|")
                .lowercase()
            deduped.putIfAbsent(key, line)
        }
        return sortStoredLines(provider, deduped.values.toList())
    }

    private fun normalizedUsageSource(sourceLabel: String?): String {
        return sourceLabel.orEmpty()
            .replace(Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"""), ":id")
            .replace(Regex("""[A-Za-z0-9_-]{18,}"""), ":id")
    }

    private fun carriedPlanLabel(
        provider: ProviderId,
        explicitPlanLabel: String?,
        currentPlanLabel: String?
    ): String? {
        normalizedPlanLabel(provider, explicitPlanLabel)?.let { return it }
        val current = normalizedPlanLabel(provider, currentPlanLabel) ?: return null
        return if (provider == ProviderId.GEMINI && current.equals("Free", ignoreCase = true)) {
            null
        } else {
            current
        }
    }

    private fun normalizedPlanLabel(provider: ProviderId, planLabel: String?): String? {
        val value = planLabel?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val compact = value.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "")
        return when (provider) {
            ProviderId.CODEX -> when (compact) {
                "prolite" -> "Pro 5x"
                else -> value
            }
            ProviderId.GEMINI -> when (compact) {
                "pro", "aipro", "googleaipro" -> "Google AI Pro"
                "ultra", "aiultra", "googleaiultra" -> "Google AI Ultra"
                "advanced", "geminiadvanced" -> "Gemini Advanced"
                "aipremium", "googleoneaipremium" -> "Google One AI Premium"
                "free", "geminifree" -> "Free"
                else -> value
            }
            else -> value
        }
    }

    private fun sortStoredLines(
        provider: ProviderId,
        lines: List<com.aiusage.mobile.local.ProviderUsageLine>
    ): List<com.aiusage.mobile.local.ProviderUsageLine> {
        if (provider != ProviderId.GEMINI) return lines
        val order = mapOf(
            "pro" to 0,
            "flash" to 1,
            "deep research" to 2
        )
        return lines.sortedWith(
            compareBy<com.aiusage.mobile.local.ProviderUsageLine> {
                order[it.label.lowercase()] ?: 100
            }.thenBy { it.label.lowercase() }
        )
    }

    private fun com.aiusage.mobile.local.ProviderUsageLine.isPlanOnlyLine(): Boolean {
        return label.contains("subscription", ignoreCase = true) &&
            remainingPercent == null &&
            usedAmount == null &&
            limitAmount == null &&
            remainingAmount == null
    }

    private fun com.aiusage.mobile.local.ProviderUsageLine.isTrustedStoredLine(): Boolean {
        return isTrustedCounterLine()
    }

    private fun com.aiusage.mobile.local.ProviderUsageLine.isTrustedCounterLine(): Boolean {
        if (isPlanOnlyLine()) return false
        if (isDerivedDefaultLine()) return false
        val labelText = listOf(label, unit).joinToString(" ").lowercase()
        val allText = listOf(label, unit, sourceLabel).joinToString(" ").lowercase()
        if (Regex("""\b(sitemap|completed)\b""").containsMatchIn(labelText)) return false
        if ("sitemap" in allText) return false
        if (providerId == ProviderId.CLAUDE) {
            val source = sourceLabel.orEmpty().lowercase()
            val window = windowText.orEmpty().lowercase()
            if (
                remainingPercent == null &&
                limitAmount == null &&
                usedAmount == null &&
                (source == "/new" || source == "/" || source.isBlank()) &&
                (labelText in setOf("session", "weekly") || window in setOf("session", "weekly"))
            ) {
                return false
            }
        }
        if (providerId == ProviderId.COPILOT) {
            if ("/features/copilot/plans" in allText) return false
            if (Regex("""\b[a-z0-9-]+\.(com|net|org|io|dev|ai)\b""").containsMatchIn(labelText)) return false
        }
        if (providerId == ProviderId.GEMINI && labelText.startsWith("gemini quota")) return false
        return true
    }

    private fun com.aiusage.mobile.local.ProviderUsageLine.isCursorLiveCounterLine(): Boolean {
        if (remainingPercent != null) return true
        if (limitAmount != null && limitAmount > 0.0) return true
        if (!resetsAt.isNullOrBlank()) return true
        val source = sourceLabel.orEmpty().lowercase()
        return "usage-summary" in source || "planusage" in source || "requestusage" in source
    }

    private fun com.aiusage.mobile.local.ProviderUsageLine.isDerivedDefaultLine(): Boolean {
        return sourceLabel?.contains("subscription default", ignoreCase = true) == true
    }

    private fun refreshDisplayOutputs() {
        val updatedAt = Instant.now().toString()
        val usageRepository = LocalUsageRepository(applicationContext)
        val preferencesRepository = ProviderPreferencesRepository(applicationContext)
        val displayOnlyJson = usageRepository.exportDisplayOnlyCache(
            order = preferencesRepository.providerOrder(),
            hidden = preferencesRepository.hiddenProviders(),
            updatedAt = updatedAt
        )
        WidgetSnapshotCache(applicationContext).writeLocalDisplaySnapshot(displayOnlyJson, updatedAt)
        UsageLimitNotificationController.updateFromCache(applicationContext)
        scope.launch {
            AIUsageUnifiedGlanceWidget().updateAll(applicationContext)
            ProviderUsageGlanceWidget().updateAll(applicationContext)
        }
    }

    private class CollectionWebViewClient(
        private val providerId: ProviderId,
        private val service: ProviderUsageCollectionService
        ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (request.isForMainFrame && shouldBlock(request.url.toString())) {
                Log.w(
                    ProviderCollectionDiagnostics.TAG,
                    "collection blocked provider=${providerId.storageId} url=" +
                        ProviderCollectionDiagnostics.safeUrl(request.url.toString())
                )
                service.loadNextProbeUrl()
                return true
            }
            return false
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (shouldBlock(url)) {
                Log.w(
                    ProviderCollectionDiagnostics.TAG,
                    "collection blocked provider=${providerId.storageId} url=" +
                        ProviderCollectionDiagnostics.safeUrl(url)
                )
                service.loadNextProbeUrl()
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (url != null && shouldBlock(url)) {
                Log.w(
                    ProviderCollectionDiagnostics.TAG,
                    "collection blockedStart provider=${providerId.storageId} url=" +
                        ProviderCollectionDiagnostics.safeUrl(url)
                )
                service.loadNextProbeUrl()
                return
            }
            super.onPageStarted(view, url, favicon)
            Log.d(
                ProviderCollectionDiagnostics.TAG,
                "collection pageStarted provider=${providerId.storageId} url=" +
                    ProviderCollectionDiagnostics.safeUrl(url)
            )
            url?.let { currentUrl -> service.installUsageHooks(currentUrl) }
        }

        override fun onPageCommitVisible(view: WebView, url: String?) {
            if (url != null && !shouldBlock(url)) {
                service.installUsageHooks(url)
            }
            super.onPageCommitVisible(view, url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            val currentUrl = url.orEmpty()
            if (shouldBlock(currentUrl)) {
                Log.w(
                    ProviderCollectionDiagnostics.TAG,
                    "collection blockedFinish provider=${providerId.storageId} url=" +
                        ProviderCollectionDiagnostics.safeUrl(currentUrl)
                )
                service.loadNextProbeUrl()
                return
            }
            super.onPageFinished(view, url)
            Log.d(
                ProviderCollectionDiagnostics.TAG,
                "collection pageFinished provider=${providerId.storageId} url=" +
                    ProviderCollectionDiagnostics.safeUrl(currentUrl)
            )
            service.scheduleUsageEvaluation(currentUrl)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (!request.isForMainFrame) {
                val url = request.url.toString()
                if (shouldLogResourceUrl(url)) {
                    Log.d(
                        ProviderCollectionDiagnostics.TAG,
                        "collection resource provider=${providerId.storageId} url=" +
                            ProviderCollectionDiagnostics.safeUrl(url)
                    )
                }
            }
            if (request.isForMainFrame && shouldBlock(request.url.toString())) {
                return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                Log.w(
                    ProviderCollectionDiagnostics.TAG,
                    "collection mainFrameError provider=${providerId.storageId} url=" +
                        "${ProviderCollectionDiagnostics.safeUrl(request.url.toString())} " +
                        ProviderCollectionDiagnostics.webError(error.errorCode, error.description)
                )
                service.loadNextProbeUrl()
            }
            super.onReceivedError(view, request, error)
        }

        @Deprecated("Deprecated in Java")
        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            Log.w(
                ProviderCollectionDiagnostics.TAG,
                "collection mainFrameError provider=${providerId.storageId} url=" +
                    "${ProviderCollectionDiagnostics.safeUrl(failingUrl)} " +
                    ProviderCollectionDiagnostics.webError(errorCode, description)
            )
            service.loadNextProbeUrl()
            super.onReceivedError(view, errorCode, description, failingUrl)
        }

        private fun shouldBlock(url: String): Boolean {
            return shouldBlock(providerId, url)
        }

        private fun shouldLogResourceUrl(url: String): Boolean {
            if (!ProviderHostAllowlist.isAllowed(providerId, url)) return false
            val path = Uri.parse(url).path.orEmpty()
            return RESOURCE_DIAGNOSTIC_PATTERN.containsMatchIn(path)
        }

        companion object {
            private val RESOURCE_DIAGNOSTIC_PATTERN =
                Regex(
                    "backend-api|/api/|account|accounts|usage|limit|quota|subscription|billing|rate|entitlement|settings",
                    RegexOption.IGNORE_CASE
                )

            fun shouldBlock(providerId: ProviderId, url: String): Boolean {
                val scheme = Uri.parse(url).scheme?.lowercase().orEmpty()
                if (scheme.isNotBlank() && scheme != "http" && scheme != "https") return true
                return !ProviderHostAllowlist.isAllowed(providerId, url)
            }
        }
    }

    private class UsageBridge(
        private val service: ProviderUsageCollectionService
    ) {
        @JavascriptInterface
        fun onUsagePayload(payload: String?) {
            service.mainHandler.post {
                service.handleBridgeUsagePayload(payload.orEmpty())
            }
        }
    }

    companion object {
        const val SOURCE_LOGIN = "login"
        const val SOURCE_REFRESH = "refresh"
        private const val EXTRA_PROVIDER_ID = "com.aiusage.mobile.providers.extra.COLLECT_PROVIDER_ID"
        private const val EXTRA_SOURCE = "com.aiusage.mobile.providers.extra.COLLECT_SOURCE"
        private const val USAGE_BRIDGE_NAME = "AIUsageLocalCollector"
        private const val NOTIFICATION_ID = 1002
        private const val PAGE_EVALUATE_DELAY_MS = 900L
        private const val PLAN_ONLY_FALLBACK_DELAY_MS = 6_000L
        private const val PROBE_TIMEOUT_MS = 20_000L
        private const val MAX_EVALUATION_ATTEMPTS = 8
        private val CLAUDE_LAST_ACTIVE_ORG = Regex("""(?:^|;\s*)lastActiveOrg=([0-9a-fA-F-]{16,})""")
        private const val MOBILE_CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/119.0.0.0 Mobile Safari/537.36"

        fun start(context: Context, providerId: ProviderId, source: String = SOURCE_REFRESH) {
            val intent = Intent(context, ProviderUsageCollectionService::class.java)
                .putExtra(EXTRA_PROVIDER_ID, providerId.storageId)
                .putExtra(EXTRA_SOURCE, source)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
