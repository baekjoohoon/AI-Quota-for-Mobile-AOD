package com.aiusage.mobile.ui

import com.aiusage.mobile.local.ProviderId

sealed class AppRoute {
    object Home : AppRoute()
    data class ProviderDetail(val providerId: ProviderId) : AppRoute()
    object Settings : AppRoute()

    companion object {
        const val EXTRA_ROUTE = "com.aiusage.mobile.extra.ROUTE"
        const val EXTRA_PROVIDER_ID = "com.aiusage.mobile.extra.PROVIDER_ID"
        const val EXTRA_PROVIDER_ID_LEGACY = "provider_id"
        const val ROUTE_HOME = "home"
        const val ROUTE_PROVIDER = "provider"
        const val ROUTE_SETTINGS = "settings"

        fun fromExtras(
            route: String?,
            providerIdStorageId: String?,
            legacyProviderIdStorageId: String? = null
        ): AppRoute {
            val requestedProviderId = providerIdStorageId ?: legacyProviderIdStorageId
            return when (route) {
                ROUTE_PROVIDER -> providerDetail(requestedProviderId)
                ROUTE_SETTINGS -> Settings
                ROUTE_HOME -> Home
                else -> if (requestedProviderId.isNullOrBlank()) Home else providerDetail(requestedProviderId)
            }
        }

        fun providerDetail(providerIdStorageId: String?): AppRoute {
            return ProviderId.fromStorageId(providerIdStorageId.orEmpty())?.let(::ProviderDetail) ?: Home
        }
    }
}
