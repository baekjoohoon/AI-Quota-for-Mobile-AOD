package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId

class ProviderConnectorRegistry private constructor(
    val connectors: List<ProviderConnector>
) {
    fun connectorFor(providerId: ProviderId): ProviderConnector {
        return connectors.first { it.providerId == providerId }
    }

    companion object {
        fun default(): ProviderConnectorRegistry {
            return ProviderConnectorRegistry(
                ProviderId.defaultOrder().map { providerId ->
                    WebTextProviderConnector(
                        providerId = providerId,
                        startUrl = startUrlFor(providerId)
                    )
                }
            )
        }

        private fun startUrlFor(providerId: ProviderId): String {
            return when (providerId) {
                ProviderId.CLAUDE -> "https://claude.ai/"
                ProviderId.CODEX -> "https://chatgpt.com/"
                ProviderId.GEMINI -> "https://gemini.google.com/app"
                ProviderId.COPILOT -> "https://github.com/settings/copilot"
                ProviderId.CURSOR -> "https://cursor.com/dashboard"
            }
        }
    }
}
