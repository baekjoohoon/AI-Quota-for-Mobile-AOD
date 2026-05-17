package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUsageProbeTargetsTest {
    @Test
    fun codexChecksOnlyChatGptSurfaces() {
        val urls = ProviderUsageProbeTargets.urls(ProviderId.CODEX)

        assertTrue(urls.contains("https://chatgpt.com/"))
        assertTrue(urls.all { url -> url.contains("chatgpt.com") })
        assertFalse(urls.any { url -> url.contains("platform.openai.com") })
        assertTrue(urls.all { url -> ProviderHostAllowlist.isAllowed(ProviderId.CODEX, url) })
    }

    @Test
    fun allProviderProbeUrlsStayInsideAllowlist() {
        ProviderId.defaultOrder().forEach { providerId ->
            assertTrue(ProviderUsageProbeTargets.urls(providerId).isNotEmpty())
            assertTrue(
                ProviderUsageProbeTargets.urls(providerId).all { url ->
                    ProviderHostAllowlist.isAllowed(providerId, url)
                }
            )
        }
    }

    @Test
    fun copilotChecksBillingAndFeatureUsageSurfaces() {
        val urls = ProviderUsageProbeTargets.urls(ProviderId.COPILOT)

        assertTrue(urls.contains("https://github.com/settings/copilot"))
        assertTrue(urls.contains("https://github.com/settings/copilot/features"))
        assertTrue(urls.contains("https://github.com/settings/copilot/usage"))
        assertTrue(urls.contains("https://github.com/settings/copilot/plans"))
        assertTrue(urls.contains("https://github.com/settings/billing"))
        assertTrue(urls.contains("https://github.com/settings/billing/usage"))
        assertTrue(urls.contains("https://github.com/github-copilot/chat"))
    }

    @Test
    fun cursorChecksDashboardAndAccountApiSurfaces() {
        val urls = ProviderUsageProbeTargets.urls(ProviderId.CURSOR)

        assertTrue(urls.contains("https://cursor.com/dashboard/usage"))
        assertTrue(urls.contains("https://cursor.com/api/usage"))
        assertTrue(urls.contains("https://cursor.com/api/usage-summary"))
        assertTrue(urls.contains("https://cursor.com/api/subscription"))
        assertTrue(urls.contains("https://cursor.com/api/me"))
        assertTrue(urls.contains("https://cursor.com/api/dashboard/get-plan-info"))
    }
}
