package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedWidgetRequesterTest {
    @Test
    fun inAppWidgetRequesterUsesLauncherPinApiForAllWidgetTypes() {
        val source = File("src/main/java/com/aiquota/mobile/widget/PinnedWidgetRequester.kt").readText()

        assertTrue(source.contains("isRequestPinAppWidgetSupported"))
        assertTrue(source.contains("requestPinAppWidget"))
        assertTrue(source.contains("ProviderUsageWidgetProvider::class.java"))
        assertTrue(source.contains("AIQuotaUnifiedGlanceWidgetReceiver::class.java"))
        assertTrue(source.contains("AIQuotaCircularWidgetProvider::class.java"))
    }

    @Test
    fun providerPinSuccessReusesProviderConfigurationFlow() {
        val requesterSource = File("src/main/java/com/aiquota/mobile/widget/PinnedWidgetRequester.kt").readText()
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureActivity.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val finishBlock = activitySource.substringAfter("private fun finishWithProvider").substringBefore("private fun Int.dp")

        assertTrue(!manifest.contains(".widget.ProviderWidgetPinReceiver"))
        assertTrue(requesterSource.contains("putExtra(EXTRA_PINNED_PROVIDER_ID, providerId.storageId)"))
        assertTrue(activitySource.contains("AppWidgetManager.EXTRA_APPWIDGET_ID"))
        assertTrue(activitySource.contains("preselectedProviderFromIntent()"))
        assertTrue(finishBlock.contains("saveProviderWidgetSelection(appWidgetId, providerId)"))
        assertTrue(finishBlock.contains("ProviderWidgetImmediateRenderer.render(this, appWidgetId, providerId)"))
        assertTrue(finishBlock.contains("ProviderWidgetPostConfigureUpdater.schedule(applicationContext, appWidgetId, providerId)"))
        assertTrue(finishBlock.contains("ProviderWidgetConfigureRefreshRequester.schedule(applicationContext, appWidgetId, providerId)"))
    }

    @Test
    fun inAppPinSuccessUsesConfigureActivitiesLikeLauncherWidgetFlow() {
        val source = File("src/main/java/com/aiquota/mobile/widget/PinnedWidgetRequester.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(source.contains("ProviderWidgetConfigureActivity::class.java"))
        assertTrue(source.contains("DashboardWidgetConfigureActivity::class.java"))
        assertTrue(source.contains("PendingIntent.getActivity"))
        assertTrue(!source.contains("ProviderWidgetPinReceiver"))
        assertTrue(!manifest.contains(".widget.ProviderWidgetPinReceiver"))
    }

    @Test
    fun providerDetailAndDashboardExposeInAppWidgetAddActions() {
        val providerSource = File("src/main/java/com/aiquota/mobile/ui/provider/ProviderDetailScreen.kt").readText()
        val dashboardSource = File("src/main/java/com/aiquota/mobile/ui/dashboard/UnifiedDashboardScreen.kt").readText()
        val appShellSource = File("src/main/java/com/aiquota/mobile/ui/AIQuotaAppShell.kt").readText()

        assertTrue(providerSource.contains("onAddWidget: () -> Unit"))
        assertTrue(providerSource.contains("R.string.provider_add_widget"))
        assertTrue(dashboardSource.contains("onAddWidget: () -> Unit"))
        assertTrue(dashboardSource.contains("R.string.dashboard_add_widget"))
        assertTrue(appShellSource.contains("DashboardWidgetPickerDialog"))
        assertTrue(appShellSource.contains("PinnedWidgetRequester.requestProviderWidget"))
        assertTrue(appShellSource.contains("PinnedWidgetRequester.requestDashboardWidget"))
    }
}
