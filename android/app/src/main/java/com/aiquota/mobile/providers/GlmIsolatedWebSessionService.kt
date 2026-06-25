package com.aiquota.mobile.providers

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aiquota.mobile.local.ProviderId

class GlmIsolatedWebSessionService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var resultReceiver: ResultReceiver? = null
    private var completed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_COLLECT -> startCollection(intent)
            ACTION_CLEAR -> clearSession(intent)
            ACTION_CANCEL -> finish()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyWebView()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startCollection(intent: Intent) {
        destroyWebView()
        completed = false
        resultReceiver = resultReceiverFrom(intent)
        val startUrl = intent.getStringExtra(EXTRA_START_URL).orEmpty()
        val timeoutMillis = intent.getLongExtra(EXTRA_TIMEOUT_MILLIS, ProviderRefreshPlan.PROVIDER_REFRESH_TIMEOUT_MILLIS)
        mainHandler.postDelayed({
            completeFailure(ProviderRefreshTimeoutPolicy.failureFor(ProviderId.GLM, webView?.url ?: startUrl))
        }, timeoutMillis)
        CookieManager.getInstance().setAcceptCookie(true)
        webView = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }
            addJavascriptInterface(Bridge(), BRIDGE_NAME)
            webViewClient = GlmWebClient()
            loadUrl(startUrl)
        }
    }

    private fun clearSession(intent: Intent) {
        destroyWebView()
        completed = false
        resultReceiver = resultReceiverFrom(intent)
        WebStorage.getInstance().deleteAllData()
        val cookieManager = CookieManager.getInstance()
        mainHandler.postDelayed({
            cookieManager.flush()
            sendResult(RESULT_CLEARED, Bundle.EMPTY)
        }, CLEAR_CALLBACK_FALLBACK_MS)
        cookieManager.removeAllCookies {
            cookieManager.flush()
            sendResult(RESULT_CLEARED, Bundle.EMPTY)
        }
    }

    private fun injectCollectorIfReady(view: WebView, url: String, pageText: String) {
        if (completed) return
        val cookies = CookieManager.getInstance().getCookie(url)
            ?.split(";")
            ?.mapNotNull { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            ?.toMap()
            .orEmpty()
        if (!ProviderWebCollectorScripts.shouldRunCollector(ProviderId.GLM, url, cookies, pageText)) return
        view.evaluateJavascript(
            ProviderWebCollectorScripts.build(
                providerId = ProviderId.GLM,
                cookies = cookies,
                geminiCollectorAsset = "",
                antigravityCollectorAsset = "",
                observedAccountId = null,
                pageText = pageText,
                pageUrl = url,
                awaitInteractiveLoginUsage = false
            ),
            null
        )
    }

    private fun pageText(view: WebView, url: String, block: (String) -> Unit) {
        view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
            if (completed) return@evaluateJavascript
            val text = decodeJsString(encoded)
            block(text)
            if (ProviderWebCollectorScripts.isRefreshLoginPage(ProviderId.GLM, url, text)) {
                completeFailure(ProviderRefreshFailure.interactiveAuthRequired(LOGIN_PAGE_REACHED_MESSAGE))
            }
        }
    }

    private fun completePayload(rawPayload: String) {
        CookieManager.getInstance().flush()
        sendResult(
            RESULT_PAYLOAD,
            Bundle().apply { putString(EXTRA_RAW_PAYLOAD, rawPayload) }
        )
    }

    private fun completeFailure(failure: ProviderRefreshFailure) {
        sendResult(
            RESULT_FAILURE,
            Bundle().apply {
                putString(EXTRA_FAILURE_KIND, failure.kind.name)
                putString(EXTRA_FAILURE_MESSAGE, failure.message)
            }
        )
    }

    private fun sendResult(code: Int, bundle: Bundle) {
        if (completed) return
        completed = true
        mainHandler.removeCallbacksAndMessages(null)
        resultReceiver?.send(code, bundle)
        resultReceiver = null
        finish()
    }

    private fun finish() {
        destroyWebView()
        stopSelf()
    }

    private fun destroyWebView() {
        webView?.let { view ->
            runCatching { view.stopLoading() }
            runCatching { view.removeJavascriptInterface(BRIDGE_NAME) }
            runCatching { view.destroy() }
        }
        webView = null
    }

    private fun resultReceiverFrom(intent: Intent): ResultReceiver? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }
    }

    private fun decodeJsString(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return runCatching { org.json.JSONObject("""{"value":$value}""").optString("value") }.getOrDefault("")
    }

    private inner class GlmWebClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            GlmLoginPostRedirects.usageRedirectUrl(ProviderId.GLM, url)?.let { usageUrl ->
                view.stopLoading()
                view.loadUrl(usageUrl)
                return
            }
            injectCollectorIfReady(view, url, "")
        }

        override fun onLoadResource(view: WebView, url: String) {
            val pageUrl = view.url ?: url
            if (!ProviderWebCollectorScripts.shouldRunCollectorFromResource(ProviderId.GLM, pageUrl, url)) return
            pageText(view, pageUrl) { text -> injectCollectorIfReady(view, pageUrl, text) }
        }

        override fun onPageFinished(view: WebView, url: String) {
            GlmLoginPostRedirects.usageRedirectUrl(ProviderId.GLM, url)?.let { usageUrl ->
                view.loadUrl(usageUrl)
                return
            }
            pageText(view, url) { text -> injectCollectorIfReady(view, url, text) }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            completeFailure(
                ProviderRefreshFailure(
                    ProviderRefreshFailureKind.TRANSIENT_PAGE_LOAD,
                    "Background refresh page failed to load."
                )
            )
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun postUsagePayload(rawPayload: String) {
            mainHandler.post {
                val pageUrl = webView?.url.orEmpty().ifBlank { GlmProviderUrls.WEB_USAGE_URL }
                if (!ProviderWebCollectorScripts.shouldAcceptCollectorPayload(ProviderId.GLM, pageUrl, rawPayload)) return@post
                completePayload(rawPayload)
            }
        }

        @JavascriptInterface
        fun postCollectorError(rawError: String) {
            mainHandler.post {
                val pageUrl = webView?.url.orEmpty().ifBlank { GlmProviderUrls.WEB_USAGE_URL }
                if (!ProviderWebCollectorScripts.shouldAcceptCollectorError(ProviderId.GLM, pageUrl, rawError)) return@post
                completeFailure(ProviderCollectorErrorPolicy.failureFor(ProviderId.GLM, rawError))
            }
        }
    }

    companion object {
        const val EXTRA_RESULT_RECEIVER = "result_receiver"
        const val EXTRA_RAW_PAYLOAD = "raw_payload"
        const val EXTRA_FAILURE_KIND = "failure_kind"
        const val EXTRA_FAILURE_MESSAGE = "failure_message"
        private const val EXTRA_START_URL = "start_url"
        private const val EXTRA_TIMEOUT_MILLIS = "timeout_millis"
        private const val ACTION_COLLECT = "com.aiquota.mobile.action.GLM_WEB_COLLECT"
        private const val ACTION_CLEAR = "com.aiquota.mobile.action.GLM_WEB_CLEAR"
        private const val ACTION_CANCEL = "com.aiquota.mobile.action.GLM_WEB_CANCEL"
        private const val BRIDGE_NAME = "AIQuotaCollectorBridge"
        private const val LOGIN_PAGE_REACHED_MESSAGE = "Background refresh reached a provider login page."
        private const val PAGE_CAPTURE_SCRIPT =
            "(function(){return (document.documentElement.innerText||document.title||'').slice(0,12000);})()"
        private const val CLEAR_CALLBACK_FALLBACK_MS = 2_000L
        const val RESULT_PAYLOAD = 1
        const val RESULT_FAILURE = 2
        const val RESULT_CLEARED = 3

        fun collectIntent(context: Context, startUrl: String, timeoutMillis: Long): Intent {
            return Intent(context, GlmIsolatedWebSessionService::class.java)
                .setAction(ACTION_COLLECT)
                .putExtra(EXTRA_START_URL, startUrl)
                .putExtra(EXTRA_TIMEOUT_MILLIS, timeoutMillis)
        }

        fun clearIntent(context: Context): Intent {
            return Intent(context, GlmIsolatedWebSessionService::class.java)
                .setAction(ACTION_CLEAR)
        }

        fun cancelIntent(context: Context): Intent {
            return Intent(context, GlmIsolatedWebSessionService::class.java)
                .setAction(ACTION_CANCEL)
        }
    }
}
