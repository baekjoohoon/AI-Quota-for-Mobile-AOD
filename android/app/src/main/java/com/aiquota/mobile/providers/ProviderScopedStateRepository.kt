package com.aiquota.mobile.providers

import android.content.Context
import android.content.SharedPreferences
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageSnapshot

class ProviderScopedStateRepository(context: Context) {
    private val appContext = context.applicationContext

    fun saveSnapshots(snapshots: List<ProviderUsageSnapshot>) {
        snapshots.forEach(::saveSnapshot)
    }

    fun saveSnapshot(snapshot: ProviderUsageSnapshot) {
        val providerId = snapshot.providerId
        val stores = ProviderScriptProviders.storeNamesFor(providerId)
        val metadata = ProviderScriptProviders.metadataFor(providerId)

        val usageDataEditor = appContext.getSharedPreferences(stores.usageData, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER_ID, providerId.storageId)
            .putString(KEY_SNAPSHOT, ProviderSnapshotCodec.encode(listOf(snapshot)))
            .putString(KEY_UPDATED_AT, snapshot.updatedAt)
        if (providerId == ProviderId.GLM) {
            usageDataEditor.commit()
        } else {
            usageDataEditor.apply()
        }

        appContext.getSharedPreferences(stores.accountData, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER_ID, providerId.storageId)
            .putOptionalString(KEY_ACCOUNT, snapshot.account)
            .putOptionalString(KEY_PLAN, snapshot.planLabel)
            .putString(KEY_CONNECTION_STATE, snapshot.connectionState.name)
            .putString(KEY_UPDATED_AT, snapshot.updatedAt)
            .apply()

        appContext.getSharedPreferences(stores.scriptData, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER_ID, providerId.storageId)
            .putString(KEY_SCRIPT_VERSION, metadata.version)
            .putString(KEY_UPDATED_AT, snapshot.updatedAt)
            .apply()
    }

    fun readUsageSnapshot(providerId: ProviderId): ProviderUsageSnapshot? {
        val stores = ProviderScriptProviders.storeNamesFor(providerId)
        val raw = appContext.getSharedPreferences(stores.usageData, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, "")
            .orEmpty()
        return ProviderSnapshotCodec.decode(raw).firstOrNull { it.providerId == providerId }
    }

    fun saveOpenCodeUsageUrl(url: String) {
        val usageUrl = OpenCodeUsagePageRoutes.canonicalGoUsageUrlFrom(url) ?: return
        val stores = ProviderScriptProviders.storeNamesFor(ProviderId.OPENCODE)
        appContext.getSharedPreferences(stores.scriptData, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPENCODE_USAGE_URL, usageUrl)
            .apply()
    }

    fun readOpenCodeUsageUrl(): String? {
        val stores = ProviderScriptProviders.storeNamesFor(ProviderId.OPENCODE)
        val raw = appContext.getSharedPreferences(stores.scriptData, Context.MODE_PRIVATE)
            .getString(KEY_OPENCODE_USAGE_URL, "")
            .orEmpty()
        return OpenCodeUsagePageRoutes.canonicalGoUsageUrlFrom(raw)
    }

    fun clearOpenCodeUsageUrl() {
        val stores = ProviderScriptProviders.storeNamesFor(ProviderId.OPENCODE)
        appContext.getSharedPreferences(stores.scriptData, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_OPENCODE_USAGE_URL)
            .apply()
    }

    private fun SharedPreferences.Editor.putOptionalString(
        key: String,
        value: String?
    ): SharedPreferences.Editor {
        if (value.isNullOrBlank()) return remove(key)
        return putString(key, value)
    }

    private companion object {
        const val KEY_PROVIDER_ID = "provider_id"
        const val KEY_SNAPSHOT = "snapshot"
        const val KEY_ACCOUNT = "account"
        const val KEY_PLAN = "plan"
        const val KEY_CONNECTION_STATE = "connection_state"
        const val KEY_SCRIPT_VERSION = "script_version"
        const val KEY_OPENCODE_USAGE_URL = "opencode_usage_url"
        const val KEY_UPDATED_AT = "updated_at"
    }
}
