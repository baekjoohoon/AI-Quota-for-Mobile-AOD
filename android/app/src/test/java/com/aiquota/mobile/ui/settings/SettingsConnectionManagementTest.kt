package com.aiquota.mobile.ui.settings

import com.aiquota.mobile.local.ProviderConnectionState
import com.aiquota.mobile.local.ProviderId
import com.aiquota.mobile.local.ProviderRefreshState
import com.aiquota.mobile.local.ProviderUsageSnapshot
import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsConnectionManagementTest {
    @Test
    fun settingsShowsThemeBeforeConnectionManagement() {
        val source = File("src/main/java/com/aiquota/mobile/ui/settings/SettingsPanel.kt").readText()
        val content = source.substringAfter("verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacingDp.dp)")
            .substringBefore("@Composable\nprivate fun NotificationSettingsSection")

        assertTrue(content.indexOf("ThemeSettingsSection") < content.indexOf("ConnectionManagementSection"))
        assertFalse(content.contains("HiddenProvidersSection"))
    }

    @Test
    fun connectionManagementUsesProviderOrderAndKeepsAllProviders() {
        val order = listOf(
            ProviderId.CURSOR,
            ProviderId.ANTIGRAVITY,
            ProviderId.CLAUDE
        )

        assertEquals(
            listOf(
                ProviderId.CURSOR,
                ProviderId.ANTIGRAVITY,
                ProviderId.CLAUDE,
                ProviderId.CODEX,
                ProviderId.GLM,
                ProviderId.OPENCODE,
                ProviderId.GEMINI,
                ProviderId.COPILOT
            ),
            settingsConnectionProviderOrder(order)
        )
    }

    @Test
    fun connectionManagementDisconnectsOnlyKnownProviderSessions() {
        assertTrue(
            settingsConnectionDisconnectable(
                ProviderUsageSnapshot.connectedWithoutUsage(ProviderId.CODEX, "Usage pending")
            )
        )
        assertTrue(
            settingsConnectionDisconnectable(
                ProviderUsageSnapshot(
                    providerId = ProviderId.CLAUDE,
                    connectionState = ProviderConnectionState.INTERACTIVE_AUTH_REQUIRED
                )
            )
        )
        assertFalse(settingsConnectionDisconnectable(null))
        assertFalse(settingsConnectionDisconnectable(ProviderUsageSnapshot.disconnected(ProviderId.GEMINI)))
    }

    @Test
    fun connectionManagementUsesConnectActionForDisconnectedProviders() {
        assertEquals(
            SettingsConnectionAction.CONNECT,
            settingsConnectionAction(ProviderUsageSnapshot.disconnected(ProviderId.GEMINI))
        )
        assertEquals(
            SettingsConnectionAction.CONNECT,
            settingsConnectionAction(ProviderId.CODEX, null)
        )
        assertEquals(
            SettingsConnectionAction.DISCONNECT,
            settingsConnectionAction(
                ProviderUsageSnapshot.connectedWithoutUsage(ProviderId.CODEX, "Usage pending")
            )
        )
        assertEquals(
            SettingsConnectionAction.CONNECT,
            settingsConnectionAction(null)
        )
    }

    @Test
    fun connectionManagementUsesDifferentTextColorsForConnectAndDisconnectActions() {
        assertEquals(
            Color.Blue,
            settingsConnectionActionTextColor(
                action = SettingsConnectionAction.CONNECT,
                connectColor = Color.Blue,
                disconnectColor = Color.Red
            )
        )
        assertEquals(
            Color.Red,
            settingsConnectionActionTextColor(
                action = SettingsConnectionAction.DISCONNECT,
                connectColor = Color.Blue,
                disconnectColor = Color.Red
            )
        )
        assertEquals(
            Color.Gray,
            settingsConnectionActionTextColor(
                action = SettingsConnectionAction.NONE,
                connectColor = Color.Blue,
                disconnectColor = Color.Red,
                disabledColor = Color.Gray
            )
        )
    }

    @Test
    fun connectionManagementUsesMutedTextColorForDisconnectButtons() {
        val source = File("src/main/java/com/aiquota/mobile/ui/settings/SettingsPanel.kt").readText()
        val managementSection = source.substringAfter("private fun ConnectionManagementSection")
            .substringBefore("@Composable\nprivate fun ThemeSettingsSection")

        assertTrue(managementSection.contains("connectColor = colors.primary"))
        assertTrue(managementSection.contains("disconnectColor = colors.textMuted"))
    }

    @Test
    fun appShellPassesConnectionManagementCallbacks() {
        val source = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val settingsCall = source.substringAfter("AppRoute.Settings -> SettingsPanel(")
            .substringBefore("modifier = Modifier.fillMaxSize()")

        assertTrue(settingsCall.contains("liveRefreshState = liveRefreshState"))
        assertTrue(settingsCall.contains("providerOrder = providerOrder"))
        assertTrue(settingsCall.contains("snapshots = snapshots"))
        assertTrue(settingsCall.contains("onConnectProvider = ::connectProvider"))
        assertTrue(settingsCall.contains("onDisconnectProvider = ::disconnectProvider"))
        assertTrue(settingsCall.contains("onDisconnectAllProviders = ::disconnectAllProviders"))
    }

    @Test
    fun settingsShowsBatteryOptimizationRecommendationForLiveRefresh() {
        val source = File("src/main/java/com/aiquota/mobile/ui/settings/SettingsPanel.kt").readText()
        val appShell = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()
        val settingsCall = appShell.substringAfter("AppRoute.Settings -> SettingsPanel(")
            .substringBefore("modifier = Modifier.fillMaxSize()")
        val notificationSection = source.substringAfter("private fun NotificationSettingsSection")
            .substringBefore("@Composable\nprivate fun ConnectionManagementSection")
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(source.contains("batteryOptimizationExempt: Boolean"))
        assertTrue(source.contains("onOpenBatteryOptimizationSettings: () -> Unit"))
        assertTrue(settingsCall.contains("batteryOptimizationExempt = batteryOptimizationExempt"))
        assertTrue(settingsCall.contains("onOpenBatteryOptimizationSettings = ::openBatteryOptimizationSettings"))
        assertTrue(notificationSection.contains("settings_battery_optimization_title"))
        assertTrue(notificationSection.contains("settings_open_battery_optimization_settings"))
        assertTrue(notificationSection.contains("if (!batteryOptimizationExempt)"))
        assertTrue(
            notificationSection.indexOf("settings_open_battery_optimization_settings") <
                notificationSection.indexOf("settings_battery_optimization_recommended")
        )
        assertTrue(english.contains("Battery optimization"))
        assertTrue(korean.contains("배터리 최적화"))
        assertTrue(korean.contains("라이브 갱신 안정성을 위해 배터리 최적화 옵션을 제외로 설정해주세요."))
        assertFalse(korean.contains("Android가 백그라운드 라이브 모니터링을 중단할 가능성을 줄이려면"))
    }

    @Test
    fun providerDetailDoesNotHideConnectionActionWhileBusy() {
        val source = File("src/main/java/com/aiquota/mobile/ui/provider/ProviderDetailScreen.kt").readText()
        val actionAssignment = source.substringAfter("private fun ProviderSummaryBlock")
            .substringAfter("val colors = AIQuotaTheme.colors")
            .substringBefore("Row(")

        assertFalse(actionAssignment.contains("if (isBusy)"))
        assertTrue(actionAssignment.contains("val connectionAction = snapshot.primaryConnectionAction()"))
    }

    @Test
    fun connectionManagementKeepsActionVisibleWhileRefreshing() {
        assertEquals(
            SettingsConnectionAction.CONNECT,
            settingsConnectionAction(
                ProviderUsageSnapshot.disconnected(ProviderId.GEMINI).copy(
                    refreshState = ProviderRefreshState.REFRESHING
                )
            )
        )
        assertEquals(
            SettingsConnectionAction.DISCONNECT,
            settingsConnectionAction(
                ProviderUsageSnapshot(
                    providerId = ProviderId.CODEX,
                    connectionState = ProviderConnectionState.COLLECTING,
                    refreshState = ProviderRefreshState.REFRESHING
                )
            )
        )
    }

    @Test
    fun statusBarAlarmSectionShowsLiveRefreshStateAndCopy() {
        val source = File("src/main/java/com/aiquota/mobile/ui/settings/SettingsPanel.kt").readText()
        val notificationSection = source.substringAfter("private fun NotificationSettingsSection")
            .substringBefore("@Composable\nprivate fun ConnectionManagementSection")
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(source.contains("liveRefreshState: SettingsLiveRefreshState"))
        assertTrue(notificationSection.contains("settings_live_refresh_status"))
        assertTrue(notificationSection.contains("settingsLiveRefreshStateLabel"))
        assertTrue(english.contains("Remaining AI usage is available from the notification shade."))
        assertTrue(korean.contains("AI 남은 사용량을 알림창을 통해 확인 가능합니다."))
        assertTrue(korean.contains("라이브 갱신 상태: %1\$s"))
        assertTrue(korean.contains("진행 중"))
        assertTrue(korean.contains("중단"))
        assertFalse(korean.contains("활성 provider 게이지를 조용한 고정 알림으로 표시합니다."))
    }

    @Test
    fun liveRefreshStateIsRunningOnlyWhenEnabledPermissionGrantedAndHeartbeatFresh() {
        assertEquals(
            SettingsLiveRefreshState.RUNNING,
            settingsLiveRefreshState(
                notificationEnabled = true,
                canPostNotifications = true,
                heartbeatStale = false
            )
        )
        assertEquals(
            SettingsLiveRefreshState.STOPPED,
            settingsLiveRefreshState(
                notificationEnabled = false,
                canPostNotifications = true,
                heartbeatStale = false
            )
        )
        assertEquals(
            SettingsLiveRefreshState.STOPPED,
            settingsLiveRefreshState(
                notificationEnabled = true,
                canPostNotifications = false,
                heartbeatStale = false
            )
        )
        assertEquals(
            SettingsLiveRefreshState.STOPPED,
            settingsLiveRefreshState(
                notificationEnabled = true,
                canPostNotifications = true,
                heartbeatStale = true
            )
        )
    }

    @Test
    fun connectionManagementStringsReplaceHiddenProviderCopy() {
        val english = File("src/main/res/values/strings.xml").readText()
        val korean = File("src/main/res/values-ko/strings.xml").readText()

        assertTrue(english.contains("Connection management"))
        assertTrue(korean.contains("연결 관리"))
        assertTrue(korean.contains("전체 연결 해제"))
        assertFalse(english.contains("No connected providers."))
        assertFalse(korean.contains("연결된 provider가 없습니다."))
        assertFalse(korean.contains("숨긴 provider"))
    }
}
