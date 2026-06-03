package com.aiquota.mobile.local

import android.content.Context
import android.content.SharedPreferences

class ProviderPreferencesRepository(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun providerOrder(): List<ProviderId> {
        return ProviderPreferencesCodec.decodeOrder(preferences.getString(KEY_PROVIDER_ORDER, "").orEmpty())
    }

    fun hiddenProviders(): Set<ProviderId> {
        return ProviderPreferencesCodec.decodeHidden(preferences.getString(KEY_HIDDEN_PROVIDERS, "").orEmpty())
    }

    fun visibleProviders(): List<ProviderId> {
        return ProviderPreferencesCodec.visibleProviders(providerOrder(), hiddenProviders())
    }

    fun saveProviderOrder(order: List<ProviderId>) {
        preferences.edit()
            .putString(KEY_PROVIDER_ORDER, ProviderPreferencesCodec.encodeOrder(order))
            .apply()
    }

    fun moveProvider(providerId: ProviderId, targetIndex: Int) {
        saveProviderOrder(ProviderPreferencesCodec.moveProvider(providerOrder(), providerId, targetIndex))
    }

    fun setProviderHidden(providerId: ProviderId, hiddenState: Boolean) {
        val nextHidden = ProviderPreferencesCodec.setHidden(hiddenProviders(), providerId, hiddenState)
        preferences.edit()
            .putString(KEY_HIDDEN_PROVIDERS, ProviderPreferencesCodec.encodeHidden(nextHidden))
            .apply()
    }

    fun dashboardWidgetProviderOrder(appWidgetId: Int): List<ProviderId> {
        val key = "$KEY_DASHBOARD_WIDGET_PROVIDER_ORDER_PREFIX$appWidgetId"
        val value = preferences.getString(key, null)
        return if (value == null) {
            providerOrder()
        } else {
            ProviderPreferencesCodec.decodeOrder(value)
        }
    }

    fun dashboardWidgetHiddenProviders(appWidgetId: Int): Set<ProviderId> {
        val key = "$KEY_DASHBOARD_WIDGET_HIDDEN_PROVIDERS_PREFIX$appWidgetId"
        val value = preferences.getString(key, null)
        return if (value == null) {
            hiddenProviders()
        } else {
            ProviderPreferencesCodec.decodeHidden(value)
        }
    }

    fun saveDashboardWidgetProviderOrder(appWidgetId: Int, order: List<ProviderId>) {
        val key = "$KEY_DASHBOARD_WIDGET_PROVIDER_ORDER_PREFIX$appWidgetId"
        preferences.edit()
            .putString(key, ProviderPreferencesCodec.encodeOrder(order))
            .apply()
    }

    fun saveDashboardWidgetHiddenProviders(appWidgetId: Int, hidden: Set<ProviderId>) {
        val key = "$KEY_DASHBOARD_WIDGET_HIDDEN_PROVIDERS_PREFIX$appWidgetId"
        preferences.edit()
            .putString(key, ProviderPreferencesCodec.encodeHidden(hidden))
            .apply()
    }

    fun setDashboardWidgetProviderHidden(appWidgetId: Int, providerId: ProviderId, hiddenState: Boolean) {
        val nextHidden = ProviderPreferencesCodec.setHidden(
            dashboardWidgetHiddenProviders(appWidgetId),
            providerId,
            hiddenState
        )
        saveDashboardWidgetHiddenProviders(appWidgetId, nextHidden)
    }

    fun clearDashboardWidgetConfiguration(appWidgetId: Int) {
        preferences.edit()
            .remove("$KEY_DASHBOARD_WIDGET_PROVIDER_ORDER_PREFIX$appWidgetId")
            .remove("$KEY_DASHBOARD_WIDGET_HIDDEN_PROVIDERS_PREFIX$appWidgetId")
            .apply()
    }

    fun providerWidgetSelection(appWidgetId: Int): ProviderId? {
        val key = "$KEY_PROVIDER_WIDGET_SELECTION_PREFIX$appWidgetId"
        return ProviderId.fromStorageId(preferences.getString(key, "").orEmpty())
    }

    fun saveProviderWidgetSelection(appWidgetId: Int, providerId: ProviderId) {
        val key = "$KEY_PROVIDER_WIDGET_SELECTION_PREFIX$appWidgetId"
        preferences.edit()
            .putString(key, providerId.storageId)
            .apply()
    }

    fun clearProviderWidgetSelection(appWidgetId: Int) {
        val key = "$KEY_PROVIDER_WIDGET_SELECTION_PREFIX$appWidgetId"
        preferences.edit()
            .remove(key)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "ai_quota_provider_preferences"
        private const val KEY_PROVIDER_ORDER = "provider_order"
        private const val KEY_HIDDEN_PROVIDERS = "hidden_providers"
        private const val KEY_PROVIDER_WIDGET_SELECTION_PREFIX = "provider_widget_selection_"
        private const val KEY_DASHBOARD_WIDGET_PROVIDER_ORDER_PREFIX = "dashboard_widget_provider_order_"
        private const val KEY_DASHBOARD_WIDGET_HIDDEN_PROVIDERS_PREFIX = "dashboard_widget_hidden_providers_"
    }
}
