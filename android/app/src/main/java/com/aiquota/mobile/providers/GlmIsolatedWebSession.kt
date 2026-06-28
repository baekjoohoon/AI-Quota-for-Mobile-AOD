package com.aiquota.mobile.providers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.util.Log
import android.webkit.WebView
import com.aiquota.mobile.local.ProviderId
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object GlmIsolatedWebViewProfile {
    const val PROCESS_SUFFIX = ":glm_webview"
    private const val DATA_DIRECTORY_SUFFIX = "glm_webview"

    fun configureIfNeeded(context: Context): Boolean {
        val isGlmIsolatedProcess = currentProcessName(context).endsWith(PROCESS_SUFFIX)
        if (!isGlmIsolatedProcess) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix(DATA_DIRECTORY_SUFFIX)
        }
        return true
    }

    fun killIsolatedProcessIfRunning(context: Context, reason: String) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val currentPid = Process.myPid()
        manager.runningAppProcesses
            ?.asSequence()
            ?.filter { it.pid != currentPid && it.processName.endsWith(PROCESS_SUFFIX) }
            ?.forEach { process ->
                Log.w(TAG, "killProcess provider=glm pid=${process.pid} reason=$reason")
                Process.killProcess(process.pid)
            }
    }

    private fun currentProcessName(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return ApplicationProcessName.value()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
            .orEmpty()
    }
}

private const val TAG = "GlmIsolatedWebSession"

private object ApplicationProcessName {
    fun value(): String = android.app.Application.getProcessName()
}

sealed class GlmIsolatedUsageResult {
    data class Payload(
        val rawPayload: String,
        val cookieHeader: String?
    ) : GlmIsolatedUsageResult()
    data class Failure(val failure: ProviderRefreshFailure) : GlmIsolatedUsageResult()
}

object GlmIsolatedWebSession {
    suspend fun collectUsage(
        context: Context,
        startUrl: String,
        timeoutMillis: Long
    ): GlmIsolatedUsageResult {
        val result = awaitServiceResult(
            context = context,
            intent = GlmIsolatedWebSessionService.collectIntent(context, startUrl, timeoutMillis),
            timeoutMillis = timeoutMillis + SERVICE_RESULT_GRACE_MS,
            onResult = ::usageResultFor
        )
        if (result != null) return result
        GlmIsolatedWebViewProfile.killIsolatedProcessIfRunning(context, "collect_timeout")
        return GlmIsolatedUsageResult.Failure(ProviderRefreshTimeoutPolicy.failureFor(ProviderId.GLM, startUrl))
    }

    fun clear(context: Context) {
        runCatching {
            context.startService(GlmIsolatedWebSessionService.clearIntent(context))
        }
    }

    suspend fun clearAndWait(context: Context): Boolean {
        return awaitServiceResult(
            context = context,
            intent = GlmIsolatedWebSessionService.clearIntent(context),
            timeoutMillis = CLEAR_TIMEOUT_MS,
            onResult = { code, _ -> code == GlmIsolatedWebSessionService.RESULT_CLEARED }
        ) ?: false
    }

    private suspend fun <T> awaitServiceResult(
        context: Context,
        intent: Intent,
        timeoutMillis: Long,
        onResult: (Int, Bundle) -> T
    ): T? {
        return withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                            if (!continuation.isActive) return
                            continuation.resume(onResult(resultCode, resultData ?: Bundle.EMPTY))
                        }
                    }
                    intent.putExtra(GlmIsolatedWebSessionService.EXTRA_RESULT_RECEIVER, receiver)
                    runCatching {
                        context.startService(intent)
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    continuation.invokeOnCancellation {
                        runCatching {
                            context.startService(GlmIsolatedWebSessionService.cancelIntent(context))
                        }
                    }
                }
            }
        }
    }

    private fun usageResultFor(code: Int, bundle: Bundle): GlmIsolatedUsageResult {
        return when (code) {
            GlmIsolatedWebSessionService.RESULT_PAYLOAD ->
                GlmIsolatedUsageResult.Payload(
                    rawPayload = bundle.getString(GlmIsolatedWebSessionService.EXTRA_RAW_PAYLOAD).orEmpty(),
                    cookieHeader = bundle.getString(GlmIsolatedWebSessionService.EXTRA_COOKIE_HEADER)
                )
            else ->
                GlmIsolatedUsageResult.Failure(
                    ProviderRefreshFailure(
                        ProviderRefreshFailureKind.valueOf(
                            bundle.getString(
                                GlmIsolatedWebSessionService.EXTRA_FAILURE_KIND,
                                ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD.name
                            )
                        ),
                        bundle.getString(
                            GlmIsolatedWebSessionService.EXTRA_FAILURE_MESSAGE,
                            "GLM usage payload was not available."
                        )
                    )
                )
        }
    }

    private const val SERVICE_RESULT_GRACE_MS = 1_000L
    private const val CLEAR_TIMEOUT_MS = 5_000L
}
