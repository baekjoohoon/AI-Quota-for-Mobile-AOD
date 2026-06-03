package com.aiquota.mobile.providers

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.aiquota.mobile.local.LocalUsageRepository
import com.aiquota.mobile.local.ProviderId
import org.json.JSONArray
import org.json.JSONObject

class ProviderUsageCollectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val providerId = ProviderId.fromStorageId(intent?.getStringExtra(EXTRA_PROVIDER_ID))
        if (providerId == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val repository = LocalUsageRepository(applicationContext)
        repository.markCollecting(providerId)

        val requestedSource = payloadSource(intent?.getStringExtra(EXTRA_SOURCE))
        val rawPayload = intent?.getStringExtra(EXTRA_RAW_PAYLOAD).orEmpty()
        completeCollection(
            providerId,
            requestedSource,
            rawPayload,
            bridgePayload = null,
            bridgeRequiresAuth = false,
            bridgeDiagnostic = null,
            startId
        )
        return START_NOT_STICKY
    }

    private fun completeCollection(
        providerId: ProviderId,
        requestedSource: ProviderPayloadSource,
        rawPayload: String,
        bridgePayload: String?,
        bridgeRequiresAuth: Boolean,
        bridgeDiagnostic: String?,
        startId: Int
    ) {
        val repository = LocalUsageRepository(applicationContext)
        val effectivePayload = rawPayload.ifBlank { bridgePayload.orEmpty() }
        val source = if (rawPayload.isBlank() && !bridgePayload.isNullOrBlank()) {
            ProviderPayloadSource.PROVIDER_API
        } else {
            requestedSource
        }
        val snapshot = if (effectivePayload.isNotBlank()) {
            ProviderUsageNormalizer.normalize(providerId, effectivePayload, source)
        } else {
            null
        }

        if (snapshot != null) {
            repository.saveSnapshot(snapshot)
            Log.i(
                "AIQuotaCollector",
                "provider=${providerId.storageId} source=${source.label} lineCount=${snapshot.lines.size} planPresent=${snapshot.planLabel != null} accountPresent=${snapshot.account != null} keptPreviousSnapshot=false"
            )
        } else {
            if (bridgeRequiresAuth) {
                if (ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)) {
                    ProviderSessionResetter(applicationContext).disconnect(providerId)
                }
                repository.markSessionExpired(providerId, getString(com.aiquota.mobile.R.string.provider_status_auth_required))
            } else if (
                ProviderRefreshFailureClassifier.requiresInteractiveAuth(
                    providerId,
                    ProviderRefreshFailureKind.NO_TRUSTED_PAYLOAD
                )
            ) {
                if (ProviderRefreshSessionPolicy.shouldClearCredentialsOnRefreshAuthFailure(providerId)) {
                    ProviderSessionResetter(applicationContext).disconnect(providerId)
                }
                repository.markSessionExpired(providerId, getString(com.aiquota.mobile.R.string.provider_status_auth_required))
            } else if (providerId == ProviderId.GEMINI || providerId == ProviderId.ANTIGRAVITY) {
                repository.markGoogleUsagePending(
                    providerId,
                    bridgeDiagnostic?.takeIf { it.isNotBlank() }
                        ?: GoogleUsagePendingRetryPolicy.PENDING_MESSAGE
                )
            } else if (providerId == ProviderId.CURSOR) {
                Log.w("AIQuotaCollector", "provider=cursor payloadSummary=${cursorPayloadSummary(rawPayload)}")
                repository.failKeepingPrevious(providerId, "Collector ran. No trusted Cursor usage payload found yet.")
            } else {
                repository.failKeepingPrevious(providerId, "No trusted provider usage payload was collected.")
            }
            Log.w(
                "AIQuotaCollector",
                "provider=${providerId.storageId} source=${source.label} lineCount=0 " +
                    "requiresAuth=$bridgeRequiresAuth errorKind=no_trusted_payload keptPreviousSnapshot=true"
            )
        }
        UsageSurfaceRefresher.refresh(applicationContext, repository)

        stopSelf(startId)
    }

    private fun safeDiagnostic(message: String?): String {
        return message
            ?.replace(Regex("code=[^\\s&]+"), "code=redacted")
            ?.replace(Regex("token=[^\\s&]+"), "token=redacted")
            ?.take(180)
            ?: "none"
    }

    companion object {
        const val SOURCE_LOGIN = "login"
        const val SOURCE_REFRESH = "refresh"
        const val SOURCE_PAYLOAD = "payload"

        private const val EXTRA_PROVIDER_ID = "providerId"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_RAW_PAYLOAD = "rawPayload"

        fun start(
            context: Context,
            providerId: ProviderId,
            source: String,
            rawPayload: String? = null
        ) {
            val intent = Intent(context, ProviderUsageCollectionService::class.java)
                .putExtra(EXTRA_PROVIDER_ID, providerId.storageId)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_RAW_PAYLOAD, rawPayload)
            context.startService(intent)
        }

        private fun payloadSource(value: String?): ProviderPayloadSource {
            return when (value) {
                SOURCE_LOGIN -> ProviderPayloadSource.STRUCTURED_SCRIPT
                SOURCE_PAYLOAD -> ProviderPayloadSource.PROVIDER_API
                else -> ProviderPayloadSource.PROVIDER_API
            }
        }

        private fun cursorPayloadSummary(rawPayload: String): String {
            val json = runCatching { JSONObject(rawPayload) }.getOrNull() ?: return "invalid_json"
            fun keysOf(obj: JSONObject?): String {
                if (obj == null) return "none"
                return obj.keys().asSequence()
                    .filterNot { it.contains("token", ignoreCase = true) || it.contains("cookie", ignoreCase = true) }
                    .take(12)
                    .joinToString("|")
                    .ifBlank { "empty" }
            }
            fun lengthOf(array: JSONArray?): Int = array?.length() ?: 0
            val individualUsage = json.optJSONObject("individualUsage")
            val requestUsage = json.optJSONObject("requestUsage")
            val lines = json.optJSONArray("lines")
            return listOf(
                "root=${keysOf(json)}",
                "planUsage=${keysOf(json.optJSONObject("planUsage"))}",
                "individualUsage=${keysOf(individualUsage)}",
                "individualPlan=${keysOf(individualUsage?.optJSONObject("plan"))}",
                "individualOnDemand=${keysOf(individualUsage?.optJSONObject("onDemand"))}",
                "requestUsage=${keysOf(requestUsage)}",
                "limits=${lengthOf(json.optJSONArray("limits"))}",
                "lines=${lengthOf(lines)}",
                "creditGrants=${keysOf(json.optJSONObject("creditGrants"))}"
            ).joinToString(",")
        }
    }
}
