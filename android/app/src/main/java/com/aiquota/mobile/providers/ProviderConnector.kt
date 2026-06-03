package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageSnapshot

enum class ProviderConnectorState {
    DISCONNECTED,
    CONNECTED
}

interface ProviderConnector {
    val providerId: ProviderId
    val startUrl: String
    suspend fun connectionState(): ProviderConnectorState = ProviderConnectorState.DISCONNECTED
    suspend fun refreshUsage(rawPayload: String = ""): ProviderUsageSnapshot {
        return ProviderUsageSnapshot.disconnected(providerId).copy(
            connectionState = ProviderConnectionState.ERROR,
            message = "Open provider login before refresh."
        )
    }
    suspend fun disconnect() = Unit
}

data class DefinitionProviderConnector(
    override val providerId: ProviderId,
    override val startUrl: String
) : ProviderConnector
