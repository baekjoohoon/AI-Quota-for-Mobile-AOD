package com.aiquota.mobile.notification

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSixProvidersTest {
    @Test
    fun notificationContentIncludesEightProviderGaugeRows() {
        val content = buildUsageNotificationContent(snapshotJson = sixProviderSnapshotJson())

        assertEquals(8, content.gaugeRows.size)
    }

    @Test
    fun compactNotificationSummaryShowsEightProvidersWithoutRemainingSuffix() {
        val content = buildUsageNotificationContent(snapshotJson = sixProviderSnapshotJson())
        val lines = content.summary.lines()

        assertEquals(3, lines.size)
        assertEquals(3, lines[0].split(" | ").size)
        assertEquals(3, lines[1].split(" | ").size)
        assertEquals(2, lines[2].split(" | ").size)
        assertTrue(content.summary.contains("claude 100%"))
        assertTrue(content.summary.contains("codex 100%"))
        assertTrue(content.summary.contains("glm 100%"))
        assertTrue(content.summary.contains("opencode 100%"))
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

        assertEquals(6, content.gaugeRows.size)
        assertTrue(!content.summary.contains("codex"))
        assertTrue(!content.summary.contains("cursor"))
        assertTrue(content.summary.contains("claude 100%"))
        assertTrue(content.summary.contains("AntiG 100%"))
    }

    @Test
    fun updateMessageIsAddedToNotificationTextWithoutReplacingGaugeRows() {
        val content = buildUsageNotificationContent(
            snapshotJson = sixProviderSnapshotJson(),
            updateMessage = "Update available"
        )

        assertEquals(8, content.gaugeRows.size)
        assertTrue(content.summary.contains("claude 100%"))
        assertTrue(content.summary.contains("Update available"))
        assertTrue(content.compactText.contains("Update available"))
    }

    @Test
    fun expandedNotificationLayoutDefinesEightRows() {
        val layout = File("src/main/res/layout/notification_usage_gauges.xml").readText()
        val controller = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()

        (0..7).forEach { index ->
            assertTrue(layout.contains("@+id/notification_row_$index"))
            assertTrue(controller.contains("R.id.notification_row_$index"))
        }
        assertTrue(layout.contains("@+id/notification_settings_button"))
        assertTrue(controller.contains("R.id.notification_settings_button"))
        assertTrue(controller.contains("MainActivity.createSettingsIntent(context)"))
        assertTrue(layout.contains("android:layout_marginEnd=\"8dp\""))
        assertTrue(layout.contains("android:layout_marginStart=\"8dp\""))
        assertTrue(layout.contains("android:layout_height=\"34dp\""))
    }

    @Test
    fun expandedNotificationShowsGaugeRowsWithRemainingAndResetCaptions() {
        val layout = File("src/main/res/layout/notification_usage_gauges.xml").readText()
        val controller = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()

        assertTrue(!layout.contains("@+id/notification_title"))
        assertTrue(!controller.contains("R.id.notification_title"))
        (0..7).forEach { index ->
            assertTrue(layout.contains("@+id/notification_remaining_$index"))
            assertTrue(layout.contains("@+id/notification_reset_$index"))
            assertTrue(controller.contains("R.id.notification_remaining_$index"))
            assertTrue(controller.contains("R.id.notification_reset_$index"))
        }
        assertTrue(!layout.contains("80% left"))
        assertTrue(!layout.contains("Resets in 1h"))
        assertTrue(layout.contains("android:textSize=\"9sp\""))
        assertTrue(layout.contains("android:textSize=\"8sp\""))
        assertTrue(controller.contains("setTextViewText(row.remainingTextId"))
        assertTrue(controller.contains("setTextViewText(row.resetTextId"))
    }

    @Test
    fun collapsedNotificationShowsFourProviderCompactBatteryItems() {
        val compactLayout = File("src/main/res/layout/notification_usage_compact.xml").readText()
        val controller = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()
        val content = buildUsageNotificationContent(snapshotJson = sixProviderSnapshotJson())

        (0..3).forEach { index ->
            assertTrue(compactLayout.contains("@+id/notification_compact_item_$index"))
            assertTrue(compactLayout.contains("@+id/notification_compact_icon_$index"))
            assertTrue(compactLayout.contains("@+id/notification_compact_text_$index"))
            assertTrue(compactLayout.contains("@+id/notification_compact_progress_$index"))
            assertTrue(controller.contains("R.id.notification_compact_item_$index"))
            assertTrue(controller.contains("R.id.notification_compact_icon_$index"))
            assertTrue(controller.contains("R.id.notification_compact_text_$index"))
            assertTrue(controller.contains("R.id.notification_compact_progress_$index"))
        }
        assertFalse(compactLayout.contains("notification_compact_item_4"))
        assertFalse(compactLayout.contains("notification_compact_item_5"))
        assertTrue(controller.contains(".setContentText(content.summary)"))
        assertTrue(controller.contains("views.setImageViewResource(item.iconId"))
        assertTrue(controller.contains("views.setTextViewText(item.textId, gauge.compactRemainingText)"))
        assertTrue(controller.contains("views.setProgressBar(item.progressId"))
        assertTrue(controller.contains("content.gaugeRows.take(NOTIFICATION_COMPACT_ITEM_COUNT)"))
        assertFalse(compactLayout.contains("notification_compact_status"))
        assertFalse(compactLayout.contains("Live refresh"))
        assertFalse(compactLayout.contains("라이브 갱신"))
        assertFalse(controller.contains("notificationLiveStatusText"))
        assertFalse(controller.contains("R.id.notification_compact_status"))
        assertEquals("100% 남음", content.gaugeRows.first().compactRemainingText)
    }

    @Test
    fun collapsedNotificationUsesReadableCompactItemSizes() {
        val compactLayout = File("src/main/res/layout/notification_usage_compact.xml").readText()

        assertTrue(compactLayout.contains("android:layout_height=\"22dp\""))
        assertTrue(compactLayout.contains("android:layout_width=\"15dp\""))
        assertTrue(compactLayout.contains("android:layout_height=\"15dp\""))
        assertTrue(compactLayout.contains("android:layout_height=\"4dp\""))
        assertTrue(compactLayout.contains("android:textSize=\"9sp\""))
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
        val providers = listOf("claude", "codex", "glm", "opencode", "gemini", "copilot", "antigravity", "cursor")
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
