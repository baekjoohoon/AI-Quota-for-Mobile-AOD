package com.aiusage.mobile.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.providers.CopilotNativeUsageFetcher
import com.aiusage.mobile.providers.CursorNativeUsageFetcher
import com.aiusage.mobile.providers.ProviderDefinitionRegistry
import com.aiusage.mobile.providers.ProviderRefreshJob
import com.aiusage.mobile.providers.ProviderRefreshMode
import com.aiusage.mobile.providers.ProviderWebCollectorScripts
import com.aiusage.mobile.providers.ProviderWebViewUserAgent
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
    geminiCollectorAsset: String,
    onPayload: (QueuedProviderRefreshJob, String) -> Unit,
    onError: (QueuedProviderRefreshJob, String) -> Unit,
    onFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestJob = rememberUpdatedState(currentJob)
    val latestOnPayload = rememberUpdatedState(onPayload)
    val latestOnError = rememberUpdatedState(onError)
    val latestOnFinished = rememberUpdatedState(onFinished)
    var loadedRequestId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentJob?.requestId) {
        val job = currentJob ?: return@LaunchedEffect
        delay(BACKGROUND_COLLECTOR_TIMEOUT_MS)
        latestOnError.value(job, "Background refresh timed out.")
        latestOnFinished.value(job.requestId)
    }

    AndroidView(
        modifier = modifier
            .size(1.dp)
            .alpha(0f),
        factory = { context ->
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                configureForBackgroundCollection(cookieManager)
                addJavascriptInterface(
                    BackgroundUsageBridge(
                        currentJob = { latestJob.value },
                        onPayload = { job, payload -> latestOnPayload.value(job, payload) },
                        onError = { job, message -> latestOnError.value(job, message) },
                        onFinished = { requestId -> latestOnFinished.value(requestId) }
                    ),
                    BRIDGE_NAME
                )
                webChromeClient = BackgroundCollectorChromeClient()
                webViewClient = BackgroundCollectorWebViewClient(
                    currentJob = { latestJob.value },
                    geminiCollectorAsset = geminiCollectorAsset,
                    onError = { job, message -> latestOnError.value(job, message) },
                    onFinished = { requestId -> latestOnFinished.value(requestId) }
                )
            }
        },
        update = { webView ->
            val queuedJob = currentJob
            if (
                queuedJob != null &&
                queuedJob.job.mode == ProviderRefreshMode.HIDDEN_WEB_COLLECTOR &&
                loadedRequestId != queuedJob.requestId
            ) {
                loadedRequestId = queuedJob.requestId
                webView.loadUrl(queuedJob.job.startUrl)
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            loadedRequestId = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForBackgroundCollection(cookieManager: CookieManager) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.setSupportMultipleWindows(false)
    settings.userAgentString = ProviderWebViewUserAgent.loginUserAgent()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        cookieManager.setAcceptThirdPartyCookies(this, true)
    }
}

private class BackgroundCollectorChromeClient : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        android.util.Log.d(
            "AIUsageBgCollectorConsole",
            "${consoleMessage.message()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
        )
        return true
    }
}

private class BackgroundCollectorWebViewClient(
    private val currentJob: () -> QueuedProviderRefreshJob?,
    private val geminiCollectorAsset: String,
    private val onError: (QueuedProviderRefreshJob, String) -> Unit,
    private val onFinished: (Long) -> Unit
) : WebViewClient() {
    private val collectorInjectionKeys = mutableSetOf<String>()
    private var observedCodexAccountId: String? = null

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val job = currentJob() ?: return true
        val url = request.url.toString()
        return !ProviderDefinitionRegistry.isAllowed(job.job.providerId, url)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        captureCodexAccountId(request.url.toString())
        return null
    }

    override fun onLoadResource(view: WebView, url: String) {
        val job = currentJob() ?: return
        val pageUrl = view.url ?: url
        if (!ProviderWebCollectorScripts.shouldRunCollectorFromResource(job.job.providerId, pageUrl, url)) return
        val requestId = job.requestId
        val providerId = job.job.providerId
        view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
            val activeJob = currentJob() ?: return@evaluateJavascript
            if (activeJob.requestId != requestId || activeJob.job.providerId != providerId) return@evaluateJavascript
            if (!isSameDocument(view.url, pageUrl)) return@evaluateJavascript
            injectCollectorIfReady(view, pageUrl, decodeJsString(encoded))
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        val job = currentJob() ?: return
        val requestId = job.requestId
        val providerId = job.job.providerId
        Log.d(
            "AIUsageBgCollector",
            "pageFinished provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)}"
        )
        view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
            val activeJob = currentJob() ?: return@evaluateJavascript
            if (activeJob.requestId != requestId || activeJob.job.providerId != providerId) return@evaluateJavascript
            if (!isSameDocument(view.url, url)) return@evaluateJavascript
            injectCollectorIfReady(view, url, decodeJsString(encoded))
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        val job = currentJob() ?: return
        onError(job, "Background refresh page failed to load.")
        onFinished(job.requestId)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (!request.isForMainFrame || errorResponse.statusCode < 400) return
        val job = currentJob() ?: return
        onError(job, "Background refresh returned HTTP ${errorResponse.statusCode}.")
        onFinished(job.requestId)
    }

    private fun injectCollectorIfReady(view: WebView, url: String, pageText: String) {
        val job = currentJob() ?: return
        val providerId = job.job.providerId
        val cookies = cookiesFor(url)
        if (!ProviderWebCollectorScripts.shouldRunCollector(providerId, url, cookies, pageText)) {
            Log.d(
                "AIUsageBgCollector",
                "skipInject provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)}"
            )
            return
        }
        val injectionKey = "${job.requestId}:${providerId.storageId}:${hostOf(url)}:${pathOf(url)}"
        if (!collectorInjectionKeys.add(injectionKey)) return
        Log.d(
            "AIUsageBgCollector",
            "inject provider=${providerId.storageId} url=${hostOf(url)}${pathOf(url)}"
        )
        val script = ProviderWebCollectorScripts.build(
            providerId = providerId,
            cookies = cookies,
            geminiCollectorAsset = geminiCollectorAsset,
            observedAccountId = observedCodexAccountId,
            pageText = pageText
        )
        view.evaluateJavascript(script, null)
    }

    private fun captureCodexAccountId(url: String) {
        val job = currentJob() ?: return
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
    private val currentJob: () -> QueuedProviderRefreshJob?,
    private val onPayload: (QueuedProviderRefreshJob, String) -> Unit,
    private val onError: (QueuedProviderRefreshJob, String) -> Unit,
    private val onFinished: (Long) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postUsagePayload(rawPayload: String) {
        mainHandler.post {
            val job = currentJob() ?: return@post
            if (!ProviderWebCollectorScripts.shouldAcceptCollectorPayload(job.job.providerId, job.job.startUrl)) return@post
            Log.d(
                "AIUsageBgCollector",
                "payload provider=${job.job.providerId.storageId} length=${rawPayload.length}"
            )
            onPayload(job, rawPayload)
            onFinished(job.requestId)
        }
    }

    @JavascriptInterface
    fun postCollectorError(rawError: String) {
        mainHandler.post {
            val job = currentJob() ?: return@post
            val errorJson = runCatching { JSONObject(rawError) }.getOrNull()
            val errorKind = errorJson?.optString("errorKind", "collector_error") ?: "collector_error"
            Log.d(
                "AIUsageBgCollector",
                "error provider=${job.job.providerId.storageId} kind=$errorKind message=${errorJson?.optString("message").orEmpty()}"
            )
            onError(job, "Background collector failed: $errorKind")
            onFinished(job.requestId)
        }
    }

    @JavascriptInterface
    fun fetchCursorJson(url: String, body: String?): String {
        val job = currentJob() ?: return JSONObject().put("ok", false).put("error", "no_active_job").toString()
        if (job.job.providerId != ProviderId.CURSOR) {
            return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
        }
        return CursorNativeUsageFetcher.fetchJson(url, body)
    }

    @JavascriptInterface
    fun fetchCopilotJson(url: String): String {
        val job = currentJob() ?: return JSONObject().put("ok", false).put("error", "no_active_job").toString()
        if (job.job.providerId != ProviderId.COPILOT) {
            return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
        }
        val result = CopilotNativeUsageFetcher.fetchJson(url)
        val parsed = runCatching { JSONObject(result) }.getOrNull()
        Log.d(
            "AIUsageBgCollector",
            "provider=copilot nativeFetch endpoint=${parsed?.optString("endpoint")} status=${parsed?.optInt("status", -1)}"
        )
        return result
    }

    @JavascriptInterface
    fun fetchCopilotJsonWithAuthorization(url: String, authorizationHeader: String): String {
        val job = currentJob() ?: return JSONObject().put("ok", false).put("error", "no_active_job").toString()
        if (job.job.providerId != ProviderId.COPILOT) {
            return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
        }
        val result = CopilotNativeUsageFetcher.fetchJsonWithAuthorization(url, authorizationHeader)
        val parsed = runCatching { JSONObject(result) }.getOrNull()
        Log.d(
            "AIUsageBgCollector",
            "provider=copilot nativeFetchAuth endpoint=${parsed?.optString("endpoint")} status=${parsed?.optInt("status", -1)}"
        )
        return result
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

private fun isSameDocument(currentUrl: String?, expectedUrl: String): Boolean {
    if (currentUrl.isNullOrBlank()) return false
    return hostOf(currentUrl) == hostOf(expectedUrl) && pathOf(currentUrl) == pathOf(expectedUrl)
}

private fun decodeJsString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return runCatching { JSONObject("""{"value":$value}""").optString("value") }.getOrDefault("")
}

private const val BRIDGE_NAME = "AIUsageCollectorBridge"
private const val BACKGROUND_COLLECTOR_TIMEOUT_MS = 30_000L
private const val PAGE_CAPTURE_SCRIPT =
    "(function(){return (document.documentElement.innerText||document.title||'').slice(0,12000);})()"
