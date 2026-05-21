package com.aiusage.mobile.providers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderId
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

class WebLoginActivity : Activity() {
    private lateinit var providerId: ProviderId
    private lateinit var webView: WebView
    private lateinit var rootContainer: FrameLayout
    @Volatile
    private var finished = false
    private var firstPageLogged = false
    private var observedCodexAccountId: String? = null
    private var copilotPostLoginRedirected = false
    private val popupViews = mutableSetOf<WebView>()
    private val collectorInjectionKeys = mutableSetOf<String>()
    private val startedAtMs = SystemClock.elapsedRealtime()
    private val geminiCollectorAsset by lazy {
        runCatching {
            assets.open("gemini_collector.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        providerId = ProviderId.fromStorageId(intent.getStringExtra(EXTRA_PROVIDER_ID)) ?: run {
            finish()
            return
        }
        val definition = ProviderDefinitionRegistry.definitionFor(providerId)
        LocalUsageRepository(applicationContext).markConnecting(providerId)

        val title = TextView(this).apply {
            text = loginTitleText()
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(15, 23, 42))
            setPadding(32, 24, 32, 24)
        }
        val cookieManager = CookieManager.getInstance()
        val capabilities = ProviderLoginWebViewPolicy.capabilities()
        cookieManager.setAcceptCookie(true)
        if (capabilities.webContentsDebuggingEnabled) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webView = createConfiguredWebView(cookieManager, capabilities)
        rootContainer = FrameLayout(this).apply {
            addView(webView, loginWebViewLayoutParams())
            addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, loginTitleHeight()))
        }
        setContentView(rootContainer)
        webView.loadUrl(intent.getStringExtra(EXTRA_START_URL) ?: definition.loginStartUrl)
    }

    override fun onDestroy() {
        popupViews.toList().forEach(::destroyPopupWindow)
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(
        cookieManager: CookieManager,
        capabilities: ProviderLoginWebViewCapabilities
    ): WebView {
        return WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = capabilities.databaseEnabled
            settings.javaScriptCanOpenWindowsAutomatically = capabilities.javaScriptCanOpenWindowsAutomatically
            settings.setSupportMultipleWindows(capabilities.supportMultipleWindows)
            settings.userAgentString = ProviderWebViewUserAgent.loginUserAgent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(this, capabilities.acceptThirdPartyCookies)
            }
            addJavascriptInterface(UsageBridge(), BRIDGE_NAME)
            webChromeClient = LoginWebChromeClient()
            webViewClient = LoginWebViewClient()
        }
    }

    private fun loginWebViewLayoutParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = loginTitleHeight()
        }
    }

    private fun loginTitleHeight(): Int = 96

    private fun loginTitleText(): String {
        return "Sign in to ${providerId.displayName}"
    }

    private fun destroyPopupWindow(window: WebView) {
        if (!popupViews.remove(window)) return
        if (::rootContainer.isInitialized) {
            rootContainer.removeView(window)
        }
        window.destroy()
        Log.d("AIUsageLogin", "provider=${providerId.storageId} popupWindowClosed=true")
    }

    private inner class LoginWebChromeClient : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(
                "AIUsageLoginConsole",
                "provider=${providerId.storageId} ${consoleMessage.message()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
            )
            return true
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!::rootContainer.isInitialized) return false
            val popup = createConfiguredWebView(CookieManager.getInstance(), ProviderLoginWebViewPolicy.capabilities()).apply {
                setBackgroundColor(Color.WHITE)
            }
            popupViews.add(popup)
            rootContainer.addView(popup, loginWebViewLayoutParams())
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = popup
            resultMsg.sendToTarget()
            Log.d("AIUsageLogin", "provider=${providerId.storageId} popupWindowCreated=true")
            return true
        }

        override fun onCloseWindow(window: WebView) {
            destroyPopupWindow(window)
        }
    }

    private inner class LoginWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            injectCollectorIfReady(view, url, "")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Log.d("AIUsageLogin", "provider=${providerId.storageId} navigate=$url")
            if (ProviderLoginStrategy.isLoginComplete(providerId, url, cookiesFor(url), "")) {
                Log.i("AIUsageLogin", "provider=${providerId.storageId} oauthCallback=true host=${hostOf(url)}")
                handleOAuthCallback(url)
                return true
            }
            val shouldOverride = ProviderLoginWebViewPolicy.shouldOverrideNavigation(providerId, url)
            if (shouldOverride) {
                Log.w("AIUsageLogin", "provider=${providerId.storageId} blockedNavigation host=${hostOf(url)}")
            }
            return shouldOverride
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            captureCodexAccountId(url)
            collectCopilotInternalUserResource(request)
            Log.d("AIUsageLogin", "provider=${providerId.storageId} resource=$url")
            return if (ProviderLoginWebViewPolicy.shouldInterceptRequest(providerId, url)) {
                super.shouldInterceptRequest(view, request)
            } else {
                null
            }
        }

        override fun onLoadResource(view: WebView, url: String) {
            val pageUrl = view.url ?: url
            if (!ProviderWebCollectorScripts.shouldRunCollectorFromResource(providerId, pageUrl, url)) return
            view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
                injectCollectorIfReady(view, pageUrl, decodeJsString(encoded))
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            logFirstPageFinished(url)
            view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
                val pageText = decodeJsString(encoded)
                if (maybeRedirectCopilotToSettings(view, url, pageText)) {
                    return@evaluateJavascript
                }
                if (ProviderLoginStrategy.isLoginComplete(providerId, url, cookiesFor(url), pageText)) {
                    injectCollectorIfReady(view, url, pageText)
                } else {
                    injectCollectorIfReady(view, url, pageText)
                }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            Log.e("AIUsageLogin", "provider=${providerId.storageId} error url=${request.url} description=${error.description}")
            if (request.isForMainFrame && !ProviderLoginStrategy.isTransientNavigationError(request.url.toString(), error.errorCode)) {
                failKeepingPrevious("Provider login page failed to load.", "main_frame_load_failed")
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            Log.e("AIUsageLogin", "provider=${providerId.storageId} http status=${errorResponse.statusCode} url=${request.url}")
            if (request.isForMainFrame && ProviderLoginStrategy.isBlockingHttpError(request.url.toString(), errorResponse.statusCode)) {
                failKeepingPrevious("Provider login returned HTTP ${errorResponse.statusCode}.", "main_frame_http_${errorResponse.statusCode}")
            }
        }
    }

    private inner class UsageBridge {
        @JavascriptInterface
        fun postUsagePayload(rawPayload: String) {
            runOnUiThread {
                val pageUrl = webView.url.orEmpty()
                if (!ProviderWebCollectorScripts.shouldAcceptCollectorPayload(providerId, pageUrl)) {
                    Log.w("AIUsageCollector", "provider=${providerId.storageId} collectorMode=webview-js ignoredPayload page=${pathOf(pageUrl)}")
                    return@runOnUiThread
                }
                Log.i("AIUsageCollector", "provider=${providerId.storageId} collectorMode=webview-js rawPayloadPresent=${rawPayload.isNotBlank()}")
                finishSuccessfulLogin(rawPayload)
            }
        }

        @JavascriptInterface
        fun postCollectorError(rawError: String) {
            runOnUiThread {
                val errorKind = runCatching { JSONObject(rawError).optString("errorKind", "collector_error") }
                    .getOrDefault("collector_error")
                Log.w("AIUsageCollector", "provider=${providerId.storageId} collectorMode=webview-js errorKind=$errorKind keptPreviousSnapshot=true")
                finishConnectedWithoutUsage("Provider session reached, but trusted usage payload was not available yet.", errorKind)
            }
        }

        @JavascriptInterface
        fun fetchCursorJson(url: String, body: String?): String {
            if (providerId != ProviderId.CURSOR) {
                return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
            }
            val endpoint = cursorEndpoint(url)
                ?: return JSONObject().put("ok", false).put("error", "blocked_cursor_endpoint").toString()
            val result = CursorNativeUsageFetcher.fetchJson(url, body)
            val parsed = runCatching { JSONObject(result).optJSONObject("json") }.getOrNull()
            val status = runCatching { JSONObject(result).optInt("status", 0) }.getOrDefault(0)
            Log.d("AIUsageCollector", "provider=cursor nativeFetch endpoint=$endpoint status=$status summary=${cursorFetchSummary(parsed)}")
            return result
        }

        @JavascriptInterface
        fun fetchCopilotJson(url: String): String {
            if (providerId != ProviderId.COPILOT) {
                return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
            }
            val result = CopilotNativeUsageFetcher.fetchJson(url)
            val parsed = runCatching { JSONObject(result) }.getOrNull()
            val status = parsed?.optInt("status", 0) ?: 0
            val endpoint = parsed?.optString("endpoint").orEmpty()
            Log.d("AIUsageCollector", "provider=copilot nativeFetch endpoint=$endpoint status=$status")
            return result
        }

        @JavascriptInterface
        fun fetchCopilotJsonWithAuthorization(url: String, authorizationHeader: String): String {
            if (providerId != ProviderId.COPILOT) {
                return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
            }
            val result = CopilotNativeUsageFetcher.fetchJsonWithAuthorization(url, authorizationHeader)
            val parsed = runCatching { JSONObject(result) }.getOrNull()
            val status = parsed?.optInt("status", 0) ?: 0
            val endpoint = parsed?.optString("endpoint").orEmpty()
            Log.d("AIUsageCollector", "provider=copilot nativeFetchAuth endpoint=$endpoint status=$status")
            return result
        }
    }

    private fun maybeRedirectCopilotToSettings(view: WebView, url: String, pageText: String): Boolean {
        if (providerId != ProviderId.COPILOT || copilotPostLoginRedirected) return false
        if (!ProviderLoginStrategy.shouldRedirectCopilotToSettings(url, pageText)) return false
        copilotPostLoginRedirected = true
        Log.i("AIUsageLogin", "provider=copilot postLoginRedirect=settings/copilot from=${hostOf(url)}${pathOf(url)}")
        view.loadUrl(ProviderDefinitionRegistry.definitionFor(ProviderId.COPILOT).loginStartUrl)
        return true
    }

    private fun collectCopilotInternalUserResource(request: WebResourceRequest) {
        if (finished || providerId != ProviderId.COPILOT) return
        val url = request.url.toString()
        if (!CopilotNativeUsageFetcher.isInternalUserUrl(url)) return
        val authorizationHeader = CopilotNativeUsageFetcher.apiAuthorizationHeaderFromRequest(request.requestHeaders)
        Log.d(
            "AIUsageCollector",
            "provider=copilot resource=/copilot_internal/user hasAuth=${authorizationHeader != null}"
        )
        if (authorizationHeader == null) return
        val result = CopilotNativeUsageFetcher.fetchJsonWithAuthorization(url, authorizationHeader)
        val parsed = runCatching { JSONObject(result) }.getOrNull()
        val payload = CopilotNativeUsageFetcher.payloadFromInternalUserResponse(result)
        Log.d(
            "AIUsageCollector",
            "provider=copilot resourceInternal status=${parsed?.optInt("status", -1)} payload=${payload != null}"
        )
        if (payload == null) return
        runOnUiThread {
            if (!finished) finishSuccessfulLogin(payload)
        }
    }

    private fun cursorEndpoint(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty()
        val allowed = when (host) {
            "cursor.com", "www.cursor.com" -> path in setOf(
                "/api/auth/stripe",
                "/api/usage",
                "/api/auth/usage",
                "/api/usage-summary",
                "/api/dashboard/get-credit-grants-balance"
            )
            "api2.cursor.sh" -> path in setOf(
                "/aiserver.v1.DashboardService/GetCurrentPeriodUsage",
                "/aiserver.v1.DashboardService/GetPlanInfo",
                "/aiserver.v1.DashboardService/GetCreditGrantsBalance",
                "/auth/usage"
            )
            else -> false
        }
        return if (allowed) path else null
    }

    private fun cursorFetchSummary(json: JSONObject?): String {
        if (json == null) return "invalid_json"
        fun keysOf(obj: JSONObject?): String {
            if (obj == null) return "none"
            return obj.keys().asSequence()
                .filterNot { it.contains("token", ignoreCase = true) || it.contains("cookie", ignoreCase = true) }
                .take(10)
                .joinToString("|")
                .ifBlank { "empty" }
        }
        val individualUsage = json.optJSONObject("individualUsage")
        return listOf(
            "root=${keysOf(json)}",
            "planUsage=${keysOf(json.optJSONObject("planUsage"))}",
            "individualUsage=${keysOf(individualUsage)}",
            "individualPlan=${keysOf(individualUsage?.optJSONObject("plan"))}",
            "usage=${keysOf(json.optJSONObject("usage"))}",
            "credit=${json.has("hasCreditGrants") || json.has("totalCents") || json.has("usedCents")}"
        ).joinToString(",")
    }

    private fun injectCollectorIfReady(view: WebView, url: String, pageText: String) {
        if (finished) return
        val cookies = cookiesFor(url)
        if (!ProviderWebCollectorScripts.shouldRunCollector(providerId, url, cookies, pageText)) return
        val injectionKey = "${providerId.storageId}:${hostOf(url)}:${pathOf(url)}"
        if (collectorInjectionKeys.add(injectionKey)) {
            Log.i("AIUsageCollector", "provider=${providerId.storageId} collectorMode=webview-js inject host=${hostOf(url)}")
        }
        val script = ProviderWebCollectorScripts.build(providerId, cookies, geminiCollectorAsset, observedCodexAccountId, pageText)
        view.evaluateJavascript(script, null)
    }

    private fun captureCodexAccountId(url: String) {
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
        if (observedCodexAccountId != accountId) {
            observedCodexAccountId = accountId
            Log.i("AIUsageLogin", "provider=codex observedAccountId=true")
        }
    }

    private fun finishSuccessfulLogin(
        rawPayload: String?,
        source: String = ProviderUsageCollectionService.SOURCE_LOGIN
    ) {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        ProviderUsageCollectionService.start(
            context = applicationContext,
            providerId = providerId,
            source = source,
            rawPayload = rawPayload
        )
        finish()
    }

    private fun handleOAuthCallback(url: String) {
        if (finished) return
        Thread {
            val result: Result<Unit>
            val payload: String?
            val unavailableMessage: String
            val unavailableKind: String
            val failedMessage: String
            val failedKind: String
            when (providerId) {
                ProviderId.GEMINI -> {
                    val repository = GeminiCliOAuthRepository(applicationContext)
                    result = repository.completeAuthorization(url)
                    payload = result.getOrNull()?.let { repository.fetchUsagePayload() }
                    unavailableMessage = "Gemini authorization succeeded, but quota payload was not available."
                    unavailableKind = "gemini_quota_unavailable"
                    failedMessage = "Gemini authorization failed."
                    failedKind = "gemini_oauth_failed"
                }
                ProviderId.CODEX -> {
                    val repository = CodexOAuthRepository(applicationContext)
                    result = repository.completeAuthorization(url)
                    payload = result.getOrNull()?.let { repository.fetchUsagePayload() }
                    unavailableMessage = "Codex authorization succeeded, but usage payload was not available."
                    unavailableKind = "codex_usage_unavailable"
                    failedMessage = "Codex authorization failed."
                    failedKind = "codex_oauth_failed"
                }
                else -> return@Thread
            }
            runOnUiThread {
                if (result.isFailure) {
                    failKeepingPrevious(failedMessage, failedKind)
                } else if (payload.isNullOrBlank()) {
                    finishConnectedWithoutUsage(unavailableMessage, unavailableKind)
                } else {
                    finishSuccessfulLogin(payload, ProviderUsageCollectionService.SOURCE_PAYLOAD)
                }
            }
        }.start()
    }

    private fun finishConnectedWithoutUsage(message: String, errorKind: String) {
        if (finished) return
        finished = true
        val repository = LocalUsageRepository(applicationContext)
        repository.markConnectedWithoutUsage(providerId, message)
        Log.w("AIUsageLogin", "provider=${providerId.storageId} errorKind=$errorKind usageUnavailable=true")
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    private fun failKeepingPrevious(message: String, errorKind: String) {
        if (finished) return
        finished = true
        val repository = LocalUsageRepository(applicationContext)
        repository.failKeepingPrevious(providerId, message)
        Log.w("AIUsageLogin", "provider=${providerId.storageId} errorKind=$errorKind keptPreviousSnapshot=true")
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    private fun logFirstPageFinished(url: String) {
        if (firstPageLogged) return
        firstPageLogged = true
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        Log.i("AIUsageLoginTiming", "${providerId.storageId} pageFinished=${elapsedMs}ms host=$host")
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

    companion object {
        private const val EXTRA_PROVIDER_ID = "providerId"
        private const val EXTRA_START_URL = "startUrl"
        private const val BRIDGE_NAME = "AIUsageCollectorBridge"
        private const val CURSOR_NATIVE_FETCH_TIMEOUT_MS = 20_000
        private const val PAGE_CAPTURE_SCRIPT =
            "(function(){return (document.documentElement.innerText||document.title||'').slice(0,12000);})()"

        fun createIntent(context: Context, providerId: ProviderId): Intent {
            val definition = ProviderDefinitionRegistry.definitionFor(providerId)
            return createIntent(context, providerId, definition.loginStartUrl)
        }

        fun createIntent(context: Context, providerId: ProviderId, startUrl: String): Intent {
            return Intent(context, WebLoginActivity::class.java)
                .putExtra(EXTRA_PROVIDER_ID, providerId.storageId)
                .putExtra(EXTRA_START_URL, startUrl)
        }
    }
}
