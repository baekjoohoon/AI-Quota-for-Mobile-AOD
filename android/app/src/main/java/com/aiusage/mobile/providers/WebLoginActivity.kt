package com.aiusage.mobile.providers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aiusage.mobile.local.ProviderId
import java.io.ByteArrayInputStream

class WebLoginActivity : Activity() {
    private var webView: WebView? = null

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

        val loginView = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = AllowlistedWebViewClient(
                providerId = providerId,
                onBlockedMainFrame = {
                    stopLoading()
                    finish()
                }
            )
        }
        webView = loginView
        setContentView(loginView)
        loginView.loadUrl(startUrl)
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private class AllowlistedWebViewClient(
        private val providerId: ProviderId,
        private val onBlockedMainFrame: WebView.() -> Unit
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return request.isForMainFrame && shouldBlock(request.url.toString())
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return shouldBlock(url)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            if (url == null || shouldBlock(url)) {
                view.onBlockedMainFrame()
                return
            }
            super.onPageStarted(view, url, favicon)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (request.isForMainFrame && shouldBlock(request.url.toString())) {
                return emptyResponse()
            }
            return super.shouldInterceptRequest(view, request)
        }

        private fun shouldBlock(url: String): Boolean {
            return !isAllowedHttps(providerId, url)
        }

        private fun emptyResponse(): WebResourceResponse {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "com.aiusage.mobile.providers.extra.PROVIDER_ID"
        const val EXTRA_START_URL = "com.aiusage.mobile.providers.extra.START_URL"

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
