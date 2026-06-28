package com.aiquota.mobile.providers

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aiquota.mobile.local.ProviderId
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal object ProviderWebSessionMaintenanceGate {
    private val mutex = Mutex()

    suspend fun <T> withMaintenanceLock(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }
}

object ProviderWebSessionCleaner {
    fun clearProviderWebSession(context: Context, providerId: ProviderId) {
        if (!ProviderWebSessionClearPolicy.shouldClearOnDisconnect(providerId)) return
        if (providerId == ProviderId.GLM) {
            GlmIsolatedWebSession.clear(context.applicationContext)
            return
        }
        clearProviderWebSession(providerId)
    }

    fun clearProviderWebSession(providerId: ProviderId) {
        if (!ProviderWebSessionClearPolicy.shouldClearOnDisconnect(providerId)) return
        clearProviderWebSession(CookieManager.getInstance(), WebStorage.getInstance(), providerId)
    }

    suspend fun clearProviderWebSessionAndWait(providerId: ProviderId) {
        if (!ProviderWebSessionClearPolicy.shouldClearOnDisconnect(providerId)) return
        ProviderWebSessionMaintenanceGate.withMaintenanceLock {
            clearProviderWebSessionAndWait(CookieManager.getInstance(), WebStorage.getInstance(), providerId)
        }
    }

    suspend fun clearProviderWebSessionAndWait(context: Context, providerId: ProviderId) {
        if (!ProviderWebSessionClearPolicy.shouldClearOnDisconnect(providerId)) return
        ProviderWebSessionMaintenanceGate.withMaintenanceLock {
            withContext(Dispatchers.Main.immediate) {
                if (providerId == ProviderId.GLM) {
                    GlmIsolatedWebSession.clearAndWait(context.applicationContext)
                    return@withContext
                }
                clearProviderWebSessionCookiesAndWait(CookieManager.getInstance(), providerId)
                clearProviderWebStorageOrigins(WebStorage.getInstance(), providerId)
                clearProviderBrowserStorageWithWebView(context.applicationContext, providerId)
            }
        }
    }

    fun clearProviderWebSession(
        cookieManager: CookieManager,
        webStorage: WebStorage,
        providerId: ProviderId
    ) {
        clearProviderWebSessionCookies(cookieManager, providerId)
        clearProviderWebStorageOrigins(webStorage, providerId)
    }

    suspend fun clearProviderWebSessionAndWait(
        cookieManager: CookieManager,
        webStorage: WebStorage,
        providerId: ProviderId
    ) {
        withContext(Dispatchers.Main.immediate) {
            clearProviderWebSessionCookiesAndWait(cookieManager, providerId)
            clearProviderWebStorageOrigins(webStorage, providerId)
        }
    }

    fun clearProviderWebSessionCookies(providerId: ProviderId) {
        if (!ProviderWebSessionClearPolicy.shouldClearOnDisconnect(providerId)) return
        clearProviderWebSessionCookies(CookieManager.getInstance(), providerId)
    }

    fun clearProviderWebSessionCookies(cookieManager: CookieManager, providerId: ProviderId) {
        ProviderWebSessionClearPolicy.cookieUrls(providerId).forEach { url ->
            ProviderWebSessionClearPolicy.expiringCookieHeaders(
                cookieHeader = cookieManager.getCookie(url),
                url = url
            ).forEach { header ->
                cookieManager.setCookie(url, header)
            }
        }
        cookieManager.flush()
    }

    private suspend fun clearProviderWebSessionCookiesAndWait(
        cookieManager: CookieManager,
        providerId: ProviderId
    ) {
        val cookieHeaders = ProviderWebSessionClearPolicy.cookieUrls(providerId).flatMap { url ->
            ProviderWebSessionClearPolicy.expiringCookieHeaders(
                cookieHeader = cookieManager.getCookie(url),
                url = url
            ).map { header -> url to header }
        }
        cookieHeaders.forEach { (url, header) ->
            val completed = withTimeoutOrNull(COOKIE_CLEAR_CALLBACK_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    runCatching {
                        cookieManager.setCookie(
                            url,
                            header,
                            ValueCallback<Boolean> {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                        )
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            if (completed == null) {
                cookieManager.setCookie(url, header)
            }
        }
        cookieManager.flush()
    }

    private fun clearProviderWebStorageOrigins(webStorage: WebStorage, providerId: ProviderId) {
        ProviderWebSessionClearPolicy.storageOrigins(providerId).forEach { origin ->
            webStorage.deleteOrigin(origin)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun clearProviderBrowserStorageWithWebView(context: Context, providerId: ProviderId) {
        ProviderWebSessionClearPolicy.browserStorageCleanupUrls(providerId).forEach { url ->
            withTimeoutOrNull(BROWSER_STORAGE_CLEAR_TIMEOUT_MS) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    var cleanupView: WebView? = null
                    fun finish() {
                        val view = cleanupView
                        cleanupView = null
                        runCatching {
                            view?.stopLoading()
                            view?.destroy()
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    cleanupView = WebView(context).apply {
                        visibility = View.GONE
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, finishedUrl: String) {
                                view.evaluateJavascript(browserStorageCleanupScript()) {
                                    finish()
                                }
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame) finish()
                            }
                        }
                    }
                    continuation.invokeOnCancellation {
                        runCatching {
                            cleanupView?.stopLoading()
                            cleanupView?.destroy()
                        }
                        cleanupView = null
                    }
                    cleanupView?.loadUrl(url)
                }
            }
        }
    }

    internal fun browserStorageCleanupScript(): String {
        return """
            (function(){
              function resolve(value) {
                return Promise.resolve(value).catch(function(){ return false; });
              }
              function deleteDatabase(name) {
                return new Promise(function(done) {
                  try {
                    var request = indexedDB.deleteDatabase(name);
                    request.onsuccess = function(){ done(true); };
                    request.onerror = function(){ done(false); };
                    request.onblocked = function(){ done(false); };
                  } catch (e) {
                    done(false);
                  }
                });
              }
              var tasks = [];
              try { localStorage.clear(); } catch (e) {}
              try { sessionStorage.clear(); } catch (e) {}
              try {
                if (window.indexedDB && indexedDB.databases) {
                  tasks.push(resolve(indexedDB.databases()).then(function(databases) {
                    return Promise.all((databases || []).map(function(database) {
                      return database && database.name ? deleteDatabase(database.name) : Promise.resolve(false);
                    }));
                  }));
                }
              } catch (e) {}
              try {
                if (window.caches) {
                  tasks.push(resolve(caches.keys()).then(function(keys) {
                    return Promise.all((keys || []).map(function(key) { return caches.delete(key); }));
                  }));
                }
              } catch (e) {}
              try {
                if (navigator.serviceWorker) {
                  tasks.push(resolve(navigator.serviceWorker.getRegistrations()).then(function(registrations) {
                    return Promise.all((registrations || []).map(function(registration) {
                      return registration.unregister();
                    }));
                  }));
                }
              } catch (e) {}
              return Promise.all(tasks).then(function(){ return true; }).catch(function(){ return false; });
            })();
        """.trimIndent()
    }

    private const val COOKIE_CLEAR_CALLBACK_TIMEOUT_MS = 750L
    private const val BROWSER_STORAGE_CLEAR_TIMEOUT_MS = 2_000L
}
