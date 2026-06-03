package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderRefreshQueuePolicyTest {
    @Test
    fun refreshJobsShareOneSequentialQueueAcrossNativeAndWebCollectors() {
        val existing = listOf(job(ProviderId.CLAUDE, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR))
        val incoming = listOf(
            job(ProviderId.GEMINI, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR),
            job(ProviderId.COPILOT, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR),
            job(ProviderId.CURSOR, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR)
        )

        val merged = ProviderRefreshQueuePolicy.merge(
            existing = existing,
            incoming = incoming,
            prioritize = false
        )

        assertEquals(
            listOf(ProviderId.CLAUDE, ProviderId.GEMINI, ProviderId.COPILOT, ProviderId.CURSOR),
            merged.map { it.providerId }
        )
    }

    @Test
    fun prioritizedManualJobMovesProviderToFrontWithoutDuplicatingQueueEntries() {
        val existing = listOf(
            job(ProviderId.CLAUDE, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR),
            job(ProviderId.CURSOR, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR)
        )
        val incoming = listOf(job(ProviderId.CURSOR, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR))

        val merged = ProviderRefreshQueuePolicy.merge(
            existing = existing,
            incoming = incoming,
            prioritize = true
        )

        assertEquals(listOf(ProviderId.CURSOR, ProviderId.CLAUDE), merged.map { it.providerId })
    }

    @Test
    fun automaticQueueKeepsAlreadyWaitingProviderInPlace() {
        val existing = listOf(
            job(ProviderId.CLAUDE, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR),
            job(ProviderId.CURSOR, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR)
        )
        val incoming = listOf(
            job(ProviderId.CURSOR, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR),
            job(ProviderId.GEMINI, ProviderRefreshMode.HIDDEN_WEB_COLLECTOR)
        )

        val merged = ProviderRefreshQueuePolicy.merge(
            existing = existing,
            incoming = incoming,
            prioritize = false
        )

        assertEquals(listOf(ProviderId.CLAUDE, ProviderId.CURSOR, ProviderId.GEMINI), merged.map { it.providerId })
    }

    @Test
    fun resetRefreshQosSupersedesManualOrAutomaticJobsForSameProvider() {
        val existing = listOf(
            ProviderRefreshPlan.manualJobFor(ProviderId.CLAUDE),
            ProviderRefreshPlan.manualJobFor(ProviderId.CODEX)
        )
        val incoming = listOf(ProviderRefreshPlan.resetJobFor(ProviderId.CODEX))

        val merged = ProviderRefreshQueuePolicy.merge(
            existing = existing,
            incoming = incoming,
            prioritize = false
        )

        assertEquals(listOf(ProviderId.CODEX, ProviderId.CLAUDE), merged.map { it.providerId })
        assertEquals(ProviderRefreshPlan.RESET_REFRESH_QOS, merged.first().qos)
    }

    private fun job(providerId: ProviderId, mode: ProviderRefreshMode): ProviderRefreshJob {
        return ProviderRefreshJob(providerId = providerId, mode = mode, startUrl = "")
    }
}
