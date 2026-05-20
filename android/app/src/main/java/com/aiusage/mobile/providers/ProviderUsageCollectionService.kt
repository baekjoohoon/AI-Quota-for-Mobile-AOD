package com.aiusage.mobile.providers

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.aiusage.mobile.local.LocalUsageRepository
import com.aiusage.mobile.local.ProviderId
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

        val source = payloadSource(intent?.getStringExtra(EXTRA_SOURCE))
        val rawPayload = intent?.getStringExtra(EXTRA_RAW_PAYLOAD).orEmpty()
        val snapshot = if (rawPayload.isNotBlank()) {
            ProviderUsageNormalizer.normalize(providerId, rawPayload, source)
        } else {
            null
        }

        if (snapshot != null) {
            repository.saveSnapshot(snapshot)
            Log.i(
                "AIUsageCollector",
                "provider=${providerId.storageId} source=${source.label} lineCount=${snapshot.lines.size} planPresent=${snapshot.planLabel != null} accountPresent=${snapshot.account != null} keptPreviousSnapshot=false"
            )
        } else {
            if (providerId == ProviderId.CURSOR) {
                Log.w("AIUsageCollector", "provider=cursor payloadSummary=${cursorPayloadSummary(rawPayload)}")
                repository.failKeepingPrevious(providerId, "Collector ran. No trusted Cursor usage payload found yet.")
            } else if (source == ProviderPayloadSource.STRUCTURED_SCRIPT) {
                repository.markConnectedWithoutUsage(providerId, "Signed in. Usage quota is not available yet.")
            } else {
                repository.failKeepingPrevious(providerId, "Collector ran. No trusted provider usage payload found yet.")
            }
            Log.w(
                "AIUsageCollector",
                "provider=${providerId.storageId} source=${source.label} lineCount=0 errorKind=no_trusted_payload keptPreviousSnapshot=true"
            )
        }
        UsageSurfaceRefresher.refresh(applicationContext, repository)

        stopSelf(startId)
        return START_NOT_STICKY
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
