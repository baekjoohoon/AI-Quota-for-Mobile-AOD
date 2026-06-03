package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUsageWidgetProviderTest {
    @Test
    fun providerWidgetUsesRemoteViewsAppWidgetProviderInsteadOfGlanceReceiver() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageWidgetProvider.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(source.contains("class ProviderUsageWidgetProvider : AppWidgetProvider()"))
        assertTrue(manifest.contains("""android:name=".widget.ProviderUsageWidgetProvider""""))
        assertFalse(manifest.contains("""android:name=".widget.ProviderUsageGlanceWidgetReceiver""""))
    }

    @Test
    fun providerWidgetRerendersSelectedProviderWhenLauncherSizeChanges() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageWidgetProvider.kt").readText()

        assertTrue(source.contains("override fun onAppWidgetOptionsChanged"))
        assertTrue(source.contains("updateWidget(context, appWidgetId)"))
        assertTrue(source.contains("ProviderWidgetImmediateRenderer.render(context, appWidgetId, providerId)"))
    }

    @Test
    fun providerRemoteViewsRendererSupportsSizeMappedRemoteViewsForExpandedRows() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetImmediateRenderer.kt").readText()
        val layout = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()

        assertTrue(source.contains("AppWidgetManager.OPTION_APPWIDGET_SIZES"))
        assertTrue(source.contains("RemoteViews(sizeMappedViews)"))
        assertTrue(source.contains("lineLimit = spec.maxLineCount"))
        assertTrue(layout.contains("@+id/provider_immediate_line_2"))
        assertTrue(layout.contains("@+id/provider_immediate_line_3"))
        assertTrue(layout.contains("@+id/provider_immediate_line_4"))
        assertTrue(layout.contains("@+id/provider_immediate_line_5"))
    }

    @Test
    fun providerRemoteViewsRowsKeepOnlyOneDpGapBeforeRemainingText() {
        val layout = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()

        assertTrue(layout.split("""android:layout_marginStart="1dp"""").size - 1 >= 5)
        assertFalse(layout.contains("""android:layout_marginStart="3dp""""))
    }

    @Test
    fun providerRemoteViewsAppliesResponsiveGaugeHeightFromHostSize() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetImmediateRenderer.kt").readText()
        val layout = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()
        val drawable = File("src/main/res/drawable/widget_provider_progress.xml").readText()

        assertTrue(source.contains("widgetHeightDp = heightDp"))
        assertTrue(source.contains("applyProgressHeight(views, ids.progressId, spec.gaugeHeightDp)"))
        assertTrue(source.contains("applyLineLayout("))
        assertTrue(source.contains("setViewLayoutMargin("))
        assertTrue(source.contains("setViewLayoutHeight(progressId, gaugeHeightDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)"))
        assertTrue(layout.contains("""android:progressDrawable="@drawable/widget_provider_progress""""))
        assertTrue(drawable.contains("""android:id="@android:id/background""""))
        assertTrue(drawable.contains("""android:id="@android:id/progress""""))
    }

    @Test
    fun providerRemoteViewsRootCentersContentVertically() {
        val layout = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()
        val rootBlock = layout.substringBefore("""android:paddingBottom="10dp">""")

        assertTrue(rootBlock.contains("""android:gravity="center_vertical""""))
    }

    @Test
    fun providerRemoteViewsFourthUsageRowUsesStandardTopGap() {
        val layout = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()
        val fourthRowBlock = layout
            .substringAfter("""android:id="@+id/provider_immediate_line_4"""")
            .substringBefore("""android:id="@+id/provider_immediate_label_4"""")

        assertTrue(fourthRowBlock.contains("""android:layout_marginTop="3dp""""))
    }

    @Test
    fun providerRemoteViewsFifthUsageRowUsesStandardTopGap() {
        val layout = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()
        val fifthRowBlock = layout
            .substringAfter("""android:id="@+id/provider_immediate_line_5"""")
            .substringBefore("""android:id="@+id/provider_immediate_label_5"""")

        assertTrue(fifthRowBlock.contains("""android:layout_marginTop="3dp""""))
    }

    @Test
    fun providerRemoteViewsLayoutUsesTenDpVerticalSafeZone() {
        val layout = File("src/main/res/layout/ai_quota_widget_provider_immediate.xml").readText()

        assertTrue(layout.contains("""android:paddingTop="10dp""""))
        assertTrue(layout.contains("""android:paddingBottom="10dp""""))
    }

    @Test
    fun providerWidgetProviderCanResizeToFourCellsTall() {
        val providerXml = File("src/main/res/xml/ai_quota_widget_provider.xml").readText()

        assertTrue(providerXml.contains("""android:maxResizeHeight="320dp""""))
    }

    @Test
    fun providerWidgetDefaultsToTwoByTwoButCanShrinkToTwoByOne() {
        val providerXml = File("src/main/res/xml/ai_quota_widget_provider.xml").readText()

        assertTrue(providerXml.contains("""android:targetCellWidth="2""""))
        assertTrue(providerXml.contains("""android:targetCellHeight="2""""))
        assertTrue(providerXml.contains("""android:minResizeWidth="110dp""""))
        assertTrue(providerXml.contains("""android:minResizeHeight="40dp""""))
    }

    @Test
    fun providerRemoteViewsRendererReservesResetCaptionSpaceInExpandedRows() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderWidgetImmediateRenderer.kt").readText()

        assertTrue(source.contains("setViewVisibility(ids.resetId, View.INVISIBLE)"))
        assertTrue(source.contains("setTextViewText(ids.resetId, RESET_CAPTION_PLACEHOLDER)"))
    }
}
