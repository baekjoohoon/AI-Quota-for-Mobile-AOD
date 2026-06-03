package com.aiquota.mobile.providers

import android.content.Context
import com.aiquota.mobile.local.ProviderId

class ProviderConnectorRegistry(val connectors: List<ProviderConnector>) {
    fun connectorFor(providerId: ProviderId): ProviderConnector {
        return connectors.first { it.providerId == providerId }
    }

    companion object {
        fun default(context: Context? = null): ProviderConnectorRegistry {
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
