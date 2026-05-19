package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProviderModelsTest {
    @Test
    fun defaultProvidersAreStableAndInDashboardOrder() {
        assertEquals(
            listOf(
                ProviderId.CLAUDE,
                ProviderId.CODEX,
                ProviderId.GEMINI,
                ProviderId.COPILOT,
                ProviderId.CURSOR
            ),
            ProviderId.defaultOrder()
        )
    }

    @Test
    fun providerStorageIdsAreLowercaseAndStable() {
        assertEquals("claude", ProviderId.CLAUDE.storageId)
        assertEquals("codex", ProviderId.CODEX.storageId)
        assertEquals("gemini", ProviderId.GEMINI.storageId)
        assertEquals("copilot", ProviderId.COPILOT.storageId)
        assertEquals("cursor", ProviderId.CURSOR.storageId)
        assertEquals("Copilot", ProviderId.COPILOT.displayName)
    }

    @Test
    fun providerPlanLabelsNormalizeOnlyWhereNeeded() {
        assertEquals("Claude Pro", ProviderId.CLAUDE.normalizedPlanLabelForDisplay("Claude Pro"))
        assertEquals("Pro", ProviderId.CLAUDE.normalizedPlanLabelForDisplay("Pro"))
        assertEquals("Pro 5x", ProviderId.CODEX.normalizedPlanLabelForDisplay("prolite"))
        assertEquals("Pro 20x", ProviderId.CODEX.normalizedPlanLabelForDisplay("pro"))
        assertEquals("Plus", ProviderId.CODEX.normalizedPlanLabelForDisplay("plus"))
        assertEquals("Gemini Plus", ProviderId.GEMINI.normalizedPlanLabelForDisplay("AI Plus"))
        assertEquals("Gemini Pro", ProviderId.GEMINI.normalizedPlanLabelForDisplay("Pro"))
        assertEquals("Gemini Ultra", ProviderId.GEMINI.normalizedPlanLabelForDisplay("google_ai_ultra"))
        assertEquals("Gemini Pro", ProviderId.GEMINI.normalizedPlanLabelForDisplay("GEMINI_PRO"))
        assertEquals("Gemini Pro", ProviderId.GEMINI.normalizedPlanLabelForDisplay("g1-pro-tier"))
        assertEquals(
            "Gemini Pro",
            ProviderId.GEMINI.normalizedPlanLabelForDisplay("Gemini Code Assist in Google One AI Pro")
        )
        assertEquals("Gemini Plus", ProviderId.GEMINI.normalizedPlanLabelForDisplay("GOOGLE_AI_PLUS"))
        assertEquals("Gemini Ultra", ProviderId.GEMINI.normalizedPlanLabelForDisplay("GEMINI_ULTRA"))
        assertEquals("Gemini Ultra", ProviderId.GEMINI.normalizedPlanLabelForDisplay("g1-ultra-tier"))
        assertEquals("Gemini Free", ProviderId.GEMINI.normalizedPlanLabelForDisplay("GOOGLE_AI_FREE"))
        assertEquals("Gemini Unknown", ProviderId.GEMINI.normalizedPlanLabelForDisplay("GEMINI_UNKNOWN"))
    }

    @Test
    fun geminiUsageLineLabelsRejectGenericFallbackUsage() {
        assertTrue(ProviderId.GEMINI.isSupportedUsageLineLabel("Gemini Pro"))
        assertTrue(ProviderId.GEMINI.isSupportedUsageLineLabel("Flash"))
        assertTrue(ProviderId.GEMINI.isSupportedUsageLineLabel("Deep Research"))
        assertTrue(!ProviderId.GEMINI.isSupportedUsageLineLabel("사용량"))
        assertTrue(!ProviderId.GEMINI.isSupportedUsageLineLabel("Usage"))
    }

    @Test
    fun claudeUsageLinesDeduplicateByQuotaWindow() {
        val lines = ProviderId.CLAUDE.deduplicateUsageLinesForStorage(
            listOf(
                ProviderUsageLine(
                    label = "Claude 5시간 한도",
                    remainingPercent = 0.98f,
                    remainingText = "98% left",
                    windowText = "5 hours",
                    resetsAt = "2026-05-19T01:00:00Z",
                    sourceLabel = "/api/organizations/6d7e7f53-6216-45b3-93bb-764f73f98c92/usage",
                    confidence = 0.70f
                ),
                ProviderUsageLine(
                    label = "Five_hour",
                    remainingPercent = 0.98f,
                    remainingText = "98% left",
                    windowText = "5 hours",
                    resetsAt = "2026-05-19T01:00:00Z",
                    sourceLabel = "/api/organizations/:id/usage",
                    confidence = 0.98f
                ),
                ProviderUsageLine(
                    label = "Claude 주간 한도",
                    remainingPercent = 1f,
                    remainingText = "100% left",
                    windowText = "7 days",
                    resetsAt = "2026-05-25T20:00:00Z",
                    sourceLabel = "/api/organizations/6d7e7f53-6216-45b3-93bb-764f73f98c92/usage",
                    confidence = 0.70f
                ),
                ProviderUsageLine(
                    label = "Seven_day",
                    remainingPercent = 1f,
                    remainingText = "100% left",
                    windowText = "7 days",
                    resetsAt = "2026-05-25T20:00:00Z",
                    sourceLabel = "/api/organizations/:id/usage",
                    confidence = 0.98f
                ),
                ProviderUsageLine(
                    label = "Claude Design",
                    remainingPercent = 1f,
                    remainingText = "100% left",
                    windowText = "7 days",
                    sourceLabel = "/api/organizations/6d7e7f53-6216-45b3-93bb-764f73f98c92/usage",
                    confidence = 0.70f
                ),
                ProviderUsageLine(
                    label = "Seven_day_omelette",
                    remainingPercent = 1f,
                    remainingText = "100% left",
                    windowText = "7 days",
                    sourceLabel = "/api/organizations/:id/usage",
                    confidence = 0.98f
                )
            )
        )

        assertEquals(listOf("Five_hour", "Seven_day", "Seven_day_omelette"), lines.map { it.label })
    }

    @Test
    fun copilotUsageLinesDeduplicateByQuotaKind() {
        val lines = ProviderId.COPILOT.deduplicateUsageLinesForStorage(
            listOf(
                ProviderUsageLine(
                    label = "Chat",
                    remainingPercent = 0.88f,
                    remainingText = "88% left",
                    category = "messages",
                    unit = "messages",
                    sourceLabel = "/settings/copilot"
                ),
                ProviderUsageLine(
                    label = "Completions",
                    remainingPercent = 1f,
                    remainingText = "4,000 of 4,000 completions left",
                    category = "completions",
                    unit = "completions",
                    sourceLabel = "/settings/copilot"
                ),
                ProviderUsageLine(
                    label = "Completions",
                    remainingPercent = 1f,
                    remainingText = "4,000 of 4,000 completions left",
                    category = "completions",
                    unit = "completions",
                    sourceLabel = "/copilot/usage"
                )
            )
        )

        assertEquals(listOf("Chat", "Completions"), lines.map { it.label })
    }

    @Test
    fun cursorFreeUsdRemainingRestoresGaugeForStoredSnapshots() {
        val lines = ProviderId.CURSOR.normalizeUsageLinesForStorage(
            planLabel = "Free",
            lines = listOf(
                ProviderUsageLine(
                    label = "Total usage",
                    remainingPercent = null,
                    remainingText = "10 USD left",
                    remainingAmount = 10.0,
                    unit = "USD",
                    category = "included_usage",
                    windowText = "monthly",
                    sourceLabel = "/dashboard",
                    confidence = 0.84f
                )
            )
        )

        val line = lines.single()
        assertEquals("Total usage", line.label)
        assertEquals(1f, line.remainingPercent)
        assertEquals("10 of 10 USD left", line.remainingText)
        assertEquals("0 used of 10", line.detailText)
        assertEquals(0.0, line.usedAmount)
        assertEquals(10.0, line.limitAmount)
        assertEquals(10.0, line.remainingAmount)
    }

    @Test
    fun fromStorageIdMatchesCaseInsensitivelyAndRejectsUnknowns() {
        assertEquals(ProviderId.CLAUDE, ProviderId.fromStorageId("CLAUDE"))
        assertEquals(ProviderId.CODEX, ProviderId.fromStorageId("Codex"))
        assertEquals(ProviderId.CURSOR, ProviderId.fromStorageId(" cursor "))
        assertNull(ProviderId.fromStorageId("unknown"))
    }

    @Test
    fun disconnectedSnapshotHasIdleEmptyDisplayState() {
        val snapshot = ProviderUsageSnapshot.disconnected(ProviderId.CLAUDE)

        assertEquals(ProviderId.CLAUDE, snapshot.providerId)
        assertEquals("Claude", snapshot.displayName)
        assertEquals(ProviderConnectionState.DISCONNECTED, snapshot.connectionState)
        assertEquals(ProviderRefreshState.IDLE, snapshot.refreshState)
        assertTrue(snapshot.lines.isEmpty())
    }

    @Test
    fun unavailableSnapshotCarriesMessageAndIdleState() {
        val snapshot = ProviderUsageSnapshot.unavailable(
            providerId = ProviderId.GEMINI,
            message = "Usage is not visible yet"
        )

        assertEquals(ProviderId.GEMINI, snapshot.providerId)
        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertEquals(ProviderRefreshState.IDLE, snapshot.refreshState)
        assertEquals("Usage is not visible yet", snapshot.message)
        assertTrue(snapshot.lines.isEmpty())
    }

    @Test
    fun staleConnectingSnapshotRecoversToUnavailableIdleState() {
        val snapshot = ProviderUsageSnapshot.disconnected(ProviderId.CLAUDE).copy(
            connectionState = ProviderConnectionState.CONNECTING,
            refreshState = ProviderRefreshState.REFRESHING,
            updatedAt = "2026-05-18T00:00:00Z",
            message = "Login opened"
        )

        val recovered = snapshot.withRecoveredStaleProgress(
            now = Instant.parse("2026-05-18T00:06:00Z")
        )

        assertEquals(ProviderConnectionState.UNAVAILABLE, recovered.connectionState)
        assertEquals(ProviderRefreshState.IDLE, recovered.refreshState)
        assertNull(recovered.message)
    }

    @Test
    fun recentRefreshingSnapshotRemainsInProgress() {
        val snapshot = ProviderUsageSnapshot.disconnected(ProviderId.CURSOR).copy(
            connectionState = ProviderConnectionState.CONNECTING,
            refreshState = ProviderRefreshState.REFRESHING,
            updatedAt = "2026-05-18T00:00:00Z",
            message = "Refresh started"
        )

        val recovered = snapshot.withRecoveredStaleProgress(
            now = Instant.parse("2026-05-18T00:04:59Z")
        )

        assertEquals(ProviderConnectionState.CONNECTING, recovered.connectionState)
        assertEquals(ProviderRefreshState.REFRESHING, recovered.refreshState)
        assertEquals("Refresh started", recovered.message)
    }
}
