package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId

class ProviderConnectorRegistry(val connectors: List<ProviderConnector>) {
    fun connectorFor(providerId: ProviderId): ProviderConnector {
        return connectors.first { it.providerId == providerId }
    }

    companion object {
        fun default(): ProviderConnectorRegistry {
            return ProviderConnectorRegistry(
                ProviderDefinitionRegistry.all().map { definition ->
                    DefinitionProviderConnector(
                        providerId = definition.providerId,
                        startUrl = definition.loginStartUrl
                    )
                }
            )
        }
    }
}
