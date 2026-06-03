package com.aiquota.mobile.local

import java.time.Instant

enum class ProviderUsageFreshness {
    NO_DATA,
    FRESH,
    COLLECTING,
    STALE,
    AUTH_REQUIRED,
    ERROR
}

fun ProviderUsageSnapshot.usageFreshness(now: Instant = Instant.now()): ProviderUsageFreshness {
    if (refreshState == ProviderRefreshState.REFRESHING || connectionState == ProviderConnectionState.COLLECTING) {
        return ProviderUsageFreshness.COLLECTING
    }
    if (lines.isEmpty()) {
        return when (connectionState) {
            ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> ProviderUsageFreshness.AUTH_REQUIRED
            ProviderConnectionState.ERROR -> ProviderUsageFreshness.ERROR
            else -> ProviderUsageFreshness.NO_DATA
        }
    }
    return when (connectionState) {
        ProviderConnectionState.CONNECTED -> ProviderUsageFreshness.FRESH
        ProviderConnectionState.STALE -> ProviderUsageFreshness.FRESH
        ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED -> ProviderUsageFreshness.AUTH_REQUIRED
        ProviderConnectionState.ERROR,
        ProviderConnectionState.UNAVAILABLE -> ProviderUsageFreshness.ERROR
        else -> ProviderUsageFreshness.FRESH
    }
}

fun ProviderUsageSnapshot.hasLastKnownUsage(now: Instant = Instant.now()): Boolean {
    return lines.isNotEmpty() && usageFreshness(now) in setOf(
        ProviderUsageFreshness.STALE,
        ProviderUsageFreshness.AUTH_REQUIRED,
        ProviderUsageFreshness.ERROR
    )
}
