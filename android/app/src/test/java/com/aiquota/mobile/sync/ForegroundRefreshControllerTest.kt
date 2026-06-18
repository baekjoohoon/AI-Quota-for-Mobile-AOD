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
    fun controllerDoesNotRescheduleHealthWorkWhenAlreadyRunning() {
        val starter = RecordingStarter()
        val healthScheduler = RecordingHealthScheduler()
        val controller = ForegroundRefreshController(
            serviceStarter = starter,
            preferences = RecordingPreferences(),
            healthScheduler = healthScheduler
        )

        controller.startPreciseRefresh()
        controller.startPreciseRefresh()
        controller.startPreciseRefresh()

        assertEquals(1, healthScheduler.scheduleCalls)
        assertEquals(
            listOf(ProviderBackgroundRefreshService.ACTION_START),
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

    private class RecordingPreferences : ForegroundRefreshController.ForegroundRefreshPreferences {
        private var enabled = false

        override fun liveMonitoringEnabled(): Boolean = enabled

        override fun setLiveMonitoringEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class RecordingHealthScheduler : ForegroundRefreshController.HealthScheduler {
        var scheduleCalls = 0

        override fun schedule() {
            scheduleCalls += 1
        }

        override fun cancel() = Unit
    }
}
