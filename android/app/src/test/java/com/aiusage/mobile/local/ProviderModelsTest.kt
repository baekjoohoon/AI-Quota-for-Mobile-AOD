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
