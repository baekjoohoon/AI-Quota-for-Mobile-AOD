package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRefreshPlanTest {
    @Test
    fun providerRefreshTimeoutsKeepFastDefaultAndGiveSlowCollectorsMoreTime() {
        assertEquals(60_000L, ProviderRefreshPlan.timeoutMillisFor(ProviderId.CODEX))
        assertEquals(10_000L, ProviderRefreshPlan.timeoutMillisFor(ProviderId.CLAUDE))
        assertEquals(45_000L, ProviderRefreshPlan.timeoutMillisFor(ProviderId.GEMINI))
        assertEquals(10_000L, ProviderRefreshPlan.timeoutMillisFor(ProviderId.COPILOT))
        assertEquals(75_000L, ProviderRefreshPlan.timeoutMillisFor(ProviderId.ANTIGRAVITY))
        assertEquals(10_000L, ProviderRefreshPlan.timeoutMillisFor(ProviderId.CURSOR))
        assertEquals(20_000L, ProviderRefreshPlan.timeoutMillisFor(ProviderId.OPENCODE))
    }

    @Test
    fun opencodeRefreshTimeoutCoversCollectorRetryWindow() {
        assertTrue(ProviderRefreshPlan.timeoutMillisFor(ProviderId.OPENCODE) >= 15_000L)
    }

    @Test
    fun manualRefreshUsesHiddenCollectorUrlsForWebSessionProvidersAndNativeForNativeRefreshProviders() {
        val jobs = listOf(
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.OPENCODE,
            ProviderId.GEMINI,
            ProviderId.COPILOT,
            ProviderId.ANTIGRAVITY,
            ProviderId.CURSOR
        ).map(ProviderRefreshPlan::manualJobFor)

        assertTrue(
            jobs
                .filterNot { it.providerId == ProviderId.ANTIGRAVITY }
                .all { it.mode == ProviderRefreshMode.HIDDEN_WEB_COLLECTOR }
        )
        assertEquals(ProviderRefreshMode.NATIVE_API, jobs.first { it.providerId == ProviderId.ANTIGRAVITY }.mode)
        assertFalse(jobs.any { it.startUrl.contains("/auth/login") || it.startUrl.contains("/login") })
        assertEquals("https://claude.ai/", jobs.first { it.providerId == ProviderId.CLAUDE }.startUrl)
        assertEquals(ProviderLoginStrategy.CODEX_CALLBACK_RECOVERY_URL, jobs.first { it.providerId == ProviderId.CODEX }.startUrl)
        assertEquals("https://opencode.ai/auth", jobs.first { it.providerId == ProviderId.OPENCODE }.startUrl)
        assertEquals("https://gemini.google.com/usage", jobs.first { it.providerId == ProviderId.GEMINI }.startUrl)
        assertEquals("https://github.com/settings/copilot/features", jobs.first { it.providerId == ProviderId.COPILOT }.startUrl)
        assertEquals("", jobs.first { it.providerId == ProviderId.ANTIGRAVITY }.startUrl)
        assertEquals("https://cursor.com/dashboard", jobs.first { it.providerId == ProviderId.CURSOR }.startUrl)
    }

    @Test
    fun geminiRefreshesThroughUsageDomAndAntigravityRefreshesThroughNativeApi() {
        val jobs = listOf(ProviderId.GEMINI, ProviderId.ANTIGRAVITY).map(ProviderRefreshPlan::manualJobFor)

        assertEquals(ProviderRefreshMode.HIDDEN_WEB_COLLECTOR, jobs.first { it.providerId == ProviderId.GEMINI }.mode)
        assertEquals(ProviderRefreshMode.NATIVE_API, jobs.first { it.providerId == ProviderId.ANTIGRAVITY }.mode)
        assertEquals("https://gemini.google.com/usage", jobs.first { it.providerId == ProviderId.GEMINI }.startUrl)
        assertEquals("", jobs.first { it.providerId == ProviderId.ANTIGRAVITY }.startUrl)
    }

    @Test
    fun geminiRefreshUsesUsageDomCollector() {
        val geminiJob = ProviderRefreshPlan.manualJobFor(ProviderId.GEMINI)

        assertEquals(ProviderRefreshMode.HIDDEN_WEB_COLLECTOR, geminiJob.mode)
        assertEquals("https://gemini.google.com/usage", geminiJob.startUrl)
    }

    @Test
    fun hiddenWebCollectorSessionsAreRetainedForWebSessionProviders() {
        val retainedProviders = listOf(
            ProviderId.CLAUDE,
            ProviderId.CODEX,
            ProviderId.GLM,
            ProviderId.OPENCODE,
            ProviderId.GEMINI,
            ProviderId.COPILOT,
            ProviderId.CURSOR
        )

        assertTrue(retainedProviders.all(ProviderHiddenWebViewRetentionPolicy::shouldRetain))
        assertFalse(ProviderHiddenWebViewRetentionPolicy.shouldRetain(ProviderId.ANTIGRAVITY))
        assertFalse(ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(ProviderRefreshFailureKind.TIMEOUT))
        assertFalse(ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(ProviderRefreshFailureKind.COLLECTOR_ERROR))
        assertTrue(ProviderHiddenWebViewRetentionPolicy.shouldRecreateAfterFailure(ProviderRefreshFailureKind.INTERACTIVE_AUTH_REQUIRED))
    }

    @Test
    fun automaticRefreshRunsEverySixtySecondsForConnectedProviders() {
        val snapshots = listOf(
            connected(ProviderId.CLAUDE),
            ProviderUsageSnapshot.disconnected(ProviderId.CODEX),
            connected(ProviderId.GEMINI),
            snapshot(ProviderId.COPILOT, ProviderConnectionState.STALE),
            snapshot(ProviderId.CURSOR, ProviderConnectionState.COLLECTING)
        )

        assertEquals(60_000L, ProviderRefreshPlan.AUTO_REFRESH_INTERVAL_MILLIS)
        assertEquals(
            listOf(ProviderId.CLAUDE, ProviderId.GEMINI, ProviderId.COPILOT),
            ProviderRefreshPlan.automaticJobsFor(snapshots).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshFollowsDashboardProviderOrder() {
        val snapshots = ProviderId.defaultOrder().map(::connected)

        assertEquals(
            ProviderId.defaultOrder(),
            ProviderRefreshPlan.automaticJobsFor(snapshots).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshSkipsGlmConnectedWithoutSubscription() {
        val snapshots = listOf(
            connected(ProviderId.CLAUDE),
            ProviderUsageSnapshot(
                providerId = ProviderId.GLM,
                connectionState = ProviderConnectionState.CONNECTED,
                planLabel = "Plan 없음",
                message = "You don't have any subscription",
                lines = emptyList()
            ),
            connected(ProviderId.GEMINI)
        )

        assertEquals(
            listOf(ProviderId.CLAUDE, ProviderId.GEMINI),
            ProviderRefreshPlan.automaticJobsFor(snapshots).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshSkipsGlmNoSubscriptionAfterFailedRefresh() {
        val snapshots = listOf(
            connected(ProviderId.CLAUDE),
            ProviderUsageSnapshot(
                providerId = ProviderId.GLM,
                connectionState = ProviderConnectionState.ERROR,
                planLabel = "Plan 없음",
                message = "Background refresh timed out.",
                lines = emptyList()
            ),
            connected(ProviderId.OPENCODE)
        )

        assertEquals(
            listOf(ProviderId.CLAUDE, ProviderId.OPENCODE),
            ProviderRefreshPlan.automaticJobsFor(snapshots).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshRetriesNonAuthCollectionFailures() {
        val now = Instant.parse("2026-06-18T09:55:00Z")
        val oldCodexFailure = ProviderUsageSnapshot.failedKeepingPrevious(
            providerId = ProviderId.CODEX,
            previous = null,
            message = "Background collector ran. No trusted usage payload found."
        ).copy(statusUpdatedAt = now.minusSeconds(16 * 60).toString())
        val snapshots = listOf(
            ProviderUsageSnapshot.connectedWithoutUsage(
                providerId = ProviderId.CURSOR,
                message = "Usage quota is not available yet."
            ),
            oldCodexFailure,
            ProviderUsageSnapshot(
                providerId = ProviderId.GEMINI,
                connectionState = ProviderConnectionState.UNAVAILABLE,
                message = "Provider session reached, but trusted usage payload was not available yet."
            ),
            ProviderUsageSnapshot.disconnected(ProviderId.CLAUDE)
        )

        assertEquals(
            listOf(ProviderId.CURSOR, ProviderId.CODEX, ProviderId.GEMINI),
            ProviderRefreshPlan.automaticJobsFor(snapshots, now = now).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshRetriesRecentLoginPageFailures() {
        val now = Instant.parse("2026-06-18T09:55:00Z")
        val recentLoginPageFailure = snapshot(ProviderId.COPILOT, ProviderConnectionState.CONNECTED).copy(
            statusUpdatedAt = now.minusSeconds(60).toString(),
            message = "Background refresh reached a provider login page."
        )

        assertEquals(
            listOf(ProviderId.COPILOT, ProviderId.GEMINI),
            ProviderRefreshPlan.automaticJobsFor(
                listOf(recentLoginPageFailure, connected(ProviderId.GEMINI)),
                now = now
            ).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshKeepsRetryingGeminiLoginPageFailureWhenLastKnownUsageExists() {
        val now = Instant.parse("2026-06-18T09:55:00Z")
        val recentGeminiLoginPageFailure = snapshot(ProviderId.GEMINI, ProviderConnectionState.CONNECTED).copy(
            statusUpdatedAt = now.minusSeconds(60).toString(),
            message = "Background refresh reached a provider login page.",
            lines = listOf(
                ProviderUsageLine(
                    key = "gemini:5_hour_limit",
                    label = "5시간 한도",
                    remainingPercent = 1f
                )
            )
        )

        assertEquals(
            listOf(ProviderId.GEMINI),
            ProviderRefreshPlan.automaticJobsFor(
                listOf(recentGeminiLoginPageFailure),
                now = now
            ).map { it.providerId }
        )
    }

    @Test
    fun manualRefreshStillRunsAfterRecentLoginPageFailure() {
        val now = Instant.parse("2026-06-18T09:55:00Z")
        val recentLoginPageFailure = snapshot(ProviderId.OPENCODE, ProviderConnectionState.CONNECTED).copy(
            statusUpdatedAt = now.minusSeconds(60).toString(),
            message = "Background refresh reached a provider login page."
        )

        assertEquals(
            listOf(ProviderId.OPENCODE),
            ProviderRefreshPlan.manualCycleJobsFor(
                manualProviderId = ProviderId.OPENCODE,
                snapshots = listOf(recentLoginPageFailure),
                now = now
            ).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshRetriesRecentCodexTimeoutFailures() {
        val now = Instant.parse("2026-06-18T09:55:00Z")
        val recentCodexFailure = snapshot(ProviderId.CODEX, ProviderConnectionState.CONNECTED).copy(
            statusUpdatedAt = now.minusSeconds(60).toString(),
            message = "Previous collection did not finish."
        )

        assertEquals(
            listOf(ProviderId.CODEX, ProviderId.GEMINI),
            ProviderRefreshPlan.automaticJobsFor(
                listOf(recentCodexFailure, connected(ProviderId.GEMINI)),
                now = now
            ).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshRetriesRecentCodexUsageUnavailable() {
        val now = Instant.parse("2026-06-18T09:55:00Z")
        val recentCodexFailure = snapshot(ProviderId.CODEX, ProviderConnectionState.CONNECTED).copy(
            statusUpdatedAt = now.minusSeconds(60).toString(),
            message = "Codex session reached, but trusted usage payload was not available. diagnostics={}"
        )

        assertEquals(
            listOf(ProviderId.CODEX, ProviderId.GEMINI),
            ProviderRefreshPlan.automaticJobsFor(
                listOf(recentCodexFailure, connected(ProviderId.GEMINI)),
                now = now
            ).map { it.providerId }
        )
    }

    @Test
    fun automaticRefreshPromotesExpiredResetProvidersToQosOne() {
        val resetAt = Instant.parse("2026-05-21T00:00:00Z")
        val snapshots = listOf(
            connected(ProviderId.CLAUDE),
            snapshot(ProviderId.CODEX, ProviderConnectionState.CONNECTED).copy(
                lines = listOf(
                    ProviderUsageLine(
                        key = "codex:primary_window",
                        label = "5시간 한도",
                        remainingPercent = 0.79f,
                        resetsAt = resetAt.toString()
                    )
                )
            )
        )

        val jobs = ProviderRefreshPlan.automaticJobsFor(snapshots, now = resetAt.plusSeconds(1))

        assertEquals(listOf(ProviderId.CODEX, ProviderId.CLAUDE), jobs.map { it.providerId })
        assertEquals(ProviderRefreshPlan.RESET_REFRESH_QOS, jobs.first().qos)
    }

    @Test
    fun manualRefreshCycleDoesNotBlockExpiredResetProvider() {
        val resetAt = Instant.parse("2026-05-21T00:00:00Z")
        val snapshots = listOf(
            connected(ProviderId.CLAUDE),
            snapshot(ProviderId.CODEX, ProviderConnectionState.CONNECTED).copy(
                lines = listOf(
                    ProviderUsageLine(
                        key = "codex:primary_window",
                        label = "5시간 한도",
                        remainingPercent = 0.79f,
                        resetsAt = resetAt.toString()
                    )
                )
            )
        )

        val jobs = ProviderRefreshPlan.manualCycleJobsFor(
            manualProviderId = ProviderId.CLAUDE,
            snapshots = snapshots,
            now = resetAt.plusSeconds(1)
        )

        assertEquals(listOf(ProviderId.CODEX, ProviderId.CLAUDE), jobs.map { it.providerId })
        assertEquals(ProviderRefreshPlan.RESET_REFRESH_QOS, jobs.first().qos)
    }

    @Test
    fun automaticRefreshSkipsGooglePrivateApiDisabledStaleStateWithoutUsageLines() {
        val snapshots = listOf(
            ProviderUsageSnapshot(
                providerId = ProviderId.GEMINI,
                connectionState = ProviderConnectionState.STALE,
                lines = emptyList(),
                message = "Gemini Cloud Code Private API is disabled for this OAuth project."
            ),
            snapshot(ProviderId.CLAUDE, ProviderConnectionState.STALE)
        )

        assertEquals(
            listOf(ProviderId.CLAUDE),
            ProviderRefreshPlan.automaticJobsFor(snapshots).map { it.providerId }
        )
    }

    @Test
    fun backgroundRefreshCadenceCountsFromCycleStartButKeepsMinimumRecoveryDelay() {
        assertEquals(60_000L, ProviderRefreshPlan.nextAutoRefreshDelayMillis(elapsedMillis = 0L))
        assertEquals(50_000L, ProviderRefreshPlan.nextAutoRefreshDelayMillis(elapsedMillis = 10_000L))
        assertEquals(5_000L, ProviderRefreshPlan.nextAutoRefreshDelayMillis(elapsedMillis = 60_000L))
        assertEquals(5_000L, ProviderRefreshPlan.nextAutoRefreshDelayMillis(elapsedMillis = 100_000L))
    }

    private fun connected(providerId: ProviderId): ProviderUsageSnapshot {
        return snapshot(providerId, ProviderConnectionState.CONNECTED)
    }

    private fun snapshot(providerId: ProviderId, state: ProviderConnectionState): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = state,
            lines = listOf(ProviderUsageLine(label = "Usage", remainingPercent = 0.5f))
        )
    }
}
