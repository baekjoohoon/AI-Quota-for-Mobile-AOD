package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProviderConnectorRegistryTest {
    @Test
    fun defaultRegistryUsesProviderDefaultOrderAndStartUrls() {
        val connectors = ProviderConnectorRegistry.default().connectors

        assertEquals(ProviderId.defaultOrder(), connectors.map { it.providerId })
        assertEquals(
            listOf(
                "https://claude.ai/",
                "https://chatgpt.com/",
                "https://gemini.google.com/app",
                "https://github.com/settings/copilot",
                "https://cursor.com/dashboard"
            ),
            connectors.map { it.startUrl }
        )
    }

    @Test
    fun connectorForReturnsMatchingConnector() {
        val registry = ProviderConnectorRegistry.default()

        ProviderId.defaultOrder().forEach { providerId ->
            assertSame(
                registry.connectors.first { it.providerId == providerId },
                registry.connectorFor(providerId)
            )
        }
    }

    @Test
    fun webTextConnectorExtractCarriesMetadataAndConnectedSnapshot() {
        val connector = WebTextProviderConnector(
            providerId = ProviderId.CODEX,
            startUrl = "https://chatgpt.com/"
        )

        val result = connector.extract("Usage\n42% remaining\nresets tomorrow")

        assertEquals(ProviderId.CODEX, result.providerId)
        assertEquals("https://chatgpt.com/", result.startUrl)
        assertSame(result.snapshot, result.usageSnapshot)
        assertEquals(ProviderId.CODEX, result.snapshot.providerId)
        assertEquals(ProviderConnectionState.CONNECTED, result.snapshot.connectionState)
        assertEquals(1, result.snapshot.lines.size)
        assertEquals("Usage", result.snapshot.lines.single().label)
        assertEquals(0.42f, result.snapshot.lines.single().remainingPercent)
        assertEquals("42% remaining", result.snapshot.lines.single().remainingText)
    }

    @Test
    fun webTextConnectorImplementsLifecycleContract() = runBlocking {
        val connector: ProviderConnector = WebTextProviderConnector(
            providerId = ProviderId.GEMINI,
            startUrl = "https://gemini.google.com/app"
        )

        assertEquals(ProviderConnectorState.DISCONNECTED, connector.connectionState())

        val snapshot = connector.refreshUsage("Plan\n67% left\nresets tonight")

        assertEquals(ProviderId.GEMINI, snapshot.providerId)
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals(1, snapshot.lines.size)
        assertEquals("Usage", snapshot.lines.single().label)
        assertEquals(0.67f, snapshot.lines.single().remainingPercent)
        assertEquals("67% left", snapshot.lines.single().remainingText)

        connector.disconnect()
        assertEquals(ProviderConnectorState.DISCONNECTED, connector.connectionState())
    }
}
