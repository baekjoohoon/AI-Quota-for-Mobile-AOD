package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId

data class ProviderStoreNames(
    val accountData: String,
    val usageData: String,
    val scriptData: String
)

data class ProviderScriptMetadata(
    val providerId: ProviderId,
    val version: String,
    val stores: ProviderStoreNames
)

data class ProviderCollectorAssets(
    val geminiCollectorAsset: String = "",
    val antigravityCollectorAsset: String = ""
)

interface ProviderScriptProvider {
    val metadata: ProviderScriptMetadata
    fun collectorScript(collectorAssets: ProviderCollectorAssets): String

    fun collectorScript(geminiCollectorAsset: String): String {
        return collectorScript(ProviderCollectorAssets(geminiCollectorAsset = geminiCollectorAsset))
    }
}

class ClaudeScriptProvider : ProviderScriptProvider {
    override val metadata: ProviderScriptMetadata = ProviderScriptProviders.metadataFor(ProviderId.CLAUDE)
    override fun collectorScript(collectorAssets: ProviderCollectorAssets): String = ProviderWebCollectorScripts.claude()
}

class CodexScriptProvider : ProviderScriptProvider {
    override val metadata: ProviderScriptMetadata = ProviderScriptProviders.metadataFor(ProviderId.CODEX)
    override fun collectorScript(collectorAssets: ProviderCollectorAssets): String = ProviderWebCollectorScripts.codexWebViewState()
}

class CopilotScriptProvider : ProviderScriptProvider {
    override val metadata: ProviderScriptMetadata = ProviderScriptProviders.metadataFor(ProviderId.COPILOT)
    override fun collectorScript(collectorAssets: ProviderCollectorAssets): String = ProviderWebCollectorScripts.copilot()
}

class GeminiScriptProvider : ProviderScriptProvider {
    override val metadata: ProviderScriptMetadata = ProviderScriptProviders.metadataFor(ProviderId.GEMINI)
    override fun collectorScript(collectorAssets: ProviderCollectorAssets): String {
        return ProviderWebCollectorScripts.gemini(collectorAssets.geminiCollectorAsset)
    }
}

class AntigravityScriptProvider : ProviderScriptProvider {
    override val metadata: ProviderScriptMetadata = ProviderScriptProviders.metadataFor(ProviderId.ANTIGRAVITY)
    override fun collectorScript(collectorAssets: ProviderCollectorAssets): String {
        val asset = collectorAssets.antigravityCollectorAsset.takeIf { it.isNotBlank() }.orEmpty()
        return if (asset.isBlank()) {
            ProviderWebCollectorScripts.antigravity()
        } else {
            asset + "\n" + ProviderWebCollectorScripts.antigravity()
        }
    }
}

class CursorScriptProvider : ProviderScriptProvider {
    override val metadata: ProviderScriptMetadata = ProviderScriptProviders.metadataFor(ProviderId.CURSOR)
    override fun collectorScript(collectorAssets: ProviderCollectorAssets): String = ProviderWebCollectorScripts.cursor()
}

object ProviderScriptProviders {
    fun providerFor(providerId: ProviderId): ProviderScriptProvider {
        return when (providerId) {
            ProviderId.CLAUDE -> ClaudeScriptProvider()
            ProviderId.CODEX -> CodexScriptProvider()
            ProviderId.COPILOT -> CopilotScriptProvider()
            ProviderId.GEMINI -> GeminiScriptProvider()
            ProviderId.ANTIGRAVITY -> AntigravityScriptProvider()
            ProviderId.CURSOR -> CursorScriptProvider()
        }
    }

    fun metadataFor(providerId: ProviderId): ProviderScriptMetadata {
        return ProviderScriptMetadata(
            providerId = providerId,
            version = scriptVersionFor(providerId),
            stores = storeNamesFor(providerId)
        )
    }

    fun storeNamesFor(providerId: ProviderId): ProviderStoreNames {
        return ProviderStoreNames(
            accountData = "account_data_${providerId.storageId}",
            usageData = "usage_data_${providerId.storageId}",
            scriptData = "script_data_${providerId.storageId}"
        )
    }

    private fun scriptVersionFor(providerId: ProviderId): String {
        return when (providerId) {
            ProviderId.CLAUDE -> "a260503"
            ProviderId.COPILOT -> "b260503"
            ProviderId.CODEX -> "e260503"
            ProviderId.GEMINI -> "d260503"
            ProviderId.ANTIGRAVITY -> "c260503"
            ProviderId.CURSOR -> "cursor-local"
        }
    }
}
