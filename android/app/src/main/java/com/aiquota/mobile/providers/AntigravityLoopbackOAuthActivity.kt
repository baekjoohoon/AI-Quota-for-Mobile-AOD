package com.aiquota.mobile.providers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.local.ProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AntigravityLoopbackOAuthActivity : Activity() {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile
    private var finished = false
    @Volatile
    private var processingCallback = false
    private lateinit var webView: WebView
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalUsageRepository(applicationContext).markConnecting(ProviderId.ANTIGRAVITY)
        webView = createOAuthWebView()
        statusView = TextView(this).apply {
            text = "Preparing Google sign-in."
            gravity = Gravity.CENTER
            textSize = 16f
            visibility = View.VISIBLE
        }
        setContentView(
            FrameLayout(this).apply {
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                addView(
                    statusView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        )
        startFirebaseOAuth()
    }

    override fun onDestroy() {
        activityScope.cancel()
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        if (!finished) {
            LocalUsageRepository(applicationContext).markLoginCancelled(
                ProviderId.ANTIGRAVITY,
                "Provider login was cancelled."
            )
            UsageSurfaceRefresher.refresh(applicationContext, LocalUsageRepository(applicationContext))
        }
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createOAuthWebView(): WebView {
        CookieManager.getInstance().setAcceptCookie(true)
        return WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.userAgentString = ProviderWebViewUserAgent.loginUserAgent(applicationContext)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return maybeHandleOAuthCallback(request.url.toString())
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    maybeHandleOAuthCallback(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (!processingCallback) statusView.visibility = View.GONE
                }
            }
        }
    }

    private fun startFirebaseOAuth() {
        activityScope.launch {
            statusView.text = "Preparing Google sign-in."
            val authorizationUrl = runCatching {
                withContext(Dispatchers.IO) {
                    AntigravityFirebaseGateway(applicationContext).startOAuth()
                }
            }.onFailure { error ->
                Log.w(TAG, "webviewOAuth startFailed=${error.javaClass.simpleName}")
            }.getOrNull()
            if (authorizationUrl.isNullOrBlank()) {
                failKeepingPrevious(
                    "Antigravity OAuth could not be started.",
                    "antigravity_oauth_start_failed"
                )
                return@launch
            }
            statusView.visibility = View.GONE
            webView.loadUrl(authorizationUrl)
        }
    }

    private fun maybeHandleOAuthCallback(url: String): Boolean {
        if (!AntigravityOAuthRepository.isLoopbackOAuthCallback(url)) return false
        if (processingCallback) return true
        processingCallback = true
        statusView.text = "Collecting Antigravity usage."
        statusView.visibility = View.VISIBLE
        webView.stopLoading()
        activityScope.launch {
            val repository = AntigravityOAuthRepository(applicationContext)
            val payload = runCatching {
                withContext(Dispatchers.IO) {
                    val tokenResult = AntigravityFirebaseGateway(applicationContext).completeOAuth(url)
                    repository.fetchUsagePayloadFromGatewayTokenResult(tokenResult)
                }
            }.onFailure { error ->
                Log.w(TAG, "webviewOAuth completeFailed=${error.javaClass.simpleName}")
            }.getOrNull()
            val diagnostic = repository.lastFailureDiagnostic()
            if (!payload.isNullOrBlank()) {
                completeWithPayload(payload)
            } else {
                finishGoogleUsagePending(
                    diagnostic ?: "Antigravity OAuth completed, but quota payload was not available.",
                    "antigravity_oauth_no_payload"
                )
            }
        }
        return true
    }

    private fun completeWithPayload(rawPayload: String) {
        if (finished) return
        finished = true
        val repository = LocalUsageRepository(applicationContext)
        val snapshot = ProviderUsageNormalizer.normalize(
            ProviderId.ANTIGRAVITY,
            rawPayload,
            ProviderPayloadSource.PROVIDER_API
        )
        if (snapshot != null) {
            repository.saveSnapshot(snapshot)
            Log.i(
                TAG,
                "webviewOAuth usageSaved lineCount=${snapshot.lines.size} planPresent=${snapshot.planLabel != null}"
            )
        } else {
            repository.markGoogleUsagePending(
                ProviderId.ANTIGRAVITY,
                "Antigravity OAuth completed, but quota payload was not trusted."
            )
            Log.w(TAG, "webviewOAuth noTrustedPayload=true")
        }
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    private fun finishGoogleUsagePending(message: String, errorKind: String) {
        if (finished) return
        finished = true
        val repository = LocalUsageRepository(applicationContext)
        repository.markGoogleUsagePending(ProviderId.ANTIGRAVITY, message)
        Log.w(TAG, "webviewOAuth errorKind=$errorKind usagePending=true")
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    private fun failKeepingPrevious(message: String, errorKind: String) {
        if (finished) return
        finished = true
        val repository = LocalUsageRepository(applicationContext)
        repository.failKeepingPrevious(ProviderId.ANTIGRAVITY, message)
        Log.w(TAG, "webviewOAuth errorKind=$errorKind keptPreviousSnapshot=true")
        UsageSurfaceRefresher.refresh(applicationContext, repository)
        finish()
    }

    companion object {
        private const val TAG = "AIQuotaAntigravity"

        fun createIntent(context: Context): Intent {
            return Intent(context, AntigravityLoopbackOAuthActivity::class.java)
        }
    }
}
