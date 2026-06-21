package com.aiquota.mobile.providers

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
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.local.ProviderId
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONObject

class WebLoginActivity : Activity() {
    private lateinit var providerId: ProviderId
    private lateinit var webView: WebView
    private lateinit var rootContainer: FrameLayout
    private lateinit var titleView: TextView
    @Volatile
    private var finished = false
    private var firstPageLogged = false
    private var observedCodexAccountId: String? = null
    private var copilotPostLoginRedirected = false
    private var lastGeminiUsageRedirectKey: String? = null
    private var lastGeminiUsageRedirectAtMs = 0L
    private var geminiUsageRedirectAttempts = 0
    private var geminiSignInClickAttempts = 0
    private var glmPostLoginRedirected = false
    private var openCodePostLoginRedirected = false
    @Volatile
    private var oauthCallbackHandled = false
    private val popupViews = mutableSetOf<WebView>()
    private val collectorInjectionKeys = mutableSetOf<String>()
    private val startedAtMs = SystemClock.elapsedRealtime()
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

    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        providerId = ProviderId.fromStorageId(intent.getStringExtra(EXTRA_PROVIDER_ID)) ?: run {
            finish()
            return
        }
        val definition = ProviderDefinitionRegistry.definitionFor(providerId)
        val repository = LocalUsageRepository(applicationContext)
        val previousConnectionState = repository.readSnapshots()
            .firstOrNull { it.providerId == providerId }
            ?.connectionState
        repository.markConnecting(providerId)

        titleView = TextView(this).apply {
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
        if (ProviderWebSessionClearPolicy.shouldClearBeforeLogin(providerId, previousConnectionState)) {
            clearProviderWebSession(cookieManager, providerId)
        }
        webView = createConfiguredWebView(cookieManager, capabilities)
        rootContainer = FrameLayout(this).apply {
            addView(webView, loginWebViewLayoutParams())
            addView(titleView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, loginTitleHeight()))
        }
        setContentView(rootContainer)
        val requestedStartUrl = intent.getStringExtra(EXTRA_START_URL) ?: definition.loginStartUrl
        webView.loadUrl(requestedStartUrl)
    }

    override fun onDestroy() {
        if (::providerId.isInitialized && !finished) {
            if (oauthCallbackHandled && isGoogleProvider()) {
                Log.i(
                    "AIQuotaLogin",
                    "provider=${providerId.storageId} googleCallbackDestroy awaitingCollector=true"
                )
            } else {
                LocalUsageRepository(applicationContext).markLoginCancelled(
                    providerId,
                    "Provider login was cancelled."
                )
                UsageSurfaceRefresher.refresh(applicationContext, LocalUsageRepository(applicationContext))
            }
        }
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
            settings.allowFileAccess = capabilities.allowFileAccess
            settings.javaScriptCanOpenWindowsAutomatically = capabilities.javaScriptCanOpenWindowsAutomatically
            settings.setSupportMultipleWindows(capabilities.supportMultipleWindows)
            settings.userAgentString = ProviderWebViewUserAgent.loginUserAgent(this@WebLoginActivity)
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
        Log.d("AIQuotaLogin", "provider=${providerId.storageId} popupWindowClosed=true")
    }

    private fun clearProviderWebSession(cookieManager: CookieManager, providerId: ProviderId) {
        ProviderWebSessionCleaner.clearProviderWebSession(cookieManager, WebStorage.getInstance(), providerId)
        Log.i("AIQuotaLogin", "provider=${providerId.storageId} reauthWebSessionCleared=true")
    }

    private inner class LoginWebChromeClient : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d(
                "AIQuotaLoginConsole",
                "provider=${providerId.storageId} consoleLevel=${consoleMessage.messageLevel()} line=${consoleMessage.lineNumber()}"
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
            Log.d("AIQuotaLogin", "provider=${providerId.storageId} popupWindowCreated=true")
            return true
        }

        override fun onCloseWindow(window: WebView) {
            destroyPopupWindow(window)
        }
    }

    private inner class LoginWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            if (handleLoginCompleteNavigation(view, url)) return
            if (maybeRedirectGeminiToUsage(view, url)) return
            if (maybeRedirectGlmToUsage(view, url)) return
            if (maybeRedirectOpenCodeToGo(view, url)) return
            injectCollectorIfReady(view, url, "")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            Log.d("AIQuotaLogin", "provider=${providerId.storageId} navigate=${safeUrlForLog(url)}")
            if (request.isForMainFrame) {
                ProviderLoginUrlRewriter.rewriteMainFrameUrl(providerId, url)?.let { rewrittenUrl ->
                    Log.i("AIQuotaLogin", "provider=${providerId.storageId} googleOAuthRewrite=select_account")
                    view.loadUrl(rewrittenUrl)
                    return true
                }
            }
            if (providerId == ProviderId.CODEX && ProviderLoginStrategy.shouldRecoverCodexLocalAuthCallback(url)) {
                recoverCodexFromLocalAuthCallback(view, url)
                return true
            }
            if (handleLoginCompleteNavigation(view, url)) return true
            if (maybeRedirectGeminiToUsage(view, url)) return true
            val shouldOverride = ProviderLoginWebViewPolicy.shouldOverrideNavigation(providerId, url)
            if (shouldOverride) {
                Log.w("AIQuotaLogin", "provider=${providerId.storageId} blockedNavigation host=${hostOf(url)}")
            }
            return shouldOverride
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            captureCodexAccountId(url)
            logGoogleCodeAssistResource(request)
            Log.d("AIQuotaLogin", "provider=${providerId.storageId} resource=${safeUrlForLog(url)}")
            if (ProviderLoginStrategy.isInteractiveLoginSessionReached(providerId, url) &&
                ProviderLoginStrategy.shouldFinishOnInteractiveLoginSessionReached(providerId)
            ) {
                view.post {
                    if (finished) return@post
                    Log.i("AIQuotaLogin", "provider=${providerId.storageId} interactiveSessionReached=true")
                    view.stopLoading()
                    finishGoogleUsagePending(
                        "Provider session reached, but trusted usage payload was not available yet.",
                        "interactive_session_reached"
                    )
                }
            }
            if (ProviderLoginStrategy.isLoginComplete(providerId, url, cookiesFor(url), "")) {
                view.post { handleLoginCompleteNavigation(view, url) }
            }
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
            if (handleLoginCompleteNavigation(view, url)) return
            if (maybeRedirectGeminiToUsage(view, url)) return
            if (maybeRedirectGlmToUsage(view, url)) return
            if (maybeRedirectOpenCodeToGo(view, url)) return
            view.evaluateJavascript(PAGE_CAPTURE_SCRIPT) { encoded ->
                val pageText = decodeJsString(encoded)
                if (maybeRedirectGeminiToUsage(view, url)) {
                    return@evaluateJavascript
                }
                if (maybeClickGeminiSignIn(view, url, pageText)) {
                    return@evaluateJavascript
                }
                if (maybeRedirectOpenCodeToGo(view, url)) {
                    return@evaluateJavascript
                }
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
            Log.e(
                "AIQuotaLogin",
                "provider=${providerId.storageId} error url=${safeUrlForLog(request.url.toString())} description=${error.description}"
            )
            if (request.isForMainFrame && handleLoginCompleteNavigation(view, request.url.toString())) return
            if (request.isForMainFrame && !ProviderLoginStrategy.isTransientNavigationError(request.url.toString(), error.errorCode)) {
                failKeepingPrevious("Provider login page failed to load.", "main_frame_load_failed")
            }
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            Log.e(
                "AIQuotaLogin",
                "provider=${providerId.storageId} http status=${errorResponse.statusCode} url=${safeUrlForLog(request.url.toString())}"
            )
            if (request.isForMainFrame && handleLoginCompleteNavigation(view, request.url.toString())) return
            if (request.isForMainFrame && ProviderLoginStrategy.isBlockingHttpError(request.url.toString(), errorResponse.statusCode)) {
                failKeepingPrevious("Provider login returned HTTP ${errorResponse.statusCode}.", "main_frame_http_${errorResponse.statusCode}")
            }
        }
    }

    private fun handleLoginCompleteNavigation(view: WebView, url: String): Boolean {
        if (ProviderLoginStrategy.isLoginComplete(providerId, url, cookiesFor(url), "")) {
            if (oauthCallbackHandled || finished) return true
            oauthCallbackHandled = true
            Log.i("AIQuotaLogin", "provider=${providerId.storageId} oauthCallback=true host=${hostOf(url)}")
            view.stopLoading()
            failKeepingPrevious("Provider login did not produce a trusted usage payload.", "login_complete_without_payload")
            return true
        }
        return false
    }

    private inner class UsageBridge {
        @JavascriptInterface
        fun postUsagePayload(rawPayload: String) {
            runOnUiThread {
                val pageUrl = webView.url.orEmpty()
                if (!ProviderWebCollectorScripts.shouldAcceptCollectorPayload(providerId, pageUrl, rawPayload)) {
                    Log.w("AIQuotaCollector", "provider=${providerId.storageId} collectorMode=webview-js ignoredPayload page=${pathOf(pageUrl)}")
                    return@runOnUiThread
                }
                saveOpenCodeUsageUrl(pageUrl)
                Log.i("AIQuotaCollector", "provider=${providerId.storageId} collectorMode=webview-js rawPayloadPresent=${rawPayload.isNotBlank()}")
                finishSuccessfulLogin(rawPayload)
            }
        }

        @JavascriptInterface
        fun postCollectorError(rawError: String) {
            runOnUiThread {
                val pageUrl = webView.url.orEmpty()
                if (!ProviderWebCollectorScripts.shouldAcceptCollectorError(providerId, pageUrl)) {
                    Log.w("AIQuotaCollector", "provider=${providerId.storageId} collectorMode=webview-js ignoredError page=${pathOf(pageUrl)}")
                    return@runOnUiThread
                }
                val errorKind = runCatching { JSONObject(rawError).optString("errorKind", "collector_error") }
                    .getOrDefault("collector_error")
                Log.w("AIQuotaCollector", "provider=${providerId.storageId} collectorMode=webview-js errorKind=$errorKind keptPreviousSnapshot=true")
                if (providerId == ProviderId.GLM && errorKind == GlmNoSubscriptionPolicy.ERROR_KIND) {
                    finishGlmNoSubscription(errorKind)
                    return@runOnUiThread
                }
                if (shouldKeepLoginOpenUntilUsagePayload(errorKind)) {
                    Log.i("AIQuotaCollector", "provider=${providerId.storageId} awaitingUsagePayload=true errorKind=$errorKind")
                    return@runOnUiThread
                }
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
            if (!isNativeFetchBridgePageAllowed(ProviderId.CURSOR)) {
                return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
            }
            val result = CursorNativeUsageFetcher.fetchJson(url, body)
            val parsed = runCatching { JSONObject(result).optJSONObject("json") }.getOrNull()
            val status = runCatching { JSONObject(result).optInt("status", 0) }.getOrDefault(0)
            Log.d("AIQuotaCollector", "provider=cursor nativeFetch endpoint=$endpoint status=$status summary=${cursorFetchSummary(parsed)}")
            return result
        }

        @JavascriptInterface
        fun fetchCopilotJson(url: String): String {
            if (providerId != ProviderId.COPILOT) {
                return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
            }
            if (!isNativeFetchBridgePageAllowed(ProviderId.COPILOT)) {
                return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
            }
            val result = CopilotNativeUsageFetcher.fetchJson(url)
            val parsed = runCatching { JSONObject(result) }.getOrNull()
            val status = parsed?.optInt("status", 0) ?: 0
            val endpoint = parsed?.optString("endpoint").orEmpty()
            Log.d("AIQuotaCollector", "provider=copilot nativeFetch endpoint=$endpoint status=$status")
            return result
        }

        @JavascriptInterface
        fun fetchCopilotJsonWithAuthorization(url: String, authorizationHeader: String): String {
            if (providerId != ProviderId.COPILOT) {
                return JSONObject().put("ok", false).put("error", "provider_mismatch").toString()
            }
            if (!isNativeFetchBridgePageAllowed(ProviderId.COPILOT)) {
                return JSONObject().put("ok", false).put("error", "blocked_bridge_page").toString()
            }
            val result = CopilotNativeUsageFetcher.fetchJsonWithAuthorization(url, authorizationHeader)
            val parsed = runCatching { JSONObject(result) }.getOrNull()
            val status = parsed?.optInt("status", 0) ?: 0
            val endpoint = parsed?.optString("endpoint").orEmpty()
            Log.d("AIQuotaCollector", "provider=copilot nativeFetchAuth endpoint=$endpoint status=$status")
            return result
        }

    private fun isNativeFetchBridgePageAllowed(expectedProviderId: ProviderId): Boolean {
            val pageUrl = webView.url.orEmpty()
            return providerId == expectedProviderId &&
                ProviderWebCollectorScripts.shouldAcceptCollectorPayload(expectedProviderId, pageUrl)
        }

    }

    private fun maybeRedirectGlmToUsage(view: WebView, url: String): Boolean {
        if (providerId != ProviderId.GLM || glmPostLoginRedirected) return false
        val usageUrl = GlmLoginPostRedirects.usageRedirectUrl(providerId, url) ?: return false
        glmPostLoginRedirected = true
        collectorInjectionKeys.clear()
        Log.i("AIQuotaLogin", "provider=glm postLoginRedirect=usage from=${hostOf(url)}${pathOf(url)}")
        view.loadUrl(usageUrl)
        return true
    }

    private fun maybeRedirectGeminiToUsage(view: WebView, url: String): Boolean {
        if (providerId != ProviderId.GEMINI) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        maybeResetGeminiUsageRedirectBudget(host)
        if (GeminiUsagePageRoutes.isUsageUrl(url)) {
            lastGeminiUsageRedirectKey = null
            lastGeminiUsageRedirectAtMs = 0L
            return false
        }
        val usageUrl = GeminiUsagePageRoutes.usageUrlFrom(url) ?: return false
        if (geminiUsageRedirectAttempts >= GEMINI_USAGE_REDIRECT_MAX_ATTEMPTS) return false
        val path = uri.path.orEmpty()
        val redirectKey = "$host:$path"
        val now = SystemClock.elapsedRealtime()
        if (redirectKey == lastGeminiUsageRedirectKey && now - lastGeminiUsageRedirectAtMs < GEMINI_USAGE_REDIRECT_MIN_INTERVAL_MS) {
            return false
        }
        lastGeminiUsageRedirectKey = redirectKey
        lastGeminiUsageRedirectAtMs = now
        geminiUsageRedirectAttempts += 1
        collectorInjectionKeys.clear()
        Log.i("AIQuotaLogin", "provider=gemini postLoginRedirect=usage from=${hostOf(url)}${pathOf(url)}")
        view.stopLoading()
        view.loadUrl(usageUrl)
        return true
    }

    private fun maybeResetGeminiUsageRedirectBudget(host: String) {
        if (providerId != ProviderId.GEMINI) return
        if (host != "myaccount.google.com" && !host.startsWith("accounts.google.")) return
        if (geminiUsageRedirectAttempts == 0 && lastGeminiUsageRedirectKey == null) return
        geminiUsageRedirectAttempts = 0
        lastGeminiUsageRedirectKey = null
        lastGeminiUsageRedirectAtMs = 0L
        Log.d("AIQuotaLogin", "provider=gemini resetUsageRedirectBudget host=$host")
    }

    private fun maybeClickGeminiSignIn(view: WebView, url: String, pageText: String): Boolean {
        if (providerId != ProviderId.GEMINI) return false
        if (geminiSignInClickAttempts >= GEMINI_SIGN_IN_CLICK_MAX_ATTEMPTS) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase(Locale.US)
        if (host != "gemini.google.com") return false
        if (!GeminiUsagePageRoutes.isLoginLandingUrl(url) && !GeminiUsagePageRoutes.isUsageUrl(url)) return false
        if (!ProviderWebCollectorScripts.isRefreshLoginPage(providerId, url, pageText)) return false
        geminiSignInClickAttempts += 1
        collectorInjectionKeys.clear()
        Log.i("AIQuotaLogin", "provider=gemini clickSignIn=true from=${hostOf(url)}${pathOf(url)}")
        view.evaluateJavascript(
            """
            (function(){
              var labels = ["로그인", "sign in", "log in"];
              var elements = Array.prototype.slice.call(document.querySelectorAll("a, button, [role='button']"));
              for (var i = 0; i < elements.length; i += 1) {
                var text = String(elements[i].innerText || elements[i].textContent || "").trim().toLowerCase();
                if (!text) continue;
                for (var j = 0; j < labels.length; j += 1) {
                  if (text.indexOf(labels[j]) >= 0) {
                    elements[i].click();
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

    private fun maybeRedirectOpenCodeToGo(view: WebView, url: String): Boolean {
        if (providerId != ProviderId.OPENCODE || openCodePostLoginRedirected) return false
        val goUsageUrl = OpenCodeUsagePageRoutes.goUsageUrlFrom(url) ?: return false
        openCodePostLoginRedirected = true
        collectorInjectionKeys.clear()
        saveOpenCodeUsageUrl(goUsageUrl)
        Log.i("AIQuotaLogin", "provider=opencode postLoginRedirect=workspace/go from=${hostOf(url)}${pathOf(url)}")
        view.loadUrl(goUsageUrl)
        return true
    }

    private fun maybeRedirectCopilotToSettings(view: WebView, url: String, pageText: String): Boolean {
        if (providerId != ProviderId.COPILOT || copilotPostLoginRedirected) return false
        if (!ProviderLoginStrategy.shouldRedirectCopilotToSettings(url, pageText)) return false
        copilotPostLoginRedirected = true
        Log.i("AIQuotaLogin", "provider=copilot postLoginRedirect=settings/copilot from=${hostOf(url)}${pathOf(url)}")
        view.loadUrl(ProviderDefinitionRegistry.definitionFor(ProviderId.COPILOT).loginStartUrl)
        return true
    }

    private fun recoverCodexFromLocalAuthCallback(view: WebView, url: String) {
        CookieManager.getInstance().flush()
        collectorInjectionKeys.clear()
        Log.i("AIQuotaLogin", "provider=codex localCallbackRecovered=true host=${hostOf(url)}")
        view.loadUrl(ProviderLoginStrategy.CODEX_CALLBACK_RECOVERY_URL)
    }

    private fun logGoogleCodeAssistResource(request: WebResourceRequest) {
        if (!isGoogleProvider()) return
        val uri = runCatching { URI(request.url.toString()) }.getOrNull() ?: return
        if (uri.host.orEmpty().lowercase() != "cloudcode-pa.googleapis.com") return
        if (!uri.path.orEmpty().contains("v1internal")) return

        val headers = request.requestHeaders.orEmpty()
        val authHeader = headerValue(headers, "Authorization")
        val exactCookieCount = GoogleWebSessionCodeAssistFetcher
            .parseCookieHeader(CookieManager.getInstance().getCookie(request.url.toString()).orEmpty())
            .size
        val resourceMethod = uri.path.orEmpty().substringAfterLast(":").ifBlank { uri.path.orEmpty() }
        Log.i(
            "AIQuotaGoogleWeb",
            "provider=${providerId.storageId} webViewCodeAssistResource " +
                "method=${request.method} api=$resourceMethod " +
                "hasAuth=${authHeader.isNotBlank()} authScheme=${authHeader.substringBefore(' ', "").take(32).ifBlank { "none" }} " +
                "hasCookieHeader=${hasHeader(headers, "Cookie")} cookieStoreCount=$exactCookieCount " +
                "origin=${hostHeader(headers, "Origin")} xOrigin=${hostHeader(headers, "X-Origin")} " +
                "referer=${hostHeader(headers, "Referer")} " +
                "contentType=${headerValue(headers, "Content-Type").substringBefore(";").ifBlank { "none" }} " +
                "xGoogAuthUser=${headerValue(headers, "X-Goog-AuthUser").take(8).ifBlank { "none" }} " +
                "secFetchSite=${headerValue(headers, "Sec-Fetch-Site").ifBlank { "none" }}"
        )
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
        val injectionKey = "${providerId.storageId}:${hostOf(url)}:${routeKeyOf(url)}"
        val firstInjectionForPage = collectorInjectionKeys.add(injectionKey)
        if (!firstInjectionForPage && !ProviderWebCollectorScripts.shouldAllowCollectorReinjection(providerId)) return
        if (firstInjectionForPage) {
            Log.i("AIQuotaCollector", "provider=${providerId.storageId} collectorMode=webview-js inject host=${hostOf(url)}")
        }
        val script = ProviderWebCollectorScripts.build(
            providerId = providerId,
            cookies = cookies,
            geminiCollectorAsset = geminiCollectorAsset,
            antigravityCollectorAsset = antigravityCollectorAsset,
            observedAccountId = observedCodexAccountId,
            pageText = pageText,
            pageUrl = url,
            awaitInteractiveLoginUsage = providerId == ProviderId.CODEX || providerId == ProviderId.GEMINI
        )
        view.evaluateJavascript(script, null)
    }

    private fun shouldKeepLoginOpenUntilUsagePayload(errorKind: String): Boolean {
        return when (providerId) {
            ProviderId.CODEX ->
                errorKind == "codex_usage_unavailable" || errorKind == "codex_auth_required"
            ProviderId.GEMINI ->
                errorKind == "gemini_no_trusted_payload" ||
                    errorKind == "gemini_collector_error" ||
                    errorKind == "gemini_login_required"
            ProviderId.GLM ->
                errorKind == "glm_no_trusted_payload"
            else -> false
        }
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
            Log.i("AIQuotaLogin", "provider=codex observedAccountId=true")
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

    private fun finishGlmNoSubscription(errorKind: String) {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        val repository = LocalUsageRepository(applicationContext)
        repository.markConnectedWithoutPlan(
            providerId = ProviderId.GLM,
            planLabel = GlmNoSubscriptionPolicy.PLAN_LABEL,
            message = GlmNoSubscriptionPolicy.MESSAGE
        )
        Log.i("AIQuotaLogin", "provider=${providerId.storageId} errorKind=$errorKind connectedWithoutPlan=true")
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    private fun finishConnectedWithoutUsage(message: String, errorKind: String) {
        if (finished) return
        if (isGoogleProvider()) {
            finishGoogleUsagePending(
                "Provider session reached, but trusted usage payload was not available yet.",
                errorKind
            )
            return
        }
        finished = true
        CookieManager.getInstance().flush()
        val repository = LocalUsageRepository(applicationContext)
        repository.markConnectedWithoutUsage(providerId, message)
        Log.w("AIQuotaLogin", "provider=${providerId.storageId} errorKind=$errorKind usageUnavailable=true")
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    private fun isGoogleProvider(): Boolean {
        return providerId == ProviderId.GEMINI || providerId == ProviderId.ANTIGRAVITY
    }

    private fun finishGoogleUsagePending(message: String, errorKind: String) {
        if (finished) return
        finished = true
        markGoogleUsagePendingAndStartCollection(message, errorKind)
        finish()
    }

    private fun markGoogleUsagePendingAndStartCollection(message: String, errorKind: String) {
        CookieManager.getInstance().flush()
        val repository = LocalUsageRepository(applicationContext)
        repository.markGoogleUsagePending(providerId, message)
        Log.w("AIQuotaLogin", "provider=${providerId.storageId} errorKind=$errorKind usagePending=true")
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        ProviderUsageCollectionService.start(
            context = applicationContext,
            providerId = providerId,
            source = ProviderUsageCollectionService.SOURCE_REFRESH
        )
    }

    private fun failKeepingPrevious(message: String, errorKind: String) {
        if (finished) return
        if (shouldKeepGoogleLoginRetryPending(errorKind, message)) {
            finishGoogleUsagePending(
                "Provider session reached, but trusted usage payload was not available yet.",
                errorKind
            )
            return
        }
        finished = true
        val repository = LocalUsageRepository(applicationContext)
        repository.failKeepingPrevious(providerId, message)
        Log.w("AIQuotaLogin", "provider=${providerId.storageId} errorKind=$errorKind keptPreviousSnapshot=true")
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    private fun saveOpenCodeUsageUrl(url: String) {
        if (providerId != ProviderId.OPENCODE) return
        ProviderScopedStateRepository(applicationContext).saveOpenCodeUsageUrl(url)
    }

    private fun shouldKeepGoogleLoginRetryPending(errorKind: String, message: String): Boolean {
        if (!isGoogleProvider()) return false
        if (errorKind == "google_oauth_failed") return false
        return errorKind.startsWith("main_frame_") || message.isRecoverableGoogleUsageFailureMessage()
    }

    private fun String.isRecoverableGoogleUsageFailureMessage(): Boolean {
        val normalized = trim().lowercase()
        if (normalized == "google authorization succeeded. usage collection will retry.") return true
        if (normalized == "previous collection did not finish.") return true
        return listOf(
            "provider login page failed to load",
            "provider login returned http",
            "trusted usage payload",
            "usage payload was not available",
            "quota payload was not available",
            "loadcodeassist returned http",
            "retrieveuserquota returned http",
            "fetchavailablemodels returned http",
            "no trusted quota",
            "missing_google_web_session_cookie",
            "background refresh page failed to load",
            "background refresh stopped",
            "collection failed"
        ).any { normalized.contains(it) }
    }

    private fun logFirstPageFinished(url: String) {
        if (firstPageLogged) return
        firstPageLogged = true
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        Log.i("AIQuotaLoginTiming", "${providerId.storageId} pageFinished=${elapsedMs}ms host=$host")
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

    private fun hostHeader(headers: Map<String, String>, name: String): String {
        return hostOf(headerValue(headers, name)).ifBlank { "none" }
    }

    private fun headerValue(headers: Map<String, String>, name: String): String {
        return headers.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            .orEmpty()
    }

    private fun hasHeader(headers: Map<String, String>, name: String): Boolean {
        return headers.keys.any { it.equals(name, ignoreCase = true) }
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

    private fun safeUrlForLog(url: String): String {
        return runCatching {
            val uri = URI(url)
            buildString {
                append(uri.scheme ?: "unknown")
                append("://")
                append(uri.host ?: "unknown")
                append(uri.path.orEmpty().ifBlank { "/" })
                val keys = uri.rawQuery.orEmpty()
                    .split("&")
                    .asSequence()
                    .map { it.substringBefore("=") }
                    .filter { it.isNotBlank() }
                    .map { key ->
                        when {
                            key.contains("code", ignoreCase = true) -> "code"
                            key.contains("token", ignoreCase = true) -> "token"
                            key.equals("state", ignoreCase = true) -> "state"
                            key.equals("continue", ignoreCase = true) -> "continue"
                            key.equals("part", ignoreCase = true) -> "part"
                            else -> key
                        }
                    }
                    .distinct()
                    .take(12)
                    .toList()
                if (keys.isNotEmpty()) {
                    append("?keys=")
                    append(keys.joinToString("|"))
                }
            }
        }.getOrDefault("invalid_url")
    }

    private fun decodeJsString(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return runCatching { JSONObject("""{"value":$value}""").optString("value") }.getOrDefault("")
    }

    companion object {
        private const val EXTRA_PROVIDER_ID = "providerId"
        private const val EXTRA_START_URL = "startUrl"
        private const val BRIDGE_NAME = "AIQuotaCollectorBridge"
        private const val CURSOR_NATIVE_FETCH_TIMEOUT_MS = 20_000
        private const val GEMINI_USAGE_REDIRECT_MIN_INTERVAL_MS = 1_500L
        private const val GEMINI_USAGE_REDIRECT_MAX_ATTEMPTS = 2
        private const val GEMINI_SIGN_IN_CLICK_MAX_ATTEMPTS = 2
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
