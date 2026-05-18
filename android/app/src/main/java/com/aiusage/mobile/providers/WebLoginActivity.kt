package com.aiusage.mobile.providers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.aiusage.mobile.R
import com.aiusage.mobile.localization.withAppLanguageForDeviceLanguage
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.ProviderUsageSnapshot
import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.math.roundToInt

class WebLoginActivity : Activity() {
    private var webView: WebView? = null
    private var activeProviderId: ProviderId? = null
    private var connectionRecorded = false
    private var usageRecorded = false
    private var loginCompletionRecorded = false
    private var cancellationRecorded = false
    private var codexOAuthCompletionStarted = false
    private val popupViews = mutableListOf<WebView>()
    private val popupContainers = mutableMapOf<WebView, FrameLayout>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLanguageForDeviceLanguage())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val providerId = intent.getStringExtra(EXTRA_PROVIDER_ID)
            ?.let(ProviderId::fromStorageId)
        val startUrl = intent.getStringExtra(EXTRA_START_URL).orEmpty()

        if (providerId == null || !isAllowedHttps(providerId, startUrl)) {
            finish()
            return
        }

        activeProviderId = providerId
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "login open provider=${providerId.storageId} url=${ProviderCollectionDiagnostics.safeUrl(startUrl)}"
        )
        val loginView = createLoginView(providerId, finishOnBlocked = true)
        webView = loginView
        setContentView(createLoginContainer(loginView))
        loginView.loadUrl(startUrl)
    }

    override fun onPause() {
        if (isFinishing) {
            saveCancelledSnapshotIfNeeded()
        }
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        saveCancelledSnapshotIfNeeded()
        popupViews.toList().forEach(::closePopupWindow)
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createLoginView(providerId: ProviderId, finishOnBlocked: Boolean): WebView {
        return WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(true)
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = MOBILE_CHROME_USER_AGENT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(UsageBridge(this@WebLoginActivity, providerId, this), USAGE_BRIDGE_NAME)
            webChromeClient = UsageWebChromeClient(providerId, this@WebLoginActivity)
            webViewClient = AllowlistedWebViewClient(
                providerId = providerId,
                onCodexOAuthCallback = { callbackUrl ->
                    handleCodexOAuthCallback(callbackUrl)
                },
                onBlockedMainFrame = { blockedUrl ->
                    Log.w(
                        ProviderCollectionDiagnostics.TAG,
                        "login blocked provider=${providerId.storageId} url=" +
                            ProviderCollectionDiagnostics.safeUrl(blockedUrl)
                    )
                    stopLoading()
                    if (finishOnBlocked) {
                        saveErrorSnapshot(providerId, getString(R.string.provider_login_open_failed_message))
                        finish()
                    } else {
                        closePopupWindow(this)
                    }
                },
                onAllowedPageStarted = { view, startedUrl ->
                    Log.d(
                        ProviderCollectionDiagnostics.TAG,
                        "login pageStarted provider=${providerId.storageId} url=" +
                            ProviderCollectionDiagnostics.safeUrl(startedUrl)
                    )
                    installProviderUsageHooks(providerId, view)
                },
                onAllowedPageFinished = { view, finishedUrl ->
                    Log.d(
                        ProviderCollectionDiagnostics.TAG,
                        "login pageFinished provider=${providerId.storageId} url=" +
                            ProviderCollectionDiagnostics.safeUrl(finishedUrl)
                    )
                    collectProviderUsage(providerId, finishedUrl, view)
                },
                onMainFrameError = { _, errorUrl, errorCode, description ->
                    Log.w(
                        ProviderCollectionDiagnostics.TAG,
                        "login mainFrameError provider=${providerId.storageId} url=" +
                            "${ProviderCollectionDiagnostics.safeUrl(errorUrl)} " +
                            ProviderCollectionDiagnostics.webError(errorCode, description)
                    )
                    saveErrorSnapshot(providerId, getString(R.string.provider_login_open_failed_message))
                    finish()
                }
            )
        }
    }

    private fun handleCodexOAuthCallback(callbackUrl: String) {
        val providerId = activeProviderId ?: return
        if (providerId != ProviderId.CODEX || codexOAuthCompletionStarted) return
        codexOAuthCompletionStarted = true
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "login codexOAuthCallback provider=${providerId.storageId}"
        )
        Thread {
            val result = CodexOAuthRepository(applicationContext).completeAuthorization(callbackUrl)
            runOnUiThread {
                if (result.isSuccess) {
                    connectionRecorded = true
                    saveConnectedSnapshot(
                        providerId = providerId,
                        message = getString(R.string.provider_refresh_started_message),
                        keepExistingLines = true
                    )
                    startBackgroundUsageCollection(providerId)
                    finishAfterProviderCapture()
                } else {
                    Log.w(
                        ProviderCollectionDiagnostics.TAG,
                        "login codexOAuthFailed provider=${providerId.storageId} " +
                            result.exceptionOrNull()?.javaClass?.simpleName.orEmpty()
                    )
                    saveErrorSnapshot(providerId, getString(R.string.provider_login_open_failed_message))
                    finish()
                }
            }
        }.start()
    }

    private fun installProviderUsageHooks(providerId: ProviderId, target: WebView) {
        if (usageRecorded) return
        target.evaluateJavascript(
            ProviderLocalUsageCollector.hookScriptFor(
                providerId,
                ProviderCollectorAssets.scriptFor(this, providerId)
            ),
            null
        )
    }

    private fun createPopupWindow(providerId: ProviderId): WebView {
        val popup = createLoginView(providerId, finishOnBlocked = false)
        popupViews.add(popup)
        return popup
    }

    private fun createLoginContainer(loginView: WebView): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(
                0,
                systemBarDimensionPx("status_bar_height") + WEB_LOGIN_TOP_SAFE_PADDING_DP.dpToPx(),
                0,
                systemBarDimensionPx("navigation_bar_height")
            )
            addView(
                loginView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun closePopupWindow(window: WebView) {
        window.stopLoading()
        val popupContainer = popupContainers.remove(window)
        if (popupContainer != null) {
            (popupContainer.parent as? ViewGroup)?.removeView(popupContainer)
        } else {
            (window.parent as? ViewGroup)?.removeView(window)
        }
        popupViews.remove(window)
        window.destroy()
    }

    private fun systemBarDimensionPx(name: String): Int {
        val resourceId = resources.getIdentifier(name, "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).roundToInt()
    }

    private fun collectProviderUsage(providerId: ProviderId, url: String, target: WebView, attempt: Int = 0) {
        if (usageRecorded) return
        target.postDelayed(
            {
                if (usageRecorded) return@postDelayed
                target.evaluateJavascript(
                    ProviderLocalUsageCollector.scriptFor(
                        providerId,
                        ProviderCollectorAssets.scriptFor(this, providerId)
                    )
                ) { rawValue ->
                    val localUsagePayload = ProviderLocalUsageCollector.decodeJavascriptString(rawValue)
                    val currentUrl = target.url ?: url
                    handleLocalUsagePayload(
                        providerId = providerId,
                        url = currentUrl,
                        localUsagePayload = localUsagePayload,
                        finishWhenNoUsage = attempt >= MAX_USAGE_CAPTURE_ATTEMPTS
                    )
                    if (
                        !usageRecorded &&
                        !connectionRecorded &&
                        attempt < MAX_USAGE_CAPTURE_ATTEMPTS
                    ) {
                        retryProviderUsageCollection(
                            providerId = providerId,
                            url = currentUrl,
                            target = target,
                            nextAttempt = attempt + 1
                        )
                    } else if (
                        !usageRecorded &&
                        !connectionRecorded &&
                        loginCompletionRecorded
                    ) {
                        finishConnectedCaptureWithoutUsage(providerId)
                    }
                }
            },
            PAGE_TEXT_CAPTURE_DELAY_MS
        )
    }

    private fun handleLocalUsagePayload(
        providerId: ProviderId,
        url: String,
        localUsagePayload: String,
        finishWhenNoUsage: Boolean = false
    ) {
        if (usageRecorded) return
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "login payload ${ProviderCollectionDiagnostics.payloadSummary(providerId, localUsagePayload)} " +
                "url=${ProviderCollectionDiagnostics.safeUrl(url)}"
        )
        val extractedSnapshot = TextUsageExtractor.extract(providerId, localUsagePayload)
        if (
            extractedSnapshot.connectionState == ProviderConnectionState.CONNECTED &&
            extractedSnapshot.lines.isNotEmpty()
        ) {
            usageRecorded = true
            connectionRecorded = true
            saveUsageSnapshot(extractedSnapshot)
            finishAfterProviderCapture()
            return
        }
        val loginComplete = ProviderLoginCompletionDetector.isLoginComplete(providerId, url, localUsagePayload)
        if (!loginComplete) {
            if (!loginCompletionRecorded) return
        } else {
            loginCompletionRecorded = true
        }
        if (!finishWhenNoUsage) return
        finishConnectedCaptureWithoutUsage(providerId)
    }

    private fun finishConnectedCaptureWithoutUsage(providerId: ProviderId) {
        if (connectionRecorded || usageRecorded) return
        if (!connectionRecorded) {
            connectionRecorded = true
            saveConnectedSnapshot(providerId, getString(R.string.provider_refresh_started_message))
        }
        startBackgroundUsageCollection(providerId)
        finishAfterProviderCapture()
    }

    private fun retryProviderUsageCollection(
        providerId: ProviderId,
        url: String,
        target: WebView,
        nextAttempt: Int
    ) {
        target.postDelayed(
            {
                if (!usageRecorded) {
                    collectProviderUsage(providerId, target.url ?: url, target, nextAttempt)
                }
            },
            USAGE_CAPTURE_RETRY_DELAY_MS
        )
    }

    private fun saveUsageSnapshot(snapshot: ProviderUsageSnapshot) {
        LocalUsageRepository(applicationContext).saveSnapshot(
            snapshot.copy(
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                message = getString(R.string.provider_usage_updated_message)
            )
        )
    }

    private fun saveCancelledSnapshotIfNeeded() {
        if (isChangingConfigurations || connectionRecorded || usageRecorded || cancellationRecorded) return
        val providerId = activeProviderId ?: return
        cancellationRecorded = true
        Log.d(
            ProviderCollectionDiagnostics.TAG,
            "login cancelled provider=${providerId.storageId}"
        )
        val repository = LocalUsageRepository(applicationContext)
        val currentSnapshot = repository.readSnapshots().firstOrNull { it.providerId == providerId }
        val snapshot = if (currentSnapshot?.connectionState == ProviderConnectionState.CONNECTED) {
            currentSnapshot
        } else {
            ProviderUsageSnapshot.disconnected(providerId)
        }
        repository.saveSnapshot(
            snapshot.copy(
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                message = getString(R.string.provider_login_cancelled_message)
            )
        )
    }

    private fun saveErrorSnapshot(providerId: ProviderId, message: String) {
        if (connectionRecorded || usageRecorded || cancellationRecorded) return
        cancellationRecorded = true
        LocalUsageRepository(applicationContext).saveSnapshot(
            ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.ERROR,
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                message = message
            )
        )
    }

    private fun saveConnectedSnapshot(
        providerId: ProviderId,
        message: String,
        keepExistingLines: Boolean = false
    ) {
        val repository = LocalUsageRepository(applicationContext)
        val currentSnapshot = repository.readSnapshots().firstOrNull { snapshot ->
            snapshot.providerId == providerId
        }
        repository.saveSnapshot(
            ProviderUsageSnapshot(
                providerId = providerId,
                connectionState = ProviderConnectionState.CONNECTED,
                refreshState = ProviderRefreshState.IDLE,
                updatedAt = Instant.now().toString(),
                lines = if (keepExistingLines) currentSnapshot?.lines.orEmpty() else emptyList(),
                message = message
            )
        )
    }

    private fun finishAfterProviderCapture() {
        CookieManager.getInstance().flush()
        setResult(RESULT_OK)
        finish()
    }

    private fun startBackgroundUsageCollection(providerId: ProviderId) {
        ProviderUsageCollectionService.start(
            context = applicationContext,
            providerId = providerId,
            source = ProviderUsageCollectionService.SOURCE_LOGIN
        )
    }

    private class AllowlistedWebViewClient(
        private val providerId: ProviderId,
        private val onCodexOAuthCallback: WebView.(String) -> Unit,
        private val onBlockedMainFrame: WebView.(String) -> Unit,
        private val onAllowedPageStarted: (WebView, String) -> Unit,
        private val onAllowedPageFinished: (WebView, String) -> Unit,
        private val onMainFrameError: (WebView, String, Int, CharSequence?) -> Unit
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (CodexOAuthRepository.isCallbackUrl(request.url.toString())) {
                view.onCodexOAuthCallback(request.url.toString())
                return true
            }
            if (shouldIgnoreNonWebNavigation(request.url.toString())) {
                return true
            }
            if (request.isForMainFrame && shouldBlock(request.url.toString())) {
                view.onBlockedMainFrame(request.url.toString())
                return true
            }
            return false
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (CodexOAuthRepository.isCallbackUrl(url)) {
                view.onCodexOAuthCallback(url)
                return true
            }
            if (shouldIgnoreNonWebNavigation(url)) {
                return true
            }
            if (shouldBlock(url)) {
                view.onBlockedMainFrame(url)
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            if (CodexOAuthRepository.isCallbackUrl(url)) {
                view.onCodexOAuthCallback(url.orEmpty())
                return
            }
            if (shouldIgnoreNonWebNavigation(url)) {
                return
            }
            if (url == null || shouldBlock(url)) {
                view.onBlockedMainFrame(url.orEmpty())
                return
            }
            super.onPageStarted(view, url, favicon)
            onAllowedPageStarted(view, url)
        }

        override fun onPageCommitVisible(view: WebView, url: String?) {
            if (url != null && !shouldBlock(url)) {
                onAllowedPageStarted(view, url)
                onAllowedPageFinished(view, url)
            }
            super.onPageCommitVisible(view, url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (CodexOAuthRepository.isCallbackUrl(url)) {
                return
            }
            if (url == null || shouldBlock(url)) {
                return
            }
            super.onPageFinished(view, url)
            onAllowedPageFinished(view, url)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (CodexOAuthRepository.isCallbackUrl(request.url.toString())) {
                return emptyResponse()
            }
            if (shouldIgnoreNonWebNavigation(request.url.toString())) {
                return emptyResponse()
            }
            if (request.isForMainFrame && shouldBlock(request.url.toString())) {
                return emptyResponse()
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                onMainFrameError(
                    view,
                    request.url.toString(),
                    error.errorCode,
                    error.description
                )
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
            onMainFrameError(view, failingUrl.orEmpty(), errorCode, description)
            super.onReceivedError(view, errorCode, description, failingUrl)
        }

        private fun shouldBlock(url: String): Boolean {
            return !isAllowedHttps(providerId, url)
        }

        private fun shouldIgnoreNonWebNavigation(url: String?): Boolean {
            val scheme = Uri.parse(url.orEmpty()).scheme?.lowercase().orEmpty()
            return scheme.isNotBlank() && scheme != "http" && scheme != "https"
        }

        private fun emptyResponse(): WebResourceResponse {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    private class UsageBridge(
        private val activity: WebLoginActivity,
        private val providerId: ProviderId,
        private val target: WebView
    ) {
        @JavascriptInterface
        fun onUsagePayload(payload: String?) {
            activity.runOnUiThread {
                activity.handleLocalUsagePayload(
                    providerId = providerId,
                    url = target.url.orEmpty(),
                    localUsagePayload = payload.orEmpty(),
                    finishWhenNoUsage = false
                )
            }
        }
    }

    private class UsageWebChromeClient(
        private val providerId: ProviderId,
        private val activity: WebLoginActivity
    ) : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message
        ): Boolean {
            val popup = activity.createPopupWindow(providerId)
            val popupContainer = activity.createLoginContainer(popup)
            activity.popupContainers[popup] = popupContainer
            activity.addContentView(
                popupContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            activity.closePopupWindow(window)
            activity.webView?.postDelayed(
                {
                    activity.webView?.reload()
                    activity.webView?.let { rootView ->
                        activity.collectProviderUsage(
                            providerId = providerId,
                            url = rootView.url.orEmpty(),
                            target = rootView
                        )
                    }
                },
                POPUP_CLOSE_REFRESH_DELAY_MS
            )
        }
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "com.aiusage.mobile.providers.extra.PROVIDER_ID"
        const val EXTRA_START_URL = "com.aiusage.mobile.providers.extra.START_URL"
        private const val USAGE_BRIDGE_NAME = "AIUsageLocalCollector"
        private const val PAGE_TEXT_CAPTURE_DELAY_MS = 700L
        private const val USAGE_CAPTURE_RETRY_DELAY_MS = 1_200L
        private const val MAX_USAGE_CAPTURE_ATTEMPTS = 4
        private const val POPUP_CLOSE_REFRESH_DELAY_MS = 300L
        private const val WEB_LOGIN_TOP_SAFE_PADDING_DP = 12
        private const val MOBILE_CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/119.0.0.0 Mobile Safari/537.36"

        fun createIntent(context: Context, providerId: ProviderId, startUrl: String): Intent {
            return Intent(context, WebLoginActivity::class.java)
                .putExtra(EXTRA_PROVIDER_ID, providerId.storageId)
                .putExtra(EXTRA_START_URL, startUrl)
        }

        private fun isAllowedHttps(providerId: ProviderId, url: String?): Boolean {
            val candidateUrl = url?.trim().orEmpty()
            if (candidateUrl.isBlank()) return false

            val isHttps = Uri.parse(candidateUrl)
                .scheme
                ?.equals("https", ignoreCase = true) == true
            return isHttps && ProviderHostAllowlist.isAllowed(providerId, candidateUrl)
        }
    }
}
