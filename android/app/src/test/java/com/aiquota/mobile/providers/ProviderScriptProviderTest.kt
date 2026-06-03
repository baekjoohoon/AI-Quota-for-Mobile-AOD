package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProviderScriptProviderTest {
    @Test
    fun webSessionProvidersHaveStoreApkStyleProviderScriptProviders() {
        val providers = mapOf(
            ProviderId.CLAUDE to ClaudeScriptProvider::class.java,
            ProviderId.CODEX to CodexScriptProvider::class.java,
            ProviderId.COPILOT to CopilotScriptProvider::class.java
        )

        providers.forEach { (providerId, providerClass) ->
            val provider = ProviderScriptProviders.providerFor(providerId)
            val stores = ProviderScriptProviders.storeNamesFor(providerId)

            assertEquals(providerClass, provider.javaClass)
            assertEquals("account_data_${providerId.storageId}", stores.accountData)
            assertEquals("usage_data_${providerId.storageId}", stores.usageData)
            assertEquals("script_data_${providerId.storageId}", stores.scriptData)
            assertEquals(providerId, provider.metadata.providerId)
            assertTrue(provider.collectorScript(geminiCollectorAsset = "").contains(providerId.storageId))
        }
    }

    @Test
    fun codexScriptProviderDoesNotDependOnOauthTokenCapture() {
        val script = ProviderScriptProviders.providerFor(ProviderId.CODEX)
            .collectorScript(geminiCollectorAsset = "")
        val common = ProviderWebCollectorScripts.commonForTest(
            providerId = ProviderId.CODEX,
            cookies = emptyMap(),
            observedAccountId = null,
            pageText = ""
        )

        assertFalse(script.contains("auth.openai.com/oauth/token"))
        assertFalse(script.contains("observedAccessToken"))
        assertFalse(common.contains("observedAccessToken"))
    }

    @Test
    fun copilotScriptUsesWebSessionFetchBeforeNativeBridgeFallback() {
        val script = ProviderScriptProviders.providerFor(ProviderId.COPILOT)
            .collectorScript(geminiCollectorAsset = "")

        assertTrue(script.contains("function nativeBridgeJson()"))
        assertTrue(script.contains("if (authorizationHeader) return Promise.resolve(nativeBridgeJson())"))
        assertTrue(
            script.indexOf("return c.fetchJson(url).then") <
                script.indexOf("var nativeResult = nativeBridgeJson();")
        )
    }
    @Test
    fun providerScopedStateRepositoryMirrorsStoreApkStyleStores() {
        val source = File("src/main/java/com/aiquota/mobile/providers/ProviderScopedStateRepository.kt").readText()
        val localUsage = File("src/main/java/com/aiquota/mobile/local/LocalUsageRepository.kt").readText()

        assertTrue(source.contains("ProviderScriptProviders.storeNamesFor(providerId)"))
        assertTrue(source.contains("getSharedPreferences(stores.accountData"))
        assertTrue(source.contains("getSharedPreferences(stores.usageData"))
        assertTrue(source.contains("getSharedPreferences(stores.scriptData"))
        assertTrue(source.contains("ProviderSnapshotCodec.encode(listOf(snapshot))"))
        assertTrue(localUsage.contains("ProviderScopedStateRepository(appContext)"))
        assertTrue(localUsage.contains("scopedStateRepository.saveSnapshots(cleaned)"))
        assertTrue(localUsage.contains("scopedStateRepository.saveSnapshots(ordered)"))
    }

    @Test
    fun antigravityHiddenCollectorUsesStoreStyleUserAgent() {
        assertEquals("antigravity", ProviderWebViewUserAgent.hiddenCollectorUserAgent(ProviderId.ANTIGRAVITY))
        assertFalse(ProviderWebViewUserAgent.loginUserAgent().equals("antigravity"))
    }
}
