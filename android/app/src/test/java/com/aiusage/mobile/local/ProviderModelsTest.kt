package com.aiusage.mobile.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
