package com.aiusage.mobile.providers

import com.aiusage.mobile.local.ProviderConnectionState
import com.aiusage.mobile.local.ProviderId
import com.aiusage.mobile.local.ProviderRefreshState
import com.aiusage.mobile.local.UsageSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextUsageExtractorTest {
    @Test
    fun extractsPercentLeftAsConnectedIdleUsageLine() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CLAUDE,
            visibleText = "Plan\n80% left\nResets in 3 hours"
        )

        assertEquals(ProviderId.CLAUDE, snapshot.providerId)
        assertEquals(ProviderConnectionState.CONNECTED, snapshot.connectionState)
        assertEquals(ProviderRefreshState.IDLE, snapshot.refreshState)
        assertEquals(1, snapshot.lines.size)
        assertEquals("Usage", snapshot.lines.single().label)
        assertEquals(0.8f, snapshot.lines.single().remainingPercent)
        assertEquals("80% left", snapshot.lines.single().remainingText)
        assertEquals("Resets in 3 hours", snapshot.lines.single().resetText)
        assertEquals(UsageSeverity.NORMAL, snapshot.lines.single().severity)
    }

    @Test
    fun extractsPercentRemainingAndWarningSeverity() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.GEMINI,
            visibleText = "You have 25% remaining until reset tomorrow"
        )

        val line = snapshot.lines.single()
        assertEquals(0.25f, line.remainingPercent)
        assertEquals("25% remaining", line.remainingText)
        assertEquals(UsageSeverity.WARNING, line.severity)
        assertEquals("Reset tomorrow", line.resetText)
    }

    @Test
    fun marksLowRemainingAsDangerSeverity() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CODEX,
            visibleText = "Only 14% left"
        )

        assertEquals(UsageSeverity.DANGER, snapshot.lines.single().severity)
    }

    @Test
    fun returnsUnavailableSnapshotWhenNoUsageTextIsVisible() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.CURSOR,
            visibleText = "Dashboard settings and billing"
        )

        assertEquals(ProviderId.CURSOR, snapshot.providerId)
        assertEquals(ProviderConnectionState.UNAVAILABLE, snapshot.connectionState)
        assertEquals(ProviderRefreshState.IDLE, snapshot.refreshState)
        assertTrue(snapshot.lines.isEmpty())
        assertNotNull(snapshot.message)
        assertTrue(snapshot.message!!.contains("No visible usage percentage"))
    }

    @Test
    fun ignoresBlankResetTextWhenUsageIsFound() {
        val snapshot = TextUsageExtractor.extract(
            providerId = ProviderId.COPILOT,
            visibleText = "35% left"
        )

        assertEquals(UsageSeverity.NORMAL, snapshot.lines.single().severity)
        assertNull(snapshot.lines.single().resetText)
    }
}
