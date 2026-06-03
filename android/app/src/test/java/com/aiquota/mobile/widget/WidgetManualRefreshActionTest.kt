package com.aiquota.mobile.widget

import com.aiquota.mobile.providers.ProviderBackgroundRefreshService
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetManualRefreshActionTest {
    @Test
    fun storeCompatibleWidgetRefreshActionDispatchesInternalRefreshAction() {
        assertEquals("u.sage.widget.action.REFRESH", WidgetRefreshActions.ACTION_WIDGET_REFRESH)
        assertEquals(
            ProviderBackgroundRefreshService.ACTION_REFRESH,
            WidgetRefreshActions.internalActionForWidgetAction(WidgetRefreshActions.ACTION_WIDGET_REFRESH)
        )
        assertNull(WidgetRefreshActions.internalActionForWidgetAction("android.appwidget.action.APPWIDGET_UPDATE"))
    }

    @Test
    fun manifestRegistersWidgetRefreshReceiverAndInternalRefreshReceiver() {
        assertTrue(
            receiverActions(".widget.WidgetManualRefreshReceiver")
                .contains(WidgetRefreshActions.ACTION_WIDGET_REFRESH)
        )
        assertTrue(
            receiverActions(".providers.ProviderRefreshReceiver")
                .contains(ProviderBackgroundRefreshService.ACTION_REFRESH)
        )
    }

    @Test
    fun circularRemoteViewsWidgetHasManualRefreshButton() {
        val xml = File("src/main/res/layout/ai_quota_widget_circular.xml").readText()

        assertTrue(xml.contains("@+id/circular_refresh_button"))
        assertTrue(xml.contains("@string/widget_refresh"))
    }

    @Test
    fun providerWidgetRefreshTargetsPressedWidgetAndSelectedProvider() {
        val providerWidgetSource = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt").readText()
        val actionsSource = File("src/main/java/com/aiquota/mobile/widget/WidgetRefreshActions.kt").readText()
        val receiverSource = File("src/main/java/com/aiquota/mobile/widget/WidgetManualRefreshReceiver.kt").readText()

        assertTrue(actionsSource.contains("EXTRA_APP_WIDGET_ID"))
        assertTrue(actionsSource.contains("EXTRA_PROVIDER_ID"))
        assertTrue(
            "Provider widgets must pass their own appWidgetId and selected provider to the refresh action.",
            providerWidgetSource.contains("widgetRefreshIntent(context, appWidgetId, providerId)")
        )
        assertTrue(
            "The pressed widget should enter local refresh feedback before the service returns.",
            receiverSource.contains("WidgetRefreshFeedback.markWidgetRefreshStarted")
        )
        assertTrue(
            "The internal refresh broadcast should preserve the pressed widget and provider target.",
            receiverSource.contains("internalRefreshIntent(context, intent)")
        )
    }

    @Test
    fun manualProviderWidgetRefreshUsesManualCyclePlan() {
        val serviceSource = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(serviceSource.contains("pendingManualProviderId"))
        assertTrue(serviceSource.contains("ProviderRefreshPlan.manualCycleJobsFor(manualProviderId"))
        assertTrue(serviceSource.contains("WidgetRefreshFeedback.clearWidgetRefresh"))
    }

    @Test
    fun manualRefreshRequestedDuringActiveCycleRunsImmediatelyAfterCurrentCycle() {
        val serviceSource = File("src/main/java/com/aiquota/mobile/providers/ProviderBackgroundRefreshService.kt").readText()

        assertTrue(serviceSource.contains("hasPendingManualRefresh()"))
        assertTrue(serviceSource.contains("if (hasPendingManualRefresh()) 0L"))
    }

    @Test
    fun systemWidgetUpdatesRefreshCachedSurfacesWithoutStartingForegroundService() {
        val starterSource = File("src/main/java/com/aiquota/mobile/widget/WidgetAutoRefreshStarter.kt").readText()
        val circularSource = File("src/main/java/com/aiquota/mobile/widget/AIQuotaCircularWidgetProvider.kt").readText()
        val dashboardSource = File("src/main/java/com/aiquota/mobile/widget/AIQuotaGlanceWidget.kt").readText()
        val providerSource = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt").readText()

        assertTrue(starterSource.contains("UsageSurfaceRefresher.refresh"))
        assertTrue(starterSource.contains("LocalUsageRepository"))
        assertTrue(!starterSource.contains("ContextCompat.startForegroundService"))
        assertTrue(!starterSource.contains("ProviderBackgroundRefreshService.ACTION_REFRESH"))
        assertTrue(!starterSource.contains("AlarmManager"))
        assertTrue(!starterSource.contains("WorkManager"))
        assertTrue(circularSource.contains("WidgetAutoRefreshStarter.requestBackgroundRefresh(context)"))
        assertTrue(dashboardSource.contains("WidgetAutoRefreshStarter.requestBackgroundRefresh(context)"))
        assertTrue(providerSource.contains("WidgetAutoRefreshStarter.requestBackgroundRefresh(context)"))
        assertTrue(
            "Automatic widget updates must not start manual spinner feedback.",
            !starterSource.contains("WidgetRefreshFeedback.markWidgetRefreshStarted")
        )
    }

    @Test
    fun delayedProviderWidgetConfigureRefreshClearsSpinnerWhenForegroundStartFails() {
        val requesterSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureRefreshRequester.kt").readText()

        assertTrue(requesterSource.contains("runCatching"))
        assertTrue(requesterSource.contains("ContextCompat.startForegroundService"))
        assertTrue(requesterSource.contains("WidgetRefreshFeedback.clearWidgetRefresh"))
        assertTrue(requesterSource.contains("ProviderWidgetImmediateRenderer.render(appContext, appWidgetId, providerId)"))
        assertTrue(!requesterSource.contains("UsageSurfaceRefresher.refresh"))
    }

    private fun receiverActions(receiverName: String): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val receivers = document.getElementsByTagName("receiver")
        return (0 until receivers.length)
            .map { receivers.item(it) }
            .first { node ->
                node.attributes.getNamedItem("android:name").nodeValue == receiverName
            }
            .childNodes
            .let { children ->
                (0 until children.length).map { children.item(it) }
            }
            .filter { it.nodeName == "intent-filter" }
            .flatMap { intentFilter ->
                val actions = intentFilter.childNodes
                (0 until actions.length).map { actions.item(it) }
            }
            .filter { it.nodeName == "action" }
            .map { it.attributes.getNamedItem("android:name").nodeValue }
    }
}
