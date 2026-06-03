package com.aiquota.mobile.providers

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSurfaceRefresherTest {
    @Test
    fun surfaceRefreshDoesNotBlockCallerWithRunBlocking() {
        val source = sourceFile("src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt")

        assertFalse(
            "Surface refresh must not block the caller while Glance widgets update.",
            source.contains("runBlocking")
        )
    }

    @Test
    fun surfaceRefreshDebouncesWidgetUpdatesOnBackgroundScope() {
        val source = sourceFile("src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt")

        assertTrue(source.contains("CoroutineScope(SupervisorJob() + Dispatchers.Default)"))
        assertTrue(source.contains("WIDGET_SURFACE_UPDATE_DEBOUNCE_MS"))
        assertTrue(source.contains("delay(WIDGET_SURFACE_UPDATE_DEBOUNCE_MS)"))
        assertTrue(source.contains("scheduleWidgetSurfaceUpdate(appContext)"))
    }

    @Test
    fun surfaceRefreshUpdatesPinnedNotificationOnlyDuringLiveMonitoring() {
        val source = sourceFile("src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt")

        assertTrue(source.contains("ForegroundRefreshController(appContext).liveMonitoringEnabled()"))
        assertTrue(source.contains("UsageLimitNotificationController.update(appContext, displayJson)"))
    }

    @Test
    fun appShellRoutesSnapshotDrivenWidgetUpdatesThroughSurfaceRefresher() {
        val source = sourceFile("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt")
        val snapshotEffect = source.substringAfter("LaunchedEffect(providerOrder, hiddenProviders, snapshots, currentTheme)")
            .substringBefore("LaunchedEffect(queuedRefreshJobs.firstOrNull()?.requestId)")

        assertTrue(snapshotEffect.contains("UsageSurfaceRefresher.refresh"))
        assertFalse(snapshotEffect.contains("AIQuotaCircularWidgetProvider.updateAll(appContext)"))
        assertFalse(snapshotEffect.contains("AIQuotaUnifiedGlanceWidget().updateAll(appContext)"))
        assertFalse(snapshotEffect.contains("ProviderUsageGlanceWidget().updateAll(appContext)"))
    }

    @Test
    fun surfaceRefreshUpdatesProviderWidgetThroughRemoteViewsProvider() {
        val source = sourceFile("src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt")

        assertTrue(source.contains("ProviderUsageWidgetProvider.updateAll(appContext)"))
        assertFalse(source.contains("ProviderUsageGlanceWidget().updateAll(appContext)"))
    }

    @Test
    fun surfaceRefreshKeepsWidgetCacheIndependentFromGlobalHiddenProviders() {
        val source = sourceFile("src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt")
        val refreshBody = source.substringAfter("fun refresh(").substringBefore("fun refreshWidgetSurfaces")

        assertTrue(refreshBody.contains("val displayJson = repository.exportDisplayOnlyCache("))
        assertTrue(refreshBody.contains("val widgetDisplayJson = repository.exportDisplayOnlyCache("))
        assertTrue(refreshBody.contains("hidden = emptySet()"))
        assertTrue(refreshBody.contains("cache.writeLocalDisplaySnapshot(widgetDisplayJson, updatedAt)"))
        assertTrue(refreshBody.contains("UsageLimitNotificationController.update(appContext, displayJson)"))
    }

    @Test
    fun surfaceRefreshLogsCircularWidgetUpdateFailures() {
        val source = sourceFile("src/main/java/com/aiquota/mobile/providers/UsageSurfaceRefresher.kt")

        assertTrue(source.contains("import android.util.Log"))
        assertTrue(source.contains(".onFailure { error ->"))
        assertTrue(source.contains("Log.w(TAG, \"Circular widget update failed.\", error)"))
    }

    private fun sourceFile(path: String): String {
        return File(path).readText()
    }
}
