package com.aiusage.mobile.sync

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aiusage.mobile.widget.AIUsageGlanceWidget
import com.aiusage.mobile.widget.AIUsageLargeGlanceWidget
import com.aiusage.mobile.widget.AIUsageCircularWidgetProvider
import com.aiusage.mobile.widget.WidgetSnapshotCache
import com.aiusage.mobile.notification.UsageLimitNotificationController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

class SnapshotRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("ai_usage_sync", Context.MODE_PRIVATE)
    private val cache = WidgetSnapshotCache(context)
    private val firestore = FirebaseFirestore.getInstance()

    fun enqueueRefresh(uid: String) {
        val input = Data.Builder().putString("uid", uid).build()
        WorkManager.getInstance(context)
            .enqueue(
                OneTimeWorkRequestBuilder<SnapshotSyncWorker>()
                    .setInputData(input)
                    .build()
            )
    }

    fun rememberSignedInUser(uid: String) {
        preferences.edit().putString("uid", uid).apply()
    }

    fun clearSignedInUser() {
        preferences.edit().remove("uid").apply()
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_REFRESH_WORK)
        UsageLimitNotificationController.cancel(context)
    }

    fun storedUid(): String? {
        return preferences.getString("uid", null)
    }

    fun scheduleWidgetRefresh(uid: String) {
        val input = Data.Builder().putString("uid", uid).build()
        val request = OneTimeWorkRequestBuilder<SnapshotSyncWorker>()
            .setInputData(input)
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WIDGET_REFRESH_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun listDevices(uid: String): List<SnapshotDevice> {
        val deviceDocuments = firestore.collection("users")
            .document(uid)
            .collection("devices")
            .get()
            .await()
            .documents

        val devices = deviceDocuments.map { deviceDocument ->
            val snapshotDocument = deviceDocument.reference
                .collection("snapshots")
                .document("latest")
                .get()
                .await()
            val parsedSnapshot = parseSnapshot(deviceDocument, snapshotDocument)
            SnapshotDevice(
                deviceId = deviceDocument.id,
                deviceName = parsedSnapshot.deviceName ?: "Unnamed device",
                status = parsedSnapshot.status,
                lastSeenAt = deviceDocument.getString("lastSeenAt"),
                fetchedAt = parsedSnapshot.fetchedAt,
                summary = parsedSnapshot.message
            )
        }
        return deduplicateDevicesByName(devices)
    }

    suspend fun refreshLatestSnapshot(uid: String, preferredDeviceId: String? = null): SnapshotRefreshResult {
        val devices = firestore.collection("users")
            .document(uid)
            .collection("devices")
            .get()
            .await()
            .documents

        if (devices.isEmpty()) {
            val result = SnapshotRefreshResult(
                deviceId = null,
                deviceName = null,
                status = SnapshotStatus.NotLinked,
                ageSeconds = null,
                fetchedAt = null,
                updatedAt = null,
                message = "No PC linked",
                providers = emptyList(),
                rawSnapshotJson = ""
            )
            saveForWidget(result.rawSnapshotJson, result.status.name, "", "")
            return result
        }

        val selectedDevice = selectDevice(devices, preferredDeviceId)
        val snapshotDocument = selectedDevice.reference
            .collection("snapshots")
            .document("latest")
            .get()
            .await()

        val result = parseSnapshot(selectedDevice, snapshotDocument)
        saveForWidget(
            snapshotJson = result.rawSnapshotJson,
            status = result.status.name,
            deviceName = result.deviceName.orEmpty(),
            updatedAt = result.updatedAt.orEmpty()
        )
        return result
    }

    suspend fun updateDeviceName(uid: String, deviceId: String, deviceName: String) {
        val normalized = deviceName.trim()
        require(normalized.isNotEmpty()) { "Device name is required" }
        firestore.collection("users")
            .document(uid)
            .collection("devices")
            .document(deviceId)
            .update("name", normalized)
            .await()
    }

    suspend fun deleteDevice(uid: String, deviceId: String) {
        val deviceReference = firestore.collection("users")
            .document(uid)
            .collection("devices")
            .document(deviceId)
        deviceReference
            .collection("snapshots")
            .document("latest")
            .delete()
            .await()
        deviceReference
            .delete()
            .await()
    }

    suspend fun saveForWidget(
        snapshotJson: String,
        status: String = "NotLinked",
        deviceName: String = "",
        updatedAt: String = ""
    ) {
        // Stores display-only snapshot cache for app and widget rendering.
        cache.write(snapshotJson, status, deviceName, updatedAt)
        UsageLimitNotificationController.update(context, snapshotJson)
        AIUsageGlanceWidget().updateAll(context)
        AIUsageLargeGlanceWidget().updateAll(context)
        AIUsageCircularWidgetProvider.updateAll(context)
    }

    fun latestCachedSnapshot(): String {
        return cache.read()
    }

    private fun selectDevice(
        devices: List<DocumentSnapshot>,
        preferredDeviceId: String?
    ): DocumentSnapshot {
        val preferred = preferredDeviceId?.let { id -> devices.firstOrNull { it.id == id } }
        if (preferred != null) return preferred
        return devices.sortedByDescending { timestampMillis(it.get("lastSeenAt")) }
            .firstOrNull { document ->
                document.getBoolean("syncEnabled") != false && document.get("revokedAt") == null
            }
            ?: devices.first()
    }

    private fun parseSnapshot(
        deviceDocument: DocumentSnapshot,
        snapshotDocument: DocumentSnapshot
    ): SnapshotRefreshResult {
        val deviceId = deviceDocument.id
        val deviceName = deviceDocument.getString("name")
        val revoked = deviceDocument.get("revokedAt") != null || deviceDocument.getBoolean("syncEnabled") == false

        if (revoked) {
            return SnapshotRefreshResult(
                deviceId = deviceId,
                deviceName = deviceName,
                status = SnapshotStatus.Revoked,
                ageSeconds = null,
                fetchedAt = null,
                updatedAt = null,
                message = "Linked PC was revoked",
                providers = emptyList(),
                rawSnapshotJson = ""
            )
        }

        if (!snapshotDocument.exists()) {
            return SnapshotRefreshResult(
                deviceId = deviceId,
                deviceName = deviceName,
                status = SnapshotStatus.NotLinked,
                ageSeconds = null,
                fetchedAt = null,
                updatedAt = null,
                message = "No uploaded snapshot yet",
                providers = emptyList(),
                rawSnapshotJson = ""
            )
        }

        val snapshotMap = snapshotDocument.data ?: emptyMap()
        val snapshotJson = JSONObject(snapshotMap).toString()
        val providers = parseProviders(snapshotDocument)
        val fetchedAt = snapshotDocument.getString("fetchedAt")
        val updatedAt = snapshotDocument.getString("uploadedAt") ?: fetchedAt
        val ageSeconds = fetchedAt?.let(::ageSeconds)
        val hasProviderError = providers.any { it.status.equals("error", ignoreCase = true) }
        val status = resolveSnapshotStatus(ageSeconds, hasProviderError)
        return SnapshotRefreshResult(
            deviceId = deviceId,
            deviceName = deviceName,
            status = status,
            ageSeconds = ageSeconds,
            fetchedAt = fetchedAt,
            updatedAt = updatedAt,
            message = statusMessage(status),
            providers = providers,
            rawSnapshotJson = snapshotJson
        )
    }

    private fun parseProviders(snapshot: DocumentSnapshot): List<SnapshotProviderUsage> {
        val providers = (snapshot.get("providers") as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>()
            ?: return emptyList()
        return providers.mapNotNull { provider ->
            val providerId = provider["providerId"]?.toString().orEmpty()
            val providerName = provider["displayName"]?.toString()
                ?: providerId.ifBlank { null }
                ?: "Provider"
            val status = provider["status"]?.toString().orEmpty()
            if (!isVisibleProvider(provider, status)) return@mapNotNull null
            val lines = (provider["lines"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                ?.mapNotNull(::parseUsageLine)
                .orEmpty()
            if (lines.isEmpty() && status != "error") return@mapNotNull null
            SnapshotProviderUsage(
                providerId = providerId,
                providerName = providerName,
                plan = provider["plan"]?.toString(),
                status = if (status == "error" && provider["errorCode"] != null) {
                    provider["errorCode"].toString()
                } else status.ifBlank { "unknown" },
                lines = lines
            )
        }
    }

    private fun isVisibleProvider(provider: Map<String, Any?>, status: String): Boolean {
        if (status.equals("disabled", ignoreCase = true)) return false
        if (provider["enabled"] == false) return false
        if (provider["active"] == false) return false
        if (provider["visible"] == false) return false
        return true
    }

    private fun parseUsageLine(line: Map<String, Any?>): SnapshotUsageLimitLine? {
        val remaining = numeric(line["remaining"])
        val used = numeric(line["used"])
        val limit = numeric(line["limit"])
        val remainingValue = remaining ?: if (used != null && limit != null) {
            (limit - used).coerceAtLeast(0.0)
        } else {
            null
        }
        val ratio = if (remainingValue != null && limit != null && limit > 0.0) {
            (remainingValue / limit).coerceIn(0.0, 1.0)
        } else {
            null
        }
        val hasVisibleMetadata = !line["label"]?.toString().isNullOrBlank() || !line["resetsAt"]?.toString().isNullOrBlank()
        val isInactivePlaceholder = used != null && used == 0.0 && ratio == 1.0 && !hasVisibleMetadata
        if (isInactivePlaceholder) return null
        if (remainingValue == null && ratio == null) return null
        return SnapshotUsageLimitLine(
            label = line["label"]?.toString()?.ifBlank { null } ?: "Limit",
            remainingText = remainingText(remainingValue, ratio),
            resetText = line["resetsAt"]?.toString()?.let(::resetText),
            remainingRatio = ratio?.toFloat()
        )
    }

    private fun remainingText(remainingValue: Double?, ratio: Double?): String {
        if (ratio != null) {
            return "${formatNumber(ratio * 100)}% left"
        }
        return "${formatNumber(remainingValue)} left"
    }

    private fun resetText(value: String): String? {
        return runCatching {
            val seconds = (Instant.parse(value).epochSecond - Instant.now().epochSecond).coerceAtLeast(0L)
            val days = seconds / (24 * 60 * 60)
            val hours = (seconds % (24 * 60 * 60)) / (60 * 60)
            val minutes = (seconds % (60 * 60)) / 60
            when {
                days > 0 -> "Resets in ${days}d ${hours}h"
                hours > 0 -> "Resets in ${hours}h ${minutes}m"
                else -> "Resets in ${minutes}m"
            }
        }.getOrNull()
    }

    private fun numeric(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun formatNumber(value: Any?): String {
        val number = (value as? Number)?.toDouble() ?: return value?.toString() ?: "-"
        return DECIMAL_FORMAT.format(number)
    }

    private fun timestampMillis(value: Any?): Long {
        return when (value) {
            is Timestamp -> value.toDate().time
            is String -> runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
            else -> 0L
        }
    }

    private fun ageSeconds(value: String): Long {
        return runCatching {
            val fetchedAt = Instant.parse(value)
            (Instant.now().epochSecond - fetchedAt.epochSecond).coerceAtLeast(0L)
        }.getOrDefault(0L)
    }

    private fun statusMessage(status: SnapshotStatus): String {
        return when (status) {
            SnapshotStatus.NotLinked -> "No PC linked"
            SnapshotStatus.Fresh -> "Snapshot is fresh"
            SnapshotStatus.Stale -> "Snapshot is getting old"
            SnapshotStatus.Offline -> "PC looks offline"
            SnapshotStatus.ProviderError -> "One or more providers need attention"
            SnapshotStatus.Revoked -> "Linked PC was revoked"
        }
    }

    private companion object {
        const val WIDGET_REFRESH_WORK = "ai_usage_widget_refresh"
        val DECIMAL_FORMAT = DecimalFormat("0.#", DecimalFormatSymbols(Locale.US)).apply {
            roundingMode = RoundingMode.HALF_UP
        }
    }
}
