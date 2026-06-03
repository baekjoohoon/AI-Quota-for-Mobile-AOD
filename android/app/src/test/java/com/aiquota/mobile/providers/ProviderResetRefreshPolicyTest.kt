package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageLine
import com.aiquota.mobile.local.ProviderUsageSnapshot
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderResetRefreshPolicyTest {
    @Test
    fun expiredCodexResetTimestampRequestsARealProviderRefresh() {
        val resetAt = Instant.parse("2026-05-21T00:00:00Z")
        val snapshot = snapshot(
            providerId = ProviderId.CODEX,
            state = ProviderConnectionState.CONNECTED,
            resetsAt = (resetAt.toEpochMilli() / 1000L).toString(),
            remainingPercent = 0.79f
        )

        val requests = ProviderResetRefreshPolicy.requests(
            snapshots = listOf(snapshot),
            now = resetAt.plusSeconds(1)
        )

        assertEquals(listOf(ProviderId.CODEX), requests.map { it.providerId })
        assertTrue(requests.single().resetKey.contains("codex:primary_window"))
        assertEquals(0.79f, snapshot.lines.single().remainingPercent ?: 0f, 0.001f)
    }

    @Test
    fun futureResetTimestampDoesNotRequestRefreshUntilItPasses() {
        val resetAt = Instant.parse("2026-05-21T00:00:00Z")
        val snapshot = snapshot(
            providerId = ProviderId.CODEX,
            state = ProviderConnectionState.CONNECTED,
            resetsAt = resetAt.toString()
        )

        assertEquals(
            emptyList<ProviderResetRefreshRequest>(),
            ProviderResetRefreshPolicy.requests(listOf(snapshot), resetAt.minusSeconds(1))
        )
        assertEquals(1000L, ProviderResetRefreshPolicy.nextResetDelayMillis(listOf(snapshot), resetAt.minusSeconds(1)))
        assertNull(ProviderResetRefreshPolicy.nextResetDelayMillis(listOf(snapshot), resetAt.plusSeconds(1)))
    }

    @Test
    fun resetRefreshSkipsSnapshotsAlreadyRefreshingOrRequiringAuth() {
        val now = Instant.parse("2026-05-21T00:00:01Z")
        val expiredReset = "2026-05-21T00:00:00Z"

        val refreshing = snapshot(
            providerId = ProviderId.CODEX,
            state = ProviderConnectionState.CONNECTED,
            refreshState = ProviderRefreshState.REFRESHING,
            resetsAt = expiredReset
        )
        val authRequired = snapshot(
            providerId = ProviderId.CLAUDE,
            state = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED,
            resetsAt = expiredReset
        )

        assertEquals(emptyList<ProviderResetRefreshRequest>(), ProviderResetRefreshPolicy.requests(listOf(refreshing, authRequired), now))
        assertNull(ProviderResetRefreshPolicy.nextResetDelayMillis(listOf(refreshing, authRequired), now.minusSeconds(2)))
    }

    @Test
    fun foregroundRefreshPlanSchedulesResetRefreshWhenResetTimestampPasses() {
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val plan = File("src/main/java/com/aiquota/mobile/providers/ProviderRefreshPlan.kt").readText()
        val service = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(plan.contains("val resetJobs = resetJobsFor(snapshots, now)"))
        assertTrue(plan.contains("ProviderResetRefreshPolicy.requests(snapshots, now)"))
        assertTrue(plan.contains(".map(::resetJobFor)"))
        assertTrue(service.contains("ProviderRefreshPlan.automaticJobsFor(repository.readSnapshots())"))
        assertTrue(!appShell.contains("resetRefreshRequestKeys"))
        assertTrue(!appShell.contains("requestProviderRefreshJobs(resetRefreshJobs"))
    }

    private fun snapshot(
        providerId: ProviderId,
        state: ProviderConnectionState,
        refreshState: ProviderRefreshState = ProviderRefreshState.IDLE,
        resetsAt: String,
        remainingPercent: Float = 0.5f
    ): ProviderUsageSnapshot {
        return ProviderUsageSnapshot(
            providerId = providerId,
            connectionState = state,
            refreshState = refreshState,
            lines = listOf(
                ProviderUsageLine(
                    key = "${providerId.storageId}:primary_window",
                    label = "5-hour limit",
                    remainingPercent = remainingPercent,
                    resetsAt = resetsAt
                )
            )
        )
    }
}
