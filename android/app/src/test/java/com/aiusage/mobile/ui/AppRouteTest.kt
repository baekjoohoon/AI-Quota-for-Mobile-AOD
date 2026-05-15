package com.aiusage.mobile.ui

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteTest {
    @Test
    fun providerRouteUsesNamespacedProviderExtra() {
        val route = AppRoute.fromExtras(
            route = AppRoute.ROUTE_PROVIDER,
            providerIdStorageId = "cursor"
        )

        assertEquals(AppRoute.ProviderDetail(ProviderId.CURSOR), route)
    }

    @Test
    fun providerRouteUsesLegacyProviderExtraForWidgetCompatibility() {
        val route = AppRoute.fromExtras(
            route = null,
            providerIdStorageId = null,
            legacyProviderIdStorageId = "claude"
        )

        assertEquals(AppRoute.ProviderDetail(ProviderId.CLAUDE), route)
    }

    @Test
    fun invalidProviderExtraFallsBackToHome() {
        val route = AppRoute.fromExtras(
            route = AppRoute.ROUTE_PROVIDER,
            providerIdStorageId = "not-a-provider"
        )

        assertEquals(AppRoute.Home, route)
    }
}
