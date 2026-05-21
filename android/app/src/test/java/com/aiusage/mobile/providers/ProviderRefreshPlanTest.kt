package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderUsageLine
import com.aiusage.mobile.local.ProviderUsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRefreshPlanTest {
    @Test
    fun manualRefreshUsesHiddenCollectorUrlsForWebSessionProviders() {
        val jobs = listOf(
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.COPILOT,
            ProviderId.CURSOR
        ).map(ProviderRefreshPlan::manualJobFor)

        assertTrue(jobs.all { it.mode == ProviderRefreshMode.HIDDEN_WEB_COLLECTOR })
        assertFalse(jobs.any { it.startUrl.contains("/auth/login") || it.startUrl.contains("/login") })
        assertEquals("https://claude.ai/", jobs.first { it.providerId == ProviderId.CLAUDE }.startUrl)
        assertEquals("https://chatgpt.com/", jobs.first { it.providerId == ProviderId.CODEX }.startUrl)
        assertEquals("https://github.com/settings/copilot", jobs.first { it.providerId == ProviderId.COPILOT }.startUrl)
    }

    @Test
    fun tokenBackedProvidersRefreshThroughNativeApiWithoutWebCollector() {
        val jobs = listOf(ProviderId.GEMINI).map(ProviderRefreshPlan::manualJobFor)

        assertTrue(jobs.all { it.mode == ProviderRefreshMode.NATIVE_API })
        assertTrue(jobs.all { it.startUrl.isBlank() })
    }

    @Test
    fun automaticRefreshRunsEveryMinuteForConnectedProviders() {
        val snapshots = listOf(
            connected(ProviderId.CLAUDE),
            ProviderUsageSnapshot.disconnected(ProviderId.CODEX),
            connected(ProviderId.GEMINI)
        )

        assertEquals(60_000L, ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS)
        assertEquals(
            listOf(ProviderId.CLAUDE, ProviderId.GEMINI),
            ProviderRefreshPlan.automaticJobsFor(snapshots).map { it.providerId }
        )
    }

    private fun connected(providerId: ProviderId): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = ProviderConnectionState.CONNECTED,
            lines = listOf(ProviderUsageLine(label = "Usage", remainingPercent = 0.5f))
        )
    }
}
