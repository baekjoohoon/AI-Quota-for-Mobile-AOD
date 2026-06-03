package com.aiquota.mobile.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class WidgetPreviewLayoutTest {
    @Test
    fun widgetPickerPreviewsUseCurrentMacosGaugePalette() {
        val fill = File("src/main/res/drawable/widget_preview_bar_fill.xml").readText()
        val track = File("src/main/res/drawable/widget_preview_bar_track.xml").readText()
        val ring = File("src/main/res/drawable/widget_preview_circle_ring.xml").readText()

        assertTrue("Preview gauge fill should match the current widget accent.", fill.contains("#CC785C"))
        assertTrue("Preview gauge track should match the current widget track.", track.contains("#E8E0D2"))
        assertTrue("Preview circular ring should match the current widget accent.", ring.contains("#CC785C"))
        assertTrue("Old green preview color should not remain.", !fill.contains("#22C55E") && !ring.contains("#22C55E"))
    }

    @Test
    fun providerWidgetUsesStaticPreviewImageForLauncherCompatibility() {
        assertEquals(
            "@drawable/ai_quota_widget_preview_provider_image",
            appWidgetPreviewImage("src/main/res/xml/ai_quota_widget_provider.xml")
        )
        assertEquals(
            "",
            appWidgetPreviewLayout("src/main/res/xml/ai_quota_widget_provider.xml")
        )

        val providerPreview = File("src/main/res/layout/ai_quota_widget_preview_provider.xml")
        val providerPreviewImage = File("src/main/res/drawable-nodpi/ai_quota_widget_preview_provider_image.png")
        assertTrue("Provider widget preview layout should exist.", providerPreview.exists())
        assertTrue("Provider widget preview image should exist for launchers that do not render previewLayout.", providerPreviewImage.exists())
        assertTrue("Provider widget preview image should be a real rendered PNG, not an empty placeholder.",
            providerPreviewImage.length() > 10_000
        )
    }

    @Test
    fun dashboardWidgetPreviewShowsCurrentSnapshotStyle() {
        val text = File("src/main/res/layout/ai_quota_widget_preview_3x2.xml").readText()

        assertTrue("Dashboard preview should show the current AI Quota title and provider rows.",
            text.contains("AI Quota") &&
                text.contains("Claude") &&
                text.contains("Codex") &&
                text.contains("Gemini") &&
                text.contains("Copilot") &&
                text.contains("Antigravity") &&
                text.contains("Cursor")
        )
        assertTrue("Dashboard preview should include current provider icons and gauge bars.",
            text.contains("@drawable/ic_provider_codex") &&
                text.contains("@drawable/ic_provider_antigravity") &&
                text.contains("@drawable/ic_provider_cursor") &&
                text.contains("@drawable/widget_preview_bar_fill")
        )
    }

    private fun appWidgetPreviewLayout(path: String): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(path))
        return (document.documentElement as Element).getAttribute("android:previewLayout")
    }

    private fun appWidgetPreviewImage(path: String): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(path))
        return (document.documentElement as Element).getAttribute("android:previewImage")
    }
}
