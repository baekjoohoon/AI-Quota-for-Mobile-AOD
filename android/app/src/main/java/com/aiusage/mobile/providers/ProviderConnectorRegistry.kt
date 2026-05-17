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
                ProviderId.GEMINI -> GEMINI_GOOGLE_ACCOUNT_CHOOSER_URL
                ProviderId.COPILOT -> "https://github.com/settings/copilot"
                ProviderId.CURSOR -> "https://cursor.com/dashboard"
            }
        }

        private const val GEMINI_GOOGLE_ACCOUNT_CHOOSER_URL =
            "https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fgemini.google.com%2Fapp&hl=ko"
    }
}
