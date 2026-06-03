package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderWidgetConfigureActivityTest {
    @Test
    fun providerWidgetCanBeReconfiguredFromLauncherSettings() {
        val providerWidgetXml = File("src/main/res/xml/ai_quota_widget_provider.xml").readText()

        assertTrue(
            "Provider widget should expose the launcher settings action after long-press.",
            providerWidgetXml.contains("android:configure=\"com.aiquota.mobile.widget.ProviderWidgetConfigureActivity\"") &&
                providerWidgetXml.contains("android:widgetFeatures=\"reconfigurable\"") &&
                !providerWidgetXml.contains("configuration_optional")
        )
    }

    @Test
    fun providerWidgetConfigurationUsesAsyncApplyAndRendersSelectedProviderDirectly() {
        val repositorySource = File("src/main/java/com/aiquota/mobile/local/ProviderPreferencesRepository.kt").readText()
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureActivity.kt").readText()
        val saveSelectionBody = repositorySource
            .substringAfter("fun saveProviderWidgetSelection")
            .substringBefore("fun clearProviderWidgetSelection")
        val finishBlock = activitySource.substringAfter("private fun finishWithProvider").substringBefore("private fun Int.dp")

        assertTrue(
            "Provider widget selection should not block UI; immediate renderer and delayed refresh receive the selected provider directly.",
            saveSelectionBody.contains(".apply()") &&
                !saveSelectionBody.contains(".commit()") &&
                finishBlock.contains("ProviderWidgetImmediateRenderer.render(this, appWidgetId, providerId)") &&
                finishBlock.contains("ProviderWidgetPostConfigureUpdater.schedule(applicationContext, appWidgetId, providerId)") &&
                finishBlock.contains("ProviderWidgetConfigureRefreshRequester.schedule(applicationContext, appWidgetId, providerId)")
        )
    }

    @Test
    fun providerWidgetConfigurationAvoidsUpdateAllWhenSpecificGlanceIdIsNotReady() {
        val updaterSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetPostConfigureUpdater.kt").readText()

        assertTrue(
            "Provider widget post-configure retries must not call updateAll because it can redraw other provider widgets with a fallback provider.",
            !updaterSource.contains(".updateAll(appContext)")
        )
    }

    @Test
    fun providerWidgetConfigurationSchedulesRefreshAfterReturningResult() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureActivity.kt").readText()
        val finishBlock = activitySource.substringAfter("private fun finishWithProvider").substringBefore("private fun Int.dp")

        assertTrue(
            "Provider widget selection should return to the launcher immediately and let a post-configure updater refresh the new widget.",
            finishBlock.contains("setResult(Activity.RESULT_OK, resultValue)") &&
                finishBlock.contains("ProviderWidgetPostConfigureUpdater.schedule(applicationContext, appWidgetId, providerId)") &&
                finishBlock.contains("finish()") &&
                !finishBlock.contains("lifecycleScope.launch")
        )
    }

    @Test
    fun providerWidgetConfigurationReturnsHomeOnlyForInAppPinCallback() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureActivity.kt").readText()
        val finishBlock = activitySource.substringAfter("private fun finishWithProvider").substringBefore("private fun Int.dp")

        assertTrue(finishBlock.contains("returnHomeIfLaunchedFromPinCallback()"))
        assertTrue(activitySource.contains("private fun returnHomeIfLaunchedFromPinCallback()"))
        assertTrue(activitySource.contains("intent?.action == null"))
        assertTrue(activitySource.contains("Intent(Intent.ACTION_MAIN)"))
        assertTrue(activitySource.contains("Intent.CATEGORY_HOME"))
        assertTrue(activitySource.contains("Intent.FLAG_ACTIVITY_NEW_TASK"))
    }

    @Test
    fun providerWidgetConfigurationSchedulesDelayedRefreshAfterSelectedProviderCacheIsShown() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureActivity.kt").readText()
        val requesterSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureRefreshRequester.kt").readText()
        val finishBlock = activitySource.substringAfter("private fun finishWithProvider").substringBefore("private fun Int.dp")

        assertTrue(
            "Adding a provider widget should show the selected provider from cache first, then schedule a delayed provider-specific refresh.",
            finishBlock.indexOf("ProviderWidgetImmediateRenderer.render(this, appWidgetId, providerId)") in 0 until
                finishBlock.indexOf("ProviderWidgetConfigureRefreshRequester.schedule(applicationContext, appWidgetId, providerId)") &&
                requesterSource.contains("PROVIDER_WIDGET_CONFIGURE_REFRESH_DELAY_MS = 5_000L") &&
                requesterSource.contains("delay(PROVIDER_WIDGET_CONFIGURE_REFRESH_DELAY_MS)") &&
                requesterSource.contains("ProviderBackgroundRefreshService.ACTION_REFRESH") &&
                requesterSource.contains("WidgetRefreshActions.EXTRA_PROVIDER_ID") &&
                requesterSource.contains("providerId.storageId")
        )
    }

    @Test
    fun providerWidgetConfigureRefreshDoesNotRunGlobalSurfaceRefreshBeforeSelectionStabilizes() {
        val requesterSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureRefreshRequester.kt").readText()
        val scheduleBlock = requesterSource.substringAfter("fun schedule").substringBefore("\n    }\n}")

        assertTrue(
            "Delayed configure refresh must not call UsageSurfaceRefresher.refresh because ProviderUsageGlanceWidget.updateAll can redraw the new widget with fallback Claude before the Glance id mapping is stable.",
            !scheduleBlock.contains("UsageSurfaceRefresher.refresh") &&
                scheduleBlock.contains("ProviderWidgetImmediateRenderer.render(appContext, appWidgetId, providerId)") &&
                scheduleBlock.contains("ProviderBackgroundRefreshService.ACTION_REFRESH") &&
                scheduleBlock.contains("WidgetRefreshActions.EXTRA_PROVIDER_ID")
        )
    }

    @Test
    fun providerWidgetConfigurationPushesSelectedProviderRemoteViewsBeforeReturningResult() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureActivity.kt").readText()
        val finishBlock = activitySource.substringAfter("private fun finishWithProvider").substringBefore("private fun Int.dp")

        assertTrue(
            "Selected provider should be rendered through AppWidgetManager immediately, before the launcher waits for Glance.",
            finishBlock.indexOf("ProviderWidgetImmediateRenderer.render(this, appWidgetId, providerId)") in 0 until
                finishBlock.indexOf("setResult(Activity.RESULT_OK, resultValue)")
        )
    }

    @Test
    fun providerWidgetImmediateRendererUsesSelectedProviderPayload() {
        val rendererSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetImmediateRenderer.kt").readText()
        val layoutSource = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()

        assertTrue(rendererSource.contains("providerWidgetPayload("))
        assertTrue(rendererSource.contains("providerId = providerId.storageId"))
        assertTrue(rendererSource.contains("responsiveViews(context, appWidgetId, providerId)"))
        assertTrue(layoutSource.contains("@+id/provider_immediate_name"))
        assertTrue(layoutSource.contains("@+id/provider_immediate_progress"))
        assertTrue(layoutSource.contains("@+id/provider_immediate_reset"))
    }

    @Test
    fun providerWidgetImmediateLayoutDoesNotRenderProviderStatusText() {
        val rendererSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetImmediateRenderer.kt").readText()
        val layoutSource = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()

        assertTrue(
            "RemoteViews provider widget should not render cached provider status text because transient states such as collecting usage can clip the selected provider header.",
            rendererSource.contains("provider_immediate_status, View.GONE") &&
                !rendererSource.contains("providerWidgetStatusLabel(payload.status)")
        )
        assertTrue(
            "Immediate provider placeholder should use the same 2x1 gauge thickness as the Glance provider widget.",
            layoutSource.contains("android:layout_height=\"8dp\"") &&
                layoutSource.contains("android:progressDrawable=\"@drawable/widget_provider_progress\"")
        )
    }

    @Test
    fun providerWidgetPostConfigureUpdaterRetriesUntilLauncherCreatesGlanceId() {
        assertTrue(
            "Post-configure refresh should retry quickly because the launcher may attach the new widget after the configure Activity finishes.",
            providerWidgetPostConfigureRetryDelaysMs().contentEquals(longArrayOf(0L, 250L, 750L, 1_500L, 3_000L, 4_500L))
        )
    }

    @Test
    fun providerWidgetPostConfigureUpdaterReissuesSelectedProviderRemoteViewsDuringRetries() {
        val updaterSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetPostConfigureUpdater.kt").readText()
        val scheduleBlock = updaterSource.substringAfter("fun schedule").substringBefore("\n    }\n}")

        assertTrue(
            "Post-configure retry must keep pushing selected-provider RemoteViews after the launcher attaches the host view.",
            scheduleBlock.contains("providerId: ProviderId") &&
                scheduleBlock.contains("ProviderWidgetImmediateRenderer.render(appContext, appWidgetId, providerId)")
        )
    }

    @Test
    fun providerWidgetPostConfigureUpdaterDoesNotLetGlanceOverwriteImmediateSelection() {
        val updaterSource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetPostConfigureUpdater.kt").readText()
        val retryBlock = updaterSource
            .substringAfter("providerWidgetPostConfigureRetryDelaysMs().forEach")
            .substringBefore("\n            }")

        assertTrue(
            "Post-configure retries should only reissue selected-provider RemoteViews; early Glance updates can arrive late and overwrite the selected provider with fallback content.",
            retryBlock.contains("ProviderWidgetImmediateRenderer.render(appContext, appWidgetId, providerId)") &&
                !updaterSource.contains("GlanceAppWidgetManager") &&
                !updaterSource.contains("ProviderUsageGlanceWidget()") &&
                !updaterSource.contains("widget.update")
        )
    }

    @Test
    fun providerWidgetConfigurationUsesThemedIconRowsInsteadOfPlainButtons() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureActivity.kt").readText()

        assertTrue(
            "Provider selection should use the active app theme.",
            activitySource.contains("widgetConfigureStyle") &&
                activitySource.contains("ThemePreferencesRepository")
        )
        assertTrue(
            "Provider selection rows should show provider icons with names.",
            activitySource.contains("providerIconRes(providerId)")
        )
        assertTrue(
            "Provider selection should not render providers as plain text-only buttons.",
            activitySource.contains("providerSelectionRow") &&
                !activitySource.contains("Button(this).apply")
        )
    }

    @Test
    fun providerWidgetConfigurationCentersContentVerticallyWhenViewportHasExtraSpace() {
        val activitySource = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetConfigureActivity.kt").readText()
        val rootBlock = activitySource.substringAfter("val root = LinearLayout(this).apply").substringBefore("root.addView(")
        val scrollBlock = activitySource.substringAfter("ScrollView(this).apply").substringAfter("addView(")

        assertTrue(
            "Provider selection content should be vertically centered when the screen is taller than the list.",
            rootBlock.contains("gravity = Gravity.CENTER_VERTICAL")
        )
        assertTrue(
            "Provider selection root should fill the ScrollView viewport so vertical centering has real space to use.",
            activitySource.contains("isFillViewport = true") &&
                scrollBlock.contains("ViewGroup.LayoutParams.MATCH_PARENT,\n                        ViewGroup.LayoutParams.MATCH_PARENT")
        )
    }
}
