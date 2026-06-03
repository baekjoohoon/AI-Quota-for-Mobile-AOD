package com.aiquota.mobile.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProviderWidgetLabelWrapTest {
    @Test
    fun providerWidgetKeepsUsageLabelsSingleLineSoGaugeAlwaysFits() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt").readText()

        assertTrue(source.contains("maxLines = 1"))
        assertTrue(!source.contains("providerWidgetLineLabelMaxLines"))
    }

    @Test
    fun providerWidgetReadsLauncherOptionHeightForResizeState() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt").readText()

        assertTrue(source.contains("getAppWidgetOptions(appWidgetId)"))
        assertTrue(source.contains("hostOptionHeightDp"))
    }

    @Test
    fun providerWidgetContentCentersAfterMeasuredHeightIncludingLauncherOptions() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt").readText()
        val contentBlock = source
            .substringAfter("private fun ProviderWidgetContent(")
            .substringBefore("private fun ProviderHeader(")

        assertTrue(source.contains("hostOptionHeightDp"))
        assertTrue(contentBlock.contains("val centerContent = providerWidgetUsesCenteredContent(measuredHeightDp)"))
        assertTrue(contentBlock.contains("Spacer(modifier = GlanceModifier.defaultWeight())"))
    }

    @Test
    fun providerWidgetDisplayNameStaysSingleLine() {
        val source = File("src/main/java/com/aiquota/mobile/widget/ProviderUsageGlanceWidget.kt").readText()
        val displayNameTextBlock = source.substringAfter("text = payload.displayName").substringBefore("if (spec.cellHeight")

        assertTrue(displayNameTextBlock.contains("maxLines = 1"))
    }
}
