package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
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
}
