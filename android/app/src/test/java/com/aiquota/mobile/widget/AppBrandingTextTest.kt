package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBrandingTextTest {
    @Test
    fun appAndWidgetVisibleBrandingUsesAiQuota() {
        val brandingFiles = listOf(
            "src/main/res/values/strings.xml",
            "src/main/res/values-ko/strings.xml",
            "src/main/res/layout/notification_usage_gauges.xml",
            "src/main/java/com/aiquota/mobile/notification/UsageNotificationContent.kt",
            "src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt",
            "src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt",
            "src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt",
            "src/main/java/com/aiquota/mobile/ui/provider/ProviderDetailScreen.kt",
            "src/main/java/com/aiquota/mobile/providers/GeminiCliOAuthRepository.kt"
        )

        val combined = brandingFiles.joinToString("\n") { path -> File(path).readText() }

        assertFalse(combined.contains("AI " + "Usage"))
        assertTrue(combined.contains("AI Quota"))
    }

    @Test
    fun dashboardWidgetDoesNotRenderAiQuotaHeaderText() {
        val dashboardWidget = File("src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt").readText()

        assertFalse(dashboardWidget.contains("\"AI Quota\""))
    }
}
