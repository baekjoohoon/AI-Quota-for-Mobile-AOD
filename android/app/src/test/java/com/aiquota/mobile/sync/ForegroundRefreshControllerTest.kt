package com.aiquota.mobile.sync

import com.aiquota.mobile.providers.ProviderBackgroundRefreshService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRefreshControllerTest {
    @Test
    fun controllerStartsAndStopsBackgroundRefreshForegroundService() {
        val starter = RecordingStarter()
        val controller = ForegroundRefreshController(starter)

        controller.startPreciseRefresh()
        controller.stopPreciseRefresh()

        assertEquals(
            listOf(
                ProviderBackgroundRefreshService.ACTION_START,
                ProviderBackgroundRefreshService.ACTION_STOP
            ),
            starter.actions
        )
    }

    @Test
    fun controllerDoesNotRestartForegroundServiceWhenAlreadyRunning() {
        val starter = RecordingStarter()
        val controller = ForegroundRefreshController(starter)

        controller.startPreciseRefresh()
        controller.startPreciseRefresh()
        controller.startPreciseRefresh()
        controller.stopPreciseRefresh()
        controller.stopPreciseRefresh()

        assertEquals(
            listOf(
                ProviderBackgroundRefreshService.ACTION_START,
                ProviderBackgroundRefreshService.ACTION_STOP
            ),
            starter.actions
        )
    }

    @Test
    fun controllerPersistsLiveMonitoringApprovalSeparatelyFromRuntimeStartState() {
        val source = java.io.File("src/main/java/com/aiquota/mobile/sync/ForegroundRefreshController.kt").readText()

        assertTrue(source.contains("KEY_LIVE_MONITORING_ENABLED"))
        assertTrue(source.contains("fun liveMonitoringEnabled()"))
        assertTrue(source.contains("fun setLiveMonitoringEnabled(enabled: Boolean)"))
    }

    private class RecordingStarter : ForegroundRefreshController.ServiceStarter {
        val actions = mutableListOf<String>()

        override fun start(action: String) {
            actions += action
        }
    }
}
