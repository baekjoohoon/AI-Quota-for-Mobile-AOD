package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderAuthStrategyTest {
    @Test
    fun providerStrategiesMatchFreshnessSpec() {
        assertEquals(
            ProviderAuthStoreKind.WEBVIEW_PROFILE,
            ProviderDefinitionRegistry.definitionFor(ProviderId.CLAUDE).authStoreKind
        )
        assertEquals(
            ProviderAuthStoreKind.WEBVIEW_PROFILE,
            ProviderDefinitionRegistry.definitionFor(ProviderId.CODEX).authStoreKind
        )
        assertEquals(
            ProviderAuthStoreKind.WEBVIEW_PROFILE,
            ProviderDefinitionRegistry.definitionFor(ProviderId.COPILOT).authStoreKind
        )
        assertEquals(
            ProviderAuthStoreKind.NATIVE_TOKEN,
            ProviderDefinitionRegistry.definitionFor(ProviderId.GEMINI).authStoreKind
        )
        assertEquals(
            ProviderAuthStoreKind.NATIVE_TOKEN,
            ProviderDefinitionRegistry.definitionFor(ProviderId.ANTIGRAVITY).authStoreKind
        )
        assertEquals(
            ProviderAuthStoreKind.TOKEN_OR_WEBVIEW_FALLBACK,
            ProviderDefinitionRegistry.definitionFor(ProviderId.CURSOR).authStoreKind
        )
    }
}
