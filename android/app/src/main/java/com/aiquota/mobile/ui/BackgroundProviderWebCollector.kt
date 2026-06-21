package com.aiquota.mobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.providers.CopilotNativeUsageFetcher
import com.aiquota.mobile.providers.CursorNativeUsageFetcher
import com.aiquota.mobile.providers.ProviderDefinitionRegistry
import com.aiquota.mobile.providers.ProviderCollectorErrorPolicy
import com.aiquota.mobile.providers.ProviderHiddenWebViewRetentionPolicy
import com.aiquota.mobile.providers.ProviderRefreshFailure
import com.aiquota.mobile.providers.ProviderRefreshFailureKind
import com.aiquota.mobile.providers.ProviderRefreshHttpErrorPolicy
import com.aiquota.mobile.providers.ProviderRefreshJob
import com.aiquota.mobile.providers.ProviderRefreshMode
import com.aiquota.mobile.providers.ProviderRefreshPlan
import com.aiquota.mobile.providers.ProviderScopedStateRepository
import com.aiquota.mobile.providers.ProviderRefreshTimeoutPolicy
import com.aiquota.mobile.providers.ProviderWebCollectorScripts
import com.aiquota.mobile.providers.ProviderWebViewUserAgent
import java.net.URI
import kotlinx.coroutines.delay
import org.json.JSONObject

data class QueuedProviderRefreshJob(
    val requestId: Long,
    val job: ProviderRefreshJob
)

@Composable
fun BackgroundProviderWebCollector(
    currentJob: QueuedProviderRefreshJob?,
    sessionResetEvent: Pair<ProviderId, Long>? = null,
    geminiCollectorAsset: String,
    antigravityCollectorAsset: String,
    onPayload: (QueuedProviderRefreshJob, String) -> Unit,
    onError: (QueuedProviderRefreshJob, ProviderRefreshFailure) -> Unit,
    onFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestJob = rememberUpdatedState(currentJob)
    val latestOnPayload = rememberUpdatedState(onPayload)
    val latestOnError = rememberUpdatedState(onError)
    val latestOnFinished = rememberUpdatedState(onFinished)
    val webCollectorJob = currentJob?.takeIf { it.job.mode == ProviderRefreshMode.HIDDEN_WEB_COLLECTOR }
    val retainedWebViews = remember { mutableMapOf<ProviderId, WebView>() }
    val loadedRequestIds = remember { mutableMapOf<ProviderId, Long>() }

    fun destroyProviderWebView(providerId: ProviderId) {
        loadedRequestIds.remove(providerId)
        retainedWebViews.remove(providerId)?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroyBackgroundCollector()
        }
    }

    fun destroyAllProviderWebViews() {
        retainedWebViews.keys.toList().forEach(::destroyProviderWebView)
    }

    LaunchedEffect(sessionResetEvent) {
        sessionResetEvent?.first?.let(::destroyProviderWebView)
    }

    LaunchedEffect(currentJob?.requestId) {
        val job = currentJob ?: return@LaunchedEffect
        delay(ProviderRefreshPlan.timeoutMillisFor(job.job.providerId))
        val lastUrl = retainedWebViews[job.job.providerId]?.url ?: job.job.startUrl
        val failure = ProviderRefreshTimeoutPolicy.failureFor(job.job.providerId, lastUrl)
        if (job.job.mode == ProviderRefreshMode.HIDDEN_WEB_COLLECTOR &&
            ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(failure.kind)
        ) {
            destroyProviderWebView(job.job.providerId)
        }
        latestOnError.value(job, failure)
        latestOnFinished.value(job.requestId)
    }

    AndroidView(
        modifier = modifier
            .size(1.dp)
            .alpha(0f),
        factory = { context ->
            FrameLayout(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                visibility = View.INVISIBLE
            }
        },
        update = { container ->
            val activeJob = webCollectorJob
            if (activeJob == null) {
                retainedWebViews.values.forEach { it.visibility = View.INVISIBLE }
            } else {
                val providerId = activeJob.job.providerId
                if (ProviderHiddenWebViewRetentionPolicy.shouldRetain(providerId)) {
                    val webView = retainedWebViews.getOrPut(providerId) {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        WebView(container.context).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            configureForBackgroundCollection(cookieManager, providerId)
                            addJavascriptInterface(
                                BackgroundUsageBridge(
                                    ownerProviderId = providerId,
                                    context = container.context.applicationContext,
                                    currentJob = { latestJob.value },
                                    currentPageUrl = { retainedWebViews[providerId]?.url.orEmpty() },
                                    onPayload = { job, payload -> latestOnPayload.value(job, payload) },
                                    onError = { job, message -> latestOnError.value(job, message) },
                                    onFinished = { requestId -> latestOnFinished.value(requestId) },
                                    onRecreateWebView = { failedProviderId -> destroyProviderWebView(failedProviderId) }
                                ),
                                BRIDGE_NAME
                            )
                            webChromeClient = BackgroundCollectorChromeClient()
                            webViewClient = BackgroundCollectorWebViewClient(
                                ownerProviderId = providerId,
                                currentJob = { latestJob.value },
                                geminiCollectorAsset = geminiCollectorAsset,
                                antigravityCollectorAsset = antigravityCollectorAsset,
                                onPayload = { job, payload -> latestOnPayload.value(job, payload) },
                                onError = { job, message -> latestOnError.value(job, message) },
                                onFinished = { requestId -> latestOnFinished.value(requestId) },
                                onRecreateWebView = { failedProviderId -> destroyProviderWebView(failedProviderId) }
                            )
                        }
                    }
                    if (webView.parent !== container) {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        container.addView(webView, FrameLayout.LayoutParams(1, 1))
                    }
                    retainedWebViews.forEach { (retainedProviderId, retainedWebView) ->
                        retainedWebView.visibility = if (retainedProviderId == providerId) View.VISIBLE else View.INVISIBLE
                    }
                    if (loadedRequestIds[providerId] != activeJob.requestId) {
                        loadedRequestIds[providerId] = activeJob.requestId
                        Log.d(
                            "AIQuotaBgCollector",
                            "load provider=${providerId.storageId} start=${hostOf(activeJob.job.startUrl)}${pathOf(activeJob.job.startUrl)}"
                        )
                        webView.loadUrl(activeJob.job.startUrl)
                    }
                }
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            destroyAllProviderWebViews()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForBackgroundCollection(cookieManager: CookieManager, providerId: ProviderId) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.allowFileAccess = false
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.setSupportMultipleWindows(false)
    settings.userAgentString = ProviderWebViewUserAgent.hiddenCollectorUserAgent(context, providerId)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        cookieManager.setAcceptThirdPartyCookies(this, true)
    }
}

private fun WebView.destroyBackgroundCollector() {
    runCatching { stopLoading() }
    runCatching { removeJavascriptInterface(BRIDGE_NAME) }
    runCatching { webChromeClient = null }
    runCatching { webViewClient = WebViewClient() }
    runCatching { destroy() }
}

private class BackgroundCollectorChromeClient : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        android.util.Log.d(
            "AIQuotaBgCollectorConsole",
            "consoleLevel=${consoleMessage.messageLevel()} line=${consoleMessage.lineNumber()}"
        )
        return true
    }
}

private class BackgroundCollectorWebViewClient(
    private val ownerProviderId: ProviderId,
    private val currentJob: () -> QueuedProviderRefreshJob?,
    private val geminiCollectorAsset: String,
    private val antigravityCollectorAsset: String,
    private val onPayload: (QueuedProviderRefreshJob, String) -> Unit,
    private val onError: (QueuedProviderRefreshJob, ProviderRefreshFailure) -> Unit,
    private val onFinished: (Long) -> Unit,
    private val onRecreateWebView: (ProviderId) -> Unit
) : WebViewClient() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val collectorInjectionKeys = mutableSetOf<String>()
    private val terminalRequestIds = mutableSetOf<Long>()
    private var observedCodexAccountId: String? = null

    private fun currentProviderJob(): QueuedProviderRefreshJob? {
        return currentJob()?.takeIf { it.job.providerId == ownerProviderId }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        val job = currentProviderJob() ?: return
        if (ProviderWebCollectorScripts.isRefreshLoginPage(job.job.providerId, url)) {
            finishWithErrorOnce(job, ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE))
            return
        }
        injectCollectorIfReady(view, url, "")
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val job = currentProviderJob() ?: return true
        val url = request.url.toString()
        return !ProviderDefinitionRegistry.isCollectorNavigationAllowed(job.job.providerId, url)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        captureCodexAccountId(request.url.toString())
        return null
    }

    override fun onLoadResource(view: WebView, url: String) {
        val job = currentProviderJob() ?: return
        val pageUrl = view.url ?: url
        if (!ProviderWebCollectorScripts.shouldRunCollectorFromResource(job.job.providerId, pageUrl, url)) return
        val requestId = job.requestId
        val providerId = job.job.providerId
        view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
            val activeJob = currentProviderJob() ?: return@evaluateJavascript
            if (activeJob.requestId != requestId || activeJob.job.providerId != providerId) return@evaluateJavascript
            if (!isSameDocument(view.url, pageUrl)) return@evaluateJavascript
            val pageText = decodeJsString(encoded)
            if (ProviderWebCollectorScripts.isRefreshLoginPage(providerId, pageUrl, pageText)) {
                finishWithErrorOnce(job, ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE))
                return@evaluateJavascript
            }
            injectCollectorIfReady(view, pageUrl, pageText)
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        val job = currentProviderJob() ?: return
        val requestId = job.requestId
        val providerId = job.job.providerId
        Log.d(
            "AIQuotaBgCollector",
            "pageFinished provider=${providerId.storageId} start=${hostOf(job.job.startUrl)}${pathOf(job.job.startUrl)} url=${hostOf(url)}${pathOf(url)}"
        )
        if (ProviderWebCollectorScripts.isRefreshLoginPage(providerId, url)) {
            finishWithErrorOnce(job, ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE))
            return
        }
        view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
            val activeJob = currentProviderJob() ?: return@evaluateJavascript
            if (activeJob.requestId != requestId || activeJob.job.providerId != providerId) return@evaluateJavascript
            if (!isSameDocument(view.url, url)) return@evaluateJavascript
            val pageText = decodeJsString(encoded)
            if (ProviderWebCollectorScripts.isRefreshLoginPage(providerId, url, pageText)) {
                finishWithErrorOnce(job, ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE))
                return@evaluateJavascript
            }
            injectCollectorIfReady(view, url, pageText)
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        val job = currentProviderJob() ?: return
        val failure = ProviderRefreshFailure(
            ProviderRefreshFailureKind.TRANSIENT_PAGE_LOAD,
            "Background refresh page failed to load."
        )
        if (ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(failure.kind)) {
            onRecreateWebView(job.job.providerId)
        }
        onError(job, failure)
        onFinished(job.requestId)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (!request.isForMainFrame || errorResponse.statusCode < 400) return
        val job = currentProviderJob() ?: return
        Log.d(
            "AIQuotaBgCollector",
            "httpError provider=${job.job.providerId.storageId} status=${errorResponse.statusCode} url=${hostOf(request.url.toString())}${pathOf(request.url.toString())}"
        )
        if (ProviderRefreshHttpErrorPolicy.shouldIgnoreMainFrameHttpError(
                job.job.providerId,
                request.url.toString(),
                errorResponse.statusCode
            )
        ) {
            return
        }
        val failure = ProviderRefreshHttpErrorPolicy.failureForMainFrameHttpError(
            job.job.providerId,
            request.url.toString(),
            errorResponse.statusCode
        )
        if (ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(failure.kind)) {
            onRecreateWebView(job.job.providerId)
        }
        onError(job, failure)
        onFinished(job.requestId)
    }

    private fun injectCollectorIfReady(view: WebView, url: String, pageText: String) {
        val job = currentProviderJob() ?: return
        val providerId = job.job.providerId
        val cookies = cookiesFor(url)
        if (!ProviderWebCollectorScripts.shouldRunCollector(providerId, url, cookies, pageText)) {
            Log.d(
                "AIQuotaBgCollector",
                "skipInject provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)}"
            )
            return
        }
        val injectionKey = "${job.requestId}:${providerId.storageId}:${hostOf(url)}:${routeKeyOf(url)}"
        val firstInjectionForPage = collectorInjectionKeys.add(injectionKey)
        if (providerId != ProviderId.GEMINI && providerId != ProviderId.ANTIGRAVITY && !firstInjectionForPage) return
        Log.d(
            "AIQuotaBgCollector",
            "${if (firstInjectionForPage) "inject" else "reinject"} provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)}"
        )
        val script = ProviderWebCollectorScripts.build(
            providerId = providerId,
            cookies = cookies,
            geminiCollectorAsset = geminiCollectorAsset,
            antigravityCollectorAsset = antigravityCollectorAsset,
            observedAccountId = observedCodexAccountId,
            pageText = pageText,
            pageUrl = url
        )
        view.evaluateJavascript(script, null)
    }

    private fun finishWithErrorOnce(job: QueuedProviderRefreshJob, failure: ProviderRefreshFailure) {
        if (!terminalRequestIds.add(job.requestId)) return
        if (ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(failure.kind)) {
            onRecreateWebView(job.job.providerId)
        }
        onError(job, failure)
        onFinished(job.requestId)
    }

    private fun captureCodexAccountId(url: String) {
        val job = currentProviderJob() ?: return
        if (job.job.providerId != ProviderId.CODEX) return
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

}

private class BackgroundUsageBridge(
    private val ownerProviderId: ProviderId,
    context: Context,
    private val currentJob: () -> QueuedProviderRefreshJob?,
    private val currentPageUrl: () -> String,
    private val onPayload: (QueuedProviderRefreshJob, String) -> Unit,
    private val onError: (QueuedProviderRefreshJob, ProviderRefreshFailure) -> Unit,
    private val onFinished: (Long) -> Unit,
    private val onRecreateWebView: (ProviderId) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val applicationContext = context.applicationContext

    private fun currentProviderJob(): QueuedProviderRefreshJob? {
        return currentJob()?.takeIf { it.job.providerId == ownerProviderId }
    }

    @JavascriptInterface
    fun postUsagePayload(rawPayload: String) {
        mainHandler.post {
            val job = currentProviderJob() ?: return@post
            val pageUrl = currentPageUrl().ifBlank { job.job.startUrl }
            if (!ProviderWebCollectorScripts.shouldAcceptCollectorPayload(job.job.providerId, pageUrl, rawPayload)) {
                Log.d(
                    "AIQuotaBgCollector",
                    "dropPayload provider=${job.job.providerId.storageId} reason=untrusted_bridge_page"
                )
                return@post
            }
            Log.d(
                "AIQuotaBgCollector",
                "payload provider=${job.job.providerId.storageId} length=${rawPayload.length}"
            )
            if (job.job.providerId == ProviderId.OPENCODE) {
                ProviderScopedStateRepository(applicationContext).saveOpenCodeUsageUrl(pageUrl)
            }
            CookieManager.getInstance().flush()
            onPayload(job, rawPayload)
            onFinished(job.requestId)
        }
    }

    @JavascriptInterface
    fun postCollectorError(rawError: String) {
        mainHandler.post {
            val job = currentProviderJob() ?: return@post
            val pageUrl = currentPageUrl().ifBlank { job.job.startUrl }
            if (!ProviderWebCollectorScripts.shouldAcceptCollectorError(job.job.providerId, pageUrl, rawError)) {
                Log.d(
                    "AIQuotaBgCollector",
                    "dropCollectorError provider=${job.job.providerId.storageId} reason=untrusted_bridge_page"
                )
                return@post
            }
            val errorJson = runCatching { JSONObject(rawError) }.getOrNull()
            val errorKind = errorJson?.optString("errorKind", "collector_error") ?: "collector_error"
            val messagePresent = errorJson?.optString("message").orEmpty().isNotBlank()
            Log.d(
                "AIQuotaBgCollector",
                "error provider=${job.job.providerId.storageId} kind=$errorKind messagePresent=$messagePresent"
            )
            val failure = ProviderCollectorErrorPolicy.failureFor(job.job.providerId, rawError)
            if (ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(failure.kind)) {
                onRecreateWebView(job.job.providerId)
            }
            onError(job, failure)
            onFinished(job.requestId)
        }
    }

    @JavascriptInterface
    fun fetchCursorJson(url: String, body: String?): String {
        val job = currentProviderJob() ?: return JSONObject().put("ok", false).put("error", "no_active_job").toString()
        if (job.job.providerId != ProviderId.CURSOR) {
            return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
        }
        if (!isNativeFetchBridgePageAllowed(job)) {
            return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
        }
        return CursorNativeUsageFetcher.fetchJson(url, body)
    }

    @JavascriptInterface
    fun fetchCopilotJson(url: String): String {
        val job = currentProviderJob() ?: return JSONObject().put("ok", false).put("error", "no_active_job").toString()
        if (job.job.providerId != ProviderId.COPILOT) {
            return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
        }
        if (!isNativeFetchBridgePageAllowed(job)) {
            return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
        }
        val result = CopilotNativeUsageFetcher.fetchJson(url)
        val parsed = runCatching { JSONObject(result) }.getOrNull()
        Log.d(
            "AIQuotaBgCollector",
            "provider=copilot nativeFetch endpoint=${parsed?.optString("endpoint")} status=${parsed?.optInt("status", -1)}"
        )
        return result
    }

    @JavascriptInterface
    fun fetchCopilotJsonWithAuthorization(url: String, authorizationHeader: String): String {
        val job = currentProviderJob() ?: return JSONObject().put("ok", false).put("error", "no_active_job").toString()
        if (job.job.providerId != ProviderId.COPILOT) {
            return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
        }
        if (!isNativeFetchBridgePageAllowed(job)) {
            return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
        }
        val result = CopilotNativeUsageFetcher.fetchJsonWithAuthorization(url, authorizationHeader)
        val parsed = runCatching { JSONObject(result) }.getOrNull()
        Log.d(
            "AIQuotaBgCollector",
            "provider=copilot nativeFetchAuth endpoint=${parsed?.optString("endpoint")} status=${parsed?.optInt("status", -1)}"
        )
        return result
    }

    private fun isNativeFetchBridgePageAllowed(job: QueuedProviderRefreshJob): Boolean {
        val pageUrl = currentPageUrl().ifBlank { job.job.startUrl }
        return ProviderWebCollectorScripts.shouldAcceptCollectorPayload(job.job.providerId, pageUrl)
    }

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

private fun isSameDocument(currentUrl: String?, expectedUrl: String): Boolean {
    if (currentUrl.isNullOrBlank()) return false
    return hostOf(currentUrl) == hostOf(expectedUrl) && pathOf(currentUrl) == pathOf(expectedUrl)
}

private fun decodeJsString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return runCatching { JSONObject("""{"value":$value}""").optString("value") }.getOrDefault("")
}

private const val BRIDGE_NAME = "AIQuotaCollectorBridge"
private const val LOGIN_PAGE_REACHED_MESSAGE = "Background refresh reached a provider login page."
private const val PAGE_CAPTURE_SCRIPT =
    "(function(){return (document.documentElement.innerText||document.title||'').slice(0,12000);})()"
