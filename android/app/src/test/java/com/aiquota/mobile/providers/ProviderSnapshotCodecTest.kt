package com.aiquota.mobile.providers

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSnapshotCodecTest {
    @Test
    fun decodeDemotesConnectedSnapshotWithoutUsageLines() {
        val raw = """
            {
              "providers": [
                {
                  "providerId": "codex",
                  "connectionState": "CONNECTED",
                  "refreshState": "IDLE",
                  "updatedAt": "2026-05-19T00:00:00Z",
                  "lines": []
                }
              ]
            }
        """.trimIndent()

        val snapshot = ProviderSnapshotCodec.decode(raw).single()

        assertEquals(ProviderId.CODEX, snapshot.providerId)
        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
    }

    @Test
    fun decodeKeepsRetryPendingStaleGoogleSnapshotWithoutUsageLines() {
        val raw = """
            {
              "providers": [
                {
                  "providerId": "gemini",
                  "connectionState": "STALE",
                  "refreshState": "IDLE",
                  "updatedAt": "2026-05-27T00:00:00Z",
                  "message": "Provider session reached, but trusted usage payload was not available yet.",
                  "lines": []
                }
              ]
            }
        """.trimIndent()

        val snapshot = ProviderSnapshotCodec.decode(raw).single()

        assertEquals(ProviderId.GEMINI, snapshot.providerId)
        assertEquals(ProviderConnectionState.STALE, snapshot.connectionState)
        assertTrue(snapshot.lines.isEmpty())
    }
}
