package com.aiquota.mobile.providers

import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebStorage
import com.aiquota.mobile.local.ProviderId
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object ProviderWebSessionCleaner {
    fun clearProviderWebSession(providerId: ProviderId) {
        if (!ProviderWebSessionClearPolicy.shouldClearOnDisconnect(providerId)) return
        clearProviderWebSession(CookieManager.getInstance(), WebStorage.getInstance(), providerId)
    }

    suspend fun clearProviderWebSessionAndWait(providerId: ProviderId) {
        if (!ProviderWebSessionClearPolicy.shouldClearOnDisconnect(providerId)) return
        clearProviderWebSessionAndWait(CookieManager.getInstance(), WebStorage.getInstance(), providerId)
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

    private const val COOKIE_CLEAR_CALLBACK_TIMEOUT_MS = 750L
}
