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
    fun liveMonitoringCopyMentionsSixtySecondsAndPinnedNotification() {
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(english.contains("Status bar notification"))
        assertTrue(korean.contains("상태 표시줄 알람"))
        assertTrue(!korean.contains("상태 표시줄 게이지"))
        assertTrue(english.contains("about every 60 seconds"))
        assertTrue(english.contains("notification shade"))
        assertTrue(korean.contains("AI 남은 사용량을 알림창을 통해 확인 가능합니다."))
        assertTrue(korean.contains("약 60초마다"))
        assertTrue(english.contains("Turn on live refresh?"))
        assertTrue(korean.contains("라이브 갱신을 켤까요?"))
        assertTrue(korean.contains("AI Quota는 상태 표시줄 알람을 활성화해야 지속적으로 사용량 데이터를 수집할 수 있습니다. 자동으로 사용량을 수집하기 위해 알림 권한을 허용해주세요."))
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
