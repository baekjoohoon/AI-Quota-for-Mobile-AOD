package com.aiquota.mobile.notification

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSixProvidersTest {
    @Test
    fun notificationContentIncludesSixProviderGaugeRows() {
        val content = buildUsageNotificationContent(snapshotJson = sixProviderSnapshotJson())

        assertEquals(6, content.gaugeRows.size)
    }

    @Test
    fun compactNotificationSummaryShowsSixProvidersAsTwoRowsOfThreeWithoutRemainingSuffix() {
        val content = buildUsageNotificationContent(snapshotJson = sixProviderSnapshotJson())
        val lines = content.summary.lines()

        assertEquals(2, lines.size)
        assertEquals(3, lines[0].split(" | ").size)
        assertEquals(3, lines[1].split(" | ").size)
        assertTrue(content.summary.contains("claude 100%"))
        assertTrue(content.summary.contains("codex 100%"))
        assertTrue(content.summary.contains("gemini 100%"))
        assertTrue(content.summary.contains("copilot 100%"))
        assertTrue(content.summary.contains("AntiG 100%"))
        assertTrue(content.summary.contains("cursor 100%"))
        assertTrue(!content.summary.contains("남음"))
        assertTrue(!content.summary.contains("left"))
    }

    @Test
    fun notificationContentExcludesDisconnectedProviders() {
        val content = buildUsageNotificationContent(snapshotJson = sixProviderSnapshotJson(disconnectedProviderIds = setOf("codex", "cursor")))

        assertEquals(4, content.gaugeRows.size)
        assertTrue(!content.summary.contains("codex"))
        assertTrue(!content.summary.contains("cursor"))
        assertTrue(content.summary.contains("claude 100%"))
        assertTrue(content.summary.contains("AntiG 100%"))
    }

    @Test
    fun expandedNotificationLayoutDefinesSixRows() {
        val layout = File("src/main/res/layout/notification_usage_gauges.xml").readText()
        val controller = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()

        (0..5).forEach { index ->
            assertTrue(layout.contains("@+id/notification_row_$index"))
            assertTrue(controller.contains("R.id.notification_row_$index"))
        }
    }

    @Test
    fun expandedNotificationShowsGaugeRowsWithRemainingAndResetCaptions() {
        val layout = File("src/main/res/layout/notification_usage_gauges.xml").readText()
        val controller = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()

        assertTrue(!layout.contains("@+id/notification_title"))
        assertTrue(!controller.contains("R.id.notification_title"))
        (0..5).forEach { index ->
            assertTrue(layout.contains("@+id/notification_remaining_$index"))
            assertTrue(layout.contains("@+id/notification_reset_$index"))
            assertTrue(controller.contains("R.id.notification_remaining_$index"))
            assertTrue(controller.contains("R.id.notification_reset_$index"))
        }
        assertTrue(!layout.contains("80% left"))
        assertTrue(!layout.contains("Resets in 1h"))
        assertTrue(controller.contains("setTextViewText(row.remainingTextId"))
        assertTrue(controller.contains("setTextViewText(row.resetTextId"))
    }

    @Test
    fun collapsedNotificationShowsUsageSummaryTextOnly() {
        val compactLayout = File("src/main/res/layout/notification_usage_compact.xml").readText()
        val controller = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()

        assertTrue(compactLayout.contains("@+id/notification_compact_summary"))
        assertTrue(controller.contains(".setContentText(content.summary)"))
        assertTrue(controller.contains("setTextViewText(R.id.notification_compact_summary, content.summary)"))
        assertFalse(compactLayout.contains("notification_compact_status"))
        assertFalse(compactLayout.contains("Live refresh"))
        assertFalse(compactLayout.contains("라이브 갱신"))
        assertFalse(controller.contains("notificationLiveStatusText"))
        assertFalse(controller.contains("R.id.notification_compact_status"))
    }

    @Test
    fun expandedNotificationDoesNotShowLiveRefreshCopy() {
        val expandedLayout = File("src/main/res/layout/notification_usage_gauges.xml").readText()
        val controller = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertFalse(expandedLayout.contains("notification_live_status"))
        assertFalse(expandedLayout.contains("Live refresh"))
        assertFalse(expandedLayout.contains("라이브 갱신"))
        assertFalse(controller.contains("ProviderBackgroundRefreshStateRepository"))
        assertFalse(controller.contains("backgroundRefreshLiveState"))
        assertFalse(controller.contains("UsageNotificationLiveState"))
        assertFalse(english.contains("notification_live_status"))
        assertFalse(english.contains("Live refresh active"))
        assertFalse(english.contains("Live refresh needs attention"))
        assertFalse(korean.contains("라이브 갱신 중"))
        assertFalse(korean.contains("라이브 갱신 확인 필요"))
    }

    private fun sixProviderSnapshotJson(
        disconnectedProviderIds: Set<String> = emptySet(),
        updatedAt: String = "2026-05-29T00:00:00Z"
    ): String {
        val providers = listOf("claude", "codex", "gemini", "copilot", "antigravity", "cursor")
            .joinToString(",") { providerId ->
                val state = if (providerId in disconnectedProviderIds) "DISCONNECTED" else "CONNECTED"
                """
                {
                  "providerId": "$providerId",
                  "displayName": "$providerId",
                  "connectionState": "$state",
                  "lines": [
                    {
                      "label": "Usage",
                      "remainingPercent": 1.0,
                      "remainingText": "100% left",
                      "resetText": "Resets in 1h"
                    }
                  ]
                }
                """.trimIndent()
            }
        return """{"updatedAt":"$updatedAt","providers":[$providers]}"""
    }
}
