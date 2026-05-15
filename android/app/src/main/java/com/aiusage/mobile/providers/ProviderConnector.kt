package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderUsageSnapshot

interface ProviderConnector {
    val providerId: ProviderId
    val startUrl: String

    suspend fun connectionState(): ProviderConnectorState

    suspend fun refreshUsage(visibleText: String): ProviderUsageSnapshot

    suspend fun disconnect()
}

enum class ProviderConnectorState {
    DISCONNECTED,
    CONNECTED,
    UNAVAILABLE
}

data class ProviderConnectorResult(
    val providerId: ProviderId,
    val startUrl: String,
    val snapshot: ProviderUsageSnapshot
) {
    val usageSnapshot: ProviderUsageSnapshot
        get() = snapshot
}

class WebTextProviderConnector(
    override val providerId: ProviderId,
    override val startUrl: String
) : ProviderConnector {
    override suspend fun connectionState(): ProviderConnectorState {
        return ProviderConnectorState.DISCONNECTED
    }

    override suspend fun refreshUsage(visibleText: String): ProviderUsageSnapshot {
        return TextUsageExtractor.extract(providerId, visibleText)
    }

    override suspend fun disconnect() = Unit

    fun extract(visibleText: String): ProviderConnectorResult {
        return ProviderConnectorResult(
            providerId = providerId,
            startUrl = startUrl,
            snapshot = TextUsageExtractor.extract(providerId, visibleText)
        )
    }

    fun extractUsage(visibleText: String): ProviderConnectorResult {
        return extract(visibleText)
    }
}
