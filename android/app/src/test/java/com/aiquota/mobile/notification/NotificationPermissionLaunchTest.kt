package com.aiquota.mobile.notification

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionLaunchTest {
    @Test
    fun mainActivityDefersNotificationPermissionToThemedLiveRefreshPrompt() {
        val mainActivity = File("src/main/java/com/aiquota/mobile/MainActivity.kt").readText()
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val controller = File("src/main/java/com/aiquota/mobile/notification/UsageLimitNotificationController.kt").readText()

        assertTrue(mainActivity.contains("ForegroundRefreshController(this).liveMonitoringEnabled()"))
        assertTrue(mainActivity.contains("UsageLimitNotificationController.updateFromCache"))
        assertTrue(mainActivity.contains("UsageLimitNotificationController.cancel(this)"))
        assertTrue(!mainActivity.contains("requestPermissions("))
        assertTrue(!mainActivity.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(appShell.contains("ActivityResultContracts.RequestPermission"))
        assertTrue(appShell.contains("LiveRefreshPermissionDialog"))
        assertTrue(appShell.contains("LocalAIQuotaThemeColors provides themeColors"))
        assertTrue(controller.contains("markNotificationPermissionRequested"))
    }

    @Test
    fun liveMonitoringCopyExplainsNotificationPermission() {
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(english.contains("Status bar notification"))
        assertTrue(korean.contains("상태 표시줄 알림"))
        assertTrue(!korean.contains("상태 표시줄 게이지"))
        assertTrue(!english.contains("about every 60 seconds"))
        assertTrue(!korean.contains("약 60초마다"))
        assertTrue(english.contains("notification shade"))
        assertTrue(korean.contains("AI 남은 사용량을 알림창을 통해 확인 가능합니다."))
        assertTrue(english.contains("Turn on live refresh?"))
        assertTrue(korean.contains("라이브 갱신을 켤까요?"))
        assertTrue(korean.contains("라이브 모니터링을 이용하기 위해서는 알림 권한이 필요합니다.\\n라이브 모니터링을 위해 알림 권한을 허용해주세요."))
        assertTrue(korean.contains("<string name=\"live_refresh_prompt_enable\">알림 권한 허용</string>"))
    }

    @Test
    fun firstEntryLiveRefreshPromptRecommendsBatteryOptimizationExemption() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(manifest.contains("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertTrue(appShell.contains("PowerManager"))
        assertTrue(appShell.contains("isIgnoringBatteryOptimizations"))
        assertTrue(appShell.contains("Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
        assertTrue(appShell.contains("Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS"))
        assertTrue(appShell.contains("onOpenBatteryOptimizationSettings"))
        assertTrue(appShell.contains("live_refresh_prompt_battery_title"))
        assertTrue(appShell.contains("live_refresh_prompt_open_battery_settings"))
        assertTrue(english.contains("Recommended for 1-minute live refresh"))
        assertTrue(korean.contains("1분 주기 라이브 갱신 안정성을 위해 권장"))
    }

    @Test
    fun providerDetailDoesNotExposeScreenRefreshButton() {
        val providerDetail = File("src/main/java/com/aiquota/mobile/ui/provider/ProviderDetailScreen.kt").readText()
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()

        assertTrue(!providerDetail.contains("RefreshIconButton"))
        assertTrue(!providerDetail.contains("onRefresh:"))
        assertTrue(!appShell.contains("onRefresh = { refreshProvider"))
    }
}
